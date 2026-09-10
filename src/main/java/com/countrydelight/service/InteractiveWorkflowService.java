package com.countrydelight.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.countrydelight.api.ApiClient;
import com.countrydelight.db.DatabaseUtil;
import com.countrydelight.model.WorkflowRequest;
import java.util.*;

@Service
public class InteractiveWorkflowService {

    @Autowired
    private ApiClient apiClient;

    @Autowired
    private DatabaseUtil dbUtil;

    @Autowired
    private CustomerSearchService customerSearchService;

    @Autowired
    private ProductService productService;

    @Autowired
    private com.countrydelight.config.EnvironmentConfig envConfig;

    private String environment;
    private String customerNumber;
    private String customerId;
    private String apiCustomerId;
    private String cityId;
    private String saleDate;
    private Map<String, Object> customerDetails;
    private List<Map<String, Object>> searchResults;
    private List<Map<String, Object>> availableProducts;
    private List<Map<String, Object>> selectedProducts;
    private Boolean orderAlreadyPlaced;
    private Map<String, Object> routeSheet;
    private Long routeSheetId;

    public void setEnvironment(String environment) {
        this.environment = environment;
        // Pivot both the CMS API base/token and the database schema to the
        // selected environment (QA -> beejapuri_QA, UAT -> beejapuri_UAT).
        if (environment != null) {
            envConfig.setEnv(environment.toUpperCase());
        }
        System.out.println("Environment set to: " + environment);
    }

    public void setCustomerNumber(String customerNumber) {
        this.customerNumber = customerNumber;
        System.out.println("Customer number set to: " + customerNumber);
    }

