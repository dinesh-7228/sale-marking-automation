# -*- coding: utf-8 -*-
"""Definitive repair of PaymentService.setupAutopay (4-arg) corrupt impl body.

Two byte-real corrupt tokens remain in the 4-arg impl on disk:
  (1) illegal escape:  phone.matches("\d{10}")   -> must become \\d{10}
  (2) not-a-statement: result.put("wallet_credit", walletCredit Preview);
                        -> Preview token must be removed

We replace the whole AutoPay-series span (series Javadoc start .. helpers marker)
with a clean, correctly-escaped impl. Escaping note: to emit Java literal \\d,
the python source below must contain four backslashes.
"""
import io
import os

P = "/home/dinesh/Documents/sale-marking-automation/src/main/java/com/countrydelight/service/PaymentService.java"
p = os.path.expanduser(P)

s = io.open(p, encoding="utf-8").read()

SERIES_JDOC = "AutoPay series (API-8, per the smart-plan series in payment.md)."
HELPERS = "    /* ---------- helpers ---------- */"

i = s.find(SERIES_JDOC)
j = s.find(HELPERS, i if i != -1 else 0)
if i == -1 or j == -1:
    raise SystemExit("ANCHOR_FAIL i=%r j=%r" % (i, j))

# step back to the opening '/**' of the series Javadoc
k = s.rfind("/**", 0, i)
if k == -1:
    k = i
start = k

