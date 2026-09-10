# Testing Guide - Customer Search & Order Placement

## Prerequisites
- Application is running on http://localhost:8080 (or your configured port)
- Database is connected and populated with customer data
- You have a valid customer phone number to test with

## Test Scenario 1: Customer Search with Valid Phone Number

### Setup
- Phone number: `9810788205` (adjust based on your actual data)

### Steps
1. **Navigate to Application**
   - Open browser
   - Go to `http://localhost:8080`
   - Select Environment: QA or UAT

2. **Search Customer**
   - Input mobile number: `9810788205`
   - Click "Search Customer" OR Press Enter
   
3. **Verify Results**
   - ✅ Customer card should appear with:
     - Full name (e.g., "Dinesh Kaushik")
     - 📱 Phone: 9810788205
     - 📍 Area: Downtown (or actual area)
     - 🏢 Franchise: Downtown Center (or actual franchise)
     - 🏙️ City: Delhi (or actual city)
   
4. **Click Customer Card**
   - Click on the customer card
   - ✅ Product selection panel should appear
   - ✅ Customer summary should show:
     - Customer: Dinesh Kaushik
     - City ID: Delhi

### Expected Behavior
```
Before Click:
┌─────────────────────────────────────────┐
│ Dinesh Kaushik                          │
│ 📱 Phone: 9810788205                    │
│ 📍 Area: Downtown | 🏢 Franchise: DCF  │
│ 🏙️ City: Delhi                          │
│ ← CLICK THIS CARD                       │
└─────────────────────────────────────────┘

After Click:
✅ Product Selection Panel appears
✅ Products dropdown loads
✅ "Back" and "Proceed" buttons visible
```

---

## Test Scenario 2: Select Products & Place Order

### Prerequisites
- Customer has been selected (from Test Scenario 1)
- Product selection panel is visible

### Steps
1. **View Available Products**
   - ✅ Product list should display with:
     - Product checkbox
     - Product name
     - Product description
     - Quantity input (default: 1)
     - Order type dropdown (daily/weekend/alternate)

2. **Select First Product**
   - Check checkbox for "Milk (500ml)"
   - Set Quantity: 2
   - Order Type: daily
   - ✅ Card should highlight in light purple
   - ✅ Product should be added to selectedProducts

3. **Select Second Product**
   - Check checkbox for "Curd (500ml)"
   - Set Quantity: 1
   - Order Type: daily
   - ✅ Card should highlight

4. **Select Third Product**
   - Check checkbox for "Paneer (250g)"
   - Set Quantity: 1
   - Order Type: weekend
   - ✅ Card should highlight

5. **Deselect One Product (Test Uncheck)**
   - Uncheck "Paneer (250g)"
   - ✅ Highlight should remove
   - ✅ Product should be removed from selectedProducts

6. **Proceed with Order**
   - Check "Milk" and "Curd" again
   - Click "Proceed" button
   - ✅ Order Completion panel should appear

### Expected Output
```
Order Summary:
├─ Milk (500ml) │ Qty: 2 | daily
├─ Curd (500ml) │ Qty: 1 | daily
└─ Button: "Place Order & Mark Sale"
```

---

## Test Scenario 3: Complete Order & Mark Sale

### Prerequisites
- Products have been selected
- Order summary is visible

### Steps
1. **Review Order Summary**
   - ✅ Should show:
     - Product name
     - Quantity
     - Order type (daily/weekend/alternate)

2. **Place Order**
   - Click "Place Order & Mark Sale" button
   - ✅ Loading spinner should appear with text "Processing order & marking sale..."

3. **Verify Success**
   - ✅ After 2-5 seconds, success message should appear:
     ```
     ✅ Sale marked successfully!
     ```
   - ✅ Message should auto-dismiss after 5 seconds
   - ✅ Form should reset to initial state

4. **Form Reset Verification**
   - ✅ Mobile number input should be empty
   - ✅ Customer search panel should show
   - ✅ Product selection panel should hide
   - ✅ Order completion panel should hide

### Expected Response
```json
{
    "success": true,
    "message": "Sale marked successfully!",
    "data": {
        "orderId": 12345,
        "customerId": "9801990",
        "customerName": "Dinesh Kaushik",
        "productCount": 2,
        "productsMarked": 2
    }
}
```

---

## Test Scenario 4: Error Cases

### Test Case 4.1: Invalid Phone Number
**Input**: `12345` (invalid/non-existent)
**Expected**:
- ✅ Loading spinner appears
- ✅ After loading, warning message:
  ```
  No customers found
  ```
- ✅ No customer cards displayed
- ✅ Can search again

### Test Case 4.2: Empty Phone Number
**Input**: (empty)
**Expected**:
- ✅ Immediate error alert:
  ```
  Please enter a phone number
  ```
- ✅ No API call made
- ✅ No loading spinner

