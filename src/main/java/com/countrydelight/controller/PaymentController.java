package com.countrydelight.controller;

import com.countrydelight.service.PaymentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * Payment endpoints (payment.md): Recharge Once series.
 *
 *   POST /api/payment/recharge            body { phone, amount, configId? }
 *   GET  /api/payment/autopay-configs     ?smart_plan=yes|no|all  (autopay_config table)
 *
 * The recharge endpoint runs the payment.md APIs in series:
 * wallet_screen → juspay/offers → paymentMethods2 → getSavedCardAndRecentPayments
 * (cashback extracted here) → generateTransaction → transaction status.
 */
@RestController
@RequestMapping("/api/payment")
@CrossOrigin(origins = "*")
public class PaymentController {

    @Autowired
    private PaymentService paymentService;

    /**
     * Recharge Once series.
     * Body: { "phone": "9810788205", "amount": "16000",
     *         "configId": "5"(optional),
     *         "paymentStatus": "SUCCESS"|"PENDING"|"FAILED"(optional),
     *         "offerId": "439"(optional, pre-applied via offers/recharge/apply),
     *         "customerOfferId": "12777681"(optional) }
     *
     * When paymentStatus is provided the generated payment_requests row is
     * force-updated to that status in beejapuri_QA (the real Juspay completion
     * step can't run server-side), enabling multiple transaction records with
     * different payment statuses.
     */
    @PostMapping("/recharge")
    public ResponseEntity<?> recharge(@RequestBody(required = false) Map<String, Object> request) {
        try {
            String phone = request == null ? null : String.valueOf(request.get("phone"));
            String amount = request == null ? null : String.valueOf(request.get("amount"));
            String configId = request == null || request.get("configId") == null ? null : String.valueOf(request.get("configId"));
            String paymentStatus = request == null || request.get("paymentStatus") == null ? null : String.valueOf(request.get("paymentStatus"));
            String offerId = request == null || request.get("offerId") == null ? null : String.valueOf(request.get("offerId"));
            String customerOfferId = request == null || request.get("customerOfferId") == null ? null : String.valueOf(request.get("customerOfferId"));

            Map<String, Object> data = paymentService.rechargeOnce(phone, amount, configId, paymentStatus,
                    offerId, customerOfferId);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", data);
            String statusNote = data.get("transaction_status") == null ? ""
                    : " · Status: " + data.get("transaction_status");
            response.put("message", "✓ Recharge processed. Amount ₹" + data.get("amount")
                    + (data.get("cashback") == null || data.get("cashback").toString().isEmpty()
                        ? "" : " · Cashback ₹" + data.get("cashback"))
                    + statusNote);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(badRequest(e.getMessage()));
        } catch (Exception e) {
            System.out.println("❌ Recharge error: " + e.getMessage());
            return ResponseEntity.ok(error("Recharge failed: " + e.getMessage()));
        }
    }

    /** Lists autopay_config rows (beejapuri DB) filtered by smart_plan. */
    @GetMapping("/autopay-configs")
    public ResponseEntity<?> autopayConfigs(@RequestParam(value = "smart_plan", required = false) String smartPlan) {
        try {
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", paymentService.getAutopayConfigs(smartPlan));
            response.put("message", "✓ AutoPay configs fetched");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            System.out.println("❌ autopay_config load error: " + e.getMessage());
            return ResponseEntity.ok(error("Failed to load configs: " + e.getMessage()));
        }
    }

    /** Fetch recharge/payment offers applicable to a customer (API-2 juspay/offers). */
    @GetMapping("/offers")
    public ResponseEntity<?> rechargeOffers(@RequestParam("phone") String phone,
                                            @RequestParam(value = "amount", required = false, defaultValue = "2000") String amount) {
        try {
            Map<String, Object> data = paymentService.getRechargeOffers(phone, amount);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", data);
            response.put("message", "✓ Recharge offers fetched for " + phone + " (amount ₹" + data.get("amount") + ")");
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(badRequest(e.getMessage()));
        } catch (Exception e) {
            System.out.println("❌ Recharge offers error: " + e.getMessage());
            return ResponseEntity.ok(error("Failed to fetch offers: " + e.getMessage()));
        }
    }

    /** Fetch the latest recharge offers (app's offers/recharge/fetch). */
    @GetMapping("/offers/recharge-fetch")
    public ResponseEntity<?> fetchRechargeOffers(@RequestParam("phone") String phone) {
        try {
            Map<String, Object> data = paymentService.fetchRechargeOffers(phone);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", data);
            response.put("message", "✓ Recharge offers fetched for " + phone);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(badRequest(e.getMessage()));
        } catch (Exception e) {
            System.out.println("❌ Recharge offers fetch error: " + e.getMessage());
            return ResponseEntity.ok(error("Failed to fetch offers: " + e.getMessage()));
        }
    }

    /** Apply a recharge offer (app's offers/recharge/apply). */
    @PostMapping("/offers/recharge-apply")
    public ResponseEntity<?> applyRechargeOffer(@RequestBody(required = false) Map<String, Object> request) {
        try {
            String phone = request == null ? null : String.valueOf(request.get("phone"));
            String rechargeAmount = request == null ? null : String.valueOf(request.get("recharge_amount"));
            String couponCode = request == null ? null : String.valueOf(request.get("coupon_code"));
            String offerId = request == null ? null : String.valueOf(request.get("offer_id"));

            Map<String, Object> data = paymentService.applyRechargeOffer(phone, rechargeAmount, couponCode, offerId);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", data);
            response.put("message", "✓ Offer applied: " + (data.get("message") == null ? "" : data.get("message")));
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(badRequest(e.getMessage()));
        } catch (Exception e) {
            System.out.println("❌ Recharge offer apply error: " + e.getMessage());
            return ResponseEntity.ok(error("Failed to apply offer: " + e.getMessage()));
        }
    }

    private Map<String, Object> badRequest(String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("message", message);
        response.put("error", true);
        return response;
    }

    private Map<String, Object> error(String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("error", true);
        response.put("success", false);
        response.put("message", message);
        return response;
    }
}