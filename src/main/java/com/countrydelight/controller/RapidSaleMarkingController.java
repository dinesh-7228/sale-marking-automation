package com.countrydelight.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.countrydelight.service.RapidSaleMarkingService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/rapid-sale-marking")
@CrossOrigin(origins = "*")
public class RapidSaleMarkingController {

    @Autowired
    private RapidSaleMarkingService rapidSaleMarkingService;

    @GetMapping("/eligibility/{customerId}")
    public ResponseEntity<?> checkEligibility(@PathVariable String customerId) {
        try {
            Map<String, Object> result = rapidSaleMarkingService.checkRapidEligibility(customerId);
            result.put("success", true);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error checking rapid eligibility: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * Step-2: Fetch customer addresses from rapiddelivery DB, auto-select the
     * newest one and cache its franchise id.
     */
    @GetMapping("/addresses/{customerId}")
    public ResponseEntity<?> getAddresses(@PathVariable String customerId) {
        try {
            Map<String, Object> result = rapidSaleMarkingService.getAddressesWithAutoSelect(customerId);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error fetching addresses: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * Step-3: Fetch products for a franchise from the rapiddelivery DB
     * (product_franchise_detail active=1 -> product table).
     */
    @GetMapping("/products")
    public ResponseEntity<?> getProductsByFranchise(@RequestParam Integer franchiseId) {
        try {
            Map<String, Object> result = rapidSaleMarkingService.getProductsByFranchise(franchiseId);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error fetching products: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * Step-4: Handle a rapid order with a chosen scenario.
     * Body: { "customerId": "...", "franchiseId": 97, "selectedAddressId": 198, "products": [ {"id": 203, "quantity": 1}, ... ], "mode": "PLACE_AND_MARK"|"ONLY_MARK" }
     *
     * The selected address's franchise is normalized to 57 before the order is
     * handled. mode defaults to PLACE_AND_MARK. With ONLY_MARK no new order is
     * created; the latest already-placed order for the customer is fetched instead.
     */
    @PostMapping("/order")
    public ResponseEntity<?> placeRapidOrder(@RequestBody Map<String, Object> body) {
        try {
            String customerId = (String) body.get("customerId");
            Integer franchiseId = body.get("franchiseId") instanceof Number
                    ? ((Number) body.get("franchiseId")).intValue() : null;
            Integer selectedAddressId = body.get("selectedAddressId") instanceof Number
                    ? ((Number) body.get("selectedAddressId")).intValue() : null;
            List<Map<String, Object>> products = (List<Map<String, Object>>) body.get("products");
            String mode = (String) body.get("mode");
            Map<String, Object> result = rapidSaleMarkingService.handleRapidOrder(customerId, franchiseId, products, mode, selectedAddressId);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error handling rapid order: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * Fetch the latest already-placed rapid order for a customer
     * (Only Sale Mark scenario preview).
     */
    @GetMapping("/latest-order/{customerId}")
    public ResponseEntity<?> getLatestOrder(@PathVariable String customerId) {
        try {
            Map<String, Object> order = rapidSaleMarkingService.getLatestOrder(customerId);
            Map<String, Object> result = new HashMap<>();
            if (order == null) {
                result.put("success", false);
                result.put("message", "No already-placed rapid order found for customer " + customerId);
                return ResponseEntity.ok(result);
            }
            result.put("success", true);
            result.put("customerId", customerId);
            result.put("orderId", order.get("id"));
            result.put("orderNumber", order.get("order_number"));
            result.put("orderStatus", order.get("status"));
            result.put("franchiseId", order.get("franchise"));
            result.put("order", order);
            result.put("message", "Latest rapid order " + order.get("order_number") + " fetched for customer " + customerId);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error fetching latest order: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }
}