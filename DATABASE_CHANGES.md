# Database Changes & DBeaver Guide

## 📊 How to Change Data in DBeaver

### Quick Overview
DBeaver is a database management tool that lets you modify your MySQL database visually. Here's how to make changes:

---

## 🔧 Step-by-Step: Updating Route Sheet Date

**Scenario**: Route sheet was generated for tomorrow, but you want it for today.

### Method 1: Using DBeaver GUI (Easiest)

1. **Open DBeaver**
   - Connect to your database
   - Navigate to: `beejapuri_QA` → `Tables` → `route_sheet_details`
   - Right-click → `Open` (or double-click the table)

2. **Find Your Row**
   - Look for the customer_id column (e.g., "9943824")
   - Find the row with tomorrow's delivery_date

3. **Edit the Date**
   - Double-click on the `delivery_date` cell
   - Change the date to today
   - Press Enter to save

4. **Verify**
   - The change should appear immediately

### Method 2: Using SQL Query (Recommended for Bulk Updates)

1. **Open SQL Editor**
   - Right-click your database connection
   - Select: `SQL Editor` → `New SQL Script`

2. **Write Update Query**
   ```sql
   UPDATE route_sheet_details 
   SET delivery_date = CURDATE() 
   WHERE customer_id = '9943824' 
   AND delivery_date = DATE_ADD(CURDATE(), INTERVAL 1 DAY);
   ```

3. **Execute**
   - Press `Ctrl+Enter`
   - Check the message: "X row(s) updated"

4. **Verify**
   - Run SELECT to confirm:
     ```sql
     SELECT * FROM route_sheet_details 
     WHERE customer_id = '9943824' 
     AND delivery_date = CURDATE();
     ```

---

## 🔧 Step-by-Step: Updating Order Detail Date

**Scenario**: Order start date was set for tomorrow, need to change to today.

### Method 1: GUI Update

1. **Open Table**
   - Navigate to: `beejapuri_QA` → `Tables` → `order_detail`
   - Right-click → `Open`

2. **Find Order Rows**
   - Filter: `customer_id = 'YOUR_ID'` and `STATUS = 'Y'`
   - Find rows with tomorrow's date in `start_date` column

3. **Edit Dates**
   - Double-click each `start_date` cell
   - Change to today's date
   - Press Enter after each change

### Method 2: SQL Query (Better for Multiple Records)

```sql
UPDATE order_detail 
SET start_date = CURDATE() 
WHERE customer_id = '9943824' 
AND STATUS = 'Y' 
AND start_date = DATE_ADD(CURDATE(), INTERVAL 1 DAY);
```

**Result**: All matching orders updated to today's date.

---

## 🔍 How to Verify Changes Were Made

After updating, always verify:

```sql
-- Check route sheets are today's date
SELECT customer_id, delivery_date, status 
FROM route_sheet_details 
WHERE customer_id = '9943824' 
AND delivery_date = CURDATE();

-- Check order details are today's date
SELECT customer_id, product_id, start_date, STATUS 
FROM order_detail 
WHERE customer_id = '9943824' 
AND STATUS = 'Y'
AND start_date = CURDATE();

-- Check sales were recorded
SELECT * FROM sales_mark 
WHERE route_sheet_detail_id IN (
    SELECT id FROM route_sheet_details 
    WHERE customer_id = '9943824'
);
```

---

## 📝 Common DBeaver Operations

### Connecting to Database

1. File → New Database Connection
2. Choose MySQL
3. Enter:
   - **Server Host**: Your_DB_HOST
   - **Port**: 3306
   - **Database**: beejapuri_QA
   - **Username**: your_username
   - **Password**: your_password
4. Click Test Connection
5. Click Finish

### Opening a Table

1. Expand your connection
2. Expand: Databases → beejapuri_QA → Tables
3. Double-click any table to view data

### Running a Query

1. Right-click your connection
2. Select: SQL Editor → New SQL Script
3. Type your query
4. Press Ctrl+Enter to execute
5. View results in the Result tab

### Filtering Data

When viewing a table:
1. Click the filter icon
2. Set conditions (e.g., customer_id = '9943824')
3. Click OK

### Exporting Data

1. Right-click on table
2. Select: Export Data
3. Choose format (CSV, Excel, etc.)
4. Click Export

---

## ⚠️ Important Safety Tips

### Before Making Changes

1. **Backup Your Data**
   ```sql
   -- Create backup table
   CREATE TABLE route_sheet_details_backup AS 
   SELECT * FROM route_sheet_details;
   ```

