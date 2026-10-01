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
        return getConnectionForDatabase(getDbName());
    }

    /**
     * Opens a JDBC connection to an arbitrary database on the shared RDS host.
     * Used by features that need to touch multiple schemas (e.g. complaint cleanup
     * across complaintmanagement_* / beejapuri_* / rapiddelivery_* databases).
     */
    public Connection getConnectionForDatabase(String dbName) throws SQLException {
        // Rapiddelivery databases live on a separate RDS host with dedicated credentials.
        boolean rapidDb = dbName != null && dbName.toLowerCase().startsWith("rapiddelivery");

        String dbHost;
        String dbPort;
        String dbUser;
        String dbPassword;

        if (rapidDb) {
            dbHost = getEnvValue("db.rapid.host", "non-prod-app-rapid-dbs.cxmdwl4djaa6.ap-south-1.rds.amazonaws.com");
            dbPort = getEnvValue("db.rapid.port", "3306");
            dbUser = getEnvValue("db.rapid.user", "dinesh");
            dbPassword = getEnvValue("db.rapid.password", "3GGscfBMBH3LP4v");
        } else {
            dbHost = getEnvValue("db.host", "non-prod-apps-dbs.cxmdwl4djaa6.ap-south-1.rds.amazonaws.com");
            dbPort = getEnvValue("db.port", "3306");
            dbUser = getEnvValue("db.user", "dinesh");
            dbPassword = getEnvValue("db.password", "pjq4gry4ir6QSGh");
        }

        String url = "jdbc:mysql://" + dbHost + ":" + dbPort + "/" + dbName;
        System.out.println("🔌 Database Connection URL: " + url);

        return DriverManager.getConnection(url, dbUser, dbPassword);
    }

    /**
     * Selects the rapiddelivery database schema based on the active environment
     * used by the Rapid Sale Marking flow:
     * QA -> rapiddelivery_QA, UAT -> rapiddelivery_UAT.
     */
    public String getRapidDeliveryDbName() {
        String envName = envConfig.getEnv();
        String dbName = envName != null && envName.equalsIgnoreCase("UAT") ? "rapiddelivery_UAT" : "rapiddelivery_QA";
        System.out.println("✓ Using rapiddelivery database for " + (envName == null ? "QA" : envName.toUpperCase()) + ": " + dbName);
        return dbName;
    }

    /**
     * Fetches all addresses of a customer from the rapiddelivery database
     * (rapiddelivery_QA / rapiddelivery_UAT) ordered by id DESC
     * so the newest address is auto-selected first.
     *
     * @param customerId the customer DB id (e.g. 9935686)
     */
    public List<Map<String, Object>> getAddressesByCustomer(String customerId) throws Exception {
        String dbName = getRapidDeliveryDbName();
        String query = "SELECT * FROM address a WHERE a.CUSTOMER = ? ORDER BY a.id DESC";
        List<Map<String, Object>> addresses = new ArrayList<>();

        try (Connection con = getConnectionForDatabase(dbName);
             PreparedStatement ps = con.prepareStatement(query)) {
            ps.setString(1, customerId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    addresses.add(mapRapidRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("CRITICAL: Failed to fetch addresses for customer " + customerId + " from " + dbName + ": " + e.getMessage(), e);
        }
        System.out.println("✓ Fetched " + addresses.size() + " address(es) for customer " + customerId + " from " + dbName);
        return addresses;
    }

    /**
     * Fetches a single address row by its id from the rapiddelivery database.
     *
     * @param addressId the address row id
     * @return the address row map (lowercase keys) or null if not found
     */
    public Map<String, Object> getAddressById(Integer addressId) throws Exception {
        String dbName = getRapidDeliveryDbName();
        String query = "SELECT * FROM address a WHERE a.ID = ?";
        try (Connection con = getConnectionForDatabase(dbName);
             PreparedStatement ps = con.prepareStatement(query)) {
            ps.setInt(1, addressId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRapidRow(rs);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("CRITICAL: Failed to fetch address " + addressId + " from " + dbName + ": " + e.getMessage(), e);
        }
        System.out.println("⚠ No address found with id " + addressId);
        return null;
    }

    /**
     * Updates the FRANCHISE of a specific address in the rapiddelivery
     * database. Used to normalize the selected (newest) address's franchise to
     * the target franchise before placing the rapid order.
     *
     * @param addressId  the address row id
     * @param franchise  the new franchise id to set
     * @return true when a row was updated
     */
    public boolean updateAddressFranchise(Integer addressId, Integer franchise) throws Exception {
        String dbName = getRapidDeliveryDbName();
        String query = "UPDATE address a SET a.FRANCHISE = ? WHERE a.ID = ?";
        try (Connection con = getConnectionForDatabase(dbName);
             PreparedStatement ps = con.prepareStatement(query)) {
            ps.setInt(1, franchise);
            ps.setInt(2, addressId);
            int updated = ps.executeUpdate();
            System.out.println("✓ Updated address " + addressId + " franchise -> " + franchise + " (" + updated + " row(s)) in " + dbName);
            return updated > 0;
        } catch (SQLException e) {
            throw new RuntimeException("CRITICAL: Failed to update franchise for address " + addressId + ": " + e.getMessage(), e);
        }
    }

    /**
     * Fetches the franchise products mapping (product_franchise_detail) for a
     * franchise from the rapiddelivery database, active rows only, id DESC.
     *
     * @param franchiseId franchise id resolved from the customer's latest address
     */
    public List<Map<String, Object>> getProductFranchiseDetails(Integer franchiseId) throws Exception {
        String dbName = getRapidDeliveryDbName();
        String query = "SELECT * FROM product_franchise_detail pfd WHERE pfd.FRANCHISE = ? ORDER BY pfd.id DESC";
        List<Map<String, Object>> details = new ArrayList<>();

        try (Connection con = getConnectionForDatabase(dbName);
             PreparedStatement ps = con.prepareStatement(query)) {
            ps.setInt(1, franchiseId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    details.add(mapRapidRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("CRITICAL: Failed to fetch product_franchise_detail for franchise " + franchiseId + " from " + dbName + ": " + e.getMessage(), e);
        }
        System.out.println("✓ Fetched " + details.size() + " product_franchise_detail row(s) for franchise " + franchiseId + " from " + dbName);
        return details;
    }

    /**
     * Fetches full product records for the given product ids from the
     * rapiddelivery `product` table (SELECT p.* FROM product WHERE ID IN (...)).
     *
     * @param productIds product ids collected from product_franchise_detail
     */
    public List<Map<String, Object>> getProductsByIds(List<Integer> productIds) throws Exception {
        String dbName = getRapidDeliveryDbName();
        List<Map<String, Object>> products = new ArrayList<>();
        if (productIds == null || productIds.isEmpty()) {
            return products;
        }

        StringBuilder placeholders = new StringBuilder();
        for (int i = 0; i < productIds.size(); i++) {
            if (i > 0) placeholders.append(",");
            placeholders.append("?");
        }
        String query = "SELECT p.* FROM product p WHERE p.ID IN (" + placeholders + ")";

        try (Connection con = getConnectionForDatabase(dbName);
             PreparedStatement ps = con.prepareStatement(query)) {
            for (int i = 0; i < productIds.size(); i++) {
                ps.setInt(i + 1, productIds.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    products.add(mapRapidRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("CRITICAL: Failed to fetch products by ids from " + dbName + ": " + e.getMessage(), e);
        }
        System.out.println("✓ Fetched " + products.size() + " product(s) by id from " + dbName);
        return products;
    }

    /**
     * Fetches the latest refresh token for the given customer DB id.
     * The refresh token lives in the auth_token table (current mechanism);
     * customer_token is kept as a fallback for legacy rows.
     *
     * @param customerId the customer DB id (e.g. 9938341)
     * @return the TOKEN string (refresh token), or null if not found
     */
    public String getCustomerToken(String customerId) throws Exception {
        String authToken = getCustomerAuthToken(customerId);
        if (authToken != null && !authToken.trim().isEmpty()) {
            return authToken;
        }
        // Fallback to legacy customer_token rows
        String query = "SELECT * FROM customer_token ct WHERE ct.CUSTOMER = ? ORDER BY ct.id DESC LIMIT 1";
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(query)) {
            ps.setString(1, customerId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String token = rs.getString("TOKEN");
                    System.out.println("⚠ Using legacy customer_token row for customer " + customerId);
                    return token;
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("CRITICAL: Failed to fetch customer token for " + customerId + ": " + e.getMessage(), e);
        }
        System.out.println("⚠ No auth_token found for customer " + customerId);
        return null;
    }

    /**
     * Fetches the latest refresh token from the auth_token table for a customer.
     *
     * @param customerId the customer DB id
     * @return the AUTH_TOKEN string, or null if not found
     */
    private String getCustomerAuthToken(String customerId) throws Exception {
        String query = "SELECT * FROM auth_token at WHERE at.CUSTOMER = ? ORDER BY at.id DESC LIMIT 1";
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(query)) {
            ps.setString(1, customerId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String token = rs.getString("AUTH_TOKEN");
                    System.out.println("✓ Latest auth_token found for customer " + customerId);
                    return token;
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("CRITICAL: Failed to fetch auth_token for " + customerId + ": " + e.getMessage(), e);
        }
        return null;
    }

    /**
     * Fetches the latest already-placed order for a customer from the
     * rapiddelivery database. Used by the "Only Sale Mark" scenario where the
     * order was already placed (by the customer/app) and only the sale marking
     * automation needs to run.
     *
     * @param customerId the customer DB id (e.g. 9935686)
     * @return the latest order row map (lowercase keys) or null if not found
     */
    public Map<String, Object> getLatestOrderByCustomer(String customerId) throws Exception {
        String dbName = getRapidDeliveryDbName();
        String query = "SELECT * FROM `order` o WHERE o.CUSTOMER = ? ORDER BY o.id DESC LIMIT 1";
        try (Connection con = getConnectionForDatabase(dbName);
             PreparedStatement ps = con.prepareStatement(query)) {
            ps.setString(1, customerId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Map<String, Object> row = mapRapidRow(rs);
                    System.out.println("✓ Latest rapid order found for customer " + customerId + " -> id " + row.get("id") + " (" + row.get("order_number") + ")");
                    return row;
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("CRITICAL: Failed to fetch latest order for customer " + customerId + ": " + e.getMessage(), e);
        }
        System.out.println("⚠ No rapid order found for customer " + customerId);
        return null;
    }

    /**
     * Fetches the product_franchise_detail row for a given product + franchise
     * from the rapiddelivery database. Used to build the rapid order payload
     * (category_id, mrp, product_franchise_detail_id, selling_price, ...).
     *
     * @param productId   the product id
     * @param franchiseId the franchise id resolved from the customer's address
     * @return the pfd row map (lowercase keys) or null if not found
     */
    public Map<String, Object> getProductFranchiseDetailByProductAndFranchise(Integer productId, Integer franchiseId) throws Exception {
        String dbName = getRapidDeliveryDbName();
        String query = "SELECT * FROM product_franchise_detail pfd WHERE pfd.PRODUCT = ? AND pfd.FRANCHISE = ? ORDER BY pfd.id DESC LIMIT 1";
        try (Connection con = getConnectionForDatabase(dbName);
             PreparedStatement ps = con.prepareStatement(query)) {
            ps.setInt(1, productId);
            ps.setInt(2, franchiseId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Map<String, Object> row = mapRapidRow(rs);
                    System.out.println("✓ pfd row found: product " + productId + ", franchise " + franchiseId + " -> id " + row.get("id"));
                    return row;
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("CRITICAL: Failed to fetch pfd for product " + productId + " franchise " + franchiseId + ": " + e.getMessage(), e);
        }
        System.out.println("⚠ No product_franchise_detail found for product " + productId + " franchise " + franchiseId);
        return null;
    }

    /**
     * Converts a rapiddelivery ResultSet row into a map with lowercase keys,
     * converting BIT(1) columns to booleans and skipping binary/geometry
     * columns (e.g. the address LOCATION point) to keep JSON responses clean.
     */
    private Map<String, Object> mapRapidRow(ResultSet rs) throws SQLException {
        Map<String, Object> row = new HashMap<>();
        ResultSetMetaData meta = rs.getMetaData();
        for (int i = 1; i <= meta.getColumnCount(); i++) {
            String column = meta.getColumnName(i);
            int type = meta.getColumnType(i);
            Object value = rs.getObject(i);

            if (value instanceof byte[] && (type == Types.LONGVARBINARY || type == Types.BLOB
                    || type == Types.BINARY || type == Types.VARBINARY)) {
                // Skip blob/geometry columns (e.g. LOCATION point) for clean JSON
                continue;
            }
            if (value instanceof byte[]) {
                byte[] bytes = (byte[]) value;
                value = bytes.length > 0 && bytes[0] != 0;
            }
            row.put(column.toLowerCase(), value);
        }
        return row;
    }

    /**
     * Selects the database schema based on the active environment:
     * QA -> beejapuri_QA, UAT -> beejapuri_UAT.
     */
    private String getDbName() {
        return getActiveDbName();
    }

    /**
     * Returns the active beejapuri schema name based on the environment:
     * QA -> beejapuri_QA, UAT -> beejapuri_UAT.
     */
    public String getActiveDbName() {
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
     * Read-only lookup of the customer's most recent {@code autopay_customers} row.
     * Used to verify what the autopay.md flow actually wrote, and to source the
     * {@code WALLET_AMOUNT} threshold that API-8/9 send as {@code mandate_wallet_amount}.
     *
     * @return the row, or an empty map when the customer has no AutoPay record yet
     */
    public Map<String, Object> getAutopayCustomer(int customerId) throws Exception {
        String query = "SELECT ID, AUTOPAY_CONFIG, WALLET_AMOUNT, RECHARGE_AMOUNT, TOTAL_TRANSACTIONS, "
                + "TOTAL_RECHARGE_AMOUNT, TOTAL_CASHBACK, ACTIVE, SCREEN, SETUP_TRANSACTION_ID, CREATED_DATE "
                + "FROM autopay_customers WHERE CUSTOMER = ? ORDER BY ID DESC LIMIT 1";
        Map<String, Object> row = new HashMap<>();

        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(query)) {
            ps.setInt(1, customerId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    row.put("id", rs.getLong("ID"));
                    row.put("autopay_config", rs.getObject("AUTOPAY_CONFIG"));
                    row.put("wallet_amount", rs.getObject("WALLET_AMOUNT"));
                    row.put("recharge_amount", rs.getObject("RECHARGE_AMOUNT"));
                    row.put("total_transactions", rs.getObject("TOTAL_TRANSACTIONS"));
                    row.put("total_recharge_amount", rs.getObject("TOTAL_RECHARGE_AMOUNT"));
                    row.put("total_cashback", rs.getObject("TOTAL_CASHBACK"));
                    row.put("active", rs.getObject("ACTIVE"));
                    row.put("screen", rs.getObject("SCREEN"));
                    row.put("setup_transaction_id", rs.getObject("SETUP_TRANSACTION_ID"));
                    row.put("created_date", rs.getObject("CREATED_DATE"));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to read autopay_customers for customer " + customerId
                    + ": " + e.getMessage(), e);
        }
        return row;
    }

    /**
     * Updates a payment_requests row's PAYMENT_STATUS (and related fields) in
     * the active beejapuri database, keyed by the Juspay order/transaction id.
     * Used to record forced statuses (SUCCESS/PENDING/FAILED) when the real
     * Juspay completion step cannot be executed via the payment.md API series.
     *
     * @param transactionId  the TRANSACTION_ID (order_id) from generateTransaction
     * @param status         PAYMENT_STATUS value: SUCCESS, PENDING or FAILED
     * @param cashback       cashback to store into BENEFIT_AMOUNT ("" → skip)
     * @param customerId     customer.ID (payment_requests.CUSTOMER)
     * @param amount         recharge amount (payment_requests.AMOUNT)
     * @param paymentMethod  PAYMENT_METHOD value (e.g. "NB-DUMMY BANK")
     * @return number of rows updated
     */
    public int updatePaymentRequestStatus(String transactionId, String status, String cashback,
                                          int customerId, String amount, String paymentMethod) throws Exception {
        String db = getActiveDbName();
        String table = paymentRequestsTableFor(db);
        List<String> sets = new ArrayList<>();
        List<Object> params = new ArrayList<>();

        sets.add("PAYMENT_STATUS = ?");
        params.add(status);
        sets.add("UPDATED_DATE = NOW()");
        if (status.equalsIgnoreCase("SUCCESS")) {
            // SUCCESS also records the cashback benefit and the amount for the row.
            Double cb = parseCashback(cashback);
            if (cb != null && cb > 0) {
                sets.add("BENEFIT_AMOUNT = ?");
                params.add(cb);
            }
            sets.add("AMOUNT = ?");
            params.add(amount);
            sets.add("PAYMENT_METHOD = ?");
            params.add(paymentMethod);
            sets.add("PAYMENT_GATEWAY = ?");
            params.add("JUSPAY");
        }

        String query = "UPDATE " + table + " SET " + String.join(", ", sets)
                + " WHERE TRANSACTION_ID = ?";
        params.add(transactionId);

        try (Connection con = getConnectionForDatabase(db);
             PreparedStatement ps = con.prepareStatement(query)) {
            for (int i = 0; i < params.size(); i++) {
                Object p = params.get(i);
                if (p instanceof Number) {
                    ps.setBigDecimal(i + 1, new java.math.BigDecimal(p.toString()));
                } else {
                    ps.setString(i + 1, p.toString());
                }
            }
            int updated = ps.executeUpdate();
            System.out.println("✓ payment_requests updated [" + db + "]: " + updated
                    + " row(s), transaction_id=" + transactionId + ", status=" + status);
            return updated;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update payment_requests in " + db + ": " + e.getMessage(), e);
        }
    }

    /**
     * Credits a successful wallet recharge into beejapuri (active DB), mirroring
     * what the CMS performs when a payment completes (CHARGED):
     *   - payments         : a CHARGED row for the transaction (payment_gateway=DUMMY,
     *                         juspay_payment_method=95, pg_transaction_id=cds-{txn}-1)
     *   - wallet_balance   : BALANCE += amount + cashback, LAST_WALLET_TYPE='Funds Addition'
     *   - wallet_transactions : a 'Funds Addition' row per credit (amount, then cashback),
     *                           main row mapped to the payments row via PAYMENTS
     *   - wallet_recharge  : a new recharge row (PAYMENT_TYPE=9 Net Banking, REMARKS='ONLINE')
     *
     * @return map with old_balance, credited, cashback, new_balance, payment_id
     */
    public Map<String, Object> applyWalletCredit(int customerId, String amount, String cashback,
                                                 String offerId, String paymentMethod, String transactionId)
            throws Exception {
        String db = getActiveDbName();
        double amt = parseNumer("amount", amount, 0.0);
        double cb = parseCashbackToNumber(cashback);
        double oldBalance = 0;
        double oldCdCredits = 0;

        try (Connection con = getConnectionForDatabase(db)) {
            con.setAutoCommit(false);
            try {
                try (PreparedStatement ps = con.prepareStatement(
                        "SELECT BALANCE, CD_CREDITS_BALANCE FROM wallet_balance WHERE CUSTOMER = ?")) {
                    ps.setInt(1, customerId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            oldBalance = rs.getBigDecimal("BALANCE") == null ? 0 : rs.getBigDecimal("BALANCE").doubleValue();
                            oldCdCredits = rs.getBigDecimal("CD_CREDITS_BALANCE") == null ? 0 : rs.getBigDecimal("CD_CREDITS_BALANCE").doubleValue();
                        }
                    }
                }

                double newBalance = oldBalance + amt + cb;
                double newCdCredits = oldCdCredits + cb;

                long paymentId = insertPaymentDetails(con, customerId, amt, transactionId, nowTimestamp());
                java.util.Date now = new java.util.Date();

                // 1) wallet_recharge
                int paymentTypeId = resolvePaymentTypeId(paymentMethod);
                try (PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO wallet_recharge (CUSTOMER, DATE, RECHARGE_AMOUNT, PAYMENT_TYPE, REMARKS, CREATED_DATE) "
                        + "VALUES (?, ?, ?, ?, 'ONLINE', ?)")) {
                    ps.setInt(1, customerId);
                    ps.setTimestamp(2, new java.sql.Timestamp(now.getTime()));
                    ps.setInt(3, (int) Math.round(amt));
                    ps.setInt(4, paymentTypeId);
                    ps.setTimestamp(5, new java.sql.Timestamp(now.getTime()));
                    ps.executeUpdate();
                }

                // 2) wallet_transactions — main recharge credit (mapped to payments row)
                insertWalletTransaction(con, customerId, amt, oldBalance + amt, offerId, now, paymentId);

                // 3) wallet_transactions — cashback credit (if any, not payment-linked)
                if (cb > 0) {
                    insertWalletTransaction(con, customerId, cb, oldBalance + amt + cb, offerId, now, 0);
                }

                // 4) wallet_balance (upsert)
                try (PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO wallet_balance (CUSTOMER, BALANCE, LAST_WALLET_TYPE, LAST_TXN_DATE, CD_CREDITS_BALANCE) "
                        + "VALUES (?, ?, 'Funds Addition', ?, ?) "
                        + "ON DUPLICATE KEY UPDATE BALANCE = VALUES(BALANCE), "
                        + "LAST_WALLET_TYPE = VALUES(LAST_WALLET_TYPE), LAST_TXN_DATE = VALUES(LAST_TXN_DATE), "
                        + "CD_CREDITS_BALANCE = VALUES(CD_CREDITS_BALANCE)")) {
                    ps.setInt(1, customerId);
                    ps.setBigDecimal(2, new java.math.BigDecimal(String.valueOf(newBalance)));
                    ps.setTimestamp(3, new java.sql.Timestamp(now.getTime()));
                    ps.setBigDecimal(4, new java.math.BigDecimal(String.valueOf(newCdCredits)));
                    ps.executeUpdate();
                }

                con.commit();

                Map<String, Object> w = new HashMap<>();
                w.put("old_wallet_balance", oldBalance);
                w.put("credited", amt);
                w.put("cashback_credited", cb);
                w.put("new_wallet_balance", newBalance);
                w.put("cd_credits_balance", newCdCredits);
                w.put("payment_id", paymentId);
                w.put("transaction_id", transactionId);
                System.out.println("💰 Wallet credited [" + db + "]: customer=" + customerId
                        + " amount=" + amt + " cashback=" + cb + " old=" + oldBalance + " new=" + newBalance
                        + " payments_id=" + paymentId);
                return w;
            } catch (SQLException e) {
                con.rollback();
                throw new RuntimeException("Failed to credit wallet in " + db + ": " + e.getMessage(), e);
            }
        }
    }

    private java.sql.Timestamp nowTimestamp() {
        return new java.sql.Timestamp(new java.util.Date().getTime());
    }

    /**
     * Inserts the CHARGED payment row (mirrors the real Juspay completion flow)
     * and returns the generated payments.ID used to link wallet_transactions.PAYMENTS.
     */
    private long insertPaymentDetails(Connection con, int customerId, double amount,
                                      String transactionId, java.sql.Timestamp now) throws SQLException {
        if (transactionId == null || transactionId.trim().isEmpty()) {
            throw new SQLException("Cannot create payments row without a transaction/order id");
        }
        String pgTxnId = "cds-" + transactionId + "-1";
        try (PreparedStatement ps = con.prepareStatement(
                "INSERT INTO payments (CUSTOMER, TRANSACTION_ID, TRANSACTION_STATUS, AMOUNT, PG_TRANSACTION_ID, "
                + "PAYMENT_TYPE, PAYMENT_GATEWAY, JUSPAY_PAYMENT_METHOD, CREATED_DATE, UPDATED_DATE, APP_VERSION) "
                + "VALUES (?, ?, 'CHARGED', ?, ?, 9, 'DUMMY', 95, ?, ?, '99.99.99')",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, customerId);
            ps.setString(2, transactionId);
            ps.setBigDecimal(3, new java.math.BigDecimal(String.valueOf(amount)));
            ps.setString(4, pgTxnId);
            ps.setTimestamp(5, now);
            ps.setTimestamp(6, now);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
            throw new SQLException("No generated payments.ID for transaction " + transactionId);
        }
    }

    private void insertWalletTransaction(Connection con, int customerId, double amount,
                                         double walletBalance, String offerId, java.util.Date now, long paymentId)
            throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "INSERT INTO wallet_transactions (DATE, CUSTOMER, TYPE, AMOUNT, CREDIT_BALANCE, WALLET_BALANCE, "
                + "CREATED_DATE, OFFER_ID, PAYMENTS) VALUES (?, ?, 'Funds Addition', ?, 0, ?, ?, ?, ?)")) {
            ps.setTimestamp(1, new java.sql.Timestamp(now.getTime()));
            ps.setInt(2, customerId);
            ps.setBigDecimal(3, new java.math.BigDecimal(String.valueOf(amount)));
            ps.setBigDecimal(4, new java.math.BigDecimal(String.valueOf(walletBalance)));
            ps.setTimestamp(5, new java.sql.Timestamp(now.getTime()));
            if (offerId == null || offerId.trim().isEmpty() || "0".equals(offerId.trim())) {
                ps.setNull(6, Types.BIGINT);
            } else {
                ps.setLong(6, Long.parseLong(offerId.trim()));
            }
            if (paymentId > 0) {
                ps.setLong(7, paymentId);
            } else {
                ps.setNull(7, Types.INTEGER);
            }
            ps.executeUpdate();
        }
    }

    /**
     * Maps the payment method to the payment_type.id used by wallet_recharge.
     * NB-DUMMY BANK → 9 (Net Banking); everything else falls back to 9.
     */
    private int resolvePaymentTypeId(String paymentMethod) {
        if (paymentMethod == null || paymentMethod.trim().isEmpty()) {
            return 9;
        }
        String m = paymentMethod.trim().toUpperCase();
        if (m.contains("UPI")) return 17;
        if (m.contains("CARD")) return 19;
        if (m.contains("AUTOPAY")) return 21;
        return 9; // Net Banking
    }

    private double parseNumer(String field, String raw, double dflt) {
        if (raw == null || raw.trim().isEmpty()) {
            return dflt;
        }
        try {
            return Double.parseDouble(raw.trim().replace(",", ""));
        } catch (NumberFormatException e) {
            return dflt;
        }
    }

    private double parseCashbackToNumber(String cashback) {
        Double v = parseCashback(cashback);
        return v == null ? 0.0 : v;
    }

    private String paymentRequestsTableFor(String dbName) {
        return "payment_requests";
    }

    private Double parseCashback(String cashback) {
        if (cashback == null || cashback.trim().isEmpty()) {
            return null;
        }
        try {
            return Double.parseDouble(cashback.trim().replace(",", ""));
        } catch (NumberFormatException e) {
            return null;
        }
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
     * Checks if a route sheet exists for the customer for tomorrow's date.
     * Returns the route_sheet_details ID if found, null otherwise.
     */
    public Long getRouteSheetIdForTomorrow(String customerId) throws Exception {
        return getRouteSheetIdForDate(customerId, LocalDate.now().plusDays(1).toString());
    }

    /**
     * Checks if a route sheet exists for the customer for the given sale date.
     * Returns the route_sheet_details ID if found, null otherwise.
     *
     * @param customerId customer id from step-2 customer search
     * @param saleDate   sale marking date (dd-MM-yyyy or yyyy-MM-dd)
     */
    public Long getRouteSheetIdForDate(String customerId, String saleDate) throws Exception {
        String dateSql = normalizeDateParam(saleDate);
        String query = "SELECT ID FROM route_sheet_details " +
                       "WHERE CUSTOMER = ? AND DATE = ? ORDER BY ID DESC LIMIT 1";
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(query)) {
            ps.setString(1, customerId);
            ps.setString(2, dateSql);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Long id = rs.getLong("ID");
                    System.out.println("✓ Route sheet found for customer " + customerId + " for date (" + dateSql + "). ID: " + id);
                    return id;
                }
            }
        } catch (SQLException e) {
            System.out.println("⚠ Route sheet date check failed: " + e.getMessage());
        }
        System.out.println("⚠ No route sheet found for customer " + customerId + " for date (" + dateSql + ")");
        return null;
    }

    /**
     * Returns the latest route_sheet_details ID for the customer, or null if none exists.
     */
    public Long getLatestRouteSheetId(String customerId) throws Exception {
        String query = "SELECT ID FROM route_sheet_details " +
                       "WHERE CUSTOMER = ? ORDER BY ID DESC LIMIT 1";
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(query)) {
            ps.setString(1, customerId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong("ID");
                }
            }
        } catch (SQLException e) {
            System.out.println("⚠ Latest route sheet lookup failed: " + e.getMessage());
        }
        return null;
    }

    /**
     * Updates a specific route sheet's date to the sale marking date
     * and sets delivery_boy = 26747.
     *
     * @param routeSheetId route_sheet_details ID to update
     * @param saleDate     sale marking date (dd-MM-yyyy or yyyy-MM-dd). Defaults to today if null.
     */
    public void updateRouteSheetDateById(Long routeSheetId, String saleDate) throws Exception {
        if (routeSheetId == null) {
            throw new IllegalArgumentException("Route sheet ID is required");
        }
        String formattedDate = normalizeDateParam(saleDate);
        String query = "UPDATE route_sheet_details SET `DATE` = ?, `DELIVERY_BOY` = 26747 WHERE ID = ?";
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(query)) {
            ps.setDate(1, java.sql.Date.valueOf(formattedDate));
            ps.setLong(2, routeSheetId);
            int rowsUpdated = ps.executeUpdate();
            if (rowsUpdated > 0) {
                System.out.println("✓ Route sheet " + routeSheetId + " date updated to " + formattedDate + " and delivery_boy=26747");
            } else {
                System.out.println("⚠ No route sheet found with ID " + routeSheetId + " for date update");
            }
        } catch (SQLException e) {
            throw new RuntimeException("CRITICAL: Failed to update route sheet " + routeSheetId + " date: " + e.getMessage(), e);
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
     * Fetches active products available in a given city.
     * Mirrors the CMS /admin/v1/products/fetchProducts/V2 response structure.
     */
    public List<Map<String, Object>> fetchProductsFromDb(String customerId, Integer cityId) throws Exception {
        List<Map<String, Object>> products = new ArrayList<>();
        String query = "SELECT * FROM product p " +
                       "WHERE FIND_IN_SET(?, p.CITIES) > 0 " +
                       "AND p.customer_visible = 1 " +
                       "ORDER BY p.id DESC";

        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(query)) {
            ps.setInt(1, cityId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> product = new HashMap<>();
                    product.put("id", rs.getLong("ID"));
                    product.put("name", rs.getString("NAME"));
                    product.put("price", rs.getObject("price"));
                    product.put("image", rs.getString("IMAGE"));
                    product.put("division", rs.getString("DIVISION"));
                    product.put("maxOrder", rs.getObject("MAX_ORDER"));
                    product.put("unitOfMeasurement", rs.getString("UNIT_OF_MEASUREMENT"));
                    product.put("sku", rs.getString("SKU"));
                    product.put("barcode", rs.getString("BARCODE"));
                    products.add(product);
                }
            }
        } catch (SQLException e) {
            System.out.println("⚠ Product fetch from DB failed: " + e.getMessage());
        }
        return products;
    }

    /**
     * Resolves the numeric city id for a customer using the database.
     * Does not depend on the CMS API (which may be offline).
     *
     * Resolution paths (in order):
     *  1. customer_attributes.FRANCHISE -> franchise.FRANCHISE_CITY
     *  2. customer.DELIVERY_ADDRESS (address.ID) -> address.CITY
     *
     * @param cmsCustomerId customer_id from the customer search result
     * @return the numeric city id, or null if it cannot be determined
     */
    public Integer resolveCustomerCityId(String cmsCustomerId) throws Exception {
        if (cmsCustomerId == null || cmsCustomerId.trim().isEmpty()) {
            return null;
        }
        String dbCustomerId = getDbCustomerId(cmsCustomerId);
        if (dbCustomerId == null) {
            dbCustomerId = cmsCustomerId;
        }

        // Path 1: customer_attributes.FRANCHISE -> franchise.FRANCHISE_CITY
        String q1 = "SELECT f.FRANCHISE_CITY FROM customer_attributes ca " +
                    "JOIN franchise f ON f.ID = ca.FRANCHISE " +
                    "WHERE ca.CUSTOMER = ? AND f.FRANCHISE_CITY IS NOT NULL AND f.FRANCHISE_CITY > 0 " +
                    "ORDER BY ca.ID DESC LIMIT 1";
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(q1)) {
            ps.setString(1, dbCustomerId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Integer city = rs.getObject(1) != null ? rs.getInt(1) : null;
                    if (city != null && city > 0) {
                        System.out.println("✓ City " + city + " resolved via franchise for customer " + cmsCustomerId);
                        return city;
                    }
                }
            }
        } catch (SQLException e) {
            System.out.println("⚠ Franchise city resolution failed for " + cmsCustomerId + ": " + e.getMessage());
        }

        // Path 2: customer.DELIVERY_ADDRESS (address.ID) -> address.CITY
        String q2 = "SELECT a.CITY FROM customer c JOIN address a ON a.ID = c.DELIVERY_ADDRESS " +
                    "WHERE c.ID = ? AND a.CITY IS NOT NULL AND a.CITY > 0 LIMIT 1";
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(q2)) {
            ps.setString(1, dbCustomerId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Integer city = rs.getObject(1) != null ? rs.getInt(1) : null;
                    if (city != null && city > 0) {
                        System.out.println("✓ City " + city + " resolved via delivery address for customer " + cmsCustomerId);
                        return city;
                    }
                }
            }
        } catch (SQLException e) {
            System.out.println("⚠ Address city resolution failed for " + cmsCustomerId + ": " + e.getMessage());
        }

        System.out.println("⚠ Could not resolve a city id for customer " + cmsCustomerId + " from the database");
        return null;
    }

    /**
     * Fetches active non-delivery reasons from the `issue` table.
     * Used to populate the ND Reason dropdown in the sale-type selection UI.
     * Ordered by id DESC as per the PRD.
     * @return list of issue maps with id, reason (display text), sub_reason
     */
    public List<Map<String, Object>> getNonDeliveryReasons() throws Exception {
        String query = "SELECT ID, REASON, SUB_REASON FROM issue WHERE ACTIVE = true ORDER BY ID DESC";
        List<Map<String, Object>> reasons = new ArrayList<>();

        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(query)) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> reason = new HashMap<>();
                    reason.put("id", rs.getInt("ID"));
                    reason.put("reason", rs.getString("REASON"));
                    reason.put("sub_reason", rs.getString("SUB_REASON"));
                    reasons.add(reason);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("CRITICAL: Failed to fetch ND reasons: " + e.getMessage(), e);
        }
        System.out.println("✓ Fetched " + reasons.size() + " active ND reason(s) from issue table");
        return reasons;
    }

    /**
     * Verifies an issue (ND reason) still exists and is active.
     * Used to reject stale/invalid ND reason selections.
     *
     * @param issueId the selected issue id
     * @return true if the issue exists and is active
     */
    public boolean isActiveIssue(Integer issueId) throws Exception {
        if (issueId == null) {
            return false;
        }
        String query = "SELECT COUNT(*) FROM issue WHERE ID = ? AND ACTIVE = true";
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(query)) {
            ps.setInt(1, issueId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next() && rs.getInt(1) > 0) {
                    System.out.println("✓ Issue " + issueId + " is active");
                    return true;
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("CRITICAL: Failed to validate issue " + issueId + ": " + e.getMessage(), e);
        }
        System.out.println("⚠ Issue " + issueId + " is not active or missing");
        return false;
    }

    /**
     * Fetches the remark rows associated with the selected issue, carrying the
     * remark -> sale_distribution_detail linkage.
     *
     * Mapping: issue.id -> remark.ISSUE -> remark.SALE_DISTRIBUTION_DETAIL -> sale_distribution_detail
     *
     * @param issueId the selected ND reason (issue) id
     * @return list of remark maps (remark_id, issue_id, description, sale_distribution_detail_id)
     */
    public List<Map<String, Object>> getRemarkMapping(Integer issueId) throws Exception {
        String query = "SELECT r.ID AS remark_id, r.ISSUE AS issue_id, r.DESCRIPTION, " +
                       "r.SALE_DISTRIBUTION_DETAIL AS sale_distribution_detail_id " +
                       "FROM remark r WHERE r.ISSUE = ? ORDER BY r.ID DESC";
        List<Map<String, Object>> remarks = new ArrayList<>();

        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(query)) {
            ps.setInt(1, issueId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> remark = new HashMap<>();
                    remark.put("remark_id", rs.getLong("remark_id"));
                    remark.put("issue_id", rs.getInt("issue_id"));
                    remark.put("description", rs.getString("DESCRIPTION"));
                    remark.put("sale_distribution_detail_id", rs.getObject("sale_distribution_detail_id"));
                    remarks.add(remark);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("CRITICAL: Failed to fetch remark mapping for issue " + issueId + ": " + e.getMessage(), e);
        }
        System.out.println("✓ Fetched " + remarks.size() + " remark mapping(s) for issue " + issueId);
        return remarks;
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
