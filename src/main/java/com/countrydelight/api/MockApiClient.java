package com.countrydelight.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import java.util.*;

/**
 * Mock API Client for testing without external CMS dependency.
 * This returns realistic test data for development and demonstration.
 */
@Component
public class MockApiClient {
    private ObjectMapper mapper = new ObjectMapper();

    /**
     * Mock: Search customer by phone
     */
    public List<Map<String, Object>> searchCustomerByPhone(String phone) {
        List<Map<String, Object>> customers = new ArrayList<>();
        
        // Return mock data for known test phones
        if ("9810788205".equals(phone)) {
            Map<String, Object> customer = new HashMap<>();
            customer.put("id", "9938341");
            customer.put("db_id", "9938341");
            customer.put("name", "Dinesh Kumar");
            customer.put("phone", "9810788205");
            customer.put("email", "dinesh@countrydelight.in");
            customer.put("city", "Gurgaon");
            customer.put("city_id", 21);
            customer.put("franchise_id", 16);
            customer.put("address", "123 Main Street, Gurgaon");
            customer.put("credit_limit", 50000);
            customer.put("status", "active");
            customers.add(customer);
        } else {
            // Return generic mock customer for any other phone
            Map<String, Object> customer = new HashMap<>();
            customer.put("id", "9999999");
            customer.put("db_id", "9999999");
            customer.put("name", "Test Customer");
            customer.put("phone", phone);
            customer.put("city", "Delhi");
            customer.put("city_id", 1);
            customer.put("franchise_id", 5);
            customer.put("address", "Test Address");
            customer.put("credit_limit", 30000);
            customer.put("status", "active");
            customers.add(customer);
        }
        
        return customers;
    }

    /**
     * Mock: Fetch customer details
     */
    public Map<String, Object> getCustomerDetails(String customerId) {
        Map<String, Object> details = new HashMap<>();
        details.put("id", customerId);
        details.put("name", "Dinesh Kumar");
        details.put("phone", "9810788205");
        details.put("email", "dinesh@countrydelight.in");
        details.put("city_id", 21);
        details.put("franchise_id", 16);
        details.put("city_name", "Gurgaon");
        details.put("address", "123 Main Street, Gurgaon");
        details.put("credit_limit", 50000);
        details.put("credit_used", 15000);
        details.put("status", "active");
        details.put("created_at", "2024-01-01T00:00:00");
        return details;
    }

    /**
     * Mock: Fetch products
     */
    public List<Map<String, Object>> fetchProducts(String customerId, Integer cityId) {
        List<Map<String, Object>> products = new ArrayList<>();
        
        // Create mock product list with realistic data
        String[][] productData = {
            {"382", "Desi Danedar Ghee 900ML", "450.00", "false", "true"},
            {"383", "A2 Cow Ghee 500ML", "320.00", "false", "true"},
            {"384", "Buffalo Ghee 1LT", "380.00", "false", "true"},
            {"385", "Cow Milk 1LT", "65.00", "true", "false"},
            {"386", "Buffalo Milk 1LT", "55.00", "true", "false"},
            {"387", "Curd 400GM", "45.00", "true", "false"},
            {"388", "Paneer 500GM", "280.00", "true", "true"},
            {"389", "Butter 200GM", "150.00", "true", "true"},
            {"390", "Mozzarella Cheese 200GM", "220.00", "true", "true"},
            {"391", "Yogurt 500ML", "50.00", "true", "false"}
        };
        
        for (String[] data : productData) {
            Map<String, Object> product = new HashMap<>();
            product.put("id", Integer.parseInt(data[0]));
            product.put("name", data[1]);
            product.put("price", Double.parseDouble(data[2]));
            product.put("is_frozen", Boolean.parseBoolean(data[3]));
            product.put("is_packaging_required", Boolean.parseBoolean(data[4]));
            product.put("order_frequency", 25 + (Math.random() * 50));
            product.put("available_quantity", 1000);
            
            List<String> orderTypes = Arrays.asList("One Time", "Daily", "Alternate");
            product.put("order_types", orderTypes);
            
            products.add(product);
        }
        
        return products;
    }

    /**
     * Mock: Place order
     */
    public String placeOrder(String customerId, List<Integer> productIds, 
                            List<Integer> qty, List<String> orderTypes) {
        Map<String, Object> response = new HashMap<>();
        response.put("order_id", "ORD-" + System.currentTimeMillis());
        response.put("customer_id", customerId);
        response.put("total_items", productIds.size());
        response.put("status", "placed");
        response.put("timestamp", System.currentTimeMillis());
        
        try {
            return mapper.writeValueAsString(response);
        } catch (Exception e) {
            return "{\"error\": \"Failed to serialize response\"}";
        }
    }

    /**
     * Mock: Generate route sheet
     */
    public Map<String, Object> generateRouteSheet(String customerId) {
        Map<String, Object> response = new HashMap<>();
        response.put("route_sheet_id", "RS-" + System.currentTimeMillis());
        response.put("customer_id", customerId);
        response.put("delivery_id", Long.valueOf(System.currentTimeMillis() / 1000));
        response.put("status", "generated");
        response.put("date", new java.util.Date().toString());
        response.put("franchise_id", 16);
        return response;
    }

    /**
     * Mock: Sale marking (delivery)
     */
    public void saleMarking(Long deliveryId, List<Map<String, Object>> products, 
                           Double lat, Double lon) {
        // Mock implementation - just return successfully
        System.out.println("Mock: Sale marked for delivery " + deliveryId);
    }
}
