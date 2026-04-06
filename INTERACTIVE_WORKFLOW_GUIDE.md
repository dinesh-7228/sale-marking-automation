# Interactive Workflow API Guide

This guide explains the new interactive workflow API that follows a step-by-step process for sale marking automation.

## Overview

The interactive workflow is designed to guide users through the complete sale marking process with clear, manageable steps:

1. **Environment Selection** - Choose between QA or UAT
2. **Customer Number Entry** - Provide customer phone number
3. **Customer Search** - Search for customers by phone
4. **Customer Details** - Retrieve customer information
5. **Order Status Check** - Check if order is already placed
6. **Product Selection** - Select products and quantities
7. **Place Order** - Create order in the system
8. **Generate Route Sheet** - Create delivery route sheet
9. **Update Route Sheet Date** - Change date to today
10. **Update Order Details Date** - Change order date to today
11. **Mark Sale** - Record the delivery/sale

## API Endpoints

### Step 1: Select Environment

**Endpoint:** `POST /api/workflow/step1-environment`

**Request:**
```json
{
  "environment": "QA"
}
```

**Parameters:**
- `environment` (required): Either "QA" or "UAT"

**Response:**
```json
{
  "success": true,
  "message": "Environment selected: QA",
  "nextStep": "Step 2: Enter customer number"
}
```

---

### Step 2: Enter Customer Number

**Endpoint:** `POST /api/workflow/step2-customer-number`

**Request:**
```json
{
  "customerNumber": "9876543210"
}
```

**Parameters:**
- `customerNumber` (required): Customer's mobile number

**Response:**
```json
{
  "success": true,
  "message": "Customer number received: 9876543210",
  "nextStep": "Step 3: Searching customer..."
}
```

---

### Step 3: Search Customer

**Endpoint:** `POST /api/workflow/step3-search-customer`

**Request:** (No body required - uses stored customer number)

**Response:**
```json
{
  "success": true,
  "message": "Found 1 customer(s)",
  "data": [
    {
      "id": "12345",
      "name": "John Doe",
      "phone": "9876543210",
      "city": "Delhi",
      "city_id": 21,
      "franchise_id": "F001"
    }
  ],
  "nextStep": "Step 4: Select a customer and get details"
}
```

---

### Step 4: Get Customer Details

**Endpoint:** `POST /api/workflow/step4-customer-details`

**Request:**
```json
{
  "customerId": "12345"
}
```

**Parameters:**
- `customerId` (required): The customer's database ID from search results

**Response:**
```json
{
  "success": true,
  "message": "Customer details retrieved",
  "data": {
    "id": "12345",
    "name": "John Doe",
    "phone": "9876543210",
    "delivery_address": {
      "city_id": 21,
      "city_name": "Delhi",
      "address": "..."
    },
    "franchise_id": "F001"
  },
  "nextStep": "Step 5: Check if order already placed or proceed to fetch products"
}
```

---

### Step 5: Check Order Status

**Endpoint:** `POST /api/workflow/step5-check-order-status`

**Request:**
```json
{
  "orderAlreadyPlaced": false
}
```

**Parameters:**
- `orderAlreadyPlaced` (required): Boolean indicating if order already exists

**Response (if order not placed):**
```json
{
  "success": true,
  "message": "Found 25 available products",
  "data": [
    {
      "id": 114,
      "name": "Desi Danedar Ghee - 900 ML",
      "price": 450,
      "unit": "bottle"
    }
  ],
  "nextStep": "Step 6: Select products and quantities"
}
```

**Response (if order already placed):**
```json
{
  "success": true,
  "message": "Order already placed. Skipping steps 6-7 (place order).",
  "nextStep": "Step 8: Generate route sheet"
}
```

---

### Step 6: Select Products & Place Order

**Endpoint:** `POST /api/workflow/step6-place-order`

**Request:**
```json
{
  "products": [
    {
      "id": 114,
      "name": "Desi Danedar Ghee - 900 ML",
      "quantity": 2,
      "order_type": "daily"
    },
    {
      "id": 108,
      "name": "Paneer - 500g",
      "quantity": 1,
      "order_type": "daily"
    }
  ]
}
```

**Parameters:**
- `products` (required): Array of products with id, name, quantity, order_type
  - `id`: Product ID
  - `quantity`: Quantity to order
  - `order_type`: "daily", "weekly", "monthly" (optional, defaults to "daily")

**Response:**
```json
{
  "success": true,
  "message": "Order placed successfully",
  "orderResult": "...",
  "nextStep": "Step 8: Generate route sheet"
}
```

---

### Step 8: Generate Route Sheet

**Endpoint:** `POST /api/workflow/step8-generate-route-sheet`

**Request:** (No body required)

**Response:**
```json
{
  "success": true,
  "message": "Route sheet generated successfully",
  "routeSheetId": 637633410,
  "nextStep": "Step 9: Update route sheet date to today"
}
```

---

### Step 9: Update Route Sheet Date

**Endpoint:** `POST /api/workflow/step9-update-route-sheet-date`

**Request:** (No body required)

**Response:**
```json
{
  "success": true,
  "message": "Route sheet date updated to today",
  "nextStep": "Step 10: Update order details date to today"
}
```

---

### Step 10: Update Order Details Date

**Endpoint:** `POST /api/workflow/step10-update-order-date`

**Request:** (No body required)

**Response:**
```json
{
  "success": true,
  "message": "Order detail date updated to today",
  "nextStep": "Step 11: Mark the sale"
}
```

---

### Step 11: Mark the Sale

**Endpoint:** `POST /api/workflow/step11-mark-sale`

**Request (optional):**
```json
{
  "latitude": 28.41873333333333,
  "longitude": 77.03871166666666
}
```

**Parameters (optional):**
- `latitude`: Delivery location latitude
- `longitude`: Delivery location longitude

**Response:**
```json
{
  "success": true,
  "message": "Sale marked successfully",
  "nextStep": "Workflow completed!"
}
```

---

## Complete Workflow in One Request

### Endpoint: `POST /api/workflow/complete`

**Request:**
```json
{
  "environment": "QA",
  "customerNumber": "9876543210",
  "customerId": "12345",
  "orderAlreadyPlaced": false,
  "products": [
    {
      "id": 114,
      "name": "Desi Danedar Ghee - 900 ML",
      "quantity": 2,
      "order_type": "daily"
    }
  ],
  "latitude": 28.41873333333333,
  "longitude": 77.03871166666666
}
```

**Response:**
```json
{
  "success": true,
  "message": "Complete workflow executed successfully",
  "data": {
    "success": true,
    "message": "Complete workflow executed successfully",
    "steps": [
      {
        "step": "Environment Selection",
        "status": "SUCCESS",
        "timestamp": "..."
      },
      // ... all steps
    ],
    "customerId": "12345",
    "routeSheetId": 637633410
  }
}
```

---

## Additional Endpoints

### Get Workflow State

**Endpoint:** `GET /api/workflow/state`

**Response:**
```json
{
  "success": true,
  "data": {
    "environment": "QA",
    "customerNumber": "9876543210",
    "customerId": "12345",
    "cityId": "21",
    "orderAlreadyPlaced": false,
    "routeSheetId": 637633410,
    "selectedProductsCount": 2
  }
}
```

---

### Reset Workflow

**Endpoint:** `POST /api/workflow/reset`

**Response:**
```json
{
  "success": true,
  "message": "Workflow state reset. Ready for new workflow."
}
```

---

## Usage Flow

### Sequential Step-by-Step Approach

1. Call `/api/workflow/step1-environment` with environment
2. Call `/api/workflow/step2-customer-number` with customer number
3. Call `/api/workflow/step3-search-customer` (no params)
4. From response, get customer ID
5. Call `/api/workflow/step4-customer-details` with customer ID
6. Call `/api/workflow/step5-check-order-status` with orderAlreadyPlaced flag
7. If order not placed:
   - Call `/api/workflow/step6-place-order` with selected products
8. Call `/api/workflow/step8-generate-route-sheet` (no params)
9. Call `/api/workflow/step9-update-route-sheet-date` (no params)
10. Call `/api/workflow/step10-update-order-date` (no params)
11. Call `/api/workflow/step11-mark-sale` (with optional location)
12. Optionally call `/api/workflow/reset` for next workflow

### Complete Workflow Approach

Simply call `/api/workflow/complete` with all required parameters in one request.

---

## Error Handling

All endpoints return consistent error responses:

```json
{
  "success": false,
  "message": "Error description here"
}
```

Common errors:
- 400 Bad Request: Missing or invalid parameters
- Missing customer number when searching customer
- No customers found for phone number
- Invalid customer ID
- Missing products when placing order

---

## Flow Shortcuts

### If Order Already Placed
When `orderAlreadyPlaced: true` in Step 5, Steps 6-7 are automatically skipped. The system proceeds directly to generating the route sheet.

### Latitude & Longitude
The `latitude` and `longitude` parameters in Step 11 are optional. If not provided, default values are used.

---

## Database Updates (Automatic)

The following database updates are handled automatically:

1. **Route Sheet Date Update**: Converts date from tomorrow to today
   - Updates `route_sheet_details` table
   
2. **Order Detail Date Update**: Converts date from tomorrow to today
   - Updates `order_details` table

3. **Sales Records**: Automatically inserted after marking sale
   - Records product details in sales tracking system

---

## Example cURL Commands

### Step 1: Select Environment
```bash
curl -X POST http://localhost:8080/api/workflow/step1-environment \
  -H "Content-Type: application/json" \
  -d '{"environment":"QA"}'
```

### Step 2: Enter Customer Number
```bash
curl -X POST http://localhost:8080/api/workflow/step2-customer-number \
  -H "Content-Type: application/json" \
  -d '{"customerNumber":"9876543210"}'
```

### Step 3: Search Customer
```bash
curl -X POST http://localhost:8080/api/workflow/step3-search-customer \
  -H "Content-Type: application/json"
```

### Step 4: Get Customer Details
```bash
curl -X POST http://localhost:8080/api/workflow/step4-customer-details \
  -H "Content-Type: application/json" \
  -d '{"customerId":"12345"}'
```

### Step 5: Check Order Status
```bash
curl -X POST http://localhost:8080/api/workflow/step5-check-order-status \
  -H "Content-Type: application/json" \
  -d '{"orderAlreadyPlaced":false}'
```

### Step 6: Place Order
```bash
curl -X POST http://localhost:8080/api/workflow/step6-place-order \
  -H "Content-Type: application/json" \
  -d '{
    "products": [
      {
        "id": 114,
        "quantity": 2,
        "order_type": "daily"
      }
    ]
  }'
```

### Complete Workflow
```bash
curl -X POST http://localhost:8080/api/workflow/complete \
  -H "Content-Type: application/json" \
  -d '{
    "environment": "QA",
    "customerNumber": "9876543210",
    "customerId": "12345",
    "orderAlreadyPlaced": false,
    "products": [
      {
        "id": 114,
        "quantity": 2,
        "order_type": "daily"
      }
    ]
  }'
```

---

## Notes

- Each step maintains state on the server
- The workflow state can be viewed at any time using `/api/workflow/state`
- Reset the workflow with `/api/workflow/reset` to start a new workflow
- All date updates are automatic and use system's current date
- Location coordinates default to a predefined location if not provided
- The system handles both sequential (step-by-step) and complete (all-in-one) workflows
