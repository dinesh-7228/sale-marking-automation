package com.countrydelight.model;

import java.util.List;
import java.util.Map;

/**
 * Request model for placing orders and marking sales
 */
public class OrderRequest {
    
    public String customerId;
    public String customerPhone;
    public Integer cityId;
    public List<Integer> productIds;
    public List<Integer> quantities;
    public List<String> orderTypes;
    public List<Map<String, Object>> products;
    public Map<String, Object> locationData;
    public Double latitude;
    public Double longitude;
    public Boolean delivered;
    public String deliveryTime;
    public Integer deliveryBoyId;
    public String saleDate;
    
    // Default constructor
    public OrderRequest() {
    }
    
    // Getters and Setters
    public String getCustomerId() {
        return customerId;
    }
    
    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }
    
    public String getCustomerPhone() {
        return customerPhone;
    }
    
    public void setCustomerPhone(String customerPhone) {
        this.customerPhone = customerPhone;
    }
    
    public Integer getCityId() {
        return cityId;
    }
    
    public void setCityId(Integer cityId) {
        this.cityId = cityId;
    }
    
    public List<Integer> getProductIds() {
        return productIds;
    }
    
    public void setProductIds(List<Integer> productIds) {
        this.productIds = productIds;
    }
    
    public List<Integer> getQuantities() {
        return quantities;
    }
    
    public void setQuantities(List<Integer> quantities) {
        this.quantities = quantities;
    }
    
    public List<String> getOrderTypes() {
        return orderTypes;
    }
    
    public void setOrderTypes(List<String> orderTypes) {
        this.orderTypes = orderTypes;
    }
    
    public List<Map<String, Object>> getProducts() {
        return products;
    }
    
    public void setProducts(List<Map<String, Object>> products) {
        this.products = products;
    }
    
    public Map<String, Object> getLocationData() {
        return locationData;
    }
    
    public void setLocationData(Map<String, Object> locationData) {
        this.locationData = locationData;
    }
    
    public Double getLatitude() {
        return latitude;
    }
    
    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }
    
    public Double getLongitude() {
        return longitude;
    }
    
    public void setLongitude(Double longitude) {
        this.longitude = longitude;
    }
    
    public Boolean getDelivered() {
        return delivered;
    }
    
    public void setDelivered(Boolean delivered) {
        this.delivered = delivered;
    }
    
    public String getDeliveryTime() {
        return deliveryTime;
    }
    
    public void setDeliveryTime(String deliveryTime) {
        this.deliveryTime = deliveryTime;
    }
    
    public Integer getDeliveryBoyId() {
        return deliveryBoyId;
    }
    
    public void setDeliveryBoyId(Integer deliveryBoyId) {
        this.deliveryBoyId = deliveryBoyId;
    }
    
    public String getSaleDate() {
        return saleDate;
    }
    
    public void setSaleDate(String saleDate) {
        this.saleDate = saleDate;
    }
}
