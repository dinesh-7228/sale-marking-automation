package com.countrydelight.service;

import org.springframework.beans.factory.annotation.Autowired;
import com.countrydelight.db.DatabaseUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.*;

/**
 * Base class for customer-removal modules that re-point single rows by
 * customer DB id to the cleaned customer (TARGET_CUSTOMER_ID = 12345).
 *
 * Each concrete module targets exactly one table (e.g. autopay_customers or
 * customer_membership_group) across the beejapuri QA / UAT databases.
 */
public abstract class BaseCustomerRemovalService {

    @Autowired
    protected DatabaseUtil dbUtil;

    private static final long TARGET_CUSTOMER_ID = 12345L;

    private static final List<Map<String, Object>> DATABASES = databases();

    private static List<Map<String, Object>> databases() {
        List<Map<String, Object>> dbs = new ArrayList<>();
        dbs.add(db("beejapuri_QA", "QA"));
        dbs.add(db("beejapuri_UAT", "UAT"));
        return dbs;
    }

    private static Map<String, Object> db(String name, String env) {
        Map<String, Object> db = new HashMap<>();
        db.put("database", name);
        db.put("env", env);
        return db;
    }

    /** Table whose CUSTOMER column is re-pointed. */
    protected abstract String getTableName();

    /** Columns shown in the read-only fetch preview. */
    protected abstract String getPreviewColumns();

    public long getTargetCustomerId() {
        return TARGET_CUSTOMER_ID;
    }

    public List<Map<String, Object>> getDatabases() {
        return DATABASES;
    }

    public Map<String, Object> getTableInfo() {
        Map<String, Object> table = new HashMap<>();
        table.put("table", getTableName());
        table.put("previewColumns", getPreviewColumns());
        return table;
    }

    /**
     * Fetches (read-only) matching rows from the module table in the selected
     * databases for the given customer DB ids.
     *
     * @param customerIds customer DB ids (already normalized, non-empty)
     * @param databases   target database names (subset of DATABASES)
     * @return per-database results with rows
     */
    public Map<String, Object> fetch(List<Long> customerIds, List<String> databases) throws Exception {
        validate(customerIds, databases);

        List<Map<String, Object>> results = new ArrayList<>();
        for (String db : databases) {
            boolean known = DATABASES.stream().anyMatch(m -> db.equals(m.get("database")));
            if (!known) continue;

            List<Map<String, Object>> rows = fetchRows(db, customerIds);

            Map<String, Object> dbResult = new HashMap<>();
            dbResult.put("database", db);
            dbResult.put("env", db.endsWith("_UAT") ? "UAT" : "QA");
            dbResult.put("rows", rows);
            results.add(dbResult);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("targetCustomerId", TARGET_CUSTOMER_ID);
        response.put("table", getTableName());
        response.put("results", results);
        return response;
    }

    /**
     * Re-points the CUSTOMER column to TARGET_CUSTOMER_ID in the module table
     * in the selected databases for the given customer DB ids.
     *
     * @param customerIds customer DB ids (already normalized, non-empty)
     * @param databases   target database names (subset of DATABASES)
     * @return per-database results with per-table counts
     */
    public Map<String, Object> cleanup(List<Long> customerIds, List<String> databases) throws Exception {
        validate(customerIds, databases);

        List<Long> workSet = new ArrayList<>();
        for (Long id : customerIds) {
            if (id.equals(TARGET_CUSTOMER_ID)) continue;
            if (!workSet.contains(id)) workSet.add(id);
        }

        List<Map<String, Object>> results = new ArrayList<>();
        for (String db : databases) {
            boolean known = DATABASES.stream().anyMatch(m -> db.equals(m.get("database")));
            if (!known) continue;

            Map<String, Object> counts = updateTable(db, workSet);

            Map<String, Object> dbResult = new HashMap<>(counts);
            dbResult.put("database", db);
            dbResult.put("env", db.endsWith("_UAT") ? "UAT" : "QA");
            results.add(dbResult);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("targetCustomerId", TARGET_CUSTOMER_ID);
        response.put("table", getTableName());
        response.put("results", results);
        return response;
    }

    private void validate(List<Long> customerIds, List<String> databases) {
        if (customerIds == null || customerIds.isEmpty()) {
            throw new IllegalArgumentException("At least one customer DB id is required");
        }
        if (databases == null || databases.isEmpty()) {
            throw new IllegalArgumentException("Select at least one database");
        }
    }

    private List<Map<String, Object>> fetchRows(String dbName, List<Long> customerIds) throws Exception {
        String table = getTableName();
        String columns = getPreviewColumns();
        String sql = "SELECT " + columns + " FROM " + table + " WHERE CUSTOMER IN ("
                + placeholders(customerIds.size()) + ") ORDER BY ID DESC";

        List<Map<String, Object>> rows = new ArrayList<>();
        try (Connection con = dbUtil.getConnectionForDatabase(dbName);
             PreparedStatement ps = con.prepareStatement(sql)) {
            for (int i = 0; i < customerIds.size(); i++) {
                ps.setLong(i + 1, customerIds.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                ResultSetMetaData meta = rs.getMetaData();
                while (rs.next()) {
                    Map<String, Object> row = new HashMap<>();
                    for (int i = 1; i <= meta.getColumnCount(); i++) {
                        row.put(meta.getColumnName(i).toLowerCase(), rs.getObject(i));
                    }
                    rows.add(row);
                }
            }
        }
        System.out.println("✓ " + dbName + "." + table + ": " + rows.size() + " row(s) for customers " + customerIds);
        return rows;
    }

    private Map<String, Object> updateTable(String dbName, List<Long> customerIds) throws Exception {
        String table = getTableName();
        Map<String, Object> result = new HashMap<>();
        long found = 0;
        long updated = 0;

        if (customerIds.isEmpty()) {
            result.put("customers", customerIds);
            result.put("rowsFound", found);
            result.put("rowsUpdated", updated);
            result.put("message", "No workable customer ids (all are the target id " + TARGET_CUSTOMER_ID + ")");
            return result;
        }

        String inClause = placeholders(customerIds.size());
        try (Connection con = dbUtil.getConnectionForDatabase(dbName)) {
            String countSql = "SELECT COUNT(*) FROM " + table + " WHERE CUSTOMER IN (" + inClause + ")";
            try (PreparedStatement ps = con.prepareStatement(countSql)) {
                for (int i = 0; i < customerIds.size(); i++) {
                    ps.setLong(i + 1, customerIds.get(i));
                }
                try (ResultSet rs = ps.executeQuery()) {
                    found = rs.next() ? rs.getLong(1) : 0;
                }
            }

            if (found > 0) {
                String updateSql = "UPDATE " + table + " SET CUSTOMER = ? WHERE CUSTOMER IN (" + inClause + ")";
                try (PreparedStatement ps = con.prepareStatement(updateSql)) {
                    ps.setLong(1, TARGET_CUSTOMER_ID);
                    for (int i = 0; i < customerIds.size(); i++) {
                        ps.setLong(i + 2, customerIds.get(i));
                    }
                    updated = ps.executeUpdate();
                }
            }
            System.out.println("✓ " + dbName + "." + table + ": " + found + " row(s) found, " + updated + " moved to " + TARGET_CUSTOMER_ID);
        }

        result.put("customers", customerIds);
        result.put("rowsFound", found);
        result.put("rowsUpdated", updated);
        result.put("message", "Moved " + updated + " row(s) to " + TARGET_CUSTOMER_ID);
        return result;
    }

    private static String placeholders(int size) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < size; i++) {
            if (i > 0) sb.append(",");
            sb.append("?");
        }
        return sb.toString();
    }
}