package com.countrydelight.service;

import com.countrydelight.api.PaymentApiClient;
import com.countrydelight.db.DatabaseUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Locks the two invariants of the autopay.md flow:
 *   1. a dry run never writes (no apply, no generateTransaction, no wallet credit);
 *   2. a live run chains the previous responses — the API-7 payment method reaches
 *      generateTransaction, and the API-3 amount reaches both write steps.
 */
class PaymentServiceAutopayTest {

    private static final String PHONE = "3693690000";
    private static final int CUSTOMER_ID = 9952228;
    private static final String ORDER_ID = "JP36936900001790664449766";

    private final ObjectMapper mapper = new ObjectMapper();
    private PaymentService service;
    private PaymentApiClient api;
    private DatabaseUtil db;

    @BeforeEach
    void setUp() throws Exception {
        service = new PaymentService();
        api = mock(PaymentApiClient.class);
        db = mock(DatabaseUtil.class);
        ReflectionTestUtils.setField(service, "paymentApiClient", api);
        ReflectionTestUtils.setField(service, "dbUtil", db);

        Map<String, Object> customer = new HashMap<>();
        customer.put("db_id", (long) CUSTOMER_ID);
        when(db.getCustomerIdByPhone(PHONE)).thenReturn(customer);
        when(db.getCustomerToken(String.valueOf(CUSTOMER_ID))).thenReturn("token-abc");

        Map<String, Object> autopayRow = new HashMap<>();
        autopayRow.put("wallet_amount", new BigDecimal("150.00"));
        autopayRow.put("autopay_config", 497);
        when(db.getAutopayCustomer(CUSTOMER_ID)).thenReturn(autopayRow);

        when(api.getWalletScreen("token-abc"))
                .thenReturn(node("{\"wallet_balance\":3853.00,\"is_autopay_enabled\":true}"));
        when(api.getOffers(anyString(), anyString())).thenReturn(mapper.createObjectNode());
        when(api.fetchRechargeOffers("token-abc")).thenReturn(node(
                "{\"amount_placeholder\":1000.0,\"details\":[{\"offer_id\":177,\"coupon_code\":\"WELCOME10\"}]}"));
        when(api.getSavedCardAndRecentPayments(eq("token-abc"), eq("2"), eq(true)))
                .thenReturn(node("{\"error\":false}"));
        when(api.getPaymentMethods("token-abc", "5", true)).thenReturn(node(
                "{\"data\":{\"payment_option_dtos\":[{\"payment_options\":[{\"type\":\"UPI_APP\"},{\"type\":\"CARD\"}]}]}}"));
        when(api.getAmountsAndCashBack(eq("token-abc"), anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(node("{\"data\":{\"amount\":1000.0,\"cashback\":25.0,\"total_amount\":1025.0}}"));
        when(api.getTransactionStatus("token-abc", ORDER_ID)).thenReturn(node("{\"status\":\"SUCCESS\"}"));
    }

    @Test
    void dryRunPreviewsWritesAndTouchesNothing() throws Exception {
        Map<String, Object> result = service.autopayRechargeFlow(PHONE, "497", "", true, ORDER_ID);

        verify(api, never()).applyRechargeOffer(anyString(), anyString(), anyString(), anyString());
        verify(api, never()).generateAutopayTransaction(anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString());
        verify(db, never()).applyWalletCredit(anyInt(), anyString(), anyString(),
                anyString(), anyString(), anyString());

        assertNull(result.get("wallet_credit"));
        assertEquals("1000.0", result.get("amount"));
        assertEquals("CARD", result.get("payment_method"));

        java.util.List<Map<String, Object>> steps =
                (java.util.List<Map<String, Object>>) result.get("steps");
        assertTrue(steps.stream().anyMatch(s -> ((Number) s.get("step")).intValue() == 4
                && String.valueOf(s.get("status")).startsWith("skipped")));

        // Even with a real order id, polling SUCCESS must not credit the wallet on a dry run.
        verify(api).getTransactionStatus("token-abc", ORDER_ID);
    }

    @Test
    void liveRunChainsPaymentMethodAndCreditsWallet() throws Exception {
        when(api.generateAutopayTransaction(anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString()))
                .thenReturn(node("{\"data\":{\"order_id\":\"" + ORDER_ID + "\"}}"));
        Map<String, Object> credit = new HashMap<>();
        credit.put("new_wallet_balance", 4853.0);
        when(db.applyWalletCredit(anyInt(), anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(credit);

        Map<String, Object> result = service.autopayRechargeFlow(PHONE, "497", "", false, "");

        verify(api).applyRechargeOffer("token-abc", "1000.0", "WELCOME10", "177");
        // API-7's CARD and API-3's amount + the DB threshold must all reach API-9.
        verify(api).generateAutopayTransaction("token-abc", "1000.0", "497", "menu_screen", "150.00", "CARD", "2");
        verify(db).applyWalletCredit(CUSTOMER_ID, "1000.0", "25.0", "497", "CARD", ORDER_ID);
        assertEquals(ORDER_ID, result.get("order_id"));
    }

    @Test
    void rejectsBadPhoneBeforeAnyApiCall() throws Exception {
        try {
            service.autopayRechargeFlow("123", "497", "", true, "");
            throw new AssertionError("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            assertEquals("Enter a valid 10-digit mobile number", expected.getMessage());
        }
    }

    private JsonNode node(String json) throws Exception {
        return mapper.readTree(json);
    }
}
