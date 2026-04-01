package com.countrydelight.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.countrydelight.service.ProductService;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/product")
@CrossOrigin(origins = "*")
public class ProductController {

    @Autowired
    private ProductService productService;

    @GetMapping("/fetch")
    public ResponseEntity<?> fetchProducts(@RequestParam String customerId, @RequestParam Integer cityId) {
        try {
            List<Map<String, Object>> products = productService.fetchProducts(customerId, cityId);
            return ResponseEntity.ok(Map.of(
                "success", true,
                "data", products
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", "Invalid parameters: " + e.getMessage()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", "Error fetching products: " + e.getMessage()
            ));
        }
    }
}
