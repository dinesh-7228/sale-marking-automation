package com.countrydelight.service;

import com.countrydelight.api.PaymentApiClient;
import com.countrydelight.db.DatabaseUtil;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Orchestrates the payment.md "Recharge Once" flow:
 *
 *   API-1 wallet_screen                      → current wallet balance
 *   API-2 juspay/offers?amount={amount}      → offer_id / cashback offer
 *   API-3 paymentMethods2/{configId}         → available payment methods
 *   API-4 getSavedCardAndRecentPayments      → saved cards + recent methods (source of cashback)
 *   API-6 generateTransaction/2              → creates the transaction (order_id)
 *   API-7 transactions/{id}/status           → poll till SUCCESS
 *
 * The cashback amount shown to the user is extracted from the API-4
 * (getSavedCardAndRecentPaymentsForCustomer) response, alongside the
 * recharge amount returned from generateTransaction.
 */
@Service
public class PaymentService {

    private static final String DEFAULT_PAYMENT_METHOD = "NB-DUMMY BANK";
    private static final String DEFAULT_PAYMENT_SOURCE = "2";
    private static final String DEFAULT_CONFIG_ID = "5";
    /** autopay.md sends this as autopay_setup_source on the AutoPay setup calls. */
    private static final String AUTOPAY_SETUP_SOURCE = "menu_screen";
    /** Used when API-7 offers no mandate method; matches the recorded autopay.md run. */
    private static final String AUTOPAY_DEFAULT_PAYMENT_METHOD = "CARD";
    private static final Pattern CASHBACK_PATTERN = Pattern.compile(
            "(?:₹|Rs\\.?|INR)\\s*([0-9]+(?:[.,][0-9]+)?|[0-9]*(?:[.,][0-9]+)?)", Pattern.CASE_INSENSITIVE);

    @Autowired
    private PaymentApiClient paymentApiClient;

    @Autowired
    private DatabaseUtil dbUtil;

