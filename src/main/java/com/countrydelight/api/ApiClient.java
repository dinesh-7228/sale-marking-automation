package com.countrydelight.api;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import com.countrydelight.config.EnvironmentConfig;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.*;

@Component
public class ApiClient {

    @Autowired
    private EnvironmentConfig envConfig;

    private String getBaseUrl() {
        return envConfig.getApiBaseUrl();
    }

    private String getApiKey() {
        return envConfig.getApiKey();
    }

    private String getAuthToken() {
        return envConfig.getAuthToken();
    }


    public String placeOrder(String customerId, List<Integer> productIds, List<Integer> qty, List<String> orderTypes) throws Exception {
        if (customerId == null || productIds == null || qty == null || productIds.size() != qty.size()) {
            throw new IllegalArgumentException("Invalid order parameters");
        }

        Map<String, Object> body = new HashMap<>();
        body.put("customer_id", customerId);
        body.put("order_amount", -1);

        List<Map<String, Object>> subscriptions = new ArrayList<>();

        for (int i = 0; i < productIds.size(); i++) {
            Map<String, Object> sub = new HashMap<>();
            sub.put("id", 0);
            sub.put("quantity", qty.get(i));
            sub.put("order_start_date", LocalDate.now().format(DateTimeFormatter.ofPattern("dd-MM-yyyy")));
            sub.put("order_type", orderTypes != null && i < orderTypes.size() ? orderTypes.get(i) : "daily");
            sub.put("source", "CMS");
            sub.put("time_slot", 13);

            Map<String, Object> product = new HashMap<>();
            product.put("id", productIds.get(i));
            sub.put("product", product);

            subscriptions.add(sub);
        }

        body.put("subscriptions", subscriptions);

        Response response = RestAssured.given()
                .header("Authorization", "Bearer " + getAuthToken())
                .header("accept", "application/json, text/plain, */*")
                .contentType(ContentType.JSON)
                .body(body)
                .post(getBaseUrl() + "/admin/customers/v1/placeOrder");

        if (response.getStatusCode() < 200 || response.getStatusCode() >= 300) {
            throw new RuntimeException("Order placement failed with status " + response.getStatusCode() + 
                                     ": " + response.getBody().asString());
        }

        return response.getBody().asString();
    }

    public Map<String, Object> generateRouteSheet(String customerId) throws Exception {
        if (customerId == null || customerId.trim().isEmpty()) {
            throw new IllegalArgumentException("Customer ID cannot be empty");
        }

        Response response = RestAssured.given()
                .header("X-Api-Key", getApiKey())
                .header("accept", "application/json")
                .post(getBaseUrl() + "/api/voice/generateRouteSheetByCustomerId?customerId=" + customerId);

        if (response.getStatusCode() < 200 || response.getStatusCode() >= 300) {
            throw new RuntimeException("Route sheet generation failed with status " + response.getStatusCode() + 
                                     ": " + response.getBody().asString());
        }

        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        return mapper.readValue(response.getBody().asString(), java.util.Map.class);
    }

    public void saleMarking(Long deliveryId, List<Map<String, Object>> products, Double lat, Double lon) throws Exception {
        if (deliveryId == null || products == null || products.isEmpty()) {
            throw new IllegalArgumentException("Invalid sale marking parameters");
        }

        List<Map<String, Object>> processedProducts = new ArrayList<>();

        for (Map<String, Object> product : products) {
            Map<String, Object> p = new HashMap<>();
            p.put("change_type", "");
            p.put("deliveryId", deliveryId);
            p.put("id", product.get("id"));
            p.put("is_frozen", product.getOrDefault("is_frozen", false));
            p.put("is_packaging_required", product.getOrDefault("is_packaging_required", true));
            p.put("quantity", product.get("quantity"));
            p.put("name", product.get("name"));
            processedProducts.add(p);
        }

        Map<String, Object> location = new HashMap<>();
        location.put("accuracy", 6);
        location.put("lat", lat != null ? lat : 28.41873333333333);
        location.put("lon", lon != null ? lon : 77.03871166666666);

        Map<String, Object> data = new HashMap<>();
        data.put("delivered", true);
        data.put("delivery", deliveryId);
        data.put("delivery_boy", 1);
        data.put("delivery_id", deliveryId);
        data.put("delivery_time", String.valueOf(System.currentTimeMillis() / 1000));
        data.put("hold", false);
        data.put("hold_end_date", "");
        data.put("hold_start_date", "");
        data.put("is_decrypt", true);
        data.put("is_fnv", true);
        data.put("is_geofenced_delivery", true);
        data.put("location", location);
        data.put("non_delivery_reason", "");
        data.put("products", processedProducts);
        data.put("quantity_changed", false);
        data.put("remarks", "");
        data.put("stop", false);
        data.put("stop_start_date", "");
        data.put("temperature", null);
        data.put("time", null);
        data.put("undo", false);

        Map<String, Object> finalBody = new HashMap<>();
        finalBody.put("action", "test");
        finalBody.put("data", data);

        Response response = RestAssured.given()
                .contentType(ContentType.JSON)
                .body(finalBody)
                .post(getBaseUrl() + "/api/delivery/sale_create");

        if (response.getStatusCode() < 200 || response.getStatusCode() >= 300) {
            throw new RuntimeException("Sale marking failed with status " + response.getStatusCode() + 
                                     ": " + response.getBody().asString());
        }
    }

