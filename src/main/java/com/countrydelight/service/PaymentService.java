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
     * AutoPay series (API-8, per the smart-plan series in payment.md).
     *
     * <p>Series: resolve customer auth → {@code getSavedCardAndRecentPaymentsForCustomer}
     * with {@code payment_source="2"} (the AutoPay payment source — the same
     * {@link #DEFAULT_PAYMENT_SOURCE} value the recharge-once series already uses) →
     * {@code wallet/autopay_offers} with the selected {@code autopay_id} (the AutoPay
     * config id) + {@code recharge_amount}. On a SUCCESS status the recharge amount
     * is also credited to the customer wallet, mirroring the recharge-once series.</p>
     *
     * @param phone          10-digit customer mobile number
     * @param configId       the selected AutoPay config id (the {@code autopay_id} of API-8)
     * @param rechargeAmount the amount to be set up for AutoPay recharge
     * @return step-by-step series result incl. the autopay_offers payload + wallet credit
     */
    public Map<String, Object> setupAutopay(String phone, String configId, String rechargeAmount) throws Exception {
        return setupAutopay(phone, configId, rechargeAmount, null);
    }

    /**
     * Overload that lets the caller force the AutoPay transaction status for QA/Demo
     * (e.g. wish to observe a non-SUCCESS wallet behaviour without a real mandate).
     *
     * @param paymentStatus optional forced status (SUCCESS/PENDING/FAILED); null = poll real status.
     */
    public Map<String, Object> setupAutopay(String phone, String configId, String rechargeAmount, String paymentStatus) throws Exception {
        if (phone == null || !phone.matches("\\d{10}")) {
            throw new IllegalArgumentException("Enter a valid 10-digit mobile number");
        }
        if (configId == null || configId.trim().isEmpty()) {
            throw new IllegalArgumentException("Select an AutoPay config");
        }
        String normalizedAmount = normalizeAmount(rechargeAmount);
        String normalizedConfigId = configId.trim();

        String customerToken = resolveCustomerToken(phone);
        int customerId = resolveCustomerId(phone);

        Map<String, Object> result = new HashMap<>();
        result.put("phone", phone);
        result.put("config_id", normalizedConfigId);
        result.put("amount", normalizedAmount);

        // AutoPay saved-cards / recent payments run with payment_source "2" (the AutoPay
        // source) — same value the recharge-once series uses. This also drives the
        // cashback amount and the wallet balance before setup.
        JsonNode savedCards = paymentApiClient.getSavedCardAndRecentPayments(customerToken, DEFAULT_PAYMENT_SOURCE);
        result.put("saved_cards_response", savedCards);
        String cashback = extractCashback(savedCards);
        result.put("cashback", cashback);

        // API-8: fetch the smart-plan AutoPay offers for the selected config + amount.
        JsonNode autopayOffers = paymentApiClient.getAutopayOffers(customerToken, normalizedConfigId, normalizedAmount);
        result.put("autopay_offers", autopayOffers);

        String orderId = autopayOffers.path("data").path("order_id").asText(
                autopayOffers.path("data").path("merchant_transaction_id").asText(""));
        result.put("order_id", orderId);

        Map<String, Object> status = new HashMap<>();
        status.put("transaction_status", "PENDING_STARTED");
        String txnStatusMsg = autopayOffers.path("data").path("status").asText("");
        String finalStatus = "PENDING_STARTED";
        boolean success = "SUCCESS".equalsIgnoreCase(txnStatusMsg)
                || "SUCCESSFUL".equalsIgnoreCase(txnStatusMsg)
                || "FINISHED".equalsIgnoreCase(txnStatusMsg)
                || "COMPLETED".equalsIgnoreCase(txnStatusMsg);
        if (success) {
            finalStatus = "SUCCESS";
        }

        // Automatically credit the wallet on AutoPay setup success (mirror recharge-once).
        Map<String, Object> walletCredit = null;
        if (paymentStatus != null && !paymentStatus.trim().isEmpty()) {
            String normalizedPaymentStatus = paymentStatus.trim().toUpperCase();
            if (!("SUCCESS".equals(normalizedPaymentStatus) || "PENDING".equals(normalizedPaymentStatus)
                    || "FAILED".equals(normalizedPaymentStatus))) {
                throw new IllegalArgumentException("paymentStatus must be SUCCESS, PENDING or FAILED");
            }
            success = "SUCCESS".equals(normalizedPaymentStatus);
            finalStatus = normalizedPaymentStatus;
            status.put("forced_status", true);
        } else {
            status.put("forced_status", false);
        }

        if ("SUCCESS".equalsIgnoreCase(finalStatus)) {
            walletCredit = dbUtil.applyWalletCredit(customerId, normalizedAmount, cashback,
                    normalizedConfigId, DEFAULT_PAYMENT_METHOD, orderId);
            result.put("wallet_balance_before", walletCredit.getOrDefault("old_wallet_balance", "0"));
            result.put("updated_wallet_balance", walletCredit.get("new_wallet_balance"));
        }
        status.put("transaction_status", finalStatus);
        result.put("forced_status", status.get("forced_status"));
        result.put("wallet_credit", walletCredit);
        return result;
    }

    /* ---------- helpers ---------- */

    /**
     * AutoPay / smart-plan series (payment.md API-8-series).
     *
     * <p>Full series (mirrors the Recharge-Once series, but uses the AutoPay
     * payment context):</p>
     * <ol>
     *   <li>resolve the customer auth token + countrydelight customer id for {@code phone};</li>
     *   <li>{@code getSavedCardAndRecentPaymentsForCustomer} with {@code payment_source="2"}
     *       (the AutoPay payment source — {@link #DEFAULT_PAYMENT_SOURCE}) — this returns the
     *       saved cards / recent payments from which we extract the AutoPay cashback;</li>
     *   <li>{@code wallet/autopay_offers} (API-8) with the chosen {@code config_id} + the
     *       recharge {@code amount}, returning the smart-plan AutoPay offer payload
     *       (incl. the autopay/plan id that maps to the wallet-side mandate);</li>
     *   <li>on SUCCESS, also credit the wallet to mirror the Recharge-Once series.</li>
     * </ol>
     *
     * @param phone          10-digit customer mobile number
     * @param configId       the selected AutoPay config id (from {@link #getAutopayConfigs})
     * @param rechargeAmount the amount to be set up for AutoPay recharge
     * @return step-by-step series result incl. the autopay offer payload + wallet credit
     */
//    public Map<String, Object> setupAutopay(String phone, String configId, String rechargeAmount) throws Exception {
//        return setupAutopay(phone, configId, rechargeAmount, null);
//    }

    /**
     * Overload that lets the caller force the AutoPay transaction status for QA/Demo
     * (mirrors the recharge-once forced-status behaviour).
     *
//     * @param  optional forced status (SUCCESS/PENDING/FAILED); null = poll real status.
     */
//    public Map<String, Object> setupAutopay(String phone, String configId, String rechargeAmount, String paymentStatus) throws Exception {
//        if (phone == null || !phone.matches("\\d{10}")) {
//            throw new IllegalArgumentException("Enter a valid 10-digit mobile number");
//        }
//        if (configId == null || configId.trim().isEmpty()) {
//            throw new IllegalArgumentException("Select an AutoPay config");
//        }
//        String normalizedAmount = normalizeAmount(rechargeAmount);
//        String normalizedConfigId = configId.trim();
//
//        String customerToken = resolveCustomerToken(phone);
//        int customerId = resolveCustomerId(phone);
//
//        String normalizedPaymentStatus = null;
//        if (paymentStatus != null && !paymentStatus.trim().isEmpty()) {
//            normalizedPaymentStatus = paymentStatus.trim().toUpperCase();
//            if (!("SUCCESS".equals(normalizedPaymentStatus) || "PENDING".equals(normalizedPaymentStatus)
//                    || "FAILED".equals(normalizedPaymentStatus))) {
//                throw new IllegalArgumentException("paymentStatus must be SUCCESS, PENDING or FAILED");
//            }
//        }
//
//        Map<String, Object> result = new HashMap<>();
//        result.put("phone", phone);
//        result.put("config_id", normalizedConfigId);
//        result.put("amount", normalizedAmount);
//
//        // API-4: saved cards + recent payments, always via the AutoPay payment source "2".
//        JsonNode savedCards = paymentApiClient.getSavedCardAndRecentPayments(customerToken, DEFAULT_PAYMENT_SOURCE);
//        result.put("saved_card_response", savedCards);
//        String cashback = extractCashback(savedCards);
//        result.put("cashback", cashback);
//
//        // API-8: fetch the smart-plan AutoPay offers for the chosen config + amount.
//        JsonNode autopayOffers = paymentApiClient.getAutopayOffers(customerToken, normalizedConfigId, normalizedAmount);
//        result.put("autopay_offers", autopayOffers);
//
//        String orderId = autopayOffers.path("data").path("order_id").asText(
//                autopayOffers.path("data").path("merchant_transaction_id").asText(""));
//        result.put("order_id", orderId);
//
//        String statusMsg = autopayOffers.path("data").path("status").asText("");
//        String finalStatus = statusMsg;
//        boolean success = "SUCCESS".equalsIgnoreCase(finalStatus) || "SUCCESSFUL".equalsIgnoreCase(finalStatus)
//                || "FINISHED".equalsIgnoreCase(finalStatus) || "COMPLETED".equalsIgnoreCase(finalStatus);
//
//        Map<String, Object> status = new HashMap<>();
//        if (normalizedPaymentStatus != null) {
//            // QA/Demo forced status — mirror the recharge series' direct DB write.
//            int updated = dbUtil.updatePaymentRequestStatus(orderId, normalizedPaymentStatus, cashback, customerId,
//                    normalizedAmount, "NB-DUMMY BANK");
//            finalStatus = normalizedPaymentStatus;
//            status.put("transaction_status", normalizedPaymentStatus);
//            status.put("forced_status", true);
//            status.put("payment_requests_rows_updated", updated);
//        } else {
//            status.put("transaction_status", finalStatus);
//            status.put("forced_status", false);
//        }
//        result.putAll(status);
//
//        Map<String, Object> walletCredit = null;
//        if (success) {
//            walletCredit = dbUtil.applyWalletCredit(customerId, normalizedAmount, cashback,
//                    normalizedConfigId, "NB-DUMMY BANK", orderId);
//            result.put("wallet_balance_before", walletCredit.getOrDefault("old_wallet_balance", walletBeforeValue()));
//            result.put("updated_wallet_balance", walletCredit.get("new_wallet_balance"));
//        }
//        result.put("wallet_credit", walletCredit);
//
//        return result;
//    }

    private String walletBeforeValue() {
        return "0";
    }

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