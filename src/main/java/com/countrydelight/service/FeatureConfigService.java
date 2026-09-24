package com.countrydelight.service;

import com.countrydelight.db.DatabaseUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

/**
 * Feature Config (app_feature_config) editor.
 *
 * Reads/writes the `app_feature_config` table inside the
 * complaintmanagement_QA / complaintmanagement_UAT databases. The `ELIGIBILITY`
 * column holds a JSON array of rules, one per customer segment / priority.
 *
 * Editing is done at the granularity of a single key of a single rule: the UI
 * renders each JSON key as its own editable field)Skip, and updating one key
 * performs a read-modify-write of the ELIGIBILITY JSON on the server, so the
 * rest of the payload is never rewritten by the browser.
 */
@Service
public class FeatureConfigService {

    @Autowired
    private DatabaseUtil dbUtil;

    @Autowired
    private ObjectMapper objectMapper;

    static final List<Map<String, Object>> DATABASES = databases();

    private static List<Map<String, Object>> databases() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (String name : Arrays.asList("complaintmanagement_QA", "complaintmanagement_UAT")) {
            Map<String, Object> db = new HashMap<>();
            db.put("database", name);
            db.put("env", name.endsWith("_UAT") ? "UAT" : "QA");
            list.add(db);
        }
        return list;
    }

    public List<Map<String, Object>> getDatabases() {
        return DATABASES;
    }

    /**
     * Fetches all rows of app_feature_config in the given databases, with the
     * ELIGIBILITY JSON array parsed into a list of per-rule objects.
     *
     * @param databases target database names (subset of DATABASES)
     * @return per-database { database, env, rows: [...] }
     */
    public List<Map<String, Object>> fetch(List<String> databases) throws Exception {
        if (databases == null || databases.isEmpty()) {
            throw new IllegalArgumentException("Select at least one database");
        }

        List<Map<String, Object>> results = new ArrayList<>();
        for (String db : databases) {
            boolean known = DATABASES.stream().anyMatch(m -> db.equals(m.get("database")));
            if (!known) {
                continue;
            }
            List<Map<String, Object>> rows = new ArrayList<>();
            try (Connection con = dbUtil.getConnectionForDatabase(db);
                 PreparedStatement ps = con.prepareStatement("SELECT * FROM app_feature_config ORDER BY ID");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new HashMap<>();
                    row.put("id", rs.getLong("ID"));
                    row.put("type", rs.getObject("TYPE"));
                    row.put("enabled", rs.getBoolean("ENABLED"));
                    row.put("eligibility", parseJsonArray(rs.getString("ELIGIBILITY")));
                    row.put("formData", parseJsonObject(rs.getString("FORM_DATA")));
                    row.put("createdDate", rs.getObject("CREATED_DATE"));
                    row.put("updatedDate", rs.getObject("UPDATED_DATE"));
                    rows.add(row);
                }
            } catch (SQLException e) {
                if (e.getMessage() != null && e.getMessage().toLowerCase().contains("doesn't exist")) {
                    throw new RuntimeException("DB " + db + " has no app_feature_config table: " + e.getMessage(), e);
                }
                throw e;
            }
            Map<String, Object> dbResult = new HashMap<>();
            dbResult.put("database", db);
            dbResult.put("env", db.endsWith("_UAT") ? "UAT" : "QA");
            dbResult.put("rows", rows);
            results.add(dbResult);
        }
        return results;
    }

    /**
     * Updates a single key of a single rule in a row's ELIGIBILITY JSON, or a
     * single key of the FORM_DATA JSON (formData=true).
     *
     * The server reads the current JSON, replaces only the target key, and
     * writes the whole column back.
     *
     * @param database db name
     * @param rowId    app_feature_config row ID
     * @param field    "ELIGIBILITY" (rules array, requires ruleIndex) or "FORM_DATA" (flat object)
     * @param ruleIndex index into the eligibility array; -1 for flat object form data
     * @param key      JSON key to update
     * @param value    new raw value (string). Scalar booleans/numbers/arrays are inferred.
     */
    public Map<String, Object> updateKey(String database, Long rowId, String field,
                                        int ruleIndex, String key, String value) throws Exception {
        if (database == null || rowId == null) {
            throw new IllegalArgumentException("database and rowId are required");
        }
        if (key == null || key.trim().isEmpty()) {
            throw new IllegalArgumentException("key is required");
        }
        boolean flat = "FORM_DATA".equalsIgnoreCase(field);
        if (!flat && ruleIndex < 0) {
            throw new IllegalArgumentException("ruleIndex is required for ELIGIBILITY updates");
        }

        JsonNode parsedValue = parseJsonValue(value);

        String column = "ELIGIBILITY";
        try (Connection con = dbUtil.getConnectionForDatabase(database)) {
            String currentJson;
            try (PreparedStatement ps = con.prepareStatement("SELECT ELIGIBILITY, FORM_DATA FROM app_feature_config WHERE ID = ?")) {
                ps.setLong(1, rowId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new IllegalArgumentException("No app_feature_config row with ID " + rowId + " in " + database);
                    }
                    currentJson = flat ? rs.getString("FORM_DATA") : rs.getString("ELIGIBILITY");
                }
            }

            JsonNode root;
            if (currentJson == null || currentJson.trim().isEmpty()) {
                root = flat ? objectMapper.createObjectNode() : objectMapper.createArrayNode();
            } else {
                root = objectMapper.readTree(currentJson);
            }
            if (root == null) {
                throw new RuntimeException("Empty JSON in " + column + " for row " + rowId);
            }

            if (flat) {
                if (!(root instanceof ObjectNode)) {
                    throw new RuntimeException("FORM_DATA is not a JSON object for row " + rowId);
                }
                ((ObjectNode) root).set(key, parsedValue);
            } else {
                if (!root.isArray()) {
                    throw new RuntimeException("ELIGIBILITY is not a JSON array for row " + rowId);
                }
                JsonNode rule = root.get(ruleIndex);
                if (rule == null || !(rule instanceof ObjectNode)) {
                    throw new IllegalArgumentException("No rule at index " + ruleIndex + " in " + database);
                }
                ((ObjectNode) rule).set(key, parsedValue);
            }

            String newJson = objectMapper.writeValueAsString(root);
            try (PreparedStatement ps = con.prepareStatement("UPDATE app_feature_config SET " + column + " = ? WHERE ID = ?")) {
                ps.setString(1, newJson);
                ps.setLong(2, rowId);
                int updated = ps.executeUpdate();
                if (updated == 0) {
                    throw new RuntimeException("No row updated for ID " + rowId + " in " + database);
                }
            }
            System.out.println("✓ " + database + " row " + rowId + " " + column + "[" + ruleIndex + "]." + key +
                               " = " + newJson);
            return Collections.singletonMap("updated", true);
        }
    }

    /* ------------- helpers ------------- */

    private List<Map<String, Object>> parseJsonArray(String json) throws Exception {
        List<Map<String, Object>> list = new ArrayList<>();
        if (json == null || json.trim().isEmpty()) {
            return list;
        }
        JsonNode node = objectMapper.readTree(json);
        if (node.isArray()) {
            for (JsonNode el : node) {
                list.add(objectMapper.convertValue(el, LinkedHashMap.class));
            }
        }
        return list;
    }

    private Map<String, Object> parseJsonObject(String json) throws Exception {
        if (json == null || json.trim().isEmpty()) {
            return new LinkedHashMap<>();
        }
        JsonNode node = objectMapper.readTree(json);
        return objectMapper.convertValue(node, LinkedHashMap.class);
    }

    /**
     * Parses a raw input string into a JSON value, inferring the intended type:
     * - "true"/"false" -> boolean
     * - integer -> number
     * - "[...]" / "{...}" -> nested JSON (arrays / segmented ids / objects)
     * - anything else -> string (e.g. empty as empty string)
     */
    private JsonNode parseJsonValue(String value) throws Exception {
        String v = value == null ? "" : value.trim();
        if (v.isEmpty()) {
            return objectMapper.getNodeFactory().textNode("");
        }
        if (v.equalsIgnoreCase("true")) {
            return objectMapper.getNodeFactory().booleanNode(true);
        }
        if (v.equalsIgnoreCase("false")) {
            return objectMapper.getNodeFactory().booleanNode(false);
        }
        if (v.matches("-?\\d+")) {
            try {
                return objectMapper.getNodeFactory().numberNode(Long.parseLong(v));
            } catch (NumberFormatException e) {
                return objectMapper.getNodeFactory().numberNode(new java.math.BigDecimal(v));
            }
        }
        if (v.startsWith("[") || v.startsWith("{")) {
            try {
                return objectMapper.readTree(v);
            } catch (Exception e) {
                // fall through to string
            }
        }
        return objectMapper.getNodeFactory().textNode(v);
    }
}
