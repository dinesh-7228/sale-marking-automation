package com.countrydelight.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.countrydelight.config.EnvironmentConfig;
import java.util.Map;
import java.util.HashMap;

@RestController
@RequestMapping("/api/config")
@CrossOrigin(origins = "*")
public class EnvironmentController {

    @Autowired
    private EnvironmentConfig envConfig;

    @GetMapping("/environment")
    public ResponseEntity<?> getCurrentEnvironment() {
        return ResponseEntity.ok(Map.of(
            "success", true,
            "currentEnv", envConfig.getEnv(),
            "baseUrl", envConfig.getApiBaseUrl(),
            "adminUrl", envConfig.getAdminUrl()
        ));
    }

    @PostMapping("/environment")
    public ResponseEntity<?> setEnvironment(@RequestBody Map<String, String> request) {
        try {
            String env = request.get("environment");
            if (env == null || (!env.equalsIgnoreCase("QA") && !env.equalsIgnoreCase("UAT"))) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Invalid environment. Use 'QA' or 'UAT'"
                ));
            }
            
            envConfig.setEnv(env.toUpperCase());
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Environment switched to " + env.toUpperCase(),
                "currentEnv", envConfig.getEnv(),
                "baseUrl", envConfig.getApiBaseUrl(),
                "adminUrl", envConfig.getAdminUrl()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", "Error setting environment: " + e.getMessage()
            ));
        }
    }

    @GetMapping("/environments")
    public ResponseEntity<?> listEnvironments() {
        return ResponseEntity.ok(Map.of(
            "success", true,
            "environments", new String[]{"QA", "UAT"},
            "current", envConfig.getEnv()
        ));
    }
}
