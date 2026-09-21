package com.countrydelight.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.countrydelight.service.SaleMarkingService;
import com.countrydelight.model.OrderRequest;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@RestController
@RequestMapping("/api/order")
@CrossOrigin(origins = "*")
public class SaleMarkingController {

    @Autowired
    private SaleMarkingService service;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final ExecutorService workflowExecutor = Executors.newSingleThreadExecutor();

    /**
     * STREAMING ENDPOINT - Complete Automated Workflow with live progress.
     * Emits Server-Sent Events while the workflow runs:
     *   - event "progress" data {type:"log"|"step", message/step, status, timestamp}
     *   - event "result"    data {success, message, data}
     *   - event "error"     data {success:false, message}
     */
    @PostMapping(value = "/place-and-mark/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter placeAndMarkStream(@RequestBody OrderRequest request) {
        SseEmitter emitter = new SseEmitter(0L);
        workflowExecutor.execute(() -> {
            try {
                Map<String, Object> result = service.executeCompleteFlow(request, evt -> {
                    try {
                        emitter.send(SseEmitter.event()
                                .name("progress")
                                .data(objectMapper.writeValueAsString(evt), MediaType.APPLICATION_JSON));
                    } catch (Exception ex) {
                        throw new RuntimeException("Progress emit failed", ex);
                    }
                });

                Map<String, Object> successResponse = new HashMap<>();
                successResponse.put("success", true);
                successResponse.put("message", "✓ COMPLETE AUTOMATION: All operations completed successfully!");
                successResponse.put("data", result);

                emitter.send(SseEmitter.event()
                        .name("result")
                        .data(objectMapper.writeValueAsString(successResponse), MediaType.APPLICATION_JSON));
                emitter.complete();
            } catch (Exception e) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("message", "Error during workflow execution: " + e.getMessage());
                errorResponse.put("errorType", e.getClass().getSimpleName());
                try {
                    emitter.send(SseEmitter.event()
                            .name("error")
                            .data(objectMapper.writeValueAsString(errorResponse), MediaType.APPLICATION_JSON));
                } catch (Exception sendEx) {
                    // ignore - client likely disconnected
                }
                emitter.complete();
            }
        });
        return emitter;
    }

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
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("message", "Customer ID is required");
                return ResponseEntity.badRequest().body(errorResponse);
            }
            
            if (request.getProducts() == null || request.getProducts().isEmpty()) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("message", "At least one product is required");
                return ResponseEntity.badRequest().body(errorResponse);
            }

            // Execute complete flow
            Map<String, Object> result = service.executeCompleteFlow(request);
            
            Map<String, Object> successResponse = new HashMap<>();
            successResponse.put("success", true);
            successResponse.put("message", "✓ COMPLETE AUTOMATION: All operations completed successfully!");
            successResponse.put("data", result);
            
            return ResponseEntity.ok(successResponse);
            
        } catch (IllegalArgumentException e) {
            System.out.println("❌ Validation Error: " + e.getMessage());
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Validation error: " + e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        } catch (Exception e) {
            System.out.println("❌ Execution Error: " + e.getMessage());
            e.printStackTrace();
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Error during workflow execution: " + e.getMessage());
            errorResponse.put("errorType", e.getClass().getSimpleName());
            return ResponseEntity.badRequest().body(errorResponse);
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
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "✓ Sale Marking Completed Successfully!");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Error: " + e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    /**
     * Fetch active non-delivery reasons from the `issue` table (id DESC).
     * Used to populate the ND Reason dropdown for the current environment DB.
     */
    @GetMapping("/nd-reasons")
    public ResponseEntity<?> getNdReasons() {
        try {
            List<Map<String, Object>> reasons = service.getNdReasons();
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("count", reasons.size());
            response.put("data", reasons);
            response.put("message", "✓ ND reasons fetched from issue table");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Error fetching ND reasons: " + e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    /**
     * Fetch remark -> sale_distribution_detail mapping for a selected issue id
     */
    @GetMapping("/nd-remarks/{issueId}")
    public ResponseEntity<?> getRemarkMapping(@PathVariable Integer issueId) {
        try {
            List<Map<String, Object>> remarks = service.getRemarkMapping(issueId);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("issueId", issueId);
            response.put("count", remarks.size());
            response.put("data", remarks);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Error fetching remark mapping: " + e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    /**
     * Get route sheet details for a customer
     */
    @GetMapping("/route-sheet/{customerId}")
    public ResponseEntity<?> getRouteSheetDetails(@PathVariable String customerId) {
        try {
            Map<String, Object> details = service.getRouteSheetDetails(customerId);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", details);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Error fetching route sheet: " + e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
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
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("message", "Phone number is required");
                return ResponseEntity.badRequest().body(errorResponse);
            }
            
            System.out.println("\n>>> Searching customer by phone: " + phone);
            List<Map<String, Object>> customers = service.searchCustomerByPhone(phone);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("count", customers.size());
            response.put("data", customers);
            response.put("message", "✓ Customer search completed");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            System.out.println("❌ Error searching customer: " + e.getMessage());
            e.printStackTrace();
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Error searching customer: " + e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
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
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("message", "DB ID is required");
                return ResponseEntity.badRequest().body(errorResponse);
            }
            
            System.out.println("\n>>> Fetching customer details for DB ID: " + dbId);
            Map<String, Object> customerDetails = service.getCustomerDetails(dbId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", customerDetails);
            response.put("message", "✓ Customer details retrieved");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            System.out.println("❌ Error fetching customer details: " + e.getMessage());
            e.printStackTrace();
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Error fetching customer details: " + e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }
}
