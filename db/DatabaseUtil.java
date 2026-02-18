@Component
public class DatabaseUtil {

    private Connection getConnection() throws Exception {
        return DriverManager.getConnection(
                "jdbc:mysql://DB_HOST:3306/beejapuri_QA",
                "username",
                "password");
    }

    public void updateRouteSheetDate(String customerId) {
        String query = "UPDATE route_sheet_details SET delivery_date = CURDATE() WHERE CUSTOMER = ?";

        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(query)) {

            ps.setString(1, customerId);
            ps.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void updateOrderDetailDate(String customerId) {
        String query = "UPDATE order_detail SET delivery_date = CURDATE() WHERE CUSTOMER = ? AND STATUS='Y'";

        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(query)) {

            ps.setString(1, customerId);
            ps.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
