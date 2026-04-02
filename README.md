# Sale Marking Automation System

Complete automation system for marking sales with customer search, product selection, order placement, and route sheet generation.

## Features Implemented

### 1. **Customer Search API** (`/api/customer/search`)
   - Search customers by phone number
   - Returns customer details: DB ID, name, city, franchise info
   - Validates franchise_id and city information

### 2. **Product Fetching API** (`/api/product/fetch`)
   - Fetch products by customer ID and city ID
   - Shows product details: name, price, frozen status, packaging requirements
   - Includes order type options (daily, weekend, alternate)

### 3. **Order Placement & Sale Marking** (`/api/order/place-and-mark`)
   - Complete workflow in single endpoint
   - Steps:
     1. Place order via CMS API
     2. Generate route sheet
     3. Update route sheet date (tomorrow → today)
     4. Update order detail date (tomorrow → today)
     5. Mark sale via delivery API
     6. Insert sale records in database

### 4. **Interactive Web UI**
   - Search customers by phone number
   - Select multiple products with quantities and order types
   - Confirm order summary
   - Real-time status updates and error messages
   - Mobile-responsive design

## File Structure

```
sale-marking-automation/
├── api/
│   └── ApiClient.java              # Enhanced API client for CMS calls
├── controller/
│   ├── CustomerController.java      # Customer search endpoints
│   ├── ProductController.java       # Product fetch endpoints
│   └── SaleMarkingController.java   # Order & sale marking endpoints
├── service/
│   ├── CustomerSearchService.java   # Customer search logic
│   ├── ProductService.java          # Product fetch logic
│   └── SaleMarkingService.java      # Complete workflow orchestration
├── db/
│   └── DatabaseUtil.java            # Database operations (updated)
├── model/
│   └── ProductRequest.java          # Request model
├── resources/
│   └── templates/
│       └── index.html               # Interactive UI
└── README.md                        # This file
```

## API Endpoints

### Customer Search
```
GET /api/customer/search?phone=9999999999
Response: {
  "success": true,
  "data": [
    {
      "db_id": "9943824",
      "name": "John Doe",
      "phone": "9999999999",
      "city_id": 21,
      "franchise_id": "FR123",
      "address": "123 Main St"
    }
  ]
}
```

### Product Fetch
```
GET /api/product/fetch?customerId=9943824&cityId=21
Response: {
  "success": true,
  "data": [
    {
      "id": 382,
      "name": "Desi Danedar Ghee - 900 ML",
      "price": 450.00,
      "is_frozen": false,
      "is_packaging_required": true,
      "order_types": ["daily", "weekend", "alternate"]
    }
  ]
}
```

### Place Order & Mark Sale
```
POST /api/order/place-and-mark
Request Body: {
  "customerId": "9943824",
  "customerName": "John Doe",
  "cityId": 21,
  "latitude": null,
  "longitude": null,
  "products": [
    {
      "id": 382,
      "name": "Desi Danedar Ghee - 900 ML",
      "quantity": 2,
      "order_type": "daily"
    }
  ]
}
Response: {
  "success": true,
  "message": "Sale marking completed successfully!",
  "data": {
    "orderPlaced": true,
    "routeSheetGenerated": true,
    "routeSheetDateUpdated": true,
    "orderDetailDateUpdated": true,
    "saleMarked": true
  }
}
```

## How to Use

### Via Web UI
1. Open browser and go to `http://localhost:8080`
2. Enter customer phone number and search
3. Select customer from results
4. Choose products and quantities
5. Select order type for each product (daily/weekend/alternate)
6. Review order summary
7. Click "Place Order & Mark Sale" to complete

### Via API (cURL)
```bash
# Search customer
curl 'http://localhost:8080/api/customer/search?phone=9999999999'

# Fetch products
curl 'http://localhost:8080/api/product/fetch?customerId=9943824&cityId=21'

# Place order and mark sale
curl -X POST 'http://localhost:8080/api/order/place-and-mark' \
  -H 'Content-Type: application/json' \
  -d '{
    "customerId": "9943824",
    "customerName": "John Doe",
    "cityId": 21,
    "latitude": null,
    "longitude": null,
    "products": [
      {"id": 382, "name": "Ghee", "quantity": 2, "order_type": "daily"}
    ]
  }'
```

## Configuration Required

### Update DatabaseUtil.java with your credentials:
```java
return DriverManager.getConnection(
    "jdbc:mysql://YOUR_DB_HOST:3306/beejapuri_QA",
    "YOUR_USERNAME",
    "YOUR_PASSWORD");
```

### Update API tokens in service classes (if they expire):
- `AUTHORIZATION_TOKEN` in CustomerSearchService.java
- `API_KEY` in ApiClient.java

## Database Operations

The system automatically performs these database operations:

1. **Route Sheet Date Update**:
   ```sql
   UPDATE route_sheet_details 
   SET delivery_date = CURDATE() 
   WHERE customer_id = ? 
   AND delivery_date = DATE_ADD(CURDATE(), INTERVAL 1 DAY)
   ```

2. **Order Detail Date Update**:
   ```sql
   UPDATE order_detail 
   SET start_date = CURDATE() 
   WHERE customer_id = ? 
   AND status = 'Y' 
   AND start_date = DATE_ADD(CURDATE(), INTERVAL 1 DAY)
   ```

3. **Sales Record Insertion**:
   ```sql
   INSERT INTO sales_mark (route_sheet_detail_id, product_id, quantity, created_date)
   VALUES (?, ?, ?, NOW())
   ```

## Manual Database Management with DBeaver

See `DBEAVER_GUIDE.md` in the session state folder for detailed instructions on:
- Connecting to the database
- Updating route sheet and order details manually
- Running custom SQL queries
- Verifying data changes

## Error Handling

The system includes comprehensive error handling for:
- Empty phone numbers or invalid formats
- Missing customer city or franchise information
- API failures from CMS/voice services
- Database connection errors
- Invalid product selections

## Testing Workflow

1. **Search Customer**: Use a test customer phone number
2. **Validate Info**: Ensure franchise_id is set in system
3. **Select Products**: Choose 1-2 products with quantities
4. **Verify API Calls**: Check service logs for API responses
5. **Check Database**: Use DBeaver to verify all date updates
6. **Confirm Sales**: Query sales_mark table for records

## Troubleshooting

**Issue**: Customer not found
- **Solution**: Verify phone number is correct in database

**Issue**: No products available
- **Solution**: Check city_id is correctly assigned to customer
- **Solution**: Verify products exist for this city

**Issue**: Route sheet not updated
- **Solution**: Check if route sheet was generated tomorrow
- **Solution**: Verify customer_id format matches database

**Issue**: Database connection fails
- **Solution**: Update DB_HOST, username, password in DatabaseUtil.java
- **Solution**: Check MySQL server is running

## Future Enhancements

- [ ] Geolocation tracking for delivery
- [ ] Payment integration
- [ ] SMS notifications to customers
- [ ] Delivery boy mobile app
- [ ] Real-time route optimization
- [ ] Customer rating and feedback
- [ ] Inventory management
