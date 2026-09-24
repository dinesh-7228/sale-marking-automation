package com.countrydelight.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.countrydelight.db.DatabaseUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Types;
import java.util.*;

/**
 * Complaint Clean Up.
 *
 * Re-points all complaints of one or more customers to a single cleaned customer
 * DB id (TARGET_CUSTOMER_ID = 12345) across the complaintmanagement / beejapuri /
 * rapiddelivery databases (QA & UAT).
 *
 * The customer's current DB id is resolved from the beejapuri `customer` table
 * using the primary contact number (mobile). For UAT databases the id is resolved
 * from beejapuri_UAT, for QA databases from beejapuri_QA.
 */
@Service
public class ComplaintService {

    @Autowired
    private DatabaseUtil dbUtil;

    private static final long TARGET_CUSTOMER_ID = 12345L;

    static final List<Map<String, Object>> DATABASES = databases();

    private static List<Map<String, Object>> databases() {
        List<String> names = Arrays.asList(
            "complaintmanagement_QA",
            "complaintmanagement_UAT",
            "beejapuri_QA",
            "beejapuri_UAT",
            "rapiddelivery_QA",
            "rapiddelivery_UAT");
        List<Map<String, Object>> dbs = new ArrayList<>();
        for (String name : names) {
            Map<String, Object> db = new HashMap<>();
            db.put("database", name);
            db.put("env", name.endsWith("_UAT") ? "UAT" : "QA");
            dbs.add(db);
        }
        return dbs;
    }

    public long getTargetCustomerId() {
        return TARGET_CUSTOMER_ID;
    }

    public List<Map<String, Object>> getDatabases() {
        return DATABASES;
    }

