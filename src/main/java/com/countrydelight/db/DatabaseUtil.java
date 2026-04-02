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
        String dbHost = getEnvValue("DB_HOST", "non-prod-apps-dbs.cxmdwl4djaa6.ap-south-1.rds.amazonaws.com");
        String dbPort = getEnvValue("DB_PORT", "3306");
        String dbName = getEnvValue("DB_NAME", "");
        String dbUser = getEnvValue("DB_USER", "dinesh");
        String dbPassword = getEnvValue("DB_PASSWORD", "pjq4gry4ir6QSGh");

        String url = "jdbc:mysql://" + dbHost + ":" + dbPort + "/" + dbName;
        
        return DriverManager.getConnection(url, dbUser, dbPassword);
    }

    private String getEnvValue(String key, String defaultValue) {
        String value = env.getProperty(key);
        return value != null && !value.isEmpty() ? value : defaultValue;
    }

    /**
     * Automatically updates route sheet date from tomorrow to today
     * Called immediately after route sheet generation
     */
    public void updateRouteSheetDate(String customerId) throws Exception {
        String query = "UPDATE route_sheet_details SET delivery_date = CURDATE() " +
                       "WHERE customer_id = ? AND delivery_date = DATE_ADD(CURDATE(), INTERVAL 1 DAY)";

        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(query)) {

            ps.setString(1, customerId);
            int rowsUpdated = ps.executeUpdate();
            
            if (rowsUpdated > 0) {
                System.out.println("✓ Route sheet date updated to TODAY for customer: " + customerId);
            } else {
                System.out.println("⚠ No route sheet found for date correction. Customer: " + customerId);
            }
        } catch (SQLException e) {
            throw new RuntimeException("CRITICAL: Failed to update route sheet date for customer " + customerId + 
                                     ": " + e.getMessage(), e);
        }
    }

    /**
     * Automatically updates order detail start date from tomorrow to today
     * Called immediately after order placement
     */
    public void updateOrderDetailDate(String customerId) throws Exception {
        String query = "UPDATE order_detail SET start_date = CURDATE() " +
                       "WHERE customer_id = ? AND STATUS = 'Y' AND start_date = DATE_ADD(CURDATE(), INTERVAL 1 DAY)";

        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(query)) {

            ps.setString(1, customerId);
            int rowsUpdated = ps.executeUpdate();
            
            if (rowsUpdated > 0) {
                System.out.println("✓ Order detail start date updated to TODAY for customer: " + customerId);
            } else {
                System.out.println("⚠ No active orders found for date correction. Customer: " + customerId);
            }
        } catch (SQLException e) {
            throw new RuntimeException("CRITICAL: Failed to update order detail date for customer " + customerId + 
                                     ": " + e.getMessage(), e);
        }
    }

    /**
     * Retrieves the latest route sheet details for a customer
     * Used to get the ID for sales marking
     */
    public Map<String, Object> getRouteSheetDetails(String customerId) throws Exception {
        String query = "SELECT id, customer_id, delivery_date FROM route_sheet_details " +
                       "WHERE customer_id = ? ORDER BY id DESC LIMIT 1";
        Map<String, Object> result = new HashMap<>();

        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(query)) {

            ps.setString(1, customerId);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                result.put("id", rs.getLong("id"));
                result.put("customer_id", rs.getString("customer_id"));
                result.put("delivery_date", rs.getDate("delivery_date"));
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
     * Gets the latest order details for a customer
     */
    public List<Map<String, Object>> getOrderDetails(String customerId) throws Exception {
        String query = "SELECT id, customer_id, product_id, quantity, status, start_date " +
                       "FROM order_detail WHERE customer_id = ? AND status = 'Y' " +
                       "ORDER BY id DESC LIMIT 10";
        List<Map<String, Object>> results = new ArrayList<>();

        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(query)) {

            ps.setString(1, customerId);
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                Map<String, Object> order = new HashMap<>();
                order.put("id", rs.getLong("id"));
                order.put("customer_id", rs.getString("customer_id"));
                order.put("product_id", rs.getInt("product_id"));
                order.put("quantity", rs.getInt("quantity"));
                order.put("status", rs.getString("status"));
                order.put("start_date", rs.getDate("start_date"));
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
     * Atomic operation - either completes fully or fails
     */
    public void insertSaleRecord(Long routeSheetDetailId, Integer productId, Integer quantity) throws Exception {
        String query = "INSERT INTO sales_mark (route_sheet_detail_id, product_id, quantity, created_date) " +
                       "VALUES (?, ?, ?, NOW())";

        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(query, Statement.RETURN_GENERATED_KEYS)) {

            ps.setLong(1, routeSheetDetailId);
            ps.setInt(2, productId);
            ps.setInt(3, quantity);
            int rowsInserted = ps.executeUpdate();
            
            if (rowsInserted > 0) {
                ResultSet generatedKeys = ps.getGeneratedKeys();
                if (generatedKeys.next()) {
                    long saleId = generatedKeys.getLong(1);
                    System.out.println("✓ Sale record inserted. Sale ID: " + saleId + 
                                     ", Product ID: " + productId + ", Quantity: " + quantity);
                }
            } else {
                throw new RuntimeException("Failed to insert sale record for product: " + productId);
            }
        } catch (SQLException e) {
            throw new RuntimeException("CRITICAL: Failed to insert sales record for product " + productId + 
                                     ": " + e.getMessage(), e);
        }
    }

    /**
     * Inserts multiple sales records in transaction
     * All succeed or all fail together
     */
    public void insertSalesRecordsBatch(Long routeSheetDetailId, List<Map<String, Object>> products) throws Exception {
        if (routeSheetDetailId == null || products == null || products.isEmpty()) {
            throw new IllegalArgumentException("Invalid parameters for batch sales insertion");
        }

        String query = "INSERT INTO sales_mark (route_sheet_detail_id, product_id, quantity, created_date) " +
                       "VALUES (?, ?, ?, NOW())";

        Connection con = null;
        try {
            con = getConnection();
            con.setAutoCommit(false);  // Start transaction
            
            PreparedStatement ps = con.prepareStatement(query);
            
            for (Map<String, Object> product : products) {
                Integer productId = ((Number) product.get("id")).intValue();
                Integer quantity = ((Number) product.get("quantity")).intValue();
                
                ps.setLong(1, routeSheetDetailId);
                ps.setInt(2, productId);
                ps.setInt(3, quantity);
                ps.addBatch();
            }
            
            int[] rowsInserted = ps.executeBatch();
            con.commit();
            
            System.out.println("✓ Batch sales records inserted. Count: " + rowsInserted.length);
            
        } catch (SQLException e) {
            if (con != null) {
                try {
                    con.rollback();
                    System.out.println("⚠ Transaction rolled back due to error");
                } catch (SQLException rollbackEx) {
                    System.out.println("⚠ Rollback failed: " + rollbackEx.getMessage());
                }
            }
            throw new RuntimeException("CRITICAL: Failed to insert batch sales records: " + e.getMessage(), e);
        } finally {
            if (con != null) {
                try {
                    con.setAutoCommit(true);
                    con.close();
                } catch (SQLException e) {
                    System.out.println("⚠ Error closing connection: " + e.getMessage());
                }
            }
        }
    }

    /**
     * Verifies that dates were updated correctly
     * Used for post-operation validation
     */
    public Map<String, Object> verifyDateUpdates(String customerId) throws Exception {
        Map<String, Object> verification = new HashMap<>();
        
        try (Connection con = getConnection()) {
            // Check route sheet date
            String routeQuery = "SELECT COUNT(*) as count FROM route_sheet_details " +
                               "WHERE customer_id = ? AND delivery_date = CURDATE()";
            PreparedStatement ps1 = con.prepareStatement(routeQuery);
            ps1.setString(1, customerId);
            ResultSet rs1 = ps1.executeQuery();
            if (rs1.next()) {
                verification.put("routeSheetUpdated", rs1.getInt("count") > 0);
            }
            
            // Check order detail date
            String orderQuery = "SELECT COUNT(*) as count FROM order_detail " +
                               "WHERE customer_id = ? AND STATUS = 'Y' AND start_date = CURDATE()";
            PreparedStatement ps2 = con.prepareStatement(orderQuery);
            ps2.setString(1, customerId);
            ResultSet rs2 = ps2.executeQuery();
            if (rs2.next()) {
                verification.put("orderDetailUpdated", rs2.getInt("count") > 0);
            }
            
        } catch (SQLException e) {
            System.out.println("⚠ Verification check failed: " + e.getMessage());
            verification.put("verificationFailed", true);
        }
        
        return verification;
    }
}