    /**
     * Search customer by phone with JWT authentication
     */
    public List<Map<String, Object>> searchCustomerByPhone(String phone) throws Exception {
        if (phone == null || phone.trim().isEmpty()) {
            throw new IllegalArgumentException("Phone number cannot be empty");
        }

        Response response = RestAssured.given()
                .header("Authorization", "Bearer " + getAuthToken())
                .header("accept", "application/json, text/plain, */*")
                .queryParam("phone", phone)
                .queryParam("pageNumber", 1)
                .queryParam("pageSize", 25)
                .queryParam("sortBy", "id")
                .queryParam("sortDirection", 1)
                .get(getBaseUrl() + "/admin/v1/customers/getCustomer");

        if (response.getStatusCode() < 200 || response.getStatusCode() >= 300) {
            throw new RuntimeException("Customer search failed with status " + response.getStatusCode() + 
                                     ": " + response.getBody().asString());
        }

        ObjectMapper mapper = new ObjectMapper();
        Map<String, Object> responseBody = mapper.readValue(response.getBody().asString(), Map.class);
        
        List<Map<String, Object>> customers = new ArrayList<>();
        Object dataObj = responseBody.get("data");
        
        if (dataObj instanceof List) {
            customers = (List<Map<String, Object>>) dataObj;
        }

        return customers;
    }

    /**
     * Fetch products by customer and city
     */
    public List<Map<String, Object>> fetchProducts(String customerId, Integer cityId) throws Exception {
        if (customerId == null || customerId.trim().isEmpty()) {
            throw new IllegalArgumentException("Customer ID cannot be empty");
        }
        if (cityId == null) {
            throw new IllegalArgumentException("City ID cannot be empty");
        }

        Response response = RestAssured.given()
                .header("Authorization", "Bearer " + getAuthToken())
                .header("accept", "application/json, text/plain, */*")
                .queryParam("customerId", customerId)
                .queryParam("showOnlyCustomerVisible", true)
                .queryParam("cityId", cityId)
                .get(getBaseUrl() + "/admin/v1/products/fetchProducts/V2");

        if (response.getStatusCode() < 200 || response.getStatusCode() >= 300) {
            throw new RuntimeException("Product fetch failed with status " + response.getStatusCode() + 
                                     ": " + response.getBody().asString());
        }

        ObjectMapper mapper = new ObjectMapper();
        String responseBody = response.getBody().asString();
        
        List<Map<String, Object>> products = new ArrayList<>();
        
        // Try to parse as array directly
        try {
            products = mapper.readValue(responseBody, List.class);
        } catch (Exception e) {
            // If array parsing fails, try as object with data field
            try {
                Map<String, Object> responseMap = mapper.readValue(responseBody, Map.class);
                Object dataObj = responseMap.get("data");
                if (dataObj instanceof List) {
                    products = (List<Map<String, Object>>) dataObj;
                }
            } catch (Exception e2) {
                throw new RuntimeException("Failed to parse products response: " + e.getMessage());
            }
        }

        return products;
    }

    /**
     * Fetch customer details including franchise_id and city_id
     */
    public Map<String, Object> getCustomerDetails(String db_id) throws Exception {
        if (db_id == null || db_id.trim().isEmpty()) {
            throw new IllegalArgumentException("Customer ID cannot be empty");
        }

        Response response = RestAssured.given()
                .header("Authorization", "Bearer " + getAuthToken())
                .header("accept", "application/json, text/plain, */*")
                    .get(getBaseUrl() + "/admin/v1/customers/getCustomerDetails/" + db_id);

        if (response.getStatusCode() < 200 || response.getStatusCode() >= 300) {
            throw new RuntimeException("Customer details fetch failed with status " + response.getStatusCode() + 
                                     ": " + response.getBody().asString());
        }

        ObjectMapper mapper = new ObjectMapper();
        Map<String, Object> customerDetails = mapper.readValue(response.getBody().asString(), Map.class);
        
        return customerDetails;
    }
}