    /**
     * Runs the recharge-once series for the given phone + amount.
     *
     * @param phone        10-digit customer mobile number
     * @param amount       recharge amount (e.g. "16000" or "16000.0")
     * @param configId     paymentMethods2 config id (default "5"), may be null
     * @param paymentStatus optional forced status (SUCCESS / PENDING / FAILED).
     *                      The generateTransaction order can't reach SUCCESS via
     *                      curl (the Juspay completion step, API-5, needs the
     *                      SDK/merchant key), so when provided the payment_requests
     *                      row is updated to this status directly in the DB.
     * @param appliedOfferId optional offer_id already applied via offers/recharge/apply.
     *                       When null the offer is picked from API-2 (juspay/offers).
     * @param appliedCustomerOfferId optional customer_offer_id returned by the apply API.
     * @return step-by-step result incl. recharge amount + cashback amount
     */
    public Map<String, Object> rechargeOnce(String phone, String amount, String configId, String paymentStatus,
            String appliedOfferId, String appliedCustomerOfferId) throws Exception {
        if (phone == null || !phone.matches("\\d{10}")) {
            throw new IllegalArgumentException("Enter a valid 10-digit mobile number");
        }
        if (amount == null || amount.trim().isEmpty()) {
            throw new IllegalArgumentException("Recharge amount is required");
        }
        if (paymentStatus != null && !paymentStatus.trim().isEmpty()) {
            paymentStatus = paymentStatus.trim().toUpperCase();
            if (!("SUCCESS".equals(paymentStatus)
                    || "PENDING".equals(paymentStatus)
                    || "FAILED".equals(paymentStatus))) {
                throw new IllegalArgumentException("paymentStatus must be SUCCESS, PENDING or FAILED");
            }
        }

        String normalizedAmount = normalizeAmount(amount);
        String cfgId = (configId == null || configId.trim().isEmpty()) ? DEFAULT_CONFIG_ID : configId.trim();

        // Resolve the customer's auth token from the beejapuri DB (auth_token table).
        String customerToken = resolveCustomerToken(phone);
        int customerId = resolveCustomerId(phone);

        Map<String, Object> result = new HashMap<>();
        result.put("phone", phone);
        result.put("amount", normalizedAmount);
        result.put("config_id", cfgId);

        // API-1: wallet screen → current balance before recharge.
        JsonNode wallet = paymentApiClient.getWalletScreen(customerToken);
        result.put("wallet_screen", wallet);
        double walletBefore = wallet.path("wallet_balance").asDouble(0.0);
        result.put("wallet_balance_before", walletBefore);

        // API-2: look up payment offers for the amount (drives cashback offering).
        JsonNode offers = paymentApiClient.getOffers(customerToken, normalizedAmount);
        result.put("offers", offers);
        String offerId = (appliedOfferId == null || appliedOfferId.trim().isEmpty())
                ? extractOfferId(offers) : appliedOfferId.trim();
        result.put("offer_id", offerId);
        String customerOfferId = (appliedCustomerOfferId == null || appliedCustomerOfferId.trim().isEmpty())
                ? offerId : appliedCustomerOfferId.trim();
        result.put("customer_offer_id", customerOfferId);

        // API-3: available payment methods.
        JsonNode paymentMethods = paymentApiClient.getPaymentMethods(customerToken, cfgId);
        result.put("payment_methods", paymentMethods);

        // API-4: saved cards + recent methods → cashback amount source.
        JsonNode savedCards = paymentApiClient.getSavedCardAndRecentPayments(customerToken, DEFAULT_PAYMENT_SOURCE);
        result.put("saved_cards_response", savedCards);
        String cashback = extractCashback(savedCards);
        result.put("cashback", cashback);

        // Always use "NB_DUMMY" / "Dummy Bank" for Recharge Once — never other payment modes.
        String paymentMethod = resolveDummyPaymentMethod(paymentMethods, savedCards);
        result.put("payment_method_resolved", paymentMethod);

        // API-6: generate the transaction.
        JsonNode txn = paymentApiClient.generateTransaction(customerToken, normalizedAmount,
                customerOfferId, offerId, paymentMethod, DEFAULT_PAYMENT_SOURCE);
        result.put("transaction", txn);
        String orderId = txn.path("data").path("order_id").asText(
                txn.path("data").path("merchant_transaction_id").asText(""));
        result.put("order_id", orderId);

        // API-7: poll status (or apply a forced status when requested).
        Map<String, Object> status = pollTransactionStatus(customerToken, orderId);
        String finalStatus = (String) status.get("transaction_status");
        if (paymentStatus != null) {
            // Real Juspay completion isn't possible via curl; write the chosen
            // status straight into payment_requests in beejapuri_QA so multiple
            // transaction records with SUCCESS/PENDING/FAILED can be created.
            int updated = dbUtil.updatePaymentRequestStatus(orderId, paymentStatus, cashback, customerId,
                    normalizedAmount, paymentMethod);
            finalStatus = paymentStatus;
            status.put("transaction_status", paymentStatus);
            status.put("forced_status", true);
            status.put("payment_requests_rows_updated", updated);
        } else {
            status.put("forced_status", false);
        }

        // On SUCCESS also credit the wallet (wallet_balance, wallet_transactions, wallet_recharge).
        if ("SUCCESS".equalsIgnoreCase(finalStatus) || "SUCCESSFUL".equalsIgnoreCase(finalStatus)
                || "FINISHED".equalsIgnoreCase(finalStatus)) {
            Map<String, Object> walletCredit = dbUtil.applyWalletCredit(customerId, normalizedAmount, cashback,
                    offerId, paymentMethod, orderId);
            status.put("wallet_credit", walletCredit);
            // Update the before/after wallet figures returned to the UI.
            status.put("wallet_balance_before", walletCredit.getOrDefault("old_wallet_balance", walletBefore));
            status.put("updated_wallet_balance", walletCredit.get("new_wallet_balance"));
        } else {
            status.put("wallet_credit", null);
        }
        result.putAll(status);

        return result;
    }

