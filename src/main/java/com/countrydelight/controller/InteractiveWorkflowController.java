package com.countrydelight.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.countrydelight.service.InteractiveWorkflowService;
import com.countrydelight.model.WorkflowRequest;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

@RestController
@RequestMapping("/api/workflow")
@CrossOrigin(origins = "*")
public class InteractiveWorkflowController {

    @Autowired
    private InteractiveWorkflowService workflowService;

    /**
     * Step 1: Ask for environment (QA or UAT)
     */
    @PostMapping("/step1-environment")
    public ResponseEntity<?> selectEnvironment(@RequestBody Map<String, String> request) {
        try {
            String environment = request.get("environment");
            
            if (environment == null || environment.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Environment is required (QA or UAT)"
                ));
            }
            
            if (!environment.equalsIgnoreCase("QA") && !environment.equalsIgnoreCase("UAT")) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Environment must be QA or UAT"
                ));
            }
            
            workflowService.setEnvironment(environment.toUpperCase());
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Environment selected: " + environment.toUpperCase(),
                "nextStep", "Step 2: Enter customer number"
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", "Error: " + e.getMessage()
            ));
        }
    }

    /**
     * Step 2: Ask for customer number
     */
    @PostMapping("/step2-customer-number")
    public ResponseEntity<?> enterCustomerNumber(@RequestBody Map<String, String> request) {
        try {
            String customerNumber = request.get("customerNumber");
            
            if (customerNumber == null || customerNumber.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Customer number is required"
                ));
            }
            
            workflowService.setCustomerNumber(customerNumber);
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Customer number received: " + customerNumber,
                "nextStep", "Step 3: Searching customer..."
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", "Error: " + e.getMessage()
            ));
        }
    }

    /**
     * Step 3: Search customer by phone number
     */
    @PostMapping("/step3-search-customer")
    public ResponseEntity<?> searchCustomer() {
        try {
            String customerNumber = workflowService.getCustomerNumber();
            
            if (customerNumber == null || customerNumber.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Customer number not set. Please complete step 2 first."
                ));
            }
            
            List<Map<String, Object>> customers = workflowService.searchCustomer(customerNumber);
            
            if (customers.isEmpty()) {
                return ResponseEntity.ok(Map.of(
                    "success", false,
                    "message", "No customers found with this phone number"
                ));
            }
            
            workflowService.setSearchResults(customers);
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Found " + customers.size() + " customer(s)",
                "data", customers,
                "nextStep", "Step 4: Select a customer and get details"
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", "Error searching customer: " + e.getMessage()
            ));
        }
    }

    /**
     * Step 4: Get customer details by ID
     */
    @PostMapping("/step4-customer-details")
    public ResponseEntity<?> getCustomerDetails(@RequestBody Map<String, String> request) {
        try {
            String customerId = request.get("customerId");
            
            if (customerId == null || customerId.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Customer ID is required"
                ));
            }
            
            Map<String, Object> customerDetails = workflowService.getCustomerDetails(customerId);
            
            workflowService.setCustomerId(customerId);
            workflowService.setCustomerDetails(customerDetails);
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Customer details retrieved",
                "data", customerDetails,
                "nextStep", "Step 5: Check if order already placed or proceed to fetch products"
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", "Error fetching customer details: " + e.getMessage()
            ));
        }
    }

    /**
     * Step 5: Check if order is already placed
     * If yes: skip to step 8 (generate route sheet)
     * If no: fetch products
     */
    @PostMapping("/step5-check-order-status")
    public ResponseEntity<?> checkOrderStatus(@RequestBody Map<String, Object> request) {
        try {
            Boolean orderAlreadyPlaced = (Boolean) request.get("orderAlreadyPlaced");
            
            if (orderAlreadyPlaced == null) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "orderAlreadyPlaced flag is required"
                ));
            }
            
            workflowService.setOrderAlreadyPlaced(orderAlreadyPlaced);
            
            if (orderAlreadyPlaced) {
                return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Order already placed. Skipping steps 6-7 (place order).",
                    "nextStep", "Step 8: Generate route sheet"
                ));
            } else {
                // Fetch products
                String customerId = workflowService.getCustomerId();
                String cityId = workflowService.getCityId();
                
                if (customerId == null || cityId == null) {
                    return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "message", "Customer ID or City ID not found. Complete previous steps first."
                    ));
                }
                
                List<Map<String, Object>> products = workflowService.fetchProducts(customerId, cityId);
                workflowService.setAvailableProducts(products);
                
                return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Found " + products.size() + " available products",
                    "data", products,
                    "nextStep", "Step 6: Select products and quantities"
                ));
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", "Error checking order status: " + e.getMessage()
            ));
        }
    }

    /**
     * Step 6 & 7: Select products, quantities, and place order
     */
    @PostMapping("/step6-place-order")
    public ResponseEntity<?> selectProductsAndPlaceOrder(@RequestBody Map<String, Object> request) {
        try {
            List<Map<String, Object>> selectedProducts = (List<Map<String, Object>>) request.get("products");
            
            if (selectedProducts == null || selectedProducts.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "At least one product is required"
                ));
            }
            
            String customerId = workflowService.getCustomerId();
            
            workflowService.setSelectedProducts(selectedProducts);
            
            // Step-8: sale_marking_date (defaults to current date)
            String saleDate = request.get("saleDate") != null ? request.get("saleDate").toString() : null;
            if (saleDate == null || saleDate.trim().isEmpty()) {
                saleDate = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy"));
            }
            workflowService.setSaleDate(saleDate);
            
            // Place order
            String orderResult = workflowService.placeOrder(customerId, selectedProducts, saleDate);
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Order placed successfully",
                "orderResult", orderResult,
                "saleDate", saleDate,
                "nextStep", "Step 8: Generate route sheet"
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", "Error placing order: " + e.getMessage()
            ));
        }
    }

    /**
     * Step 8: Generate route sheet (Voice API) + verify it exists for TOMORROW
     */
    @PostMapping("/step8-generate-route-sheet")
    public ResponseEntity<?> generateRouteSheet() {
        try {
            String customerId = workflowService.getCustomerId();
            
            if (customerId == null || customerId.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Customer ID not found. Complete previous steps first."
                ));
            }

            // Hit the CMS voice API to generate the route sheet
            Map<String, Object> routeSheet = workflowService.generateRouteSheet(customerId);
            workflowService.setRouteSheet(routeSheet);

            // Verify route sheet exists for TOMORROW — exit if it does not
            String tomorrow = java.time.LocalDate.now().plusDays(1).toString();
            Long tomorrowId = workflowService.getRouteSheetIdForTomorrow(customerId);
            if (tomorrowId == null) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "EXITING: No route sheet generated for tomorrow (" + tomorrow + ") for customer " + customerId + ". Cannot proceed."
                ));
            }

            workflowService.setRouteSheetId(tomorrowId);

            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Route sheet generated & verified for tomorrow (" + tomorrow + ")",
                "routeSheetId", tomorrowId,
                "routeSheetDate", tomorrow,
                "nextStep", "Step 9: Update route sheet date to today"
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", "Error generating route sheet: " + e.getMessage()
            ));
        }
    }

    /**
     * Step 9: Update route sheet date to today
     */
    @PostMapping("/step9-update-route-sheet-date")
    public ResponseEntity<?> updateRouteSheetDate() {
        try {
            String customerId = workflowService.getCustomerId();
            
            if (customerId == null || customerId.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Customer ID not found"
                ));
            }
            
            workflowService.updateRouteSheetDate(customerId, workflowService.getSaleDate());
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Route sheet date updated to sale marking date with delivery_boy=26747",
                "nextStep", "Step 10: Update order details date to sale marking date"
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", "Warning - Route sheet date update failed: " + e.getMessage()
            ));
        }
    }

    /**
     * Step 10: Update order_details order_start_date to sale marking date
     */
    @PostMapping("/step10-update-order-date")
    public ResponseEntity<?> updateOrderDate() {
        try {
            String customerId = workflowService.getCustomerId();
            
            if (customerId == null || customerId.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Customer ID not found"
                ));
            }
            
            workflowService.updateOrderDetailDate(customerId, workflowService.getSaleDate());
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Order detail date updated to sale marking date",
                "nextStep", "Step 11: Mark the sale"
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", "Warning - Order detail date update failed: " + e.getMessage()
            ));
        }
    }

    /**
     * Step 11: Mark the sale
     */
    @PostMapping("/step11-mark-sale")
    public ResponseEntity<?> markSale(@RequestBody(required = false) Map<String, Object> request) {
        try {
            String customerId = workflowService.getCustomerId();
            Long deliveryId = workflowService.getRouteSheetId();
            
            if (customerId == null || customerId.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Customer ID not found"
                ));
            }
            
            if (deliveryId == null) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Route sheet ID not found. Complete step 8 first."
                ));
            }
            
            Double latitude = null;
            Double longitude = null;
            
            if (request != null) {
                Object lat = request.get("latitude");
                Object lon = request.get("longitude");
                
                if (lat != null) latitude = ((Number) lat).doubleValue();
                if (lon != null) longitude = ((Number) lon).doubleValue();
            }
            
            String saleType = request != null ? (String) request.get("saleType") : null;
            if (saleType == null || saleType.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Please select Full Sale or Non Delivery."
                ));
            }
            saleType = saleType.trim().toUpperCase();
            if (!"FULL_SALE".equals(saleType) && !"NON_DELIVERY".equals(saleType)) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Sale type must be FULL_SALE or NON_DELIVERY"
                ));
            }
            
            Integer ndReasonId = null;
            if ("NON_DELIVERY".equals(saleType)) {
                Object ndReason = request != null ? request.get("ndReasonId") : null;
                if (ndReason == null) {
                    return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "message", "Please select an ND Reason."
                    ));
                }
                ndReasonId = ((Number) ndReason).intValue();
            }
            
            List<Map<String, Object>> products = workflowService.getSelectedProducts();
            if (products == null || products.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "No products found. Complete previous steps first."
                ));
            }
            
            workflowService.markSale(deliveryId, products, latitude, longitude, workflowService.getSaleDate(), saleType, ndReasonId);
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "saleType", saleType,
                "ndReasonId", ndReasonId,
                "message", "Sale " + (ndReasonId != null ? "(NON_DELIVERY)" : "marked") + " successfully",
                "nextStep", "Workflow completed!"
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", "Error marking sale: " + e.getMessage()
            ));
        }
    }

    /**
     * Complete workflow in one request
     */
    @PostMapping("/complete")
    public ResponseEntity<?> completeWorkflow(@RequestBody WorkflowRequest request) {
        try {
            Map<String, Object> result = workflowService.executeCompleteWorkflow(request);
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Complete workflow executed successfully",
                "data", result
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", "Error executing workflow: " + e.getMessage()
            ));
        }
    }

    /**
     * Fetch active non-delivery reasons from the `issue` table (id DESC)
     * for the current environment — used to populate the ND Reason dropdown.
     */
    @GetMapping("/nd-reasons")
    public ResponseEntity<?> getNdReasons() {
        try {
            List<Map<String, Object>> reasons = workflowService.getNdReasons();
            return ResponseEntity.ok(Map.of(
                "success", true,
                "count", reasons.size(),
                "data", reasons,
                "message", "✓ ND reasons fetched from issue table"
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", "Error fetching ND reasons: " + e.getMessage()
            ));
        }
    }

    /**
     * Get current workflow state
     */
    @GetMapping("/state")
    public ResponseEntity<?> getWorkflowState() {
        try {
            Map<String, Object> state = workflowService.getWorkflowState();
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "data", state
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", "Error getting workflow state: " + e.getMessage()
            ));
        }
    }

    /**
     * Reset workflow state
     */
    @PostMapping("/reset")
    public ResponseEntity<?> resetWorkflow() {
        try {
            workflowService.resetWorkflowState();
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Workflow state reset. Ready for new workflow."
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", "Error resetting workflow: " + e.getMessage()
            ));
        }
    }
}