### Test Case 4.3: No Products Selected
**Scenario**: 
1. Select customer
2. Don't select any products
3. Click "Proceed"

**Expected**:
- ✅ Error alert appears:
  ```
  Please select at least one product
  ```
- ✅ Stay on product selection panel
- ✅ Can select products again

### Test Case 4.4: Network Error
**Scenario**: Disconnect network before clicking search
**Expected**:
- ✅ Loading spinner appears
- ✅ After timeout, error message:
  ```
  Error: Network error
  ```
- ✅ Can retry search

---

## Test Scenario 5: Environment Switching

### Steps
1. **Change Environment**
   - Click Environment dropdown at top
   - Select "UAT"
   - ✅ Environment status badge updates to "UAT"
   - ✅ Success message: "Switched to UAT environment"

2. **Test with UAT**
   - Enter UAT customer phone number
   - Click Search
   - ✅ Results from UAT database should appear

3. **Switch Back**
   - Select "QA" environment
   - ✅ Status updates back to QA
   - ✅ Can search QA customers

---

## API Endpoint Verification

### Test with Postman (Optional)

#### 1. Customer Search API
```
GET /api/customer/search?phone=9810788205

Response:
{
    "success": true,
    "data": [{
        "ID": 9938341,
        "CUSTOMER_ID": "9801990",
        "FIRST_NAME": "dinesh",
        "PRIMARY_CONTACT_NUMBER": "9810788205",
        "AREA": "Downtown",
        "FRANCHISE": "Downtown Center",
        "CITY": "Delhi",
        "attributes": [...],
        "hasAttributes": true
    }]
}
```

#### 2. Product Fetch API
```
GET /api/product/fetch?customerId=9938341&cityId=Delhi

Response:
{
    "success": true,
    "data": [{
        "id": 1,
        "name": "Milk (500ml)",
        "description": "Fresh milk",
        "price": 50
    }]
}
```

#### 3. Order Placement API
```
POST /api/order/place-and-mark
Content-Type: application/json

Body:
{
    "customerId": "9801990",
    "customerName": "Dinesh Kaushik",
    "cityId": "Delhi",
    "latitude": null,
    "longitude": null,
    "products": [
        {"id": 1, "name": "Milk", "quantity": 2, "order_type": "daily"},
        {"id": 2, "name": "Curd", "quantity": 1, "order_type": "daily"}
    ]
}

Response:
{
    "success": true,
    "message": "Sale marked successfully!",
    "data": {
        "orderId": 12345,
        "productCount": 2,
        "productsMarked": 2
    }
}
```

---

## Debugging Tips

### Browser Console Check
1. Open Developer Tools (F12)
2. Go to Console tab
3. Should NOT see any errors
4. Look for debug logs:
   ```
   🔍 Searching customer by phone: 9810788205
   ✓ Found 1 customers
   📋 Fetching attributes for customer ID: 9938341
   ✓ Attributes added for customer ID: 9938341
   ```

### Network Tab Check
1. Open Developer Tools (F12)
2. Go to Network tab
3. Clear network history
4. Perform customer search
5. Should see:
   - ✅ `/api/customer/search?phone=9810788205` - 200 OK
   - ✅ Response includes all customer fields + attributes

### Local Storage/Session Check
1. Open Developer Tools (F12)
2. Go to Application/Storage tab
3. No critical data should be stored
4. Selected customer is in JavaScript memory only

---

## Performance Metrics

### Expected Response Times
- **Customer Search**: < 1 second
- **Product Fetch**: < 500ms
- **Order Placement**: < 2 seconds

### Expected Behavior
- ✅ Loading spinner shows while waiting
- ✅ Alerts appear on success/error
- ✅ UI remains responsive

---

## Test Data

### Sample Customers (adjust based on your database)
```
Phone Numbers to Test:
├─ 9810788205 - Should have area, franchise, city
├─ 9876543210 - Alternative test customer
├─ 1234567890 - Non-existent (should show error)
└─ 12345     - Invalid format (should show error)
```

---

## Success Criteria

All of the following must pass for successful deployment:

- [ ] Customer search returns results without undefined values
- [ ] Customer card displays all information correctly
- [ ] Clicking customer card loads products
- [ ] Product selection works (check/uncheck)
- [ ] Order summary displays correctly
- [ ] Order placement succeeds and shows success message
- [ ] Form resets after successful order
- [ ] Error messages appear for invalid inputs
- [ ] Environment switching works
- [ ] No console errors
- [ ] Response times are acceptable
- [ ] UI is responsive and intuitive

---

## Sign-Off

**Test Completed**: _______________
**Tester Name**: ___________________
**Date**: _________________________
**Issues Found**: __________________

---

## Support

If tests fail:
1. Check application logs for backend errors
2. Verify database connectivity
3. Check if customer phone numbers exist in database
4. Verify environment configuration
5. Clear browser cache (Ctrl+Shift+Delete)
6. Check database query results manually

**Contact**: Development Team