    /**
     * Fetches the recharge/payment offers that can be applied for the customer,
     * via API-2 (juspay/offers?amount={amount}).
     *
     * @param phone  10-digit customer mobile number
     * @param amount recharge amount (used to qualify offers)
     * @return the raw offers payload plus a flattened applicable-offer summary
     */
    public Map<String, Object> getRechargeOffers(String phone, String amount) throws Exception {
        if (phone == null || !phone.matches("\\d{10}")) {
            throw new IllegalArgumentException("Enter a valid 10-digit mobile number");
        }
        if (amount == null || amount.trim().isEmpty()) {
            throw new IllegalArgumentException("Recharge amount is required");
        }
        String normalizedAmount = normalizeAmount(amount);
        String customerToken = resolveCustomerToken(phone);

        JsonNode offers = paymentApiClient.getOffers(customerToken, normalizedAmount);

        Map<String, Object> result = new HashMap<>();
        result.put("phone", phone);
        result.put("amount", normalizedAmount);
        result.put("offers_response", offers);

        // Flatten the applicable offers for the UI.
        List<Map<String, Object>> applicable = new ArrayList<>();
        JsonNode offersNode = offers.path("offers");
        if (offersNode.isArray()) {
            for (JsonNode offer : offersNode) {
                Map<String, Object> o = new HashMap<>();
                o.put("offer_id", offer.path("offer_id").asText());
                o.put("title", offer.path("title").asText());
                o.put("sub_title", offer.path("sub_title").asText());
                o.put("type", offer.path("type").asText());
                o.put("cashback", offer.path("cashback").asDouble(0));
                applicable.add(o);
            }
        }
        result.put("applicable_offers", applicable);

        JsonNode best = offers.path("best_offer_combinations");
        List<Map<String, Object>> bestCombos = new ArrayList<>();
        if (best.isArray()) {
            for (JsonNode combo : best) {
                Map<String, Object> c = new HashMap<>();
                c.put("offer_ids", combo.path("offer_ids"));
                c.put("total_cashback", combo.path("total_cashback").asDouble(0));
                c.put("description", combo.path("description").asText());
                bestCombos.add(c);
            }
        }
        result.put("best_offer_combinations", bestCombos);
        result.put("payment_offer", offers.path("payment_offer"));
        return result;
    }

    /**
     * Fetches the latest recharge offers via the app's recharge-offers end-point
     * (API `offers/recharge/fetch`). The `amount_placeholder` returned is what the
     * app pre-fills for the customer; each `details[]` entry is a selectable offer.
     *
     * @param phone 10-digit customer mobile number
     * @return amount_placeholder + flattened offer list + raw response
     */
    public Map<String, Object> fetchRechargeOffers(String phone) throws Exception {
        if (phone == null || !phone.matches("\\d{10}")) {
            throw new IllegalArgumentException("Enter a valid 10-digit mobile number");
        }
        String customerToken = resolveCustomerToken(phone);
        JsonNode resp = paymentApiClient.fetchRechargeOffers(customerToken);

        Map<String, Object> result = new HashMap<>();
        result.put("phone", phone);
        result.put("amount_placeholder", resp.path("amount_placeholder").asDouble(0));
        result.put("manual_recharge_title", resp.path("manual_recharge_title").asText(""));
        result.put("recharge_cta", resp.path("recharge_cta").asText(""));

        List<Map<String, Object>> offers = new ArrayList<>();
        JsonNode details = resp.path("details");
        if (details.isArray()) {
            for (JsonNode d : details) {
                Map<String, Object> o = new HashMap<>();
                o.put("offer_id", d.path("offer_id").asText());
                o.put("coupon_code", d.path("coupon_code").asText());
                o.put("title", d.path("banner_details").path("title").asText());
                o.put("body", d.path("banner_details").path("body").asText());
                o.put("min_amount", d.path("min_amount").asDouble(0));
                o.put("max_amount", d.path("max_amount").asDouble(0));
                o.put("benefit_amount", d.path("benefit_amount").asDouble(0));
                o.put("direct_cash_amount", d.path("direct_cash_amount").asDouble(0));
                o.put("active", d.path("active").asBoolean(false));
                o.put("is_fomo", d.path("is_fomo").asBoolean(false));
                o.put("offer_construct", d.path("offer_construct").asText(""));
                offers.add(o);
            }
        }
        result.put("offers", offers);
        result.put("offers_response", resp);
        return result;
    }

