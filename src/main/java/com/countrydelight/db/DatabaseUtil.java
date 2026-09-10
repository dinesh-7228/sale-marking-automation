package com.countrydelight.db;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import com.countrydelight.config.EnvironmentConfig;
import java.sql.*;
import java.util.*;
import java.time.LocalDate;


@Component
public class DatabaseUtil {

    @Autowired
    private Environment env;

    @Autowired
    private EnvironmentConfig envConfig;

private Connection getConnection() throws SQLException {
        String dbHost = getEnvValue("db.host", "non-prod-apps-dbs.cxmdwl4djaa6.ap-south-1.rds.amazonaws.com");
        String dbPort = getEnvValue("db.port", "3306");
        String dbName = getDbName();
        String dbUser = getEnvValue("db.user", "dinesh");
        String dbPassword = getEnvValue("db.password", "pjqg4ry4ir6QSGh");

        String url = "jdbc:mysql://" + dbHost + ":" + dbPort + "/" + dbName;
        System.out.println("🔌 Database Connection URL: " + url);
        
        return DriverManager.getConnection(url, dbUser, dbPassword);
    }

    /**
     * Selects the database schema based on the active environment:
     * QA -> beejapuri_QA, UAT -> beejapuri_UAT.
     */
    private String getDbName() {
        String envName = envConfig.getEnv();
        String dbName = envName != null && envName.equalsIgnoreCase("UAT") ? "beejapuri_UAT" : "beejapuri_QA";
        System.out.println("✓ Using database for " + (envName == null ? "QA" : envName.toUpperCase()) + ": " + dbName);
        return dbName;
    }

    private String getEnvValue(String key, String defaultValue) {
        // Try from environment first
        String envValue = System.getenv(key.replace(".", "_").toUpperCase());
        if (envValue != null && !envValue.isEmpty()) {
            System.out.println("✓ Using environment variable: " + key + " = " + envValue);
            return envValue;
        }
        
        // Try from Spring properties
        String propValue = env.getProperty(key);
        if (propValue != null && !propValue.isEmpty()) {
            System.out.println("✓ Using Spring property: " + key + " = " + propValue);
            return propValue;
        }
        
        // Use default
        System.out.println("ℹ Using default for " + key + ": " + defaultValue);
        return defaultValue;
    }

