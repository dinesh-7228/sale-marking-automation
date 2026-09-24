package com.countrydelight.controller;

import com.countrydelight.service.FeatureConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * Feature Config (app_feature_config) editor.
 *
 * Endpoints:
 *   GET  /api/feature-config/databases  → list of target complaintmanagement DBs
 *   POST /api/feature-config/fetch      → body {databases:[...]} → JSON rows
 *   POST /api/feature-config/update-key → body {database,rowId,field,ruleIndex,key,value}
 *                                         → read-modify-write ONE JSON key at a time
 */
@RestController
@RequestMapping("/api/feature-config")
@CrossOrigin(origins = "*")
public class FeatureConfigController {

    @Autowired
    private FeatureConfigService service;

    @GetMapping("/databases")
    public ResponseEntity<?> getDatabases() {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", service.getDatabases());
        response.put("message", "✓ Feature config target databases fetched");
        return ResponseEntity.ok(response);
    }

    /** Fetch all rows for the selected databases. */
    @PostMapping("/fetch")
    public ResponseEntity<?> fetch(@RequestBody Map<String, Object> request) {
        try {
            List<String> databases = parseDatabases(request.get("databases"));
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", service.fetch(databases));
            response.put("message", "✓ Feature configs fetched");
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(badRequest(e.getMessage()));
        } catch (Exception e) {
            System.out.println("❌ Feature config fetch error: " + e.getMessage());
            return ResponseEntity.badRequest().body(badRequest("Error fetching feature configs: " + e.getMessage()));
        }
    }

    /**
     * Update a single key at a time.
     * Body: { "database": "...", "rowId": 1, "field": "ELIGIBILITY"|"FORM_DATA",
     *         "ruleIndex": 0, "key": "refund_amount_limit", "value": "2000" }
     */
    @PostMapping("/update-key")
    public ResponseEntity<?> updateKey(@RequestBody Map<String, Object> request) {
        try {
            String database = String.valueOf(request.get("database"));
            Long rowId = Long.valueOf(String.valueOf(request.get("rowId")));
            String field = request.get("field") == null ? "ELIGIBILITY" : String.valueOf(request.get("field"));
            int ruleIndex = request.get("ruleIndex") == null ? -1 : Integer.parseInt(String.valueOf(request.get("ruleIndex")));
            String key = String.valueOf(request.get("key"));
            String value = request.get("value") == null ? "" : String.valueOf(request.get("value"));

            Map<String, Object> data = service.updateKey(database, rowId, field, ruleIndex, key, value);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", data);
            response.put("message", "✓ Updated " + key + " in " + database + " row " + rowId);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(badRequest(e.getMessage()));
        } catch (Exception e) {
            System.out.println("❌ Feature config update error: " + e.getMessage());
            return ResponseEntity.badRequest().body(badRequest("Error updating key: " + e.getMessage()));
        }
    }

    private Map<String, Object> badRequest(String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("message", message);
        return response;
    }

    private List<String> parseDatabases(Object raw) {
        List<String> list = new ArrayList<>();
        if (raw instanceof List) {
            for (Object o : (List<?>) raw) {
                if (o != null && !o.toString().trim().isEmpty()) list.add(o.toString().trim());
            }
        }
        if (list.isEmpty()) {
            throw new IllegalArgumentException("Select at least one database");
        }
        return list;
    }
}
