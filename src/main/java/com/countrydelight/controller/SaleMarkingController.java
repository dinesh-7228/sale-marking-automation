package com.countrydelight.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.countrydelight.service.SaleMarkingService;
import com.countrydelight.model.OrderRequest;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

@RestController
@RequestMapping("/api/order")
@CrossOrigin(origins = "*")
public class SaleMarkingController {

    @Autowired
    private SaleMarkingService service;

    /**
     * MAIN ENDPOINT - Complete Automated Workflow
     * No manual database operations needed!
     * 
     * Automatically:
     * 1. Places order
     * 2. Generates route sheet
     * 3. Updates route_sheet_details date
     * 4. Updates order_detail date
     * 5. Marks sale
     * 6. Records sales data
     * 7. Verifies everything
     */
    @PostMapping("/place-and-mark")
    public ResponseEntity<?> placeOrderAndMark(@RequestBody OrderRequest request) {
        try {
            System.out.println("\n>>> INITIATING COMPLETE AUTOMATED WORKFLOW <<<\n");
            
            // Validate request
            if (request.getCustomerId() == null || request.getCustomerId().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Customer ID is required"
                ));
            }
            
            if (request.getProducts() == null || request.getProducts().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "At least one product is required"
                ));
            }

            // Execute complete flow
            Map<String, Object> result = service.executeCompleteFlow(request);
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "✓ COMPLETE AUTOMATION: All operations completed successfully!",
                "data", result
            ));
            
        } catch (IllegalArgumentException e) {
            System.out.println("❌ Validation Error: " + e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", "Validation error: " + e.getMessage()
            ));
        } catch (Exception e) {
            System.out.println("❌ Execution Error: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", "Error during workflow execution: " + e.getMessage(),
                "errorType", e.getClass().getSimpleName()
            ));
        }
    }

    /**
     * Legacy endpoint - kept for backward compatibility
     */
    @PostMapping("/run")
    public ResponseEntity<?> runFlow(@RequestParam String customerId,
                                     @RequestParam List<Integer> productId,
                                     @RequestParam List<Integer> qty) {
        try {
            service.executeFlow(customerId, productId, qty);
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "✓ Sale Marking Completed Successfully!"
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", "Error: " + e.getMessage()
            ));
        }
    }

    /**
     * Get route sheet details for a customer
     */
    @GetMapping("/route-sheet/{customerId}")
    public ResponseEntity<?> getRouteSheetDetails(@PathVariable String customerId) {
        try {
            Map<String, Object> details = service.getRouteSheetDetails(customerId);
            return ResponseEntity.ok(Map.of(
                "success", true,
                "data", details
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", "Error fetching route sheet: " + e.getMessage()
            ));
        }
    }

    /**
     * Search customer by mobile number
     * Returns customer details including id (DB_id) and customer_id
     */
    @GetMapping("/customer/search")
    public ResponseEntity<?> searchCustomerByPhone(@RequestParam String phone) {
        try {
            if (phone == null || phone.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Phone number is required"
                ));
            }
            
            System.out.println("\n>>> Searching customer by phone: " + phone);
            List<Map<String, Object>> customers = service.searchCustomerByPhone(phone);
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "count", customers.size(),
                "data", customers,
                "message", "✓ Customer search completed"
            ));
        } catch (Exception e) {
            System.out.println("❌ Error searching customer: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", "Error searching customer: " + e.getMessage()
            ));
        }
    }

    /**
     * Get customer details by DB ID
     * Returns full customer information including franchise_id, city_id, etc.
     */
    @GetMapping("/customer/details/{dbId}")
    public ResponseEntity<?> getCustomerDetails(@PathVariable String dbId) {
        try {
            if (dbId == null || dbId.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "DB ID is required"
                ));
            }
            
            System.out.println("\n>>> Fetching customer details for DB ID: " + dbId);
            Map<String, Object> customerDetails = service.getCustomerDetails(dbId);
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "data", customerDetails,
                "message", "✓ Customer details retrieved"
            ));
        } catch (Exception e) {
            System.out.println("❌ Error fetching customer details: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", "Error fetching customer details: " + e.getMessage()
            ));
        }
    }
}


