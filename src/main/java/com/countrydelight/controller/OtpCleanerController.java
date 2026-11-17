package com.countrydelight.controller;

import com.countrydelight.service.OtpCleanerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/otp")
@CrossOrigin(origins = "*")
public class OtpCleanerController {

    @Autowired
    private OtpCleanerService service;

    @GetMapping("/databases")
    public ResponseEntity<?> getDatabases() {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", service.getDatabases());
        return ResponseEntity.ok(response);
    }

    /**
     * Read-only: list all sign_up_otp rows for one mobile.
     * Body: { "mobile": "5248484848", "databases": ["beejapuri_QA"] }
     */
    @PostMapping("/fetch")
    public ResponseEntity<?> fetch(@RequestBody Map<String, Object> request) {
        try {
            String mobile = parseMobile(request.get("mobile"));
            List<String> databases = parseDatabases(request.get("databases"));
            return ResponseEntity.ok(ok("✓ OTPs fetched", service.fetch(mobile, databases)));
        } catch (IllegalArgumentException e) {
            return badRequest(e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            return badRequest("Error while fetching OTPs: " + e.getMessage());
        }
    }

    /**
     * Deletes every sign_up_otp row for one mobile.
     * Body: { "mobile": "5248484848", "databases": ["beejapuri_QA"] }
     */
    @PostMapping("/delete")
    public ResponseEntity<?> delete(@RequestBody Map<String, Object> request) {
        try {
            String mobile = parseMobile(request.get("mobile"));
            List<String> databases = parseDatabases(request.get("databases"));
            return ResponseEntity.ok(ok("✓ OTPs deleted", service.delete(mobile, databases)));
        } catch (IllegalArgumentException e) {
            return badRequest(e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            return badRequest("Error while deleting OTPs: " + e.getMessage());
        }
    }

    private static Map<String, Object> ok(String message, Map<String, Object> data) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", message);
        response.put("data", data);
        return response;
    }

    private static ResponseEntity<?> badRequest(String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("message", message);
        return ResponseEntity.badRequest().body(response);
    }

    /** Accepts 10 digits, optionally prefixed with 0 or 91. */
    private String parseMobile(Object raw) {
        if (raw == null || raw.toString().trim().isEmpty()) {
            throw new IllegalArgumentException("Mobile number is required");
        }
        String digits = raw.toString().replaceAll("\\D", "");
        if (digits.length() == 11 && digits.startsWith("0")) {
            digits = digits.substring(1);
        } else if (digits.length() == 12 && digits.startsWith("91")) {
            digits = digits.substring(2);
        }
        if (!digits.matches("\\d{10}")) {
            throw new IllegalArgumentException("Invalid mobile number: " + raw + " (must be a 10 digit number)");
        }
        return digits;
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
