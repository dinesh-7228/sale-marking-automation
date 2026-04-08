package com.countrydelight.api;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.countrydelight.db.DatabaseUtil;
import java.util.*;

/**
 * Mock API Client - Database fallback implementation
 * Used when real CMS API is unavailable
 * Provides database-backed customer search and data retrieval
 */
@Component
public class MockApiClient {

    @Autowired
    private DatabaseUtil databaseUtil;

    public List<Map<String, Object>> searchCustomerByPhone(String phone) {
        try {
            return databaseUtil.searchCustomerByPhone(phone);
        } catch (Exception e) {
            System.err.println("⚠ Database customer search failed: " + e.getMessage());
            throw new RuntimeException("Failed to search customer by phone from database: " + e.getMessage(), e);
        }
    }

    public Map<String, Object> getCustomerDetails(String customerId) {
        try {
            return databaseUtil.getCustomerDetailsFromDb(customerId);
        } catch (Exception e) {
            System.err.println("⚠ Database customer details fetch failed: " + e.getMessage());
            throw new RuntimeException("Failed to fetch customer details from database: " + e.getMessage(), e);
        }
    }

    /**
     * Fetch customer attributes by customer ID
     */
    public List<Map<String, Object>> getCustomerAttributes(String customerId) {
        try {
            return databaseUtil.getCustomerAttributes(customerId);
        } catch (Exception e) {
            System.err.println("⚠ Database customer attributes fetch failed: " + e.getMessage());
            throw new RuntimeException("Failed to fetch customer attributes from database: " + e.getMessage(), e);
        }
    }

    public List<Map<String, Object>> fetchProducts(String customerId, Integer cityId) {
        throw new UnsupportedOperationException("Product fetch requires real CMS API endpoint.");
    }

    public String placeOrder(String customerId, List<Integer> productIds, 
                            List<Integer> qty, List<String> orderTypes) {
        throw new UnsupportedOperationException("Order placement requires real CMS API endpoint.");
    }

    public Map<String, Object> generateRouteSheet(String customerId) {
        throw new UnsupportedOperationException("Route sheet generation requires real Voice API endpoint.");
    }

    public void saleMarking(Long deliveryId, List<Map<String, Object>> products, 
                           Double lat, Double lon) {
        throw new UnsupportedOperationException("Sale marking requires real Delivery API endpoint.");
    }
}
