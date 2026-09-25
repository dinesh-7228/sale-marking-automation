# -*- coding: utf-8 -*-
"""Rewrite the AutoPay series region of PaymentService.java cleanly.

Anchors (both UNIQUE in the file, confirmed by grep -c):
  SERIES_JDOC : Javadoc line 'AutoPay series (API-8, per the smart-plan series in payment.md).'
  HELPERS     : marker line '    /* ---------- helpers ---------- */'

Replaces [start-of-series-javadoc .. helpers-marker) with a single clean block
containing exactly ONE 3-arg overload + ONE 4-arg impl (no duplicates, no stray
tokens, correct \\d escapes).
"""
import io

p = "/home/dinesh/Documents/sale-marking-automation/src/main/java/com/countrydelight/service/PaymentService.java"
s = io.open(p, encoding="utf-8").read()

ser_jdoc = "AutoPay series (API-8, per the smart-plan series in payment.md)."
helpers_marker = "    /* ---------- helpers ---------- */"

i = s.find(ser_jdoc)
j = s.find(helpers_marker)
if i == -1:
    raise SystemExit("SERIES_JDOC_NOT_FOUND")
if j == -1:
    raise SystemExit("HELPERS_NOT_FOUND")
# back i up to the opening '/**' of that javadoc
k = s.rfind("/**", 0, i)
if k != -1 and k > s.rfind("/*", 0, i) and (s.count("*/", 0, i) <= s.count("/**", 0, i)):
    # i now equals the '/**' that has no earlier close -- too fiddly; fall back:
    pass
# reopen start: grab from the last blank-line-before-javadoc, inclusive of '/**'
start = s.rfind("/**", 0, i)
if start == -1:
    start = i
else:
    # ensure start points at the javadoc for THIS series (ignore nested /** in code)
    pre = s[:start]
    if pre.count("/*") > pre.count("*/"):
        # unbalanced: likely inside a comment; search backwards for a safe '/**'
        cand = None
        idx = -1
        while True:
            idx = s.find("/**", idx + 1)
            if idx == -1 or idx >= i:
                break
            cand = idx
        start = cand if cand is not None else i

NEW_SERIES = u"""    /**
     * AutoPay series (API-8, per the smart-plan series in payment.md).
     *
     * <p>Series: resolve the customer auth token (AutoPay uses the saved-card /
     * recent-payments endpoint with {@code payment_source="2"} — the same source the
     * recharge-once series uses), then fetch the AutoPay offers for the selected config
     * + recharge amount via {@code wallet/autopay_offers}, then apply the wallet credit
     * on SUCCESS. The recharge amount for AutoPay comes from the selected smart-plan
     * config.</p>
     *
     * @param phone          10-digit customer mobile number
     * @param configId       the AutoPay config id (the autopay_id from autopay-configs)
     * @param rechargeAmount the recharge/smart-plan amount configured for AutoPay
     * @return step-by-step series result incl. autopay_offers payload + wallet credit
     */
    public Map<String, Object> setupAutopay(String phone, String configId, String rechargeAmount) throws Exception {
        return setupAutopay(phone, configId, rechargeAmount, null);
    }

    /**
     * AutoPay setup with an optional forced transaction status (lets QA observe the
     * non-SUCCESS wallet path). {@code paymentStatus} may be null to poll the real
     * status from the AutoPay-offers response.
     *
     * @param paymentStatus optional forced status (SUCCESS/PENDING/FAILED); null = real.
     */
    public Map<String, Object> setupAutopay(String phone, String configId, String rechargeAmount, String paymentStatus) throws Exception {
        if (phone == null || !phone.matches("\\\\d{10}")) {
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
        result.put("recharge_amount", normalizedAmount);

        // AutoPay uses payment_source "2" for the saved-card / recent-payments call —
        // the same source as the recharge-once series (DEFAULT_PAYMENT_SOURCE).
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

        String finalStatus = "PENDING_STARTED";
        String txnStatusMsg = autopayOffers.path("data").path("status").asText("");
        boolean success = "SUCCESS".equalsIgnoreCase(txnStatusMsg)
                || "SUCCESSFUL".equalsIgnoreCase(txnStatusMsg)
                || "FINISHED".equalsIgnoreCase(txnStatusMsg)
                || "COMPLETED".equalsIgnoreCase(txnStatusMsg);
        if (success) {
            finalStatus = "SUCCESS";
        }

        boolean forced = paymentStatus != null && !paymentStatus.trim().isEmpty();
        if (forced) {
            String normalized = paymentStatus.trim().toUpperCase();
            switch (normalized) {
                case "SUCCESS":
                case "PENDING":
                case "FAILED":
                    finalStatus = normalized;
                    break;
                default:
                    throw new IllegalArgumentException("paymentStatus must be SUCCESS, PENDING or FAILED");
            }
        }

        Map<String, Object> walletCredit = null;
        if ("SUCCESS".equalsIgnoreCase(finalStatus)) {
            walletCredit = dbUtil.applyWalletCredit(customerId, normalizedAmount, cashback,
                    normalizedConfigId, DEFAULT_PAYMENT_METHOD, orderId);
            result.put("wallet_balance_before", walletCredit.getOrDefault("old_wallet_balance", "0"));
            result.put("updated_wallet_balance", walletCredit.get("new_wallet_balance"));
        }

        result.put("forced_status", forced);
        result.put("transaction_status", finalStatus);
        result.put("wallet_credit", walletCredit);
        return result;
    }

    /**
     * AutoPay series (API-8): return the available smart-plan configs (autopay_id,
     * plan_name, smart_plan yes/no, cashback, amounts) for the given phone.
     */
    public Map<String, Object> getAutopayConfigs(String phone, String configId) throws Exception {
        return getAutopayConfigs(phone, configId, null);
    }

    /**
     * AutoPay series (API-8): resolve + return the smart-plan config list for AutoPay.
     */
    public List<Map<String, Object>> getAutopayConfigs(String phone) throws Exception {
        String customerToken = resolveCustomerToken(phone);
        int customerId = resolveCustomerId(phoneapsed);
        JsonNode savedCards = paymentApiClient.getSavedCardAndRecentPayments(customerToken, DEFAULT_PAYMENT_SOURCE);
        return jsonNodeToAutopayConfigList(savedCards, customerId, phone);
    }

    /**
     * AutoPay series (API-8): resolve + return a single smart-plan config.
     */
    public Map<String, Object> getAutopayConfigs(String phone, String configId, String unused) throws Exception {
        return getAutopayConfig(phone, configId, true);
    }

    /** Maps a saved-card / recent-payments response into AutoPay config rows. */
    public List<Map<String, Object>> jsonNodeToAutopayConfigList(JsonNode savedCards, int customerId, String phone) {
        return ConfigMapper.autopayConfigsFromSavedCards(savedCards, customerId, phone);
    }

"""
new_s = s[:start] + NEW_SERIES + s[j:]
io.open(p, "w", encoding="utf-8", newline="").write(new_s)
io.open("/tmp/opencode/rewrite_series_out.txt", "w", encoding="utf-8").write(
    "REWRITE_OK start=%d j=%d old=%d new=%d\n" % (start, j, len(s), len(new_s)))
print("REWRITE_OK start=%d j=%d old=%d new=%d" % (start, j, len(s), len(new_s)))
