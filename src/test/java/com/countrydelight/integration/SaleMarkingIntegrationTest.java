package com.countrydelight.integration;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import static io.restassured.RestAssured.*;
import static org.hamcrest.Matchers.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Integration tests for Sale Marking Automation
 * Tests customer search, details fetch, and complete workflow
 */
@TestMethodOrder(OrderAnnotation.class)
@DisplayName("Sale Marking Integration Tests")
public class SaleMarkingIntegrationTest {

    private static final String BASE_URL = "http://localhost:6000";
    private static final String PHONE_NUMBER = "9310352764";
    private static final String TEST_DB_ID = "9999999";

    @BeforeAll
    public static void setup() {
        RestAssured.baseURI = BASE_URL;
    }

    @Test
    @Order(1)
    @DisplayName("Test 1: Search customer by phone number")
    public void testSearchCustomerByPhone() {
        System.out.println("\n>>> TEST 1: Search Customer by Phone");
        System.out.println("Phone: " + PHONE_NUMBER);
        
        given()
            .queryParam("phone", PHONE_NUMBER)
            .when()
            .get("/api/order/customer/search")
            .then()
            .statusCode(200)
            .body("success", equalTo(true))
            .body("count", greaterThan(0))
            .body("data", notNullValue())
            .body("data[0].phone", equalTo(PHONE_NUMBER));
        
        System.out.println("✓ Customer search successful");
    }

    @Test
    @Order(2)
    @DisplayName("Test 2: Get customer details by DB ID")
    public void testGetCustomerDetails() {
        System.out.println("\n>>> TEST 2: Get Customer Details");
        System.out.println("DB ID: " + TEST_DB_ID);
        
        given()
            .pathParam("dbId", TEST_DB_ID)
            .when()
            .get("/api/order/customer/details/{dbId}")
            .then()
            .statusCode(200)
            .body("success", equalTo(true))
            .body("data", notNullValue())
            .body("data.id", equalTo(TEST_DB_ID));
        
        System.out.println("✓ Customer details retrieved successfully");
    }

    @Test
    @Order(3)
    @DisplayName("Test 3: Validate response structure")
    public void testResponseStructure() {
        System.out.println("\n>>> TEST 3: Validate Response Structure");
        
        given()
            .queryParam("phone", PHONE_NUMBER)
            .when()
            .get("/api/order/customer/search")
            .then()
            .statusCode(200)
            .body("success", notNullValue())
            .body("message", notNullValue())
            .body("data", instanceOf(List.class));
        
        System.out.println("✓ Response structure is valid");
    }

    @Test
    @Order(4)
    @DisplayName("Test 4: Route sheet retrieval")
    public void testRouteSheetRetrieval() {
        System.out.println("\n>>> TEST 4: Route Sheet Retrieval");
        
        given()
            .pathParam("customerId", TEST_DB_ID)
            .when()
            .get("/api/order/route-sheet/{customerId}")
            .then()
            .statusCode(200)
            .body("success", equalTo(true));
        
        System.out.println("✓ Route sheet retrieval endpoint works");
    }
}
