package com.countrydelight.service;

import com.countrydelight.db.DatabaseUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.*;

/**
 * OTP Cleaner: lists and deletes every sign_up_otp row for one customer mobile
 * number in the beejapuri QA / UAT databases.
 */
@Service
public class OtpCleanerService {

    private static final String TABLE = "sign_up_otp";
    static final List<String> DATABASES = Arrays.asList("beejapuri_QA", "beejapuri_UAT");

    @Autowired
    private DatabaseUtil dbUtil;

    public List<String> getDatabases() {
        return DATABASES;
    }

    public Map<String, Object> fetch(String mobile, List<String> databases) {
        List<Map<String, Object>> results = new ArrayList<>();
        for (String db : databases) {
            if (!DATABASES.contains(db)) {
                continue;
            }
            Map<String, Object> dbResult = dbResult(db);
            List<Map<String, Object>> rows = new ArrayList<>();
            String sql = "SELECT * FROM " + TABLE + " WHERE MOBILE_NUMBER = ? ORDER BY ID DESC";
            try (Connection con = dbUtil.getConnectionForDatabase(db);
                 PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setString(1, mobile);
                try (ResultSet rs = ps.executeQuery()) {
                    ResultSetMetaData meta = rs.getMetaData();
                    while (rs.next()) {
                        Map<String, Object> row = new LinkedHashMap<>();
                        for (int i = 1; i <= meta.getColumnCount(); i++) {
                            row.put(meta.getColumnName(i).toLowerCase(), rs.getObject(i));
                        }
                        rows.add(row);
                    }
                }
            } catch (Exception e) {
                dbResult.put("error", e.getMessage());
            }
            dbResult.put("rows", rows);
            dbResult.put("count", rows.size());
            results.add(dbResult);
        }
        return data(mobile, results);
    }

    public Map<String, Object> delete(String mobile, List<String> databases) {
        List<Map<String, Object>> results = new ArrayList<>();
        long totalDeleted = 0;
        for (String db : databases) {
            if (!DATABASES.contains(db)) {
                continue;
            }
            Map<String, Object> dbResult = dbResult(db);
            String sql = "DELETE FROM " + TABLE + " WHERE MOBILE_NUMBER = ?";
            try (Connection con = dbUtil.getConnectionForDatabase(db);
                 PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setString(1, mobile);
                int deleted = ps.executeUpdate();
                dbResult.put("deleted", deleted);
                totalDeleted += deleted;
                System.out.println("✓ Deleted " + deleted + " OTP(s) for mobile " + mobile + " in " + db);
            } catch (Exception e) {
                dbResult.put("deleted", 0);
                dbResult.put("error", e.getMessage());
            }
            results.add(dbResult);
        }
        Map<String, Object> data = data(mobile, results);
        data.put("totalDeleted", totalDeleted);
        return data;
    }

    private Map<String, Object> dbResult(String db) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("database", db);
        result.put("env", db.endsWith("_UAT") ? "UAT" : "QA");
        return result;
    }

    private Map<String, Object> data(String mobile, List<Map<String, Object>> results) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("mobile", mobile);
        data.put("results", results);
        return data;
    }
}
