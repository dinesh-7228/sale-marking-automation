package com.countrydelight.api;

import org.springframework.stereotype.Component;
import java.util.*;

/**
 * Mock API Client - Removed all mock data
 * Production implementation should replace this with real API calls to CMS, Voice, and Delivery systems
 */
@Component
public class MockApiClient {

    public List<Map<String, Object>> searchCustomerByPhone(String phone) {
        throw new UnsupportedOperationException("Mock implementation removed. Use real CMS API endpoint.");
    }

    public Map<String, Object> getCustomerDetails(String customerId) {
        throw new UnsupportedOperationException("Mock implementation removed. Use real CMS API endpoint.");
    }

    public List<Map<String, Object>> fetchProducts(String customerId, Integer cityId) {
        throw new UnsupportedOperationException("Mock implementation removed. Use real CMS API endpoint.");
    }

    public String placeOrder(String customerId, List<Integer> productIds, 
                            List<Integer> qty, List<String> orderTypes) {
        throw new UnsupportedOperationException("Mock implementation removed. Use real CMS API endpoint.");
    }

    public Map<String, Object> generateRouteSheet(String customerId) {
        throw new UnsupportedOperationException("Mock implementation removed. Use real Voice API endpoint.");
    }

    public void saleMarking(Long deliveryId, List<Map<String, Object>> products, 
                           Double lat, Double lon) {
        throw new UnsupportedOperationException("Mock implementation removed. Use real Delivery API endpoint.");
    }
}
