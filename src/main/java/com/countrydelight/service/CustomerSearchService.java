package com.countrydelight.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.countrydelight.api.ApiClient;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

@Service
public class CustomerSearchService {

    @Autowired
    private ApiClient apiClient;
    
    @Autowired
    private com.countrydelight.db.DatabaseUtil databaseUtil;
    
    private final ObjectMapper objectMapper = new ObjectMapper();

    public List<Map<String, Object>> searchCustomerByPhone(String phoneNumber) throws Exception {
        if (phoneNumber == null || phoneNumber.trim().isEmpty()) {
            throw new IllegalArgumentException("Phone number cannot be empty");
        }

        try {
            System.out.println("🔍 Searching customer by phone: " + phoneNumber);
            
            // Use ApiClient to search customers
            List<Map<String, Object>> customers = apiClient.searchCustomerByPhone(phoneNumber);
            
            if (customers.isEmpty()) {
                System.out.println("✗ No customers found");
                return customers;
            }
            
            System.out.println("✓ Found " + customers.size() + " customers");
            return customers;
            
        } catch (Exception e) {
            System.err.println("✗ Customer search failed: " + e.getMessage());
            throw new RuntimeException("Error searching customer: " + e.getMessage(), e);
        }
    }

    public Map<String, Object> validateCustomer(Map<String, Object> customer) throws Exception {
        Map<String, Object> validationResult = new HashMap<>();
        validationResult.put("isValid", true);
        validationResult.put("message", "Customer validation passed");
        
        // Note: No longer blocking users for missing franchise, area, or city
        // These will be fetched from customer_attributes table separately
        
        return validationResult;
    }

    /**
     * Fetch customer attributes (area, franchise, city) from customer_attributes table
     * Returns empty map if no attributes found - user is NOT blocked
     */
    public Map<String, Object> getCustomerAttributes(String customerId) throws Exception {
        if (customerId == null || customerId.trim().isEmpty()) {
            throw new IllegalArgumentException("Customer ID cannot be empty");
        }

        try {
            System.out.println("🔍 Fetching customer attributes for customer: " + customerId);
            
            List<Map<String, Object>> attributes = databaseUtil.getCustomerAttributes(customerId);
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("customerId", customerId);
            
            if (attributes.isEmpty()) {
                System.out.println("⚠️  No customer attributes found for customer: " + customerId);
                result.put("attributes", new ArrayList<>());
                result.put("hasAttributes", false);
                return result;
            }
            
            result.put("attributes", attributes);
            result.put("hasAttributes", true);
            
            // Extract primary attribute if available
            Map<String, Object> primaryAttr = attributes.get(0);
            result.put("area", primaryAttr.get("AREA"));
            result.put("franchise", primaryAttr.get("FRANCHISE"));
            result.put("city", primaryAttr.get("CITY"));
            
            System.out.println("✓ Found " + attributes.size() + " attribute(s) for customer");
            return result;
            
        } catch (Exception e) {
            System.err.println("⚠️  Failed to fetch customer attributes: " + e.getMessage());
            // Return success with empty attributes instead of throwing error
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("customerId", customerId);
            result.put("attributes", new ArrayList<>());
            result.put("hasAttributes", false);
            result.put("message", "Customer attributes not found - user can still proceed");
            return result;
        }
    }
}
