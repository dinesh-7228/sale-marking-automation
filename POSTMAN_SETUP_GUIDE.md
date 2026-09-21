# Postman Integration Guide

## ✅ Status
- **API Server**: ✅ Running on `http://localhost:8080`
- **Collection**: Created at `/postman_collection.json`
- **Base URL**: `http://localhost:8080/api/workflow`

---

## 📥 Import Collection into Postman

### Step 1: Open Postman
- Download and install from [getpostman.com](https://www.getpostman.com/downloads/)
- Or use the web version at [web.postman.co](https://web.postman.co)

### Step 2: Import the Collection
1. Click **Import** (top-left corner)
2. Select **Upload Files**
3. Choose `/postman_collection.json` from your project root
4. Click **Import**

---

## 📋 Available Endpoints

### **Utility Endpoints**
| Method | Endpoint | Purpose |
|--------|----------|---------|
| GET | `/state` | Get current workflow state |
| POST | `/reset` | Reset workflow state |

### **Step-by-Step Flow (11 Steps)**
| Step | Method | Endpoint | Purpose |
|------|--------|----------|---------|
| 1 | POST | `/step1-environment` | Select QA or UAT |
| 2 | POST | `/step2-customer-number` | Enter customer phone |
| 3 | POST | `/step3-search-customer` | Search customer by phone |
| 4 | POST | `/step4-customer-details` | Get customer details |
| 5 | POST | `/step5-check-order-status` | Check if order exists |
| 6 | POST | `/step6-place-order` | Place order with products |
| 8 | POST | `/step8-generate-route-sheet` | Generate route sheet |
| 9 | POST | `/step9-update-route-sheet-date` | Update date to today |
| 10 | POST | `/step10-update-order-date` | Update order date to today |
| 11 | POST | `/step11-mark-sale` | Mark sale with GPS (optional) |

### **Complete Workflow (Recommended)**
| Method | Endpoint | Purpose |
|--------|----------|---------|
| POST | `/complete` | Execute all steps in one API call |

---

## 🚀 Quick Start

### Option 1: Complete Workflow (Fastest)
Execute all steps in a single API call:

```json
POST /complete
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
  "latitude": 28.5355,
  "longitude": 77.3910
}
```

**Response**: Complete workflow result with all steps executed

---

### Option 2: Step-by-Step Flow
1. **Reset** (clear previous state)
   ```
   POST /reset
   ```

2. **Step 1**: Select Environment
   ```json
   POST /step1-environment
   {"environment": "QA"}
   ```

3. **Step 2**: Enter Customer Number
   ```json
   POST /step2-customer-number
   {"customerNumber": "9876543210"}
   ```

4. **Step 3**: Search Customer
   ```
   POST /step3-search-customer
   ```

5. **Step 4**: Get Customer Details
   ```json
   POST /step4-customer-details
   {"customerId": "12345"}
   ```

6. **Step 5**: Check Order Status
   ```json
   POST /step5-check-order-status
   {"orderAlreadyPlaced": false}
   ```

7. **Step 6**: Place Order
   ```json
   POST /step6-place-order
   {
     "products": [
       {"id": 114, "quantity": 2, "order_type": "daily"}
     ]
   }
   ```

8. **Step 8**: Generate Route Sheet
   ```
   POST /step8-generate-route-sheet
   ```

9. **Step 9**: Update Route Sheet Date
   ```
   POST /step9-update-route-sheet-date
   ```

10. **Step 10**: Update Order Date
    ```
    POST /step10-update-order-date
    ```

11. **Step 11**: Mark Sale
    ```json
    POST /step11-mark-sale
    {
      "latitude": 28.5355,
      "longitude": 77.3910
    }
    ```

---

## 📊 Response Format

### Success Response
```json
{
  "success": true,
  "message": "Action completed successfully",
  "data": { /* endpoint-specific data */ },
  "nextStep": "Next action to take"
}
```

### Error Response
```json
{
  "success": false,
  "message": "Error description"
}
```

---

## 🧪 Testing in Postman

### Test 1: Check API Health
```
GET http://localhost:8080/api/workflow/state
Expected: 200 OK with current workflow state
```

### Test 2: Complete Workflow
```
POST http://localhost:8080/api/workflow/complete
Use the complete workflow request body (see above)
Expected: 200 OK with all steps executed
```

### Test 3: Reset State
```
POST http://localhost:8080/api/workflow/reset
Expected: 200 OK with confirmation message
```

---

## 💡 Pro Tips

1. **Use Environment Variables** (Optional)
   - Create a Postman environment with base URL: `http://localhost:8080/api/workflow`
   - Use `{{baseUrl}}` in all requests

2. **Save Responses** for debugging
   - Postman automatically saves responses for each request

3. **Use the Complete Endpoint**
   - Faster than executing step-by-step
   - Better for automation and batch processing

4. **Check Workflow State**
   - Use `GET /state` to verify workflow state between steps

5. **Reset Between Tests**
   - Use `POST /reset` to clear state before new test runs

---

## 🔗 API Base URL
```
http://localhost:8080/api/workflow
```

All endpoints use this base URL.

---

## 📝 Notes

- API is running on **port 8080**
- All requests use **JSON** format
- **Content-Type**: `application/json`
- GPS coordinates (latitude/longitude) are **optional** in step 11

---

## 📞 Troubleshooting

### API not responding?
```bash
# Check if server is running
curl http://localhost:8080/api/workflow/state

# Restart the server
cd /home/dinesh/Documents/sale-marking-automation
java -jar target/sale-marking-automation-1.0.0.jar
```

### Port 8080 already in use?
```bash
# Find process using port 8080
lsof -i :8080

# Kill the process (replace PID)
kill -9 <PID>
```

---

**Collection Updated**: 2026-04-13  
**Status**: ✅ Ready for use