    /**
     * Runs the cleanup for the given mobiles in the given databases.
     *
     * @param mobiles   normalized mobile numbers (10 digits each)
     * @param databases target database names (subset of DATABASES)
     * @return per-database results with per-mobile status
     */
    public Map<String, Object> cleanup(List<String> mobiles, List<String> databases) throws Exception {
        if (mobiles == null || mobiles.isEmpty()) {
            throw new IllegalArgumentException("At least one mobile number is required");
        }
        if (databases == null || databases.isEmpty()) {
            throw new IllegalArgumentException("Select at least one database");
        }

        List<Map<String, Object>> results = new ArrayList<>();

        for (String db : databases) {
            boolean known = DATABASES.stream().anyMatch(m -> db.equals(m.get("database")));
            if (!known) {
                continue;
            }
            String resolverDb = db.endsWith("_UAT") ? "beejapuri_UAT" : "beejapuri_QA";

            List<Map<String, Object>> updates = new ArrayList<>();
            for (String mobile : mobiles) {
                Map<String, Object> entry = new HashMap<>();
                entry.put("mobile", mobile);
                try {
                    Long customerId = resolveCustomerIdByMobile(resolverDb, mobile);
                    if (customerId == null) {
                        entry.put("status", "CUSTOMER_NOT_FOUND");
                        entry.put("message", "No customer found for mobile in " + resolverDb);
                        updates.add(entry);
                        continue;
                    }
                    entry.put("customerId", customerId);
                    if (customerId == TARGET_CUSTOMER_ID) {
                        entry.put("status", "ALREADY_TARGET");
                        entry.put("message", "Customer is already the target id " + TARGET_CUSTOMER_ID);
                        updates.add(entry);
                        continue;
                    }
                    Map<String, Object> counts = countAndUpdateComplaints(db, customerId);
                    entry.put("complaintsFound", counts.get("complaintsFound"));
                    entry.put("complaintsUpdated", counts.get("complaintsUpdated"));
                    entry.put("status", "UPDATED");
                    entry.put("message", "Moved " + counts.get("complaintsUpdated") + " complaint(s) to " + TARGET_CUSTOMER_ID);
                } catch (Exception e) {
                    entry.put("status", "ERROR");
                    entry.put("message", e.getMessage());
                }
                updates.add(entry);
            }

            Map<String, Object> dbResult = new HashMap<>();
            dbResult.put("database", db);
            dbResult.put("env", db.endsWith("_UAT") ? "UAT" : "QA");
            dbResult.put("updates", updates);
            results.add(dbResult);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("targetCustomerId", TARGET_CUSTOMER_ID);
        response.put("results", results);
        return response;
    }

    /**
     * Fetches (read-only) all complaints for the given mobiles in the given databases.
     *
     * @param mobiles   normalized mobile numbers (10 digits each)
     * @param databases target database names (subset of DATABASES)
     * @return per-database results with per-mobile complaint rows
     */
    public Map<String, Object> fetchComplaints(List<String> mobiles, List<String> databases) throws Exception {
        if (mobiles == null || mobiles.isEmpty()) {
            throw new IllegalArgumentException("At least one mobile number is required");
        }
        if (databases == null || databases.isEmpty()) {
            throw new IllegalArgumentException("Select at least one database");
        }

        List<Map<String, Object>> results = new ArrayList<>();

        for (String db : databases) {
            boolean known = DATABASES.stream().anyMatch(m -> db.equals(m.get("database")));
            if (!known) {
                continue;
            }
            String resolverDb = db.endsWith("_UAT") ? "beejapuri_UAT" : "beejapuri_QA";

            List<Map<String, Object>> entries = new ArrayList<>();
            for (String mobile : mobiles) {
                Map<String, Object> entry = new HashMap<>();
                entry.put("mobile", mobile);
                try {
                    Long customerId = resolveCustomerIdByMobile(resolverDb, mobile);
                    if (customerId == null) {
                        entry.put("status", "CUSTOMER_NOT_FOUND");
                        entry.put("message", "No customer found for mobile in " + resolverDb);
                        entries.add(entry);
                        continue;
                    }
                    entry.put("customerId", customerId);
                    List<Map<String, Object>> complaints = fetchComplaintsForCustomer(db, customerId);
                    entry.put("complaints", complaints);
                    entry.put("complaintsCount", complaints.size());
                    entry.put("status", "FETCHED");
                    entry.put("message", "Fetched " + complaints.size() + " complaint(s) for customer " + customerId);
                } catch (Exception e) {
                    entry.put("status", "ERROR");
                    entry.put("message", e.getMessage());
                }
                entries.add(entry);
            }

            Map<String, Object> dbResult = new HashMap<>();
            dbResult.put("database", db);
            dbResult.put("env", db.endsWith("_UAT") ? "UAT" : "QA");
            dbResult.put("entries", entries);
            results.add(dbResult);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("targetCustomerId", TARGET_CUSTOMER_ID);
        response.put("results", results);
        return response;
    }

    /**
     * Fetches all complaint rows referencing the customer id from the target database.
     * Throws if the complaint table is missing. Blob/geometry columns are skipped.
     */
    private List<Map<String, Object>> fetchComplaintsForCustomer(String dbName, Long customerId) throws Exception {
        List<Map<String, Object>> complaints = new ArrayList<>();
        String sql = "SELECT * FROM complaint WHERE customer = ?";
        try (Connection con = dbUtil.getConnectionForDatabase(dbName);
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, customerId);
            try (ResultSet rs = ps.executeQuery()) {
                ResultSetMetaData meta = rs.getMetaData();
                while (rs.next()) {
                    Map<String, Object> row = new HashMap<>();
                    for (int i = 1; i <= meta.getColumnCount(); i++) {
                        String column = meta.getColumnName(i);
                        int type = meta.getColumnType(i);
                        Object value = rs.getObject(i);
                        if (value instanceof byte[] && (type == Types.LONGVARBINARY || type == Types.BLOB
                                || type == Types.BINARY || type == Types.VARBINARY)) {
                            continue;
                        }
                        if (value instanceof byte[]) {
                            byte[] bytes = (byte[]) value;
                            value = bytes.length > 0 && bytes[0] != 0;
                        }
                        row.put(column.toLowerCase(), value);
                    }
                    complaints.add(row);
                }
            }
        } catch (java.sql.SQLException e) {
            if (e.getMessage() != null && e.getMessage().toLowerCase().contains("doesn't exist")) {
                throw new RuntimeException("DB " + dbName + " has no complaint table: " + e.getMessage(), e);
            }
            throw e;
        }
        System.out.println("✓ Fetched " + complaints.size() + " complaint(s) for customer " + customerId + " in " + dbName);
        return complaints;
    }

    /**
     * Resolves the customer DB id for a mobile number from a beejapuri database.
     */
    private Long resolveCustomerIdByMobile(String dbName, String mobile) throws Exception {
        String query = "SELECT ID FROM customer WHERE PRIMARY_CONTACT_NUMBER = ? ORDER BY ID DESC LIMIT 1";
        try (Connection con = dbUtil.getConnectionForDatabase(dbName);
             PreparedStatement ps = con.prepareStatement(query)) {
            ps.setString(1, mobile);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    long id = rs.getLong("ID");
                    System.out.println("✓ Resolved mobile " + mobile + " -> customer id " + id + " in " + dbName);
                    return id;
                }
            }
        }
        System.out.println("⚠ No customer found for mobile " + mobile + " in " + dbName);
        return null;
    }

    /**
     * Counts complaints referencing the old customer id in the target database,
     * then moves them to TARGET_CUSTOMER_ID. Throws if the complaint table is missing.
     */
    private Map<String, Object> countAndUpdateComplaints(String dbName, Long oldCustomerId) throws Exception {
        Map<String, Object> result = new HashMap<>();
        long countBefore;
        long updated = 0;

        try (Connection con = dbUtil.getConnectionForDatabase(dbName)) {
            String countSql = "SELECT COUNT(*) FROM complaint WHERE customer = ?";
            try (PreparedStatement ps = con.prepareStatement(countSql)) {
                ps.setLong(1, oldCustomerId);
                try (ResultSet rs = ps.executeQuery()) {
                    countBefore = rs.next() ? rs.getLong(1) : 0;
                }
            }

            if (countBefore > 0) {
                String updateSql = "UPDATE complaint SET customer = ? WHERE customer = ?";
                try (PreparedStatement ps = con.prepareStatement(updateSql)) {
                    ps.setLong(1, TARGET_CUSTOMER_ID);
                    ps.setLong(2, oldCustomerId);
                    updated = ps.executeUpdate();
                }
            }
            System.out.println("✓ " + dbName + ": " + countBefore + " complaint(s) found, " + updated + " moved to " + TARGET_CUSTOMER_ID);
        } catch (java.sql.SQLException e) {
            if (e.getMessage() != null && e.getMessage().toLowerCase().contains("doesn't exist")) {
                throw new RuntimeException("DB " + dbName + " has no complaint table: " + e.getMessage(), e);
            }
            throw e;
        }

        result.put("complaintsFound", countBefore);
        result.put("complaintsUpdated", updated);
        return result;
    }
}