package com.countrydelight.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.countrydelight.service.ComplaintService;

import java.util.*;

@RestController
@RequestMapping("/api/complaint")
@CrossOrigin(origins = "*")
public class ComplaintController {

    @Autowired
    private ComplaintService service;

    /**
     * List the databases supported for complaint cleanup.
     */
    @GetMapping("/databases")
    public ResponseEntity<?> getDatabases() {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("targetCustomerId", service.getTargetCustomerId());
        response.put("data", service.getDatabases());
        response.put("message", "✓ Complaint cleanup target databases fetched");
        return ResponseEntity.ok(response);
    }

    /**
     * Fetch (read-only) all complaints for the given mobile numbers in the given databases.
     * Body: { "mobiles": ["9876543210", ...], "databases": ["complaintmanagement_QA", ...] }
     */
    @PostMapping("/fetch")
    public ResponseEntity<?> fetchComplaints(@RequestBody Map<String, Object> request) {
        try {
            List<String> mobiles = parseMobiles(request.get("mobiles"));
            List<String> databases = parseDatabases(request.get("databases"));

            Map<String, Object> data = service.fetchComplaints(mobiles, databases);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "✓ Complaints fetched");
            response.put("data", data);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        } catch (Exception e) {
            System.out.println("❌ Fetch complaints error: " + e.getMessage());
            e.printStackTrace();
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Error while fetching complaints: " + e.getMessage());
            errorResponse.put("errorType", e.getClass().getSimpleName());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    /**
     * Run complaint cleanup for the given mobile numbers in the given databases.
     * Body: { "mobiles": ["9876543210", ...], "databases": ["complaintmanagement_QA", ...] }
     */
    @PostMapping("/cleanup")
    public ResponseEntity<?> cleanup(@RequestBody Map<String, Object> request) {
        try {
            List<String> mobiles = parseMobiles(request.get("mobiles"));
            List<String> databases = parseDatabases(request.get("databases"));

            Map<String, Object> data = service.cleanup(mobiles, databases);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "✓ Complaint cleanup completed");
            response.put("data", data);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        } catch (Exception e) {
            System.out.println("❌ Complaint cleanup error: " + e.getMessage());
            e.printStackTrace();
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Error during complaint cleanup: " + e.getMessage());
            errorResponse.put("errorType", e.getClass().getSimpleName());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    /**
     * Normalizes mobiles from a list or a single string (newline/comma separated).
     * Strips country codes (+91 / 91 with leading 0) and validates 10 digits.
     */
    private List<String> parseMobiles(Object mobileRaw) {
        List<String> raw = new ArrayList<>();
        if (mobileRaw instanceof List) {
            for (Object o : (List<?>) mobileRaw) {
                if (o != null) raw.add(o.toString().trim());
            }
        } else if (mobileRaw != null) {
            for (String part : mobileRaw.toString().split("[,;\\n]+")) {
                String p = part.trim();
                if (!p.isEmpty()) raw.add(p);
            }
        }

        List<String> cleaned = new ArrayList<>();
        for (String mobile : raw) {
            String digits = mobile.replaceAll("\\D", "");
            if (digits.length() == 11 && digits.startsWith("0")) {
                digits = digits.substring(1);
            } else if (digits.length() == 12 && digits.startsWith("91")) {
                digits = digits.substring(2);
            }
            if (!digits.matches("\\d{10}")) {
                throw new IllegalArgumentException("Invalid mobile number: " + mobile + " (must be a 10 digit number)");
            }
            if (!cleaned.contains(digits)) {
                cleaned.add(digits);
            }
        }
        if (cleaned.isEmpty()) {
            throw new IllegalArgumentException("At least one mobile number is required");
        }
        return cleaned;
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
}