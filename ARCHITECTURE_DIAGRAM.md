# Architecture & Data Flow Diagram

## 🏗️ System Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│                         FRONTEND (UI)                               │
│                                                                     │
│  ┌──────────────────────────────────────────────────────────────┐  │
│  │                    HTML Templates                            │  │
│  │  • templates/index.html                                      │  │
│  │  • static/index.html                                         │  │
│  └──────────────────────────────────────────────────────────────┘  │
│                              │                                       │
│                              ↓                                       │
│  ┌──────────────────────────────────────────────────────────────┐  │
│  │                    JavaScript Functions                      │  │
│  │  • searchCustomer()     - Calls API                          │  │
│  │  • displayCustomers()   - Shows customer cards [FIXED]       │  │
│  │  • selectCustomer()     - Activates selection [FIXED]        │  │
│  │  • fetchProducts()      - Loads products [FIXED]             │  │
│  │  • completeOrder()      - Places order [FIXED]               │  │
│  └──────────────────────────────────────────────────────────────┘  │
│                              │                                       │
│                              ↓                                       │
│  ┌──────────────────────────────────────────────────────────────┐  │
│  │                   REST API Calls                             │  │
│  │  • /api/customer/search?phone=XXX                            │  │
│  │  • /api/product/fetch?customerId=X&cityId=Y                 │  │
│  │  • /api/order/place-and-mark (POST)                          │  │
│  └──────────────────────────────────────────────────────────────┘  │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
                              │
                    HTTP Requests/Responses
                              │
                              ↓
┌─────────────────────────────────────────────────────────────────────┐
│                        BACKEND (API)                                │
│                                                                     │
│  ┌──────────────────────────────────────────────────────────────┐  │
│  │              Spring Boot REST Controllers                    │  │
│  │  • CustomerController.searchByPhone()                        │  │
│  │  • ProductController.fetchProducts()                         │  │
│  │  • OrderController.placeAndMark()                            │  │
│  └──────────────────────────────────────────────────────────────┘  │
│                              │                                       │
│                              ↓                                       │
│  ┌──────────────────────────────────────────────────────────────┐  │
│  │                   Business Services                          │  │
│  │  • CustomerSearchService [ENHANCED]                          │  │
│  │    - searchCustomerByPhone()                                 │  │
│  │    - Fetches customer_attributes [NEW]                       │  │
│  │    - Enriches with AREA, FRANCHISE, CITY                     │  │
│  │                                                              │  │
│  │  • ProductService                                            │  │
│  │  • OrderService                                              │  │
│  └──────────────────────────────────────────────────────────────┘  │
│                              │                                       │
│                              ↓                                       │
│  ┌──────────────────────────────────────────────────────────────┐  │
│  │                  Database Utilities                          │  │
│  │  • DatabaseUtil.searchCustomerByPhone()                      │  │
│  │  • DatabaseUtil.getCustomerAttributes()  [NEW USAGE]         │  │
│  │  • DatabaseUtil.getOrderDetails()                            │  │
│  │  • DatabaseUtil.insertSaleRecord()                           │  │
│  └──────────────────────────────────────────────────────────────┘  │
│                              │                                       │
│                              ↓                                       │
│  ┌──────────────────────────────────────────────────────────────┐  │
│  │                    JDBC Connection                           │  │
│  │  • MySQL Connection Pool                                     │  │
│  │  • Prepared Statements                                       │  │
│  │  • Transaction Management                                    │  │
│  └──────────────────────────────────────────────────────────────┘  │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
                              │
                         SQL Queries
                              │
                              ↓
┌─────────────────────────────────────────────────────────────────────┐
│                         DATABASE                                     │
│                                                                     │
│  Tables:                                                            │
│  ├─ customer                      [Search by phone]                │
│  ├─ customer_attributes           [Fetch AREA/FRANCHISE/CITY]      │
│  ├─ product                       [Product list]                   │
│  ├─ order_detail                  [Create orders]                  │
│  ├─ route_sheet_details           [Sales marking]                  │
│  └─ sale_distribution_detail      [Sales records]                  │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

---

## 🔄 Customer Search Data Flow

