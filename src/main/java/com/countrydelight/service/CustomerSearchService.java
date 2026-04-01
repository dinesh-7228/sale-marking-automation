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
        validationResult.put("message", "");
        
        if (customer.get("franchise_id") == null || customer.get("franchise_id").toString().trim().isEmpty() || customer.get("franchise_id").toString().equals("null")) {
            validationResult.put("isValid", false);
            validationResult.put("message", "Please update the customer address and franchise information in the system");
        }
        
        if (customer.get("city_id") == null) {
            validationResult.put("isValid", false);
            validationResult.put("message", "Customer city information is missing");
        }
        
        return validationResult;
    }
}
