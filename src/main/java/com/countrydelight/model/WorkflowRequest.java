package com.countrydelight.model;

import java.util.List;
import java.util.Map;

public class WorkflowRequest {
    public String environment;
    public String customerNumber;
    public String customerId;
    public Boolean orderAlreadyPlaced;
    public List<Map<String, Object>> products;
    public Double latitude;
    public Double longitude;
    public String saleDate;

    public WorkflowRequest() {
    }

    public WorkflowRequest(String environment, String customerNumber, String customerId,
                           Boolean orderAlreadyPlaced, List<Map<String, Object>> products,
                           Double latitude, Double longitude, String saleDate) {
        this.environment = environment;
        this.customerNumber = customerNumber;
        this.customerId = customerId;
        this.orderAlreadyPlaced = orderAlreadyPlaced;
        this.products = products;
        this.latitude = latitude;
        this.longitude = longitude;
        this.saleDate = saleDate;
    }

    @Override
    public String toString() {
        return "WorkflowRequest{" +
                "environment='" + environment + '\'' +
                ", customerNumber='" + customerNumber + '\'' +
                ", customerId='" + customerId + '\'' +
                ", orderAlreadyPlaced=" + orderAlreadyPlaced +
                ", products=" + products +
                ", latitude=" + latitude +
                ", longitude=" + longitude +
                ", saleDate='" + saleDate + '\'' +
                '}';
    }
}
