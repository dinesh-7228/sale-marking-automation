package com.countrydelight.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.countrydelight.api.ApiClient;
import com.countrydelight.db.DatabaseUtil;
import java.util.List;
import java.util.Map;

@Service
public class ProductService {

    @Autowired
    private ApiClient apiClient;

    @Autowired
    private DatabaseUtil databaseUtil;

    /**
     * Resolves a numeric city id for the customer when one was not supplied.
     * Tries CMS customer details first, then falls back to the database
     * (customer_attributes.FRANCHISE -> franchise.FRANCHISE_CITY and
     * customer.DELIVERY_ADDRESS -> address.CITY).
     *
     * @param customerId customer id from the search result (CUSTOMER_ID)
     * @param cityId already supplied city id, or null/empty
     * @return a valid city id, throwing if it cannot be resolved
     */
    public Integer resolveCityId(String customerId, Integer cityId) throws Exception {
        if (cityId != null && cityId > 0) {
            return cityId;
        }

        // Try the CMS customer details (delivery_address.city_id) first,
        // this is what the previous CITY attribute fallback relied on.
        try {
            String dbCustomerId = databaseUtil.getDbCustomerId(customerId);
            Map<String, Object> details = apiClient.getCustomerDetails(
                dbCustomerId != null ? dbCustomerId : customerId);
            Integer cmsCityId = extractCityId(details);
            if (cmsCityId != null && cmsCityId > 0) {
                System.out.println("✓ City " + cmsCityId + " resolved via CMS customer details for customer " + customerId);
                return cmsCityId;
            }
        } catch (Exception e) {
            System.out.println("⚠ CMS city resolution failed for " + customerId + ": " + e.getMessage());
        }

        // Fall back to the database so we do not depend on the CMS API.
        Integer dbCityId = databaseUtil.resolveCustomerCityId(customerId);
        if (dbCityId != null && dbCityId > 0) {
            return dbCityId;
        }

        throw new IllegalArgumentException(
            "Valid City ID is required and could not be auto-resolved for customer " + customerId);
    }

    @SuppressWarnings("unchecked")
    private Integer extractCityId(Map<String, Object> details) {
        if (details == null) {
            return null;
        }
        Object deliveryAddress = details.get("delivery_address");
        if (deliveryAddress instanceof Map) {
            Object cityId = ((Map<String, Object>) deliveryAddress).get("city_id");
            if (cityId instanceof Number) {
                return ((Number) cityId).intValue();
            }
        }
        Object cityId = details.get("deliveryAddress.city_id");
        if (cityId instanceof Number) {
            return ((Number) cityId).intValue();
        }
        return null;
    }

    public List<Map<String, Object>> fetchProducts(String customerId, Integer cityId) throws Exception {
        if (customerId == null || customerId.trim().isEmpty()) {
            throw new IllegalArgumentException("Customer ID cannot be empty");
        }
        Integer resolvedCityId = resolveCityId(customerId, cityId);

        return apiClient.fetchProducts(customerId, resolvedCityId);
    }
}