    /**
     * Fetches customer ID from customer table using phone number
     */
    public Map<String, Object> getCustomerIdByPhone(String phone) throws Exception {
        String query = "SELECT c.ID, c.CUSTOMER_ID FROM customer c WHERE c.PRIMARY_CONTACT_NUMBER = ?";
        Map<String, Object> result = new HashMap<>();

        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(query)) {

            ps.setString(1, phone);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                result.put("db_id", rs.getLong("ID"));
                result.put("customer_id", rs.getString("CUSTOMER_ID"));
                System.out.println("✓ Customer found. DB ID: " + result.get("db_id") + ", Customer ID: " + result.get("customer_id"));
            } else {
                System.out.println("⚠ No customer found for phone: " + phone);
            }
        } catch (SQLException e) {
            throw new RuntimeException("CRITICAL: Failed to fetch customer by phone " + phone + ": " + e.getMessage(), e);
        }

        return result;
    }

    /**
     * Resolves the internal DB customer ID from the CMS customer_id.
     *
     * The CMS APIs (placeOrder, addFunds, generateRouteSheet) reference the
     * customer by the search result's customer_id (e.g. 9767981), while the
     * route_sheet_details / order_detail tables reference the customer by the
     * DB primary key ID (e.g. 9918305).
     *
     * @param cmsCustomerId customer_id from the customer search result (Step-3)
     * @return the DB ID (customer.ID) for the customer, or null if not found
     */
    public String getDbCustomerId(String cmsCustomerId) throws Exception {
        if (cmsCustomerId == null || cmsCustomerId.trim().isEmpty()) {
            return null;
        }
        String query = "SELECT ID FROM customer WHERE CUSTOMER_ID = ?";
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(query)) {
            ps.setString(1, cmsCustomerId.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String dbId = String.valueOf(rs.getLong("ID"));
                    System.out.println("✓ Resolved CMS customer_id " + cmsCustomerId + " -> DB ID " + dbId);
                    return dbId;
                }
            }
            System.out.println("⚠ No DB customer found for CMS customer_id: " + cmsCustomerId);
        } catch (SQLException e) {
            throw new RuntimeException("CRITICAL: Failed to resolve DB customer id for " + cmsCustomerId + ": " + e.getMessage(), e);
        }
        return null;
    }

    /**
     * Fetches customer details from customer_attributes table
     */
    public Map<String, Object> getCustomerAttributes(Long customerId) throws Exception {
        String query = "SELECT * FROM customer_attributes ca WHERE ca.CUSTOMER = ?";
        Map<String, Object> result = new HashMap<>();

        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(query)) {

            ps.setLong(1, customerId);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                ResultSetMetaData metadata = rs.getMetaData();
                for (int i = 1; i <= metadata.getColumnCount(); i++) {
                    result.put(metadata.getColumnName(i), rs.getObject(i));
                }
                System.out.println("✓ Customer attributes fetched for ID: " + customerId);
            } else {
                System.out.println("⚠ No attributes found for customer: " + customerId);
            }
        } catch (SQLException e) {
            throw new RuntimeException("CRITICAL: Failed to fetch customer attributes for " + customerId + ": " + e.getMessage(), e);
        }

        return result;
    }

    /**
     * Automatically updates route sheet date to the sale_marking_date
     * and sets delivery_boy = 26747.
     * If saleDate is null, defaults to current date.
     * 
     * Called immediately after route sheet generation
     * Handles both 'delivery_date' and 'DATE' column names
     */
    public void updateRouteSheetDate(String customerId) throws Exception {
        updateRouteSheetDate(customerId, null);
    }

    /**
     * Updates route sheet date to the specified sale_marking_date
     * and sets delivery_boy = 26747.
     *
     * @param customerId customer id from step-2 customer search
     * @param saleDate   date string (yyyy-MM-dd or dd-MM-yyyy). Defaults to today if null.
     */
    public void updateRouteSheetDate(String customerId, String saleDate) throws Exception {
        String formattedDate = normalizeDateParam(saleDate);
        try {
            updateRouteSheetDateInternal(customerId, formattedDate);
        } catch (SQLException e) {
            if (e.getMessage() != null && (e.getMessage().contains("Unknown column") ||
                e.getMessage().contains("no such column"))) {
                System.out.println("⚠ Column 'delivery_date' not found, trying 'DATE'...");
                updateRouteSheetDateAlternative(customerId, formattedDate);
            } else {
                throw new RuntimeException("CRITICAL: Failed to update route sheet date for customer " + customerId +
                                         ": " + e.getMessage(), e);
            }
        }
    }

    private void updateRouteSheetDateInternal(String customerId, String dateSql) throws SQLException {
        // Step-9: target the latest route sheet (ORDER BY ID DESC) and set DELIVERY_BOY = 26747
        String query = "UPDATE route_sheet_details " +
                       "SET `DATE` = ?, `DELIVERY_BOY` = 26747 " +
                       "WHERE CUSTOMER = ? " +
                       "AND ID = (SELECT * FROM (SELECT MAX(ID) FROM route_sheet_details WHERE CUSTOMER = ?) t)";
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(query)) {
            ps.setDate(1, java.sql.Date.valueOf(dateSql));
            ps.setString(2, customerId);
            ps.setString(3, customerId);
            int rowsUpdated = ps.executeUpdate();
            if (rowsUpdated > 0) {
                System.out.println("✓ Route sheet date updated to " + dateSql + " and delivery_boy=26747 for customer: " + customerId);
            } else {
                System.out.println("⚠ No route sheet found for date/delivery_boy update. Customer: " + customerId);
            }
        }
    }

    /**
     * Alternative: Updates route sheet date using only backticked columns
     */
    private void updateRouteSheetDateAlternative(String customerId, String dateSql) throws SQLException {
        String query = "UPDATE route_sheet_details " +
                       "SET `DATE` = ?, `DELIVERY_BOY` = 26747 " +
                       "WHERE `CUSTOMER` = ? " +
                       "AND `ID` = (SELECT * FROM (SELECT MAX(`ID`) FROM route_sheet_details WHERE `CUSTOMER` = ?) t)";
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(query)) {
            ps.setDate(1, java.sql.Date.valueOf(dateSql));
            ps.setString(2, customerId);
            ps.setString(3, customerId);
            int rowsUpdated = ps.executeUpdate();
            if (rowsUpdated > 0) {
                System.out.println("✓ Route sheet `DATE` updated to " + dateSql + " and delivery_boy=26747 for customer: " + customerId);
            } else {
                System.out.println("⚠ No route sheet found for alternative date/delivery_boy update. Customer: " + customerId);
            }
        }
    }

    /**
     * Automatically updates order detail start date to today (sale marking date)
     * Called immediately after order placement
     * 
     * Sets ORDER_START_DATE = sale_marking_date (today by default)
     */
    public void updateOrderDetailDate(String customerId) throws Exception {
        updateOrderDetailDate(customerId, null);
    }

    /**
     * Updates order detail start date to the specified sale_marking_date.
     *
     * Per API doc Step-10:
     * UPDATE order_detail
     * SET ORDER_START_DATE = ORDER_START_DATE - INTERVAL 1 DAY
     * WHERE CUSTOMER = ?
     *   AND STATUS = 'Y'
     *   AND ORDER_START_DATE >= CURRENT_DATE + INTERVAL 1 DAY
     *   AND ORDER_START_DATE < CURRENT_DATE + INTERVAL 2 DAY;
     * 
     * to the sale marking date (ORDER_START_DATE = sale_marking_date)
     *
     * @param customerId customer id from step-2 customer search
     * @param saleDate   sale marking date (dd-MM-yyyy). Defaults to today if null.
     */
    public void updateOrderDetailDate(String customerId, String saleDate) throws Exception {
        String normalizeDate = normalizeDateParam(saleDate);
        String whereClause = "WHERE CUSTOMER = ? AND STATUS = 'Y' " +
                             "AND ORDER_START_DATE >= CURRENT_DATE + INTERVAL 1 DAY " +
                             "AND ORDER_START_DATE < CURRENT_DATE + INTERVAL 2 DAY";

        try {
            String query = "UPDATE order_detail SET ORDER_START_DATE = ? " + whereClause;
            try (Connection con = getConnection();
                 PreparedStatement ps = con.prepareStatement(query)) {
                ps.setDate(1, java.sql.Date.valueOf(normalizeDate));
                ps.setString(2, customerId);
                int rowsUpdated = ps.executeUpdate();
                if (rowsUpdated > 0) {
                    System.out.println("✓ Order detail ORDER_START_DATE updated to " + normalizeDate + " for customer: " + customerId);
                } else {
                    System.out.println("⚠ No active orders found for date correction. Customer: " + customerId);
                }
            }
        } catch (SQLException e) {
            if (e.getMessage() != null && (e.getMessage().contains("Unknown column") ||
                e.getMessage().contains("no such column"))) {
                System.out.println("⚠ Column 'ORDER_START_DATE' not found, trying alternative...");
                tryAlternativeUpdateOrderDetailDate(customerId, normalizeDate);
            } else {
                throw new RuntimeException("CRITICAL: Failed to update order detail date for customer " + customerId +
                                         ": " + e.getMessage(), e);
            }
        }
    }

    /**
     * Alternative: Updates order detail date using `ORDER_START_DATE` column
     * Sets ORDER_START_DATE to the sale_marking_date
     */
    private void tryAlternativeUpdateOrderDetailDate(String customerId, String dateSql) throws Exception {
        String query = "UPDATE order_detail " +
                       "SET `ORDER_START_DATE` = ? " +
                       "WHERE `CUSTOMER` = ? AND `STATUS` = 'Y' " +
                       "AND `ORDER_START_DATE` >= CURRENT_DATE + INTERVAL 1 DAY " +
                       "AND `ORDER_START_DATE` < CURRENT_DATE + INTERVAL 2 DAY";

        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(query)) {
            ps.setDate(1, java.sql.Date.valueOf(dateSql));
            ps.setString(2, customerId);
            int rowsUpdated = ps.executeUpdate();
            if (rowsUpdated > 0) {
                System.out.println("✓ Order detail `ORDER_START_DATE` updated to " + dateSql + " for customer: " + customerId);
            } else {
                System.out.println("⚠ No active orders found for alternative date correction. Customer: " + customerId);
            }
        } catch (SQLException e) {
            throw new RuntimeException("CRITICAL: Failed to update order detail date (both attempts) for customer " +
                                     customerId + ": " + e.getMessage(), e);
        }
    }

    /**
     * Retrieves the latest route sheet details for a customer
     * Used to get the ID for sales marking
     */
    public Map<String, Object> getRouteSheetDetails(String customerId) throws Exception {
        String query = "SELECT ID, CUSTOMER, DATE FROM route_sheet_details " +
                       "WHERE CUSTOMER = ? ORDER BY ID DESC LIMIT 1";
        Map<String, Object> result = new HashMap<>();

        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(query)) {

            ps.setString(1, customerId);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                result.put("id", rs.getLong("ID"));
                result.put("customer", rs.getString("CUSTOMER"));
                result.put("date", rs.getDate("DATE"));
                System.out.println("✓ Route sheet details retrieved. ID: " + result.get("id"));
            } else {
                System.out.println("⚠ No route sheet found for customer: " + customerId);
            }
        } catch (SQLException e) {
            throw new RuntimeException("CRITICAL: Failed to fetch route sheet details for customer " + customerId + 
                                     ": " + e.getMessage(), e);
        }

        return result;
    }

    /**
     * Retrieves the delivery_boy id from route_sheet_details for a customer
     * Used for the sale marking API (delivery_boy field)
     *
     * @param customerId customer id from step-2 customer search
     * @return delivery boy id, or null if not found
     */
    public Integer getDeliveryBoy(String customerId) throws Exception {
        String query = "SELECT DELIVERY_BOY FROM route_sheet_details " +
                       "WHERE CUSTOMER = ? ORDER BY ID DESC LIMIT 1";
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(query)) {
            ps.setString(1, customerId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                Integer deliveryBoy = rs.getObject("DELIVERY_BOY") != null ? rs.getInt("DELIVERY_BOY") : null;
                System.out.println("✓ Delivery boy retrieved for customer " + customerId + ": " + deliveryBoy);
                return deliveryBoy;
            }
            System.out.println("⚠ No route sheet found for delivery boy lookup. Customer: " + customerId);
        } catch (SQLException e) {
            System.out.println("⚠ Delivery boy lookup failed: " + e.getMessage());
        }
        return null;
    }

    /**
     * Fetches the customer's current wallet balance from the latest route sheet
     * (Step-6 wallet balance check). Read-only.
     *
     * @param customerId customer id from step-2 customer search
     * @return wallet balance, or null if not found
     */
    public Double getWalletBalance(String customerId) throws Exception {
        String query = "SELECT CURRENT_WALLET_BALANCE FROM route_sheet_details " +
                       "WHERE CUSTOMER = ? ORDER BY ID DESC LIMIT 1";
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(query)) {
            ps.setString(1, customerId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                Double balance = rs.getObject("CURRENT_WALLET_BALANCE") != null ? rs.getDouble("CURRENT_WALLET_BALANCE") : null;
                System.out.println("✓ Wallet balance retrieved for customer " + customerId + ": " + balance);
                return balance;
            }
            System.out.println("⚠ No route sheet found for wallet balance lookup. Customer: " + customerId);
        } catch (SQLException e) {
            System.out.println("⚠ Wallet balance lookup failed: " + e.getMessage());
        }
        return null;
    }

    /**
     * Normalizes a sale date string to yyyy-MM-dd format (for java.sql.Date)
     * Accepts dd-MM-yyyy or yyyy-MM-dd. Defaults to today if null/invalid.
     */
    private String normalizeDateParam(String saleDate) {
        if (saleDate == null || saleDate.trim().isEmpty()) {
            return LocalDate.now().toString();
        }
        String trimmed = saleDate.trim();
        try {
            if (trimmed.matches("\\d{2}-\\d{2}-\\d{4}")) {
                java.time.format.DateTimeFormatter f = java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy");
                return LocalDate.parse(trimmed, f).toString();
            }
            if (trimmed.matches("\\d{4}-\\d{2}-\\d{2}")) {
                return LocalDate.parse(trimmed).toString();
            }
        } catch (Exception e) {
            System.out.println("⚠ Invalid sale date '" + saleDate + "', defaulting to today");
        }
        return LocalDate.now().toString();
    }

    /**
     * Gets the latest order details for a customer
     */
    public List<Map<String, Object>> getOrderDetails(String customerId) throws Exception {
        String query = "SELECT ID, CUSTOMER, PRODUCT_ID, QUANTITY, STATUS, ORDER_START_DATE " +
                       "FROM order_detail WHERE CUSTOMER = ? AND STATUS = 'Y' " +
                       "ORDER BY ID DESC LIMIT 10";
        List<Map<String, Object>> results = new ArrayList<>();

        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(query)) {

            ps.setString(1, customerId);
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                Map<String, Object> order = new HashMap<>();
                order.put("id", rs.getLong("ID"));
                order.put("customer", rs.getString("CUSTOMER"));
                order.put("product_id", rs.getInt("PRODUCT_ID"));
                order.put("quantity", rs.getInt("QUANTITY"));
                order.put("status", rs.getString("STATUS"));
                order.put("order_start_date", rs.getDate("ORDER_START_DATE"));
                results.add(order);
            }
            
            if (!results.isEmpty()) {
                System.out.println("✓ Order details retrieved. Count: " + results.size());
            }
        } catch (SQLException e) {
            throw new RuntimeException("CRITICAL: Failed to fetch order details for customer " + customerId + 
                                     ": " + e.getMessage(), e);
        }

        return results;
    }

    /**
     * Inserts a sales record linking route sheet and product
     * Uses sale_distribution_detail table for validation.
     * A sale_distribution_detail row is delivery-level (one per route sheet
     * detail), keyed by ROUTE_SHEET_DETAILS. If the sale_create API (Step-11)
     * already recorded the delivery, this is a no-op (idempotent).
     */
    public void insertSaleRecord(Long routeSheetDetailId) throws Exception {
        ensureSaleDistributionRow(routeSheetDetailId);
    }

    /**
     * Inserts the sale distribution row for a route sheet detail if not already
     * recorded by the sale_create API. One row per delivery (the table has no
     * product/quantity columns), so it is called once per route sheet detail.
     */
    private Long ensureSaleDistributionRow(Long routeSheetDetailId) throws Exception {
        if (routeSheetDetailId == null) {
            throw new IllegalArgumentException("Route sheet detail id is required for sale recording");
        }

        try (Connection con = getConnection()) {
            String checkSql = "SELECT COUNT(*) FROM sale_distribution_detail WHERE ROUTE_SHEET_DETAILS = ?";
            try (PreparedStatement ps = con.prepareStatement(checkSql)) {
                ps.setLong(1, routeSheetDetailId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next() && rs.getInt(1) > 0) {
                        System.out.println("✓ Sale already recorded for route sheet detail: " + routeSheetDetailId + " (no-op)");
                        return routeSheetDetailId;
                    }
                }
            }

            String insertSql = "INSERT INTO sale_distribution_detail " +
                               "(ROUTE_SHEET_DETAILS, CUSTOMER, ROUTE_SHEET, ROUTE, FRANCHISE, DELIVERY_BOY, DATE, DELIVERED, TO_BE_DELIVERED, CREATED_DATE) " +
                               "SELECT ID, CUSTOMER, ROUTE_SHEET, ROUTE, FRANCHISE, DELIVERY_BOY, DATE, 1, 'N', NOW() " +
                               "FROM route_sheet_details WHERE ID = ?";
            try (PreparedStatement ps = con.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS)) {
                ps.setLong(1, routeSheetDetailId);
                int rows = ps.executeUpdate();
                if (rows > 0) {
                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        if (keys.next()) {
                            System.out.println("✓ Sale distribution row inserted. ID: " + keys.getLong(1) + " for route sheet detail: " + routeSheetDetailId);
                        }
                    }
                    return routeSheetDetailId;
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("CRITICAL: Failed to record sale for route sheet detail " + routeSheetDetailId +
                                     ": " + e.getMessage(), e);
        }
        return null;
    }

    /**
     * Records sales for a batch of products against a route sheet detail.
     * The sale record is delivery-level (per route sheet detail), so a single
     * idempotent row is ensured per detail.
     */
    public void insertSalesRecordsBatch(Long routeSheetDetailId, List<Map<String, Object>> products) throws Exception {
        if (routeSheetDetailId == null || products == null || products.isEmpty()) {
            throw new IllegalArgumentException("Invalid parameters for batch sales insertion");
        }
        System.out.println("Recording sale distribution for route sheet detail: " + routeSheetDetailId + " (" + products.size() + " product(s))");
        ensureSaleDistributionRow(routeSheetDetailId);
    }

    /**
     * Verifies that sales were marked correctly from sale_distribution_detail table
     */
    public Map<String, Object> verifySaleDistribution(Long routeSheetId) throws Exception {
        Map<String, Object> verification = new HashMap<>();
        
        try (Connection con = getConnection()) {
            String query = "SELECT COUNT(*) as count FROM sale_distribution_detail " +
                          "WHERE ROUTE_SHEET_DETAILS = ? AND DATE >= CURDATE()";
            PreparedStatement ps = con.prepareStatement(query);
            ps.setLong(1, routeSheetId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                verification.put("saleMarked", rs.getInt("count") > 0);
                System.out.println("✓ Sale distribution verified. Records: " + rs.getInt("count"));
            }
        } catch (SQLException e) {
            System.out.println("⚠ Sale distribution verification failed: " + e.getMessage());
            verification.put("verificationFailed", true);
        }
        
        return verification;
    }

    /**

    /**
     * Search customer by primary contact number (mobile number)
     * Returns list of matching customers with their details
     */
    public List<Map<String, Object>> searchCustomerByPhone(String phoneNumber) throws Exception {
        if (phoneNumber == null || phoneNumber.trim().isEmpty()) {
            throw new IllegalArgumentException("Phone number cannot be empty");
        }

        String query = "SELECT * FROM customer c WHERE c.PRIMARY_CONTACT_NUMBER = ?";
        List<Map<String, Object>> customers = new ArrayList<>();

        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(query)) {

            ps.setString(1, phoneNumber);
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                Map<String, Object> customer = new HashMap<>();
                
                // Extract all columns from result set
                ResultSetMetaData metaData = rs.getMetaData();
                int columnCount = metaData.getColumnCount();
                
                for (int i = 1; i <= columnCount; i++) {
                    String columnName = metaData.getColumnName(i);
                    customer.put(columnName, rs.getObject(i));
                }
                
                customers.add(customer);
            }

            if (!customers.isEmpty()) {
                System.out.println("✓ Found " + customers.size() + " customer(s) for phone: " + phoneNumber);
            } else {
                System.out.println("⚠ No customers found for phone: " + phoneNumber);
            }

        } catch (SQLException e) {
            throw new RuntimeException("Failed to search customer by phone " + phoneNumber + 
                                     ": " + e.getMessage(), e);
        }

        return customers;
    }

    /**
     * Fetch customer attributes by customer ID
     * Returns list of attributes for the given customer
     */
    public List<Map<String, Object>> getCustomerAttributes(String customerId) throws Exception {
        if (customerId == null || customerId.trim().isEmpty()) {
            throw new IllegalArgumentException("Customer ID cannot be empty");
        }

        String query = "SELECT * FROM customer_attributes ca WHERE ca.CUSTOMER = ?";
        List<Map<String, Object>> attributes = new ArrayList<>();

        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(query)) {

            ps.setString(1, customerId);
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                Map<String, Object> attribute = new HashMap<>();
                
                // Extract all columns from result set
                ResultSetMetaData metaData = rs.getMetaData();
                int columnCount = metaData.getColumnCount();
                
                for (int i = 1; i <= columnCount; i++) {
                    String columnName = metaData.getColumnName(i);
                    attribute.put(columnName, rs.getObject(i));
                }
                
                attributes.add(attribute);
            }

            if (!attributes.isEmpty()) {
                System.out.println("✓ Found " + attributes.size() + " attribute(s) for customer: " + customerId);
            } else {
                System.out.println("⚠ No attributes found for customer: " + customerId);
            }

        } catch (SQLException e) {
            throw new RuntimeException("Failed to fetch customer attributes for customer " + customerId + 
                                     ": " + e.getMessage(), e);
        }

        return attributes;
    }

    /**
     * Fetch complete customer details including address and franchise info
     * Used for customer validation
     */
    public Map<String, Object> getCustomerDetailsFromDb(String customerId) throws Exception {
        if (customerId == null || customerId.trim().isEmpty()) {
            throw new IllegalArgumentException("Customer ID cannot be empty");
        }

        String query = "SELECT * FROM customer WHERE ID = ?";
        Map<String, Object> customerDetails = new HashMap<>();

        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(query)) {

            ps.setString(1, customerId);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                ResultSetMetaData metaData = rs.getMetaData();
                int columnCount = metaData.getColumnCount();
                
                for (int i = 1; i <= columnCount; i++) {
                    String columnName = metaData.getColumnName(i);
                    customerDetails.put(columnName, rs.getObject(i));
                }
                
                System.out.println("✓ Customer details retrieved for ID: " + customerId);
            } else {
                System.out.println("⚠ No customer found with ID: " + customerId);
            }

        } catch (SQLException e) {
            throw new RuntimeException("Failed to fetch customer details for ID " + customerId + 
                                     ": " + e.getMessage(), e);
        }

        return customerDetails;
    }

    public Map<String, Object> verifyDateUpdates(String customerId, String saleDate) {
        Map<String, Object> verification = new HashMap<>();
        String dateSql = normalizeDateParam(saleDate);

        try (Connection con = getConnection()) {
            // Route sheet date check (Step-9)
            String rsCheck = "SELECT DATE FROM route_sheet_details WHERE CUSTOMER = ? ORDER BY ID DESC LIMIT 1";
            try (PreparedStatement ps = con.prepareStatement(rsCheck)) {
                ps.setString(1, customerId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        java.sql.Date dbDate = rs.getDate("DATE");
                        boolean ok = dbDate != null && dbDate.toLocalDate().toString().equals(dateSql);
                        verification.put("routeSheetUpdated", ok);
                        System.out.println("✓ Route sheet date: " + dbDate + " (expected " + dateSql + ") -> " + ok);
                    } else {
                        verification.put("routeSheetUpdated", false);
                        System.out.println("⚠ No route sheet found for date verification. Customer: " + customerId);
                    }
                }
            }

            // Order detail date check (Step-10)
            String odCheck = "SELECT ORDER_START_DATE FROM order_detail WHERE CUSTOMER = ? AND STATUS = 'Y' ORDER BY ID DESC LIMIT 1";
            try (PreparedStatement ps = con.prepareStatement(odCheck)) {
                ps.setString(1, customerId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        java.sql.Date dbDate = rs.getDate("ORDER_START_DATE");
                        boolean ok = dbDate != null && dbDate.toLocalDate().toString().equals(dateSql);
                        verification.put("orderDetailUpdated", ok);
                        System.out.println("✓ Order start date: " + dbDate + " (expected " + dateSql + ") -> " + ok);
                    } else {
                        verification.put("orderDetailUpdated", false);
                        System.out.println("⚠ No active order found for date verification. Customer: " + customerId);
                    }
                }
            }
        } catch (SQLException e) {
            System.out.println("⚠ Date update verification failed: " + e.getMessage());
            verification.put("verificationFailed", true);
        }

        return verification;
    }
}