2. **Use WHERE Clauses**
   - Never run UPDATE without WHERE
   - Always test SELECT first
   - Example (SAFE):
     ```sql
     SELECT * FROM route_sheet_details 
     WHERE customer_id = '9943824'
     AND delivery_date > CURDATE();
     ```

3. **Check Row Count**
   - Always verify how many rows will be affected
   - If it's more than expected, stop and review

### After Making Changes

1. **Verify with SELECT**
   - Check the data looks correct
   - Confirm date ranges are accurate

2. **Check Dependent Records**
   - If you update route_sheet_details, check order_detail
   - If you update order_detail, check sales_mark
   - Ensure consistency across tables

3. **Keep Audit Trail**
   - Document what you changed
   - Record the date and customer IDs affected
   - Take screenshots for reference

---

## 🔑 Key Columns to Know

### route_sheet_details Table
| Column | Type | Purpose |
|--------|------|---------|
| id | BIGINT | Primary key |
| customer_id | VARCHAR | Customer DB ID |
| delivery_date | DATE | **← You update this** |
| status | VARCHAR | Active/Inactive status |
| created_date | DATETIME | When created |

### order_detail Table
| Column | Type | Purpose |
|--------|------|---------|
| id | BIGINT | Primary key |
| customer_id | VARCHAR | Customer DB ID |
| product_id | INT | Product ID |
| quantity | INT | Order quantity |
| start_date | DATE | **← You update this** |
| status | VARCHAR | 'Y' for active |
| created_date | DATETIME | When created |

### sales_mark Table
| Column | Type | Purpose |
|--------|------|---------|
| id | BIGINT | Primary key |
| route_sheet_detail_id | BIGINT | Links to route sheet |
| product_id | INT | Product ID |
| quantity | INT | Delivered quantity |
| created_date | DATETIME | When marked as sale |

---

## 💡 Useful SQL Functions

### Working with Dates

```sql
-- Get today's date
SELECT CURDATE();  -- 2026-04-01

-- Get tomorrow's date
SELECT DATE_ADD(CURDATE(), INTERVAL 1 DAY);  -- 2026-04-02

-- Get yesterday's date
SELECT DATE_SUB(CURDATE(), INTERVAL 1 DAY);  -- 2026-03-31

-- Compare dates
WHERE delivery_date = CURDATE()           -- today
WHERE delivery_date > CURDATE()           -- future
WHERE delivery_date < CURDATE()           -- past
WHERE delivery_date = CURDATE() + INTERVAL 1 DAY   -- tomorrow
```

### Aggregation Queries

```sql
-- Count records by date
SELECT delivery_date, COUNT(*) as count 
FROM route_sheet_details 
GROUP BY delivery_date;

-- Sum quantities by customer
SELECT customer_id, SUM(quantity) as total 
FROM order_detail 
GROUP BY customer_id;

-- Find missing data
SELECT * FROM route_sheet_details 
WHERE delivery_date IS NULL;
```

---

## 🆘 Troubleshooting

### "Connection Refused"
- Check MySQL server is running
- Verify hostname is correct
- Check firewall allows port 3306

### "Access Denied for user"
- Verify username and password
- Check user has SELECT/UPDATE privileges
- Try with admin account first

### "Deadlock Found"
- Close and reopen the table
- If persists, restart DBeaver
- Check if another session is blocking

### Changes Not Showing
- Click refresh button in DBeaver
- Press F5 to reload table
- Close and reopen the table
- Check if auto-commit is enabled

---

## ✅ Checklist Before Running Production

- [ ] All customer DB IDs are correct
- [ ] Dates are properly formatted (YYYY-MM-DD)
- [ ] WHERE clause filters correct records
- [ ] Verified record count before running UPDATE
- [ ] Backup created before bulk operations
- [ ] Post-update verification completed
- [ ] Sales records properly inserted
- [ ] No duplicate entries created
- [ ] All three tables synchronized (route_sheet_details, order_detail, sales_mark)

---

## 📞 Quick Reference

**To change delivery_date for a customer:**
```sql
UPDATE route_sheet_details 
SET delivery_date = CURDATE() 
WHERE customer_id = 'YOUR_ID'
AND delivery_date > CURDATE();
```

**To change order start_date:**
```sql
UPDATE order_detail 
SET start_date = CURDATE() 
WHERE customer_id = 'YOUR_ID' 
AND STATUS = 'Y'
AND start_date > CURDATE();
```

**To verify changes:**
```sql
SELECT * FROM route_sheet_details 
WHERE customer_id = 'YOUR_ID' 
AND delivery_date = CURDATE();
```

That's it! You now know how to manage your database changes. Happy updating! 🎉
