package com.countrydelight.controller;

import com.countrydelight.service.BaseCustomerRemovalService;
import com.countrydelight.service.MembershipCustomerRemovalService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/payment/membership-customer-removal")
@CrossOrigin(origins = "*")
public class MembershipCustomerRemovalController extends BaseCustomerRemovalController {

    @Autowired
    private MembershipCustomerRemovalService service;

    @Override
    protected BaseCustomerRemovalService getService() {
        return service;
    }

    @GetMapping("/config")
    public ResponseEntity<?> config() {
        return getConfig();
    }

    @PostMapping("/fetch")
    public ResponseEntity<?> fetch(@RequestBody Map<String, Object> request) {
        return super.fetch(request);
    }

    @PostMapping("/run")
    public ResponseEntity<?> run(@RequestBody Map<String, Object> request) {
        return super.run(request);
    }
}