NEW_SERIES = (
    "/**\n"
    "     * AutoPay series (API-8, per the smart-plan series in payment.md).\n"
    "     *\n"
    "     * <p>Series: resolve the customer auth token + id, then fetch the saved\n"
    "     * cards / recent payments for this customer using payment_source \"2\" (the\n"
    "     * AutoPay payment source — same source value the recharge-once series uses),\n"
    "     * then request the smart-plan AutoPay offers for the selected config + amount\n"
    "     * via {@code wallet/autopay_offers}, then on SUCCESS apply the wallet credit.</p>\n"
    "     *\n"
    "     * @param phone           10-digit customer mobile number\n"
    "     * @param configId        the AutoPay config id (the autopay_id from API-7 configs)\n"
    "     * @param rechargeAmount  the recharge amount to set up under AutoPay\n"
    "     * @return step-by-step series result incl. the autopay_offers payload + wallet credit\n"
    "     */\n"
    "    public Map<String, Object> setupAutopay(String phone, String configId, String rechargeAmount) throws Exception {\n"
    "        return setupAutopay(phone, configId, rechargeAmount, null);\n"
    "    }\n"
    "\n"
    "    /**\n"
    "     * Overload that lets QA force the transaction status for demo/negative-path checks\n"
    "     * (SUCCESS / PENDING / FAILED) without needing a real AutoPay mandate.\n"
    "     *\n"
    "     * @param paymentStatus optional forced status; null = poll the real status\n"
    "     */\n"
    "    public Map<String, Object> setupAutopay(String phone, String configId, String rechargeAmount,\n"
    "                                             String paymentStatus) throws Exception {\n"
    "        if (phone == null || !phone.matches(\"\\\\d{10}\")) {\n"
    "            throw new IllegalArgumentException(\"Enter a valid 10-digit mobile number\");\n"
    "        }\n"
    "        if (configId == null || configId.trim().isEmpty()) {\n"
    "            throw new IllegalArgumentException(\"Select an AutoPay config\");\n"
    "        }\n"
    "        String normalizedAmount = normalizeAmount(rechargeAmount);\n"
    "        String normalizedConfigId = configId.trim();\n"
    "\n"
    "        String customerToken = resolveCustomerToken(phone);\n"
    "        int customerId = resolveCustomerId(phone);\n"
    "\n"
    "        Map<String, Object> result = new HashMap<>();\n"
    "        result.put(\"phone\", phone);\n"
    "        result.put(\"config_id\", normalizedConfigId);\n"
    "        result.put(\"recharge_amount\", normalizedAmount);\n"
    "\n"
    "        // AutoPay uses payment_source \"2\" — the same source the recharge-once series uses\n"
    "        // for getSavedCardAndRecentPaymentsForCustomer.\n"
    "        JsonNode savedCards = paymentApiClient.getSavedCardAndRecentPayments(customerToken, DEFAULT_PAYMENT_SOURCE);\n"
    "        result.put(\"saved_cards_response\", savedCards);\n"
    "\n"
    "        String cashback = extractCashback(savedCards);\n"
    "        result.put(\"cashback\", cashback);\n"
    "\n"
    "        // API-8: fetch smart-plan AutoPay offers for the selected config + amount.\n"
    "        JsonNode autopayOffers = paymentApiClient.getAutopayOffers(customerToken, normalizedConfigId, normalizedAmount);\n"
    "        result.put(\"autopay_offers\", autopayOffers);\n"
    "\n"
    "        String orderId = autopayOffers.path(\"data\").path(\"order_id\").asText(\n"
    "                autopayOffers.path(\"data\").path(\"merchant_transaction_id\").asText(\"\"));\n"
    "        result.put(\"order_id\", orderId);\n"
    "\n"
    "        String finalStatus = \"PENDING_STARTED\";\n"
    "        String txnStatus = autopayOffers.path(\"data\").path(\"status\").asText(\"\");\n"
    "        boolean success = isSuccessTxnStatus(txnStatus);\n"
    "        if (success) {\n"
    "            finalStatus = \"SUCCESS\";\n"
    "        }\n"
    "\n"
    "        String forcedStatus = null;\n"
    "        if (paymentStatus != null && !paymentStatus.trim().isEmpty()) {\n"
    "            forcedStatus = paymentStatus.trim().toUpperCase();\n"
    "            if (!(\"SUCCESS\".equals(forcedStatus) || \"PENDING\".equals(forcedStatus)\n"
    "                    || \"FAILED\".equals(forcedStatus))) {\n"
    "                throw new IllegalArgumentException(\"paymentStatus must be SUCCESS, PENDING or FAILED\");\n"
    "            }\n"
    "            finalStatus = forcedStatus;\n"
    "        }\n"
    "\n"
    "        Map<String, Object> walletCredit = null;\n"
    "        if (\"SUCCESS\".equalsIgnoreCase(finalStatus)) {\n"
    "            walletCredit = dbUtil.applyWalletCredit(customerId, normalizedAmount, cashback,\n"
    "                    normalizedConfigId, DEFAULT_PAYMENT_METHOD, orderId);\n"
    "            result.put(\"wallet_balance_before\", walletCredit.getOrDefault(\"old_wallet_balance\", \"0\"));\n"
    "            result.put(\"updated_wallet_balance\", walletCredit.get(\"new_wallet_balance\"));\n"
    "        }\n"
    "\n"
    "        Map<String, Object> status = new HashMap<>();\n"
    "        status.put(\"transaction_status\", finalStatus);\n"
    "        status.put(\"forced_status\", forcedStatus != null);\n"
    "        result.put(\"forced_status\", forcedStatus != null);\n"
    "        result.put(\"status\", status);\n"
    "        result.put(\"wallet_credit\", walletCredit);\n"
    "        return result;\n"
    "    }\n"
    "\n"
"    /* ---------- helpers ---------- */\n"
)

new_s = s[:start] + NEW_SERIES + s[j:]
out = io.open(os.path.expanduser(
    "/home/dinesh/Documents/sale-marking-automation/.out/rewrite_result.py.txt"), "w", encoding="utf-8")
out.write("OK start=%d series=%d helpers=%d j=%d old=%d new=%d\n"
          % (start, i, j, j, len(s), len(new_s)))
out.close()
io.open(os.path.expanduser(
    "/home/dinesh/Documents/sale-marking-automation/.out/rewrite_done.py.txt"),
    "w", encoding="utf-8").write("DONE_FOR_REAL\n")
io.open(p, "w", encoding="utf-8", newline="").write(new_s)
print("REWRITE_OK start=%d series=%d helpers=%d old=%d new=%d sudoku=%d" % (start, i, j, len(s), len(new_s), s.count("Preview")))