```
┌─────────────────────────────────────────────────────────────────┐
│ USER ENTERS PHONE: 9810788205                                   │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│ FRONTEND: searchCustomer()                                      │
│ ├─ Validate input                                              │
│ ├─ Show loading spinner                                        │
│ └─ Fetch /api/customer/search?phone=9810788205                 │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│ BACKEND: CustomerController.searchByPhone()                     │
│ └─ Call customerSearchService.searchCustomerByPhone()           │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│ SERVICE: CustomerSearchService [ENHANCED]                       │
│                                                                 │
│ Step 1: Call apiClient.searchCustomerByPhone()                │
│         └─ Calls external API to search customers              │
│           └─ Returns: ID, CUSTOMER_ID, FIRST_NAME, etc.        │
│                                                                 │
│ Step 2: For each customer found:                               │
│         ├─ Extract customer ID                                 │
│         ├─ Call databaseUtil.getCustomerAttributes(ID)  [NEW]  │
│         │  └─ Query: SELECT * FROM customer_attributes         │
│         │     WHERE ca.CUSTOMER = ID                           │
│         └─ Enrich response with:                               │
│            ├─ AREA (from attributes)                           │
│            ├─ FRANCHISE (from attributes)                      │
│            ├─ CITY (from attributes)                           │
│            └─ attributes array (complete object)               │
│                                                                 │
│ Step 3: Return enriched customer data                          │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│ API RESPONSE (JSON)                                             │
│                                                                 │
│ {                                                               │
│   "success": true,                                              │
│   "data": [                                                     │
│     {                                                           │
│       "ID": 9938341,                                            │
│       "CUSTOMER_ID": "9801990",                                 │
│       "FIRST_NAME": "dinesh",                                   │
│       "PRIMARY_CONTACT_NUMBER": "9810788205",                   │
│       "AREA": "Downtown",          ← Added by service          │
│       "FRANCHISE": "Downtown Center", ← Added by service       │
│       "CITY": "Delhi",             ← Added by service          │
│       "attributes": [              ← Added by service          │
│         {                                                       │
│           "CUSTOMER": "9801990",                                │
│           "AREA": "Downtown",                                   │
│           "FRANCHISE": "Downtown Center",                       │
│           "CITY": "Delhi"                                       │
│         }                                                       │
│       ],                                                        │
│       "hasAttributes": true        ← Added by service          │
│     }                                                           │
│   ]                                                             │
│ }                                                               │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│ FRONTEND: displayCustomers(data.data)  [FIXED]                  │
│                                                                 │
│ For each customer:                                              │
│ ├─ Extract FIRST_NAME + LAST_NAME → fullName                   │
│ ├─ Extract AREA (or from attributes[0].AREA) → area            │
│ ├─ Extract FRANCHISE (or from attributes) → franchise          │
│ ├─ Extract CITY (or from attributes) → city                    │
│ ├─ Build onclick with correct parameters:                      │
│ │  {                                                            │
│ │    id: 9938341,                                               │
│ │    customerId: "9801990",                                     │
│ │    name: "dinesh",                                            │
│ │    area: "Downtown",                                          │
│ │    franchise: "Downtown Center",                              │
│ │    city: "Delhi",                                             │
│ │    attributes: [...]                                          │
│ │  }                                                            │
│ └─ Display customer card with emojis                           │
│                                                                 │
│    Dinesh Kaushik                                               │
│    📱 Phone: 9810788205                                         │
│    📍 Area: Downtown | 🏢 Franchise: Downtown Center          │
│    🏙️ City: Delhi                                               │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│ USER SEES CUSTOMER CARD WITH ALL INFORMATION                    │
│ └─ Card is CLICKABLE ✅                                          │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│ USER CLICKS CUSTOMER CARD                                       │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│ FRONTEND: selectCustomer(customer)  [ACTIVATED]                 │
│                                                                 │
│ ├─ Store customer object in memory:                             │
│ │  selectedCustomer = customer                                  │
│ │                                                               │
│ ├─ Update UI:                                                   │
│ │  ├─ Show customer summary                                     │
│ │  ├─ Display customer name                                     │
│ │  ├─ Display city                                              │
│ │  ├─ Hide search panel                                         │
│ │  └─ Show product selection panel                              │
│ │                                                               │
│ └─ Call fetchProducts()                                         │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│ PRODUCT SELECTION PANEL APPEARS ✅                               │
│ Ready for user to select products                               │
└─────────────────────────────────────────────────────────────────┘
```

---

## 🛒 Order Placement Data Flow

