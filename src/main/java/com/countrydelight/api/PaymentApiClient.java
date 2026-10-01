package com.countrydelight.api;

import com.countrydelight.config.EnvironmentConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Client for the Country Delight payment/recharge APIs (payment.md).
 *
 * All APIs use the customer's Authorization token plus the standard
 * Android app headers (x-source, x-payment-version, etc.).
 *
 * Series used by "Recharge Once":
 *   1. GET  /api/v2/customer/wallet/wallet_screen?action=            (wallet balance)
 *   2. GET  /api/v2/customer/juspay/offers?amount=                   (payment offers / offer_id)
 *   3. POST /api/v2/customer/paymentMethods2/{configId}?last_update= (available payment methods)
 *   4. POST /api/v2/customer/getSavedCardAndRecentPaymentsForCustomer(saved cards + recent methods)
 *   6. POST /api/v2/customer/generateTransaction/2                   (creates the transaction)
 *   7. GET  /api/v1/retail/transactions/{id}/status                  (transaction status)
 */
@Component
public class PaymentApiClient {

    @Autowired
    private EnvironmentConfig envConfig;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private String getBaseUrl() {
        return envConfig.getApiBaseUrl();
    }

    private RequestSpecification request(String customerAuth) {
        return RestAssured.given()
                .header("x-source", "Android")
                .header("x-language", "en")
                .header("x-os", "13")
                .header("x-app-version-name", "99.99.99")
                .header("x-app-version-code", "9999")
                .header("x-version-code", "9999")
                .header("x-chatbot-version", "80")
                .header("x-release-version", "33")
                .header("x-payment-version", "6")
                .header("x-rapid-version", "12")
                .header("Authorization", customerAuth);
    }