    public String getCustomerNumber() {
        return customerNumber;
    }

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
        System.out.println("Customer ID set to: " + customerId);
    }

    public String getCustomerId() {
        return customerId;
    }

    public void setCustomerDetails(Map<String, Object> details) {
        this.customerDetails = details;
        
        // Extract city_id if available
        if (details.containsKey("delivery_address")) {
            Map<String, Object> deliveryAddress = (Map<String, Object>) details.get("delivery_address");
            if (deliveryAddress != null && deliveryAddress.containsKey("city_id")) {
                this.cityId = String.valueOf(deliveryAddress.get("city_id"));
            }
        }

        // The CMS APIs (placeOrder/addFunds) need the customer_id from the search,
        // which is distinct from the DB primary key used by route_sheet_details.
        Object apiId = details.get("customer_id");
        if (apiId == null) {
            apiId = details.get("CUSTOMER_ID");
        }
        if (apiId != null) {
            this.apiCustomerId = String.valueOf(apiId);
            System.out.println("API (CMS) customer id set to: " + apiCustomerId);
        }
    }

    public String getApiCustomerId() {
        return apiCustomerId != null ? apiCustomerId : customerId;
    }

    public String getCityId() {
        return cityId;
    }

    public void setSaleDate(String saleDate) {
        this.saleDate = saleDate;
        System.out.println("Sale date set to: " + saleDate);
    }

    public String getSaleDate() {
        return saleDate;
    }

    public void setSearchResults(List<Map<String, Object>> results) {
        this.searchResults = results;
    }

    public void setAvailableProducts(List<Map<String, Object>> products) {
        this.availableProducts = products;
    }

    public void setSelectedProducts(List<Map<String, Object>> products) {
        this.selectedProducts = products;
    }

    public List<Map<String, Object>> getSelectedProducts() {
        return selectedProducts;
    }

    public void setOrderAlreadyPlaced(Boolean orderAlreadyPlaced) {
        this.orderAlreadyPlaced = orderAlreadyPlaced;
    }

    public void setRouteSheet(Map<String, Object> routeSheet) {
        this.routeSheet = routeSheet;
    }

    public void setRouteSheetId(Long routeSheetId) {
        this.routeSheetId = routeSheetId;
        System.out.println("Route sheet ID set to: " + routeSheetId);
    }

    public Long getRouteSheetId() {
        return routeSheetId;
    }

    /**
     * Step 3: Search customer by phone
     */
    public List<Map<String, Object>> searchCustomer(String customerNumber) throws Exception {
        System.out.println("\n=== Searching customer by phone: " + customerNumber + " ===");
        return customerSearchService.searchCustomerByPhone(customerNumber);
    }

    /**
     * Step 4: Get customer details
     */
    public Map<String, Object> getCustomerDetails(String customerId) throws Exception {
        System.out.println("\n=== Fetching customer details for ID: " + customerId + " ===");
        return apiClient.getCustomerDetails(customerId);
    }

    /**
     * Step 5: Fetch products for customer
     */
    public List<Map<String, Object>> fetchProducts(String customerId, String cityId) throws Exception {
        System.out.println("\n=== Fetching products for customer: " + customerId + ", city: " + cityId + " ===");
        Integer cityIdInt = Integer.parseInt(cityId);
        return productService.fetchProducts(customerId, cityIdInt);
    }

    /**
     * Step 6 & 7: Place order
     */
    public String placeOrder(String customerId, List<Map<String, Object>> products) throws Exception {
        return placeOrder(customerId, products, null);
    }

    public String placeOrder(String customerId, List<Map<String, Object>> products, String saleDate) throws Exception {
        System.out.println("\n=== Placing order for customer: " + customerId + " ===");
        
        List<Integer> productIds = new ArrayList<>();
        List<Integer> quantities = new ArrayList<>();
        List<String> orderTypes = new ArrayList<>();
        
        for (Map<String, Object> product : products) {
            productIds.add(((Number) product.get("id")).intValue());
            quantities.add(((Number) product.get("quantity")).intValue());
            orderTypes.add((String) product.getOrDefault("order_type", "daily"));
        }
        
        String result = apiClient.placeOrder(getApiCustomerId(), productIds, quantities, orderTypes, saleDate);
        System.out.println("✓ Order placed successfully");
        return result;
    }

    /**
     * Step 8: Generate route sheet
     */
    public Map<String, Object> generateRouteSheet(String customerId) throws Exception {
        System.out.println("\n=== Generating route sheet for customer: " + customerId + " ===");
        return apiClient.generateRouteSheet(customerId);
    }

    /**
     * Extract route sheet ID from response
     */
    public Long extractRouteSheetId(Map<String, Object> routeSheet) {
        if (routeSheet.containsKey("deliveryId")) {
            return ((Number) routeSheet.get("deliveryId")).longValue();
        }
        if (routeSheet.containsKey("id")) {
            return ((Number) routeSheet.get("id")).longValue();
        }
        return null;
    }

    /**
     * Fetch the latest route_sheet_details ID for the customer directly from the DB.
     * The generate route sheet API returns no id, so this is the source of truth
     * for the delivery id used by the sale marking API (Step-11).
     */
    public Long fetchLatestRouteSheetId(String customerId) throws Exception {
        System.out.println("\n=== Fetching latest route sheet ID from DB ===");
        Map<String, Object> details = dbUtil.getRouteSheetDetails(customerId);
        if (!details.isEmpty()) {
            Long id = (Long) details.get("id");
            System.out.println("✓ Route sheet ID from DB: " + id);
            return id;
        }
        System.out.println("⚠ No route sheet found in DB for customer: " + customerId);
        return null;
    }

    /**
     * Step 9: Update route sheet date to sale marking date
     */
    public void updateRouteSheetDate(String customerId) throws Exception {
        updateRouteSheetDate(customerId, null);
    }

    public void updateRouteSheetDate(String customerId, String saleDate) throws Exception {
        System.out.println("\n=== Updating route sheet date (sale date + delivery_boy=26747) ===");
        dbUtil.updateRouteSheetDate(customerId, saleDate);
        System.out.println("✓ Route sheet date updated");
    }

    /**
     * Step 10: Update order detail date to sale marking date
     */
    public void updateOrderDetailDate(String customerId) throws Exception {
        updateOrderDetailDate(customerId, null);
    }

    public void updateOrderDetailDate(String customerId, String saleDate) throws Exception {
        System.out.println("\n=== Updating order detail date to sale marking date ===");
        dbUtil.updateOrderDetailDate(customerId, saleDate);
        System.out.println("✓ Order detail date updated");
    }

    /**
     * Step 11: Mark the sale
     */
    public void markSale(Long deliveryId, List<Map<String, Object>> products, Double latitude, Double longitude) throws Exception {
        markSale(deliveryId, products, latitude, longitude, null);
    }

    public void markSale(Long deliveryId, List<Map<String, Object>> products, Double latitude, Double longitude, String saleDate) throws Exception {
        System.out.println("\n=== Marking sale ===");
        Integer deliveryBoy = null;
        if (customerId != null) {
            deliveryBoy = dbUtil.getDeliveryBoy(customerId);
        }
        apiClient.saleMarking(deliveryId, products, latitude, longitude, deliveryBoy, saleDate);
        System.out.println("✓ Sale marked successfully");
    }

    /**
     * Execute complete workflow in one request
     */
    public Map<String, Object> executeCompleteWorkflow(WorkflowRequest request) throws Exception {
        Map<String, Object> response = new HashMap<>();
        List<Map<String, String>> steps = new ArrayList<>();
        
        try {
            // Step 1: Set environment
            if (request.environment == null || request.environment.isEmpty()) {
                throw new IllegalArgumentException("Environment is required");
            }
            setEnvironment(request.environment);
            addStep(steps, "Environment Selection", "SUCCESS");

            // Step 2: Set customer number
            if (request.customerNumber == null || request.customerNumber.isEmpty()) {
                throw new IllegalArgumentException("Customer number is required");
            }
            setCustomerNumber(request.customerNumber);
            addStep(steps, "Customer Number Entry", "SUCCESS");

            // Step 8: sale_marking_date (defaults to today if not provided)
            String saleDate = request.saleDate;
            if (saleDate == null || saleDate.trim().isEmpty()) {
                saleDate = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy"));
            }
            this.saleDate = saleDate;

            // Step 3: Search customer
            List<Map<String, Object>> customers = searchCustomer(request.customerNumber);
            if (customers.isEmpty()) {
                throw new RuntimeException("No customers found");
            }
            addStep(steps, "Search Customer", "SUCCESS");

            // Step 4: Get customer details
            if (request.customerId == null || request.customerId.isEmpty()) {
                throw new IllegalArgumentException("Customer ID is required");
            }
            Map<String, Object> details = getCustomerDetails(request.customerId);
            setCustomerId(request.customerId);
            setCustomerDetails(details);
            addStep(steps, "Get Customer Details", "SUCCESS");

            // Step 5: Check if order already placed
            setOrderAlreadyPlaced(request.orderAlreadyPlaced != null && request.orderAlreadyPlaced);

            if (!orderAlreadyPlaced) {
                // Step 6: Fetch products
                List<Map<String, Object>> products = fetchProducts(request.customerId, getCityId());
                setAvailableProducts(products);
                addStep(steps, "Fetch Products", "SUCCESS");

                // Step 7: Place order
                if (request.products == null || request.products.isEmpty()) {
                    throw new IllegalArgumentException("At least one product is required");
                }
                placeOrder(request.customerId, request.products, saleDate);
                setSelectedProducts(request.products);
                addStep(steps, "Place Order", "SUCCESS");
            } else {
                addStep(steps, "Order Already Placed - Skipped Steps 6-7", "SKIPPED");
            }

            // Step 8: Generate route sheet
            Map<String, Object> rs = generateRouteSheet(request.customerId);
            Long rsId = extractRouteSheetId(rs);
            setRouteSheet(rs);
            setRouteSheetId(rsId);
            addStep(steps, "Generate Route Sheet", "SUCCESS");

            // Step 9: Update route sheet date
            updateRouteSheetDate(request.customerId, saleDate);
            addStep(steps, "Update Route Sheet Date", "SUCCESS");

            // Step 10: Update order detail date
            updateOrderDetailDate(request.customerId, saleDate);
            addStep(steps, "Update Order Detail Date", "SUCCESS");

            // Step 11: Mark sale
            if (selectedProducts == null || selectedProducts.isEmpty()) {
                selectedProducts = request.products;
            }
            markSale(rsId, selectedProducts, request.latitude, request.longitude, saleDate);
            addStep(steps, "Mark Sale", "SUCCESS");

            response.put("success", true);
            response.put("message", "Complete workflow executed successfully");
            response.put("steps", steps);
            response.put("customerId", customerId);
            response.put("routeSheetId", routeSheetId);
            response.put("saleDate", saleDate);

        } catch (Exception e) {
            System.out.println("❌ Workflow failed: " + e.getMessage());
            response.put("success", false);
            response.put("message", "Workflow failed: " + e.getMessage());
            response.put("steps", steps);
            throw e;
        }

        return response;
    }

    /**
     * Get current workflow state
     */
    public Map<String, Object> getWorkflowState() {
        Map<String, Object> state = new HashMap<>();
        state.put("environment", environment);
        state.put("customerNumber", customerNumber);
        state.put("customerId", customerId);
        state.put("apiCustomerId", apiCustomerId);
        state.put("cityId", cityId);
        state.put("saleDate", saleDate);
        state.put("orderAlreadyPlaced", orderAlreadyPlaced);
        state.put("routeSheetId", routeSheetId);
        state.put("selectedProductsCount", selectedProducts != null ? selectedProducts.size() : 0);
        return state;
    }

    /**
     * Reset workflow state
     */
    public void resetWorkflowState() {
        environment = null;
        customerNumber = null;
        customerId = null;
        apiCustomerId = null;
        cityId = null;
        saleDate = null;
        customerDetails = null;
        searchResults = null;
        availableProducts = null;
        selectedProducts = null;
        orderAlreadyPlaced = null;
        routeSheet = null;
        routeSheetId = null;
        System.out.println("✓ Workflow state reset");
    }

    private void addStep(List<Map<String, String>> steps, String name, String status) {
        Map<String, String> step = new HashMap<>();
        step.put("step", name);
        step.put("status", status);
        step.put("timestamp", new java.util.Date().toString());
        steps.add(step);
    }
}