    /**
     * Applies a recharge offer via the app's `offers/recharge/apply` API.
     *
     * @param phone          10-digit customer mobile number
     * @param rechargeAmount recharge amount used to qualify/apply the offer
     * @param couponCode     the offer's coupon code (from the fetch response)
     * @param offerId        the offer id to apply
     * @return the apply response (message, header, applied, customer_offer_id)
     */
    public Map<String, Object> applyRechargeOffer(String phone, String rechargeAmount,
                                                  String couponCode, String offerId) throws Exception {
        if (phone == null || !phone.matches("\\d{10}")) {
            throw new IllegalArgumentException("Enter a valid 10-digit mobile number");
        }
        if (offerId == null || offerId.trim().isEmpty()) {
            throw new IllegalArgumentException("offer_id is required");
        }
        String normalizedAmount = normalizeAmount(rechargeAmount);
        String customerToken = resolveCustomerToken(phone);

        JsonNode resp = paymentApiClient.applyRechargeOffer(customerToken, normalizedAmount,
                couponCode == null ? "" : couponCode.trim(), offerId.trim());

        Map<String, Object> result = new HashMap<>();
        result.put("phone", phone);
        result.put("offer_id", offerId.trim());
        result.put("coupon_code", couponCode);
        result.put("recharge_amount", normalizedAmount);
        result.put("applied", resp.path("error").isNull() || !resp.path("error").asBoolean(false));
        result.put("header", resp.path("header").asText(""));
        result.put("message", resp.path("message").asText(""));
        result.put("offer_response", resp);

        // customer_offer_id for the applied offer (used by generateTransaction).
        String customerOfferId = "";
        JsonNode offerList = resp.path("details").path("offer_list");
        if (offerList.isArray()) {
            for (JsonNode o : offerList) {
                if (offerId.trim().equals(o.path("offer_id").asText())) {
                    customerOfferId = o.path("customer_offer_id").asText("");
                    result.put("active", o.path("active").asBoolean(false));
                    break;
                }
            }
        }
        result.put("customer_offer_id", customerOfferId);
        return result;
    }

