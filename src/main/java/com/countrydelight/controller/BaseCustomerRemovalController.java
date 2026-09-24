package com.countrydelight.controller;

import com.countrydelight.service.BaseCustomerRemovalService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.*;

/**
 * Shared endpoints for customer-removal modules (autopay / membership).
 *
 *   GET  /api/payment/{type}-customer-removal/config
 *   POST /api/payment/{type}-customer-removal/fetch   body { customerIds: [...], databases: [...] }
 *   POST /api/payment/{type}-customer-removal/run     body { customerIds: [...], databases: [...] }
 */
public abstract class BaseCustomerRemovalController {

    protected abstract BaseCustomerRemovalService getService();

    public ResponseEntity<?> getConfig() {
        BaseCustomerRemovalService service = getService();
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("targetCustomerId", service.getTargetCustomerId());
        response.put("table", service.getTableInfo());
        response.put("databases", service.getDatabases());
        response.put("message", "✓ Customer removal config fetched");
        return ResponseEntity.ok(response);
    }

    public ResponseEntity<?> fetch(@RequestBody Map<String, Object> request) {
        try {
            List<Long> customerIds = parseCustomerIds(request.get("customerIds"));
            List<String> databases = parseDatabases(request.get("databases"));

            Map<String, Object> data = getService().fetch(customerIds, databases);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "✓ Rows fetched");
            response.put("data", data);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(badRequest(e.getMessage()));
        } catch (Exception e) {
            System.out.println("❌ Customer-removal fetch error: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.badRequest().body(badRequest("Error while fetching rows: " + e.getMessage()));
        }
    }

    public ResponseEntity<?> run(@RequestBody Map<String, Object> request) {
        try {
            List<Long> customerIds = parseCustomerIds(request.get("customerIds"));
            List<String> databases = parseDatabases(request.get("databases"));

            Map<String, Object> data = getService().cleanup(customerIds, databases);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "✓ Customer removal completed");
            response.put("data", data);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(badRequest(e.getMessage()));
        } catch (Exception e) {
            System.out.println("❌ Customer-removal error: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.badRequest().body(badRequest("Error during customer removal: " + e.getMessage()));
        }
    }

    /**
     * Normalizes customer ids from a list or a single string (newline/comma separated).
     * Accepts only positive numeric ids.
     */
    private List<Long> parseCustomerIds(Object raw) {
        List<String> rawValues = new ArrayList<>();
        if (raw instanceof List) {
            for (Object o : (List<?>) raw) {
                if (o != null) rawValues.add(o.toString().trim());
            }
        } else if (raw != null) {
            for (String part : raw.toString().split("[,;\\n]+")) {
                String p = part.trim();
                if (!p.isEmpty()) rawValues.add(p);
            }
        }

        List<Long> ids = new ArrayList<>();
        for (String value : rawValues) {
            if (!value.matches("\\d+")) {
                throw new IllegalArgumentException("Invalid customer DB id: " + value);
            }
            long id = Long.parseLong(value);
            if (!ids.contains(id)) ids.add(id);
        }
        if (ids.isEmpty()) {
            throw new IllegalArgumentException("At least one customer DB id is required");
        }
        return ids;
    }

    private List<String> parseDatabases(Object dbRaw) {
        List<String> list = new ArrayList<>();
        if (dbRaw instanceof List) {
            for (Object o : (List<?>) dbRaw) {
                if (o != null && !o.toString().trim().isEmpty()) list.add(o.toString().trim());
            }
        }
        if (list.isEmpty()) {
            throw new IllegalArgumentException("Select at least one database");
        }
        return list;
    }

    private Map<String, Object> badRequest(String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("message", message);
        response.put("error", true);
        return response;
    }
}