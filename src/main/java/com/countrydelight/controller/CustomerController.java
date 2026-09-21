package com.countrydelight.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.countrydelight.service.CustomerSearchService;
import com.countrydelight.api.ApiClient;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;

@RestController
@RequestMapping("/api/customer")
@CrossOrigin(origins = "*")
public class CustomerController {

    @Autowired
    private CustomerSearchService customerSearchService;
    
    @Autowired
    private ApiClient apiClient;

    @GetMapping("/search")
    public ResponseEntity<?> searchByPhone(@RequestParam String phone) {
        try {
            List<Map<String, Object>> customers = customerSearchService.searchCustomerByPhone(phone);
            
            if (customers.isEmpty()) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", "No customers found with this phone number");
                return ResponseEntity.ok(response);
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", customers);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error searching customer: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @GetMapping("/details/{customerId}")
    public ResponseEntity<?> getCustomerDetails(@PathVariable String customerId) {
        try {
            Map<String, Object> customerDetails = apiClient.getCustomerDetails(customerId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", customerDetails);
            
            // Extract city_id and franchise_id
            if (customerDetails.containsKey("delivery_address")) {
                Map<String, Object> deliveryAddress = (Map<String, Object>) customerDetails.get("delivery_address");
                response.put("city_id", deliveryAddress.get("city_id"));
                response.put("city_name", deliveryAddress.get("city_name"));
            }
            
            if (customerDetails.containsKey("franchise_id")) {
                response.put("franchise_id", customerDetails.get("franchise_id"));
            }
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error fetching customer details: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @GetMapping("/attributes/{customerId}")
    public ResponseEntity<?> getCustomerAttributes(@PathVariable String customerId) {
        try {
            Map<String, Object> attributes = customerSearchService.getCustomerAttributes(customerId);
            return ResponseEntity.ok(attributes);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("customerId", customerId);
            response.put("attributes", new ArrayList<>());
            response.put("hasAttributes", false);
            response.put("message", "Customer attributes not available - " + e.getMessage());
            return ResponseEntity.ok(response);
        }
    }

    @PostMapping("/validate")
    public ResponseEntity<?> validateCustomer(@RequestBody Map<String, Object> customer) {
        try {
            Map<String, Object> validation = customerSearchService.validateCustomer(customer);
            return ResponseEntity.ok(validation);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Validation error: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }
}
