import java.sql.*;

public class SQLExecutor {
    public static void main(String[] args) throws Exception {
        String host = "non-prod-apps-dbs.cxmdwl4djaa6.ap-south-1.rds.amazonaws.com";
        String port = "3306";
        String database = "beejapuri_QA";
        String user = "dinesh";
        String password = "pjq4gry4ir6QSGh";
        
        String url = "jdbc:mysql://" + host + ":" + port + "/" + database;
        
        Class.forName("com.mysql.cj.jdbc.Driver");
        Connection conn = DriverManager.getConnection(url, user, password);
        Statement stmt = conn.createStatement();
        
        System.out.println("✓ Connected to database: " + database);
        System.out.println("Executing SQL updates...\n");
        
        try {
            // Update 1: route_sheet
            String sql1 = "UPDATE route_sheet SET DATE='2026-04-13 12:49:19', DELIVERY_BOY='1' WHERE ID=7477643";
            int rows1 = stmt.executeUpdate(sql1);
            System.out.println("✓ Update 1 (route_sheet): " + rows1 + " rows updated");
            System.out.println("  SQL: " + sql1);
            
            // Update 2: order_detail
            String sql2 = "UPDATE order_detail SET ORDER_START_DATE='2026-04-13 12:49:19', ORDER_END_DATE='2026-04-13 12:49:19' WHERE ORDER_NUMBER='1776059922007458503'";
            int rows2 = stmt.executeUpdate(sql2);
            System.out.println("\n✓ Update 2 (order_detail): " + rows2 + " rows updated");
            System.out.println("  SQL: " + sql2);
            
            // Update 3: route_sheet_details
            String sql3 = "UPDATE route_sheet_details SET DATE='2026-04-13 12:49:19', DELIVERY_BOY='1' WHERE ID=(SELECT ID FROM (SELECT ID FROM route_sheet_details WHERE CUSTOMER=9944283 ORDER BY ID DESC LIMIT 1) t)";
            int rows3 = stmt.executeUpdate(sql3);
            System.out.println("\n✓ Update 3 (route_sheet_details): " + rows3 + " rows updated");
            System.out.println("  SQL: " + sql3);
            
            System.out.println("\n" + "=".repeat(70));
            System.out.println("✅ ALL UPDATES COMPLETED SUCCESSFULLY");
            System.out.println("Total rows updated: " + (rows1 + rows2 + rows3));
            System.out.println("=".repeat(70));
            
        } catch (SQLException e) {
            System.err.println("❌ Error executing SQL: " + e.getMessage());
            e.printStackTrace();
        } finally {
            stmt.close();
            conn.close();
        }
    }
}