```
┌─────────────────────────────────────────────────────────────────┐
│ USER SELECTS PRODUCTS & QUANTITIES                              │
│ ├─ Milk: Qty 2, Type: daily                                     │
│ ├─ Curd: Qty 1, Type: daily                                     │
│ └─ Clicks "Proceed"                                             │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│ FRONTEND: proceedToOrder()                                      │
│ ├─ Build order summary                                          │
│ └─ Show order confirmation panel                                │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│ USER REVIEWS ORDER & CLICKS "PLACE ORDER & MARK SALE"           │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│ FRONTEND: completeOrder()  [FIXED]                              │
│                                                                 │
│ ├─ Prepare order data:                                          │
│ │  {                                                            │
│ │    customerId: "9801990",  ✅ Using CUSTOMER_ID              │
│ │    customerName: "Dinesh Kaushik",                            │
│ │    cityId: "Delhi",        ✅ Using city                     │
│ │    products: [                                                │
│ │      {id: 1, name: "Milk", quantity: 2, order_type: "daily"}│
│ │      {id: 2, name: "Curd", quantity: 1, order_type: "daily"}│
│ │    ]                                                          │
│ │  }                                                            │
│ │                                                               │
│ ├─ Show loading spinner                                         │
│ └─ POST /api/order/place-and-mark                               │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│ BACKEND: OrderController.placeAndMark()                         │
│ └─ Call orderService.placeAndMarkSale()                         │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│ SERVICE: Processes Order                                        │
│                                                                 │
│ ├─ Fetch customer details from DB                              │
│ ├─ Create order_detail records (1 per product)                 │
│ ├─ Insert sales records in sale_distribution_detail            │
│ ├─ Update route_sheet_details dates                            │
│ ├─ Update order_detail dates                                   │
│ └─ Commit transaction or rollback on error                      │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│ API RESPONSE                                                    │
│                                                                 │
│ {                                                               │
│   "success": true,                                              │
│   "message": "Sale marked successfully!",                       │
│   "data": {                                                     │
│     "orderId": 12345,                                           │
│     "customerId": "9801990",                                    │
│     "productCount": 2,                                          │
│     "productsMarked": 2                                         │
│   }                                                             │
│ }                                                               │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│ FRONTEND: Handle Success                                        │
│ ├─ Hide loading spinner                                         │
│ ├─ Show success alert: "✅ Sale marked successfully!"           │
│ ├─ Auto-dismiss alert after 5 seconds                           │
│ └─ Call resetForm()                                             │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│ UI STATE RESET                                                  │
│ ├─ Clear phone input                                            │
│ ├─ Hide all panels                                              │
│ ├─ Show search panel                                            │
│ ├─ Clear selected customer                                      │
│ └─ Clear selected products                                      │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│ READY FOR NEXT ORDER ✅                                          │
└─────────────────────────────────────────────────────────────────┘
```

---

## 🗄️ Database Schema (Relevant Tables)

```sql
-- Customer table
CREATE TABLE customer (
    ID BIGINT PRIMARY KEY,
    CUSTOMER_ID VARCHAR(50),
    FIRST_NAME VARCHAR(100),
    LAST_NAME VARCHAR(100),
    PRIMARY_CONTACT_NUMBER VARCHAR(20),
    EMAIL VARCHAR(100),
    -- ... other fields
    KEY idx_phone (PRIMARY_CONTACT_NUMBER)
);

-- Customer attributes table [NEW USAGE]
CREATE TABLE customer_attributes (
    ID BIGINT PRIMARY KEY,
    CUSTOMER BIGINT,  -- Foreign key to customer.ID
    AREA VARCHAR(100),
    FRANCHISE VARCHAR(100),
    CITY VARCHAR(100),
    -- ... other attributes
    FOREIGN KEY (CUSTOMER) REFERENCES customer(ID)
);

-- Query to fetch attributes for a customer:
-- SELECT * FROM customer_attributes ca 
-- WHERE ca.CUSTOMER = ? (customer.ID)
```

---

## ✨ Key Improvements Summary

```
┌──────────────────────────────────┬──────────────────────────────┐
│        BEFORE (Broken)           │       AFTER (Fixed)          │
├──────────────────────────────────┼──────────────────────────────┤
│ db_id: undefined                 │ id: 9938341 ✅               │
│ name: undefined                  │ name: Dinesh Kaushik ✅      │
│ city_id: undefined               │ city: Delhi ✅               │
│ franchise_id: empty              │ franchise: Downtown Center ✅ │
│ No area info                     │ area: Downtown ✅            │
│ selectCustomer() commented       │ selectCustomer() active ✅   │
│ Products don't load              │ Products load ✅             │
│ Order fails to place             │ Order places successfully ✅ │
│ Data mismatch errors             │ Clean data flow ✅           │
│ Poor UX                          │ Rich UX with emojis ✅       │
└──────────────────────────────────┴──────────────────────────────┘
```

---

**Status**: ✅ Complete & Ready for Deployment
**Last Updated**: April 24, 2026
