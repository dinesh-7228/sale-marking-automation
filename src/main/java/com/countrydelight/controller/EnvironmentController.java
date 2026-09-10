package com.countrydelight.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.countrydelight.config.EnvironmentConfig;
import com.countrydelight.api.ApiClient;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/environment")
@CrossOrigin(origins = "*")
public class EnvironmentController {

    @Autowired
    private EnvironmentConfig envConfig;

    @Autowired
    private ApiClient apiClient;

    @GetMapping
    public ResponseEntity<?> getCurrentEnvironment() {
        Map<String, Object> response = new HashMap<>();
        response.put("env", envConfig.getEnv());
        response.put("success", true);
        return ResponseEntity.ok(response);
    }

    @PostMapping
    public ResponseEntity<?> setEnvironment(@RequestBody Map<String, String> request) {
        String env = request.get("env");
        if (env == null || env.trim().isEmpty()) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Environment is required (QA or UAT)");
            return ResponseEntity.badRequest().body(errorResponse);
        }
        envConfig.setEnv(env.toUpperCase());
        apiClient.resetMockMode();
        System.out.println("🏷️  Environment switched to: " + envConfig.getEnv());

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("env", envConfig.getEnv());
        response.put("message", "Environment switched to " + envConfig.getEnv() +
                ". APIs and database (beejapuri_" + envConfig.getEnv().toUpperCase() + ") now use this environment.");
        return ResponseEntity.ok(response);
    }
}