    private JsonNode parse(Response response, String step) {
        if (response.getStatusCode() < 200 || response.getStatusCode() >= 300) {
            throw new RuntimeException(step + " failed with status " + response.getStatusCode()
                    + ": " + response.getBody().asString());
        }
        String body = response.getBody().asString();
        // juspay/offers answers 200 with a zero-byte body when no offer applies.
        if (body == null || body.trim().isEmpty()) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(body);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new RuntimeException(step + " returned invalid JSON: " + e.getMessage(), e);
        }
    }

    /** API-1: Fetch wallet screen (wallet_balance, amount_placeholder, recharge tiles). */
    public JsonNode getWalletScreen(String customerAuth) {
        Response response = request(customerAuth)
                .queryParam("action", "")
                .get(getBaseUrl() + "/api/v2/customer/wallet/wallet_screen");
        return parse(response, "wallet_screen");
    }

    /** API-2: Fetch juspay offers for the given recharge amount. */
    public JsonNode getOffers(String customerAuth, String amount) {
        Response response = request(customerAuth)
                .queryParam("amount", amount)
                .get(getBaseUrl() + "/api/v2/customer/juspay/offers");
        return parse(response, "juspay/offers");
    }

    /** API-3: Fetch available payment methods (e.g. NB-DUMMY BANK). configId usually 5. */
    public JsonNode getPaymentMethods(String customerAuth, String configId) {
        return getPaymentMethods(customerAuth, configId, false);
    }

    /**
     * @param mandateEnabled the AutoPay series (autopay.md API-7) sends {@code true};
     *                        the Recharge-Once series sends {@code false}.
     */
    public JsonNode getPaymentMethods(String customerAuth, String configId, boolean mandateEnabled) {
        Response response = request(customerAuth)
                .contentType(ContentType.JSON)
                .body("{\"mandate_enabled\":" + mandateEnabled + "}")
                .post(getBaseUrl() + "/api/v2/customer/paymentMethods2/" + configId + "?last_update=");
        return parse(response, "paymentMethods2/" + configId);
    }

    /** API-4: Fetch saved cards + recent payment methods for a customer and payment source. */
    public JsonNode getSavedCardAndRecentPayments(String customerAuth, String paymentSource) {
        return getSavedCardAndRecentPayments(customerAuth, paymentSource, false);
    }

    /**
     * @param mandateEnabled the AutoPay series (autopay.md API-6) sends {@code true};
     *                        the Recharge-Once series sends {@code false}.
     */
    public JsonNode getSavedCardAndRecentPayments(String customerAuth, String paymentSource, boolean mandateEnabled) {
        Response response = request(customerAuth)
                .contentType(ContentType.JSON)
                .body("{\"payment_source\":\"" + paymentSource + "\",\"mandate_enabled\":" + mandateEnabled + "}")
                .post(getBaseUrl() + "/api/v2/customer/getSavedCardAndRecentPaymentsForCustomer");
        return parse(response, "getSavedCardAndRecentPaymentsForCustomer");
    }

    /** API-6: Generate a payment transaction for the recharge. */
    public JsonNode generateTransaction(String customerAuth, String amount, String customerOfferId,
                                        String offerId, String paymentMethod, String paymentSource) {
        String body = "{\"amount\":\"" + amount + "\",\"autopay_new_flow\":false,\"customer_offer_id\":\""
                + customerOfferId + "\",\"is_fomo\":false,\"is_updated\":\"true\",\"mandate_enabled\":false,\"offer_id\":\""
                + offerId + "\",\"payment_method\":\"" + paymentMethod + "\",\"payment_source\":\"" + paymentSource + "\"}";
        Response response = request(customerAuth)
                .contentType(ContentType.JSON)
                .body(body)
                .post(getBaseUrl() + "/api/v2/customer/generateTransaction/2");
        return parse(response, "generateTransaction/2");
    }

    /** API-7: Poll the transaction status until SUCCESS (or a terminal state). */
    public JsonNode getTransactionStatus(String customerAuth, String orderId) {
        Response response = request(customerAuth)
                .get(getBaseUrl() + "/api/v1/retail/transactions/" + orderId + "/status");
        return parse(response, "transaction status");
    }

    /** NEW: Fetch the latest recharge offers (offers/recharge/fetch) for the customer. */
    public JsonNode fetchRechargeOffers(String customerAuth) {
        Response response = request(customerAuth)
                .get(getBaseUrl() + "/api/offers/recharge/fetch");
        return parse(response, "offers/recharge/fetch");
    }

    /** NEW: Apply a recharge offer by offer id (offers/recharge/apply). */
    public JsonNode applyRechargeOffer(String customerAuth, String rechargeAmount, String couponCode, String offerId) {
        Response response = request(customerAuth)
                .contentType(ContentType.JSON)
                .body(rechargeApplyPayload(rechargeAmount, couponCode, offerId))
                .post(getBaseUrl() + "/api/offers/recharge/apply");
        return parse(response, "offers/recharge/apply");
    }

    /**
     * The exact offers/recharge/apply body. Exposed so the dry-run preview shows
     * byte-for-byte what {@link #applyRechargeOffer} would send.
     */
    public String rechargeApplyPayload(String rechargeAmount, String couponCode, String offerId) {
        return "{\"recharge_amount\":\"" + rechargeAmount + "\",\"coupon_code\":\"" + couponCode
                + "\",\"is_fomo\":\"false\",\"offer_id\":\"" + offerId + "\"}";
    }

    /**
     * The shared AutoPay payload. The doc's generateTransaction body is this same
     * payload plus {@code payment_method}, so both endpoints build it here.
     *
     * @param paymentMethod when null the field is omitted (getAmountsAndCashBack).
     */
    public String autopayPayload(String amount, String autopayId, String setupSource,
                                 String mandateWalletAmount, String paymentSource, String paymentMethod) {        String method = paymentMethod == null ? "" : ",\"payment_method\":\"" + paymentMethod + "\"";
        return "{\"amount\":\"" + amount + "\",\"autopay_id\":\"" + autopayId + "\",\"autopay_new_flow\":false,"
                + "\"autopay_setup_source\":\"" + setupSource + "\",\"is_fomo\":false,\"is_updated\":\"true\","
                + "\"mandate_amount\":\"0.0\",\"mandate_duration\":0,\"mandate_enabled\":true,"
                + "\"mandate_wallet_amount\":\"" + mandateWalletAmount + "\"" + method
                + ",\"payment_source\":\"" + paymentSource + "\"}";
    }

    /**
     * autopay.md API-8: resolve the payable amount and its cashback for the AutoPay setup.
     *
     * @param mandateWalletAmount the wallet threshold, from autopay_customers.WALLET_AMOUNT
     */
    public JsonNode getAmountsAndCashBack(String customerAuth, String amount, String autopayId,
                                          String setupSource, String mandateWalletAmount, String paymentSource) {
        Response response = request(customerAuth)
                .contentType(ContentType.JSON)
                .body(autopayPayload(amount, autopayId, setupSource, mandateWalletAmount, paymentSource, null))
                .post(getBaseUrl() + "/api/v2/customer/getAmountsAndCashBack");
        return parse(response, "getAmountsAndCashBack");
    }

    /**
     * autopay.md API-9: create the AutoPay setup transaction. Returns the order id
     * that autopay.md API-10 polls.
     *
     * <p>Kept separate from {@link #generateTransaction} on purpose: the Recharge-Once
     * body carries {@code offer_id}/{@code customer_offer_id}, this one carries
     * {@code autopay_id}/{@code mandate_wallet_amount}. They share an endpoint, not a payload.</p>
     */
    public JsonNode generateAutopayTransaction(String customerAuth, String amount, String autopayId,
                                               String setupSource, String mandateWalletAmount,
                                               String paymentMethod, String paymentSource) {
        Response response = request(customerAuth)
                .contentType(ContentType.JSON)
                .body(autopayPayload(amount, autopayId, setupSource, mandateWalletAmount, paymentSource, paymentMethod))
                .post(getBaseUrl() + "/api/v2/customer/generateTransaction/2");
        return parse(response, "generateTransaction/2 (autopay)");
    }
}