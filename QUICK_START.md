# Quick Start Guide

## 🚀 Getting Started in 5 Minutes

### Step 1: Configure Database Connection
Edit `db/DatabaseUtil.java` - Replace placeholders:
```java
return DriverManager.getConnection(
    "jdbc:mysql://YOUR_ACTUAL_HOST:3306/beejapuri_QA",
    "YOUR_ACTUAL_USERNAME",
    "YOUR_ACTUAL_PASSWORD");
```

### Step 2: Build & Run Project
```bash
cd /home/dinesh/Documents/sale-marking-automation
mvn clean package
mvn spring-boot:run
```

### Step 3: Open UI in Browser
```
http://localhost:8181

```

### Step 4: Test the Flow
1. **Search Customer**: Enter phone number (e.g., 9999999999)
2. **Select Customer**: Pick from results (must have franchise_id set)
3. **Choose Products**: Pick products and set quantities
4. **Select Order Type**: daily/weekend/alternate
5. **Confirm & Complete**: Click "Place Order & Mark Sale"

---

## 📊 What Happens Automatically

When you click "Place Order & Mark Sale", the system:

```
1. Places Order via CMS API
   ↓
2. Generates Route Sheet (for tomorrow's date)
   ↓
3. Updates Route Sheet Date to TODAY
   ↓
4. Updates Order Detail Start Date to TODAY
   ↓
5. Marks Sale via Delivery API
   ↓
6. Inserts Records in Sales Table
   ✓ DONE!
```

---

## 🔧 Database Manual Verification

### Quick SQL to Check Everything:

**Find your customer's recent orders**:
```sql
SELECT * FROM order_detail 
WHERE customer_id = '9943824' 
ORDER BY id DESC LIMIT 5;
```

**Check route sheets created today**:
```sql
SELECT * FROM route_sheet_details 
WHERE customer_id = '9943824' 
AND delivery_date = CURDATE();
```

**Verify sales were recorded**:
```sql
SELECT * FROM sales_mark 
WHERE created_date >= CURDATE()
ORDER BY created_date DESC LIMIT 10;
```

---

## 🎯 API Endpoints

| Endpoint | Method | Purpose |
|----------|--------|---------|
| `/api/customer/search` | GET | Search customer by phone |
| `/api/product/fetch` | GET | Get products for customer |
| `/api/order/place-and-mark` | POST | Complete entire workflow |
| `/api/order/route-sheet/{id}` | GET | Get route sheet details |

---

## ⚠️ Common Issues & Fixes

### "No customers found"
- ✓ Verify phone number exists in customer table
- ✓ Check phone number format matches database

### "No products available"
- ✓ Ensure city_id is set for customer
- ✓ Verify products exist for that city
- ✓ Check showOnlyCustomerVisible flag

### "Franchise information missing"
- ✓ Update customer address in CRM
- ✓ Set franchise_id in database
- ✓ Reload customer search

### Database Connection Failed
- ✓ Update DB_HOST in DatabaseUtil.java
- ✓ Verify username/password
- ✓ Check MySQL server is running
- ✓ Confirm firewall allows connection

---

## 📱 Using DBeaver for Manual Updates

If you need to manually update dates in database:

**Update Route Sheet Date**:
```sql
UPDATE route_sheet_details 
SET delivery_date = CURDATE() 
WHERE customer_id = '9943824' 
AND delivery_date > CURDATE();
```

**Update Order Start Date**:
```sql
UPDATE order_detail 
SET start_date = CURDATE() 
WHERE customer_id = '9943824' 
AND start_date > CURDATE() 
AND STATUS = 'Y';
```

See full guide: `/home/dinesh/.copilot/session-state/22ddd0ee-2526-4c56-884f-5e4bf9981390/DBEAVER_GUIDE.md`

---

## 📝 File Reference

| File | Purpose |
|------|---------|
| `api/ApiClient.java` | Handles all external API calls |
| `service/CustomerSearchService.java` | Customer search logic |
| `service/ProductService.java` | Product fetch logic |
| `service/SaleMarkingService.java` | Orchestrates complete workflow |
| `controller/*.java` | REST endpoints |
| `db/DatabaseUtil.java` | Database operations |
| `resources/templates/index.html` | Web UI |
| `README.md` | Full documentation |
| `QUICK_START.md` | This file |

---

## 🔑 Key Requirements Met

✅ Customer search by phone via CMS API  
✅ Product API integration with city_id validation  
✅ Franchise_id validation with user message  
✅ Order placement via CMS API  
✅ Route sheet generation  
✅ Automatic date correction (tomorrow → today)  
✅ Order detail date correction  
✅ Sale marking via delivery API  
✅ Interactive web UI  
✅ Error handling throughout  

---

## 📞 Support

For issues, check:
1. Database credentials in DatabaseUtil.java
2. API tokens (may expire) in service classes
3. Customer has franchise_id and city_id set
4. Network connectivity to external APIs
5. DBeaver for database verification

Happy automating! 🎉
