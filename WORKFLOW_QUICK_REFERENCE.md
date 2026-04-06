# Interactive Workflow - Quick Reference

## API Base URL
```
http://localhost:8080/api/workflow
```

## Step-by-Step Endpoints

| Step | Method | Endpoint | Purpose |
|------|--------|----------|---------|
| 1 | POST | `/step1-environment` | Select QA or UAT |
| 2 | POST | `/step2-customer-number` | Enter customer phone |
| 3 | POST | `/step3-search-customer` | Search customer by phone |
| 4 | POST | `/step4-customer-details` | Get customer details |
| 5 | POST | `/step5-check-order-status` | Check if order placed |
| 6 | POST | `/step6-place-order` | Select products & place order |
| 8 | POST | `/step8-generate-route-sheet` | Generate route sheet |
| 9 | POST | `/step9-update-route-sheet-date` | Update route sheet date |
| 10 | POST | `/step10-update-order-date` | Update order date |
| 11 | POST | `/step11-mark-sale` | Mark the sale |

## Complete Workflow (All-in-One)

**Endpoint**: `POST /api/workflow/complete`

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
    ],
    "latitude": 28.41873,
    "longitude": 77.03871
  }'
```

## Utility Endpoints

| Purpose | Method | Endpoint |
|---------|--------|----------|
| Get workflow state | GET | `/state` |
| Reset workflow | POST | `/reset` |

## Quick Examples

### Example 1: Step-by-Step Flow

```bash
# 1. Select environment
curl -X POST http://localhost:8080/api/workflow/step1-environment \
  -H "Content-Type: application/json" \
  -d '{"environment":"QA"}'

# 2. Enter customer number
curl -X POST http://localhost:8080/api/workflow/step2-customer-number \
  -H "Content-Type: application/json" \
  -d '{"customerNumber":"9876543210"}'

# 3. Search customer
curl -X POST http://localhost:8080/api/workflow/step3-search-customer \
  -H "Content-Type: application/json"

# 4. Get customer details (use ID from step 3 response)
curl -X POST http://localhost:8080/api/workflow/step4-customer-details \
  -H "Content-Type: application/json" \
  -d '{"customerId":"12345"}'

# 5. Check order status
curl -X POST http://localhost:8080/api/workflow/step5-check-order-status \
  -H "Content-Type: application/json" \
  -d '{"orderAlreadyPlaced":false}'

# 6. Place order (if order not already placed)
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

# 8. Generate route sheet
curl -X POST http://localhost:8080/api/workflow/step8-generate-route-sheet \
  -H "Content-Type: application/json"

# 9. Update route sheet date
curl -X POST http://localhost:8080/api/workflow/step9-update-route-sheet-date \
  -H "Content-Type: application/json"

# 10. Update order date
curl -X POST http://localhost:8080/api/workflow/step10-update-order-date \
  -H "Content-Type: application/json"

# 11. Mark sale
curl -X POST http://localhost:8080/api/workflow/step11-mark-sale \
  -H "Content-Type: application/json" \
  -d '{
    "latitude": 28.41873,
    "longitude": 77.03871
  }'
```

### Example 2: Complete Workflow (Single Request)

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
        "quantity": 2
      },
      {
        "id": 108,
        "quantity": 1
      }
    ]
  }'
```

### Example 3: Order Already Placed (Skip Product Selection)

```bash
# Complete flow but order already placed
curl -X POST http://localhost:8080/api/workflow/complete \
  -H "Content-Type: application/json" \
  -d '{
    "environment": "QA",
    "customerNumber": "9876543210",
    "customerId": "12345",
    "orderAlreadyPlaced": true
  }'
```

## Request/Response Fields

### Step 1: Environment Selection
**Request**: `{"environment": "QA|UAT"}`  
**Response**: Confirmation message

### Step 2: Customer Number
**Request**: `{"customerNumber": "PHONE"}`  
**Response**: Confirmation message

### Step 3: Search Customer
**Request**: (No body)  
**Response**: Array of customer objects with id, name, phone, city

### Step 4: Customer Details
**Request**: `{"customerId": "ID"}`  
**Response**: Customer details with city_id, franchise_id, address

### Step 5: Check Order Status
**Request**: `{"orderAlreadyPlaced": true|false}`  
**Response**: 
- If false: List of available products
- If true: Skip message

### Step 6: Place Order
**Request**: 
```json
{
  "products": [
    {
      "id": NUMBER,
      "quantity": NUMBER,
      "order_type": "daily|weekly|monthly"
    }
  ]
}
```
**Response**: Order confirmation

### Step 8: Generate Route Sheet
**Request**: (No body)  
**Response**: routeSheetId and details

### Step 9: Update Route Sheet Date
**Request**: (No body)  
**Response**: Confirmation

### Step 10: Update Order Date
**Request**: (No body)  
**Response**: Confirmation

### Step 11: Mark Sale
**Request** (optional): 
```json
{
  "latitude": NUMBER,
  "longitude": NUMBER
}
```
**Response**: Confirmation

## Complete Workflow Request
```json
{
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
  ],
  "latitude": 28.41873,
  "longitude": 77.03871
}
```

## Response Format

**Success**:
```json
{
  "success": true,
  "message": "Description",
  "data": {...},
  "nextStep": "Next action"
}
```

**Error**:
```json
{
  "success": false,
  "message": "Error description"
}
```

## Workflow State

### Check Current State
```bash
curl -X GET http://localhost:8080/api/workflow/state
```

**Response**:
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

### Reset Workflow
```bash
curl -X POST http://localhost:8080/api/workflow/reset
```

**Response**:
```json
{
  "success": true,
  "message": "Workflow state reset. Ready for new workflow."
}
```

## Common Scenarios

### Scenario 1: New Order with Products
1. Environment → QA/UAT
2. Customer Number → Phone
3. Search Customer
4. Get Details → Saves cityId
5. Check Status → orderAlreadyPlaced = false
6. Fetch Products → Shows available items
7. Place Order → Select & submit
8-11. Route Sheet & Sale Marking

### Scenario 2: Existing Order (Skip Placement)
1. Environment → QA/UAT
2. Customer Number → Phone
3. Search Customer
4. Get Details
5. Check Status → orderAlreadyPlaced = true ✓ SKIPS 6-7
8-11. Route Sheet & Sale Marking directly

### Scenario 3: Single API Call
Just use `/api/workflow/complete` with all parameters

## Headers Required

```
Content-Type: application/json
```

## Default Values

| Field | Default |
|-------|---------|
| order_type | "daily" |
| latitude | 28.41873333333333 |
| longitude | 77.03871166666666 |

## Built With

- Spring Boot 2.7.14
- Java 11
- RestAssured
- Jackson (JSON)

## File Locations

- **Controller**: `src/main/java/com/countrydelight/controller/InteractiveWorkflowController.java`
- **Service**: `src/main/java/com/countrydelight/service/InteractiveWorkflowService.java`
- **Model**: `src/main/java/com/countrydelight/model/WorkflowRequest.java`
- **Documentation**: `INTERACTIVE_WORKFLOW_GUIDE.md`

## Testing

### Build
```bash
mvn clean package -DskipTests
```

### Run
```bash
java -jar target/sale-marking-automation-1.0.0.jar
```

### Test
```bash
curl -X POST http://localhost:8080/api/workflow/step1-environment \
  -H "Content-Type: application/json" \
  -d '{"environment":"QA"}'
```

---

**Last Updated**: 2026-04-06  
**Status**: ✅ Ready for Testing
