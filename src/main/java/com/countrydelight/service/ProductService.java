package com.countrydelight.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.countrydelight.api.ApiClient;
import java.util.List;
import java.util.Map;

@Service
public class ProductService {

    @Autowired
    private ApiClient apiClient;

    public List<Map<String, Object>> fetchProducts(String customerId, Integer cityId) throws Exception {
        if (customerId == null || customerId.trim().isEmpty()) {
            throw new IllegalArgumentException("Customer ID cannot be empty");
        }
        if (cityId == null || cityId <= 0) {
            throw new IllegalArgumentException("Valid City ID is required");
        }

        return apiClient.fetchProducts(customerId, cityId);
    }
}