    /**
     * Lists auto-recharge configs from autopay_config (beejapuri DB),
     * optionally filtered by smart_plan (yes/no).
     */
    public List<Map<String, Object>> getAutopayConfigs(String smartPlan) throws Exception {
        StringBuilder query = new StringBuilder(
                "SELECT ID, NAME, RECHARGE_AMOUNT, CASHBACK, SMART_PLAN, ACTIVE FROM autopay_config WHERE 1=1");
        if (smartPlan != null && !smartPlan.trim().isEmpty() && !smartPlan.trim().equalsIgnoreCase("all")) {
            boolean yes = smartPlan.trim().equalsIgnoreCase("yes") || smartPlan.trim().equals("1");
            query.append(" AND SMART_PLAN = ").append(yes ? 1 : 0);
        }
        query.append(" ORDER BY PRIORITY ASC, ID ASC");

        List<Map<String, Object>> configs = new ArrayList<>();
        try (Connection con = dbUtil.getConnectionForDatabase(dbUtil.getActiveDbName());
             PreparedStatement ps = con.prepareStatement(query.toString())) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> c = new HashMap<>();
                    c.put("id", rs.getLong("ID"));
                    c.put("plan_name", rs.getString("NAME"));
                    c.put("amount", rs.getBigDecimal("RECHARGE_AMOUNT"));
                    c.put("cashback", rs.getBigDecimal("CASHBACK"));
                    c.put("smart_plan", rs.getObject("SMART_PLAN") != null && rs.getBoolean("SMART_PLAN") ? "yes" : "no");
                    c.put("active", rs.getObject("ACTIVE") != null && rs.getBoolean("ACTIVE"));
                    configs.add(c);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load autopay_config from " + dbUtil.getActiveDbName() + ": " + e.getMessage(), e);
        }
        return configs;
    }

    /**
     * The autopay.md 10-step AutoPay setup series, run in order with each step
     * consuming the previous step's response.
     *
     * <p>Read-only steps (1,2,3,5,6,7,8,10) always run against the live API. The two
     * write steps — {@code offers/recharge/apply} and {@code generateTransaction/2} —
     * are rendered as the exact JSON they would send and skipped while
     * {@code dryRun} is true, so nothing is written unless explicitly asked.</p>
     *
     * @param phone     10-digit mobile number
     * @param configId  the autopay config id (sent as {@code autopay_id})
     * @param amount    recharge amount; blank falls back to the API-3 amount_placeholder
     * @param dryRun    when true, the two write steps are previewed rather than sent
     * @param orderId   optional known order id, so step 10 can be polled in a dry run
     */
    public Map<String, Object> autopayRechargeFlow(String phone, String configId, String amount,
                                                   boolean dryRun, String orderId) throws Exception {
        if (phone == null || !phone.matches("\\d{10}")) {
            throw new IllegalArgumentException("Enter a valid 10-digit mobile number");
        }
        if (configId == null || configId.trim().isEmpty()) {
            throw new IllegalArgumentException("Select an AutoPay config");
        }
        String autopayId = configId.trim();

        String customerToken = resolveCustomerToken(phone);
        int customerId = resolveCustomerId(phone);

        Map<String, Object> result = new HashMap<>();
        result.put("phone", phone);
        result.put("autopay_id", autopayId);
        result.put("dry_run", dryRun);

        Map<String, Object> before = dbUtil.getAutopayCustomer(customerId);
        result.put("autopay_customer_before", before);
        List<Map<String, Object>> steps = new ArrayList<>();

        // API-1 — wallet balance + AutoPay availability.
        JsonNode wallet = paymentApiClient.getWalletScreen(customerToken);
        record(steps, 1, "GET /api/v2/customer/wallet/wallet_screen", "ok",
                "wallet_balance=" + wallet.path("wallet_balance").asText("")
                        + " is_autopay_enabled=" + wallet.path("is_autopay_enabled").asText(""));

        // API-2 — baseline offers. Answers 200 with an empty body when none apply.
        record(steps, 2, "GET /api/v2/customer/juspay/offers?amount=", "ok",
                describe(paymentApiClient.getOffers(customerToken, "")));

        // API-3 — supplies both the recharge amount and the offer that API-4 applies.
        JsonNode fetch = paymentApiClient.fetchRechargeOffers(customerToken);
        String rechargeAmount = (amount == null || amount.trim().isEmpty())
                ? String.valueOf(fetch.path("amount_placeholder").asDouble(0))
                : normalizeAmount(amount);
        if (Double.parseDouble(rechargeAmount) <= 0) {
            throw new IllegalArgumentException("No recharge amount available; enter one explicitly");
        }
        result.put("amount", rechargeAmount);
        String offerId = firstOfferField(fetch, "offer_id");
        String couponCode = firstOfferField(fetch, "coupon_code");
        record(steps, 3, "GET /api/offers/recharge/fetch", "ok",
                "amount_placeholder=" + fetch.path("amount_placeholder").asText("")
                        + " offer_id=" + offerId + " coupon_code=" + couponCode);

        // API-4 — WRITES. Skipped on a dry run, previewed with the real builder.
        String applyPayload = paymentApiClient.rechargeApplyPayload(rechargeAmount, couponCode, offerId);
        if (dryRun) {
            record(steps, 4, "POST /api/offers/recharge/apply", "skipped (dry run)", applyPayload);
        } else {
            record(steps, 4, "POST /api/offers/recharge/apply", "ok",
                    paymentApiClient.applyRechargeOffer(customerToken, rechargeAmount, couponCode, offerId));
        }

        // API-5 — offers re-queried against the chosen amount.
        record(steps, 5, "GET /api/v2/customer/juspay/offers?amount=" + rechargeAmount, "ok",
                describe(paymentApiClient.getOffers(customerToken, rechargeAmount)));

        // API-6 — AutoPay context always runs with mandate_enabled=true.
        record(steps, 6, "POST /api/v2/customer/getSavedCardAndRecentPaymentsForCustomer", "ok",
                paymentApiClient.getSavedCardAndRecentPayments(customerToken, DEFAULT_PAYMENT_SOURCE, true));

        // API-7 — supplies the payment_method that API-9 sends.
        JsonNode paymentMethods = paymentApiClient.getPaymentMethods(customerToken, DEFAULT_CONFIG_ID, true);
        String paymentMethod = extractAutopayPaymentMethod(paymentMethods);
        record(steps, 7, "POST /api/v2/customer/paymentMethods2/" + DEFAULT_CONFIG_ID, "ok",
                "payment_method=" + paymentMethod);

        // API-8 — resolves amount + cashback. The mandate threshold comes from the
        // customer's stored autopay row, not from the request.
        String mandateWalletAmount = String.valueOf(before.getOrDefault("wallet_amount", "0.00"));
        JsonNode amounts = paymentApiClient.getAmountsAndCashBack(customerToken, rechargeAmount, autopayId,
                AUTOPAY_SETUP_SOURCE, mandateWalletAmount, DEFAULT_PAYMENT_SOURCE);
        String cashback = String.valueOf(amounts.path("data").path("cashback").asDouble(0));
        record(steps, 8, "POST /api/v2/customer/getAmountsAndCashBack", "ok",
                "amount=" + amounts.path("data").path("amount").asText(rechargeAmount)
                        + " cashback=" + cashback
                        + " total_amount=" + amounts.path("data").path("total_amount").asText(rechargeAmount)
                        + " mandate_wallet_amount=" + mandateWalletAmount);

        // API-9 — WRITES. Skipped on a dry run, previewed with the real builder.
        String txnPayload = paymentApiClient.autopayPayload(rechargeAmount, autopayId, AUTOPAY_SETUP_SOURCE,
                mandateWalletAmount, DEFAULT_PAYMENT_SOURCE, paymentMethod);
        String resolvedOrderId = (orderId == null) ? "" : orderId.trim();
        if (dryRun) {
            record(steps, 9, "POST /api/v2/customer/generateTransaction/2", "skipped (dry run)", txnPayload);
        } else {
            JsonNode txn = paymentApiClient.generateAutopayTransaction(customerToken, rechargeAmount, autopayId,
                    AUTOPAY_SETUP_SOURCE, mandateWalletAmount, paymentMethod, DEFAULT_PAYMENT_SOURCE);
            record(steps, 9, "POST /api/v2/customer/generateTransaction/2", "ok", txn);
            resolvedOrderId = txn.path("data").path("order_id").asText(
                    txn.path("data").path("merchant_transaction_id").asText(""));
        }
        result.put("order_id", resolvedOrderId);

        // API-10 — needs an order id, so a dry run needs one supplied by hand.
        Map<String, Object> walletCredit = null;
        if (resolvedOrderId.isEmpty()) {
            record(steps, 10, "GET /api/v1/retail/transactions/{orderId}/status", "skipped",
                    "no order id — step 9 was " + (dryRun ? "previewed" : "called")
                            + "; pass an order id to poll status");
        } else {
            Map<String, Object> status = pollTransactionStatus(customerToken, resolvedOrderId);
            record(steps, 10, "GET /api/v1/retail/transactions/" + resolvedOrderId + "/status",
                    String.valueOf(status.get("transaction_status")), status);

            // A dry run must never write, even when handed a real order id just to inspect.
            if (!dryRun && "SUCCESS".equalsIgnoreCase(String.valueOf(status.get("transaction_status")))) {
                walletCredit = dbUtil.applyWalletCredit(customerId, rechargeAmount, cashback,
                        autopayId, paymentMethod, resolvedOrderId);
            }
        }

        result.put("steps", steps);
        result.put("payment_method", paymentMethod);
        result.put("cashback", cashback);
        result.put("wallet_credit", walletCredit);
        result.put("autopay_customer_after", dbUtil.getAutopayCustomer(customerId));
        return result;
    }

    private void record(List<Map<String, Object>> steps, int n, String endpoint, String status, Object detail) {
        Map<String, Object> s = new HashMap<>();
        s.put("step", n);
        s.put("endpoint", endpoint);
        s.put("status", status);
        s.put("detail", detail);
        steps.add(s);
    }

    /** juspay/offers answers 200 with a zero-byte body when no offer applies. */
    private String describe(JsonNode node) {
        return (node == null || node.isEmpty()) ? "empty body (no offer applies)" : node.toString();
    }

    /** First non-blank field across the {@code details[]} offers of offers/recharge/fetch. */
    private String firstOfferField(JsonNode fetch, String field) {
        for (JsonNode d : fetch.path("details")) {
            String value = d.path(field).asText("");
            if (!value.isEmpty()) {
                return value;
            }
        }
        return "";
    }

    /**
     * autopay.md API-9 sends a mandate method from the API-7 list (CARD / UPI_APP /
     * UPI_QR) — not the Recharge-Once "NB-DUMMY BANK". CARD is what the recorded run used.
     */
    private String extractAutopayPaymentMethod(JsonNode paymentMethods) {
        String fallback = "";
        for (JsonNode group : paymentMethods.path("data").path("payment_option_dtos")) {
            for (JsonNode option : group.path("payment_options")) {
                String type = option.path("type").asText("");
                if ("CARD".equals(type)) {
                    return type;
                }
                if (fallback.isEmpty() && !type.isEmpty()) {
                    fallback = type;
                }
            }
        }
        return fallback.isEmpty() ? AUTOPAY_DEFAULT_PAYMENT_METHOD : fallback;
    }

    /* ---------- helpers ---------- */

    private String resolveCustomerToken(String phone) throws Exception {
        Map<String, Object> customer = dbUtil.getCustomerIdByPhone(phone);
        Object dbId = customer.get("db_id");
        if (dbId == null) {
            throw new IllegalArgumentException("No customer found in " + dbUtil.getActiveDbName() + " for phone " + phone);
        }
        String token = dbUtil.getCustomerToken(String.valueOf(dbId));
        if (token == null || token.trim().isEmpty()) {
            throw new IllegalArgumentException("No auth token found for customer " + dbId);
        }
        return token.trim();
    }

    private int resolveCustomerId(String phone) throws Exception {
        Map<String, Object> customer = dbUtil.getCustomerIdByPhone(phone);
        Object dbId = customer.get("db_id");
        if (dbId == null) {
            throw new IllegalArgumentException("No customer found in " + dbUtil.getActiveDbName() + " for phone " + phone);
        }
        return ((Number) dbId).intValue();
    }

    private String normalizeAmount(String amount) {
        double value = Double.parseDouble(amount.trim());
        if (value <= 0) {
            throw new IllegalArgumentException("Recharge amount must be greater than zero");
        }
        // format like "16000.0"
        return String.valueOf(value);
    }

    /**
     * Resolves the NB_DUMMY / "Dummy Bank" payment method entry from the
     * API-3 (paymentMethods2) or API-4 (getSavedCardAndRecentPayments) response.
     *
     * The {@code payment_requests} table stores the canonical value
     * "NB-DUMMY BANK" as PAYMENT_METHOD. The API responses use
     * method="NB_DUMMY" / title="Dummy Bank". We therefore always send the
     * canonical DEFAULT_PAYMENT_METHOD ("NB-DUMMY BANK") to generateTransaction
     * so the CMS records the correct PAYMENT_METHOD value.
     */
    private String resolveDummyPaymentMethod(JsonNode paymentMethods, JsonNode savedCards) {
        JsonNode first = findDummyMethod(paymentMethods);
        if (first == null) {
            first = findDummyMethod(savedCards);
        }
        if (first != null) {
            String method = first.path("method").asText("");
            if (method.isEmpty()) {
                method = first.path("title").asText("");
            }
            String title = first.path("title").asText("");
            System.out.println("✓ Using dummy bank payment method: api->\"" + method
                    + "\" title=\"" + title + "\" → canonical \"" + DEFAULT_PAYMENT_METHOD + "\"");
        } else {
            System.out.println("⚠ NB_DUMMY entry not found; using \"" + DEFAULT_PAYMENT_METHOD + "\"");
        }
        return DEFAULT_PAYMENT_METHOD;
    }

    /**
     * Recursively scans the payment options JSON for the NB_DUMMY / "Dummy Bank"
     * entry and returns the object node containing it (or null).
     */
    private JsonNode findDummyMethod(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isObject()) {
            String method = node.path("method").asText("");
            String title = node.path("title").asText("");
            if ("NB_DUMMY".equalsIgnoreCase(method)
                    || "NB-DUMMY BANK".equalsIgnoreCase(method)
                    || "Dummy Bank".equalsIgnoreCase(title)
                    || method.toUpperCase().contains("DUMMY")) {
                return node;
            }
            var it = node.elements();
            while (it.hasNext()) {
                JsonNode found = findDummyMethod(it.next());
                if (found != null) {
                    return found;
                }
            }
        } else if (node.isArray()) {
            var it = node.elements();
            while (it.hasNext()) {
                JsonNode found = findDummyMethod(it.next());
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    /**
     * Extracts an offer_id from the API-2 (juspay/offers) response.
     * Falls back to "0" when no offer is currently active.
     */
    private String extractOfferId(JsonNode offers) {
        if (offers == null) {
            return "0";
        }
        JsonNode list = offers.path("offers");
        if (list.isArray()) {
            for (JsonNode offer : list) {
                String id = offer.path("offer_id").asText("");
                if (!id.isEmpty()) {
                    return id;
                }
            }
        }
        return "0";
    }

    /**
     * Extracts the cashback amount from the API-4
     * (getSavedCardAndRecentPaymentsForCustomer) response.
     *
     * Searches recursively for any field whose name contains "cashback" and
     * returns the largest qualifying amount. As a fallback it parses the
     * sub_title / title texts of saved cards and recent methods for an
     * amount next to the word cashback (e.g. "₹ 100 cashback on 1st txn").
     */
    private String extractCashback(JsonNode response) {
        if (response == null) {
            return "";
        }
        Set<Double> values = new HashSet<>();
        collectCashback(response, values);
        if (values.isEmpty()) {
            return "";
        }
        double max = 0;
        for (Double v : values) {
            if (v > max) {
                max = v;
            }
        }
        return formatMoney(max);
    }

    private void collectCashback(JsonNode node, Set<Double> values) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isObject()) {
            node.fields().forEachRemaining(e -> {
                String key = e.getKey().toLowerCase();
                JsonNode child = e.getValue();
                if (key.contains("cashback") && child != null && (child.isNumber() || child.isTextual())) {
                    Double v = parseNumber(child.asText());
                    if (v != null) {
                        values.add(v);
                    }
                }
                if (key.contains("cashback") && child != null && child.isTextual()) {
                    parseTextCashback(child.asText(), values);
                }
                collectCashback(child, values);
            });
        } else if (node.isArray()) {
            for (JsonNode child : node) {
                collectCashback(child, values);
            }
        } else {
            // leaf string: try parse cashback text (sub_title/title)
            String text = node.asText("");
            if (text.toLowerCase().contains("cashback")) {
                parseTextCashback(text, values);
            }
        }
    }

    private void parseTextCashback(String text, Set<Double> values) {
        Matcher m = CASHBACK_PATTERN.matcher(text);
        while (m.find()) {
            Double v = parseNumber(m.group(1));
            if (v != null) {
                values.add(v);
            }
        }
        // ranges like "Rs. 50 - 500 Cashback" → also take the upper bound
        Matcher range = Pattern.compile("([0-9]+(?:[.,][0-9]+)?)\\s*-\\s*([0-9]+(?:[.,][0-9]+)?)\\s*[Cc]ashback").matcher(text);
        while (range.find()) {
            Double hi = parseNumber(range.group(2));
            if (hi != null) {
                values.add(hi);
            }
        }
    }

    private Double parseNumber(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        String s = raw.trim().replace(",", "");
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String formatMoney(double v) {
        long l = Math.round(v * 100);
        if (l % 100 == 0) {
            return String.valueOf(l / 100);
        }
        return String.valueOf(Math.round(v * 100) / 100.0);
    }

    /**
     * Polls API-7 until the transaction reaches a terminal state
     * (SUCCESS / SUCCESSFUL / FINISHED / FAILED) or we run out of attempts.
     */
    private Map<String, Object> pollTransactionStatus(String customerToken, String orderId) throws Exception {
        Map<String, Object> result = new HashMap<>();
        if (orderId == null || orderId.isEmpty()) {
            result.put("transaction_status", "NO_ORDER_ID");
            return result;
        }
        long start = System.currentTimeMillis();
        int attempts = 0;
        String status = "IN_PROGRESS";
        while (attempts < 15 && System.currentTimeMillis() - start < 45000) {
            try {
                JsonNode node = paymentApiClient.getTransactionStatus(customerToken, orderId);
                status = node.path("status").asText(status);
                Object balance = node.path("updated_Wallet_Balance").isNull() ? null : node.path("updated_Wallet_Balance").asDouble();
                result.put("transaction_status", status);
                result.put("updated_wallet_balance", balance);
                if ("SUCCESS".equalsIgnoreCase(status) || "SUCCESSFUL".equalsIgnoreCase(status)
                        || "FINISHED".equalsIgnoreCase(status) || "FAILED".equalsIgnoreCase(status)
                        || "FAILURE".equalsIgnoreCase(status) || "FAIL".equalsIgnoreCase(status)) {
                    break;
                }
            } catch (Exception e) {
                // transient status-poll errors are not fatal; retry
                System.out.println("⚠ Transaction status poll error: " + e.getMessage());
            }
            attempts++;
            Thread.sleep(3000);
        }
        return result;
    }
}