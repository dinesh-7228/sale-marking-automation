package com.countrydelight.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * JWT Authentication Manager
 * Handles real-time JWT token generation, validation, and refresh
 */
@Component
public class JwtAuthenticationManager {

    private final String AUTH_API_URL = "https://qa-cms.countrydelight.in";
    private final String LOGIN_ENDPOINT = "/api/auth/login";
    
    private String currentToken;
    private long tokenExpirationTime;
    private final long TOKEN_REFRESH_BUFFER = 60000; // Refresh 1 minute before expiry
    
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Get valid JWT token - automatically refreshes if expired
     */
    public String getValidToken(String username, String password) throws Exception {
        if (isTokenValid()) {
            System.out.println("✓ Using cached JWT token");
            return currentToken;
        }
        
        System.out.println("🔄 Refreshing JWT token...");
        return authenticateAndGetToken(username, password);
    }

    /**
     * Authenticate with CMS and get JWT token
     */
    public String authenticateAndGetToken(String username, String password) throws Exception {
        Map<String, String> credentials = new HashMap<>();
        credentials.put("username", username);
        credentials.put("password", password);

        try {
            Response response = RestAssured.given()
                    .contentType("application/json")
                    .body(credentials)
                    .post(AUTH_API_URL + LOGIN_ENDPOINT);

            if (response.getStatusCode() != 200 && response.getStatusCode() != 201) {
                throw new RuntimeException("JWT authentication failed. Status: " + response.getStatusCode());
            }

            Map<String, Object> responseBody = objectMapper.readValue(
                    response.getBody().asString(), 
                    Map.class
            );

            String token = (String) responseBody.get("token");
            if (token == null) {
                throw new RuntimeException("No JWT token received in response");
            }

            // Extract expiration from JWT payload
            long expirationTime = extractTokenExpiration(token);
            this.currentToken = token;
            this.tokenExpirationTime = expirationTime;

            System.out.println("✓ JWT token acquired successfully");
            return token;

        } catch (Exception e) {
            System.err.println("✗ JWT Authentication failed: " + e.getMessage());
            throw new RuntimeException("Failed to obtain JWT token: " + e.getMessage(), e);
        }
    }

    /**
     * Extract expiration time from JWT token
     * JWT format: header.payload.signature
     */
    private long extractTokenExpiration(String token) throws Exception {
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            throw new RuntimeException("Invalid JWT token format");
        }

        // Decode payload (second part)
        String payload = new String(java.util.Base64.getUrlDecoder().decode(parts[1]));
        Map<String, Object> payloadMap = objectMapper.readValue(payload, Map.class);
        
        Object expObj = payloadMap.get("exp");
        if (expObj != null) {
            long expirationSeconds = Long.parseLong(expObj.toString());
            return expirationSeconds * 1000; // Convert to milliseconds
        }

        // If no exp field, use token for 1 hour
        return System.currentTimeMillis() + 3600000;
    }

    /**
     * Check if cached token is still valid
     */
    private boolean isTokenValid() {
        if (currentToken == null) {
            return false;
        }
        long timeUntilExpiry = tokenExpirationTime - System.currentTimeMillis();
        return timeUntilExpiry > TOKEN_REFRESH_BUFFER;
    }

    /**
     * Get current token without refresh
     */
    public String getCurrentToken() {
        return currentToken;
    }

    /**
     * Clear cached token
     */
    public void clearToken() {
        this.currentToken = null;
        this.tokenExpirationTime = 0;
    }

    /**
     * Check token validity with custom buffer
     */
    public boolean isTokenValidWithBuffer(long bufferMs) {
        if (currentToken == null) {
            return false;
        }
        long timeUntilExpiry = tokenExpirationTime - System.currentTimeMillis();
        return timeUntilExpiry > bufferMs;
    }
}
