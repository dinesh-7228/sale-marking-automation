package com.countrydelight.service.comparator;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Validates an uploaded bulk-comparison sheet (CSV) and remembers the batch for status polling. */
@Component
public class BulkValidator {

    private final Map<String, String> batchStatus = new ConcurrentHashMap<>();
    private final ObjectMapper mapper;

    public BulkValidator(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public Map<String, Object> validate(String csv) {
        String batchId = UUID.randomUUID().toString();
        List<List<String>> rows = parseRows(csv);
        List<Map<String, Object>> invalidRows = new ArrayList<>();
        int validRows = 0;

        if (!rows.isEmpty()) {
            List<String> headers = rows.get(0);
            for (int i = 1; i < rows.size(); i++) {
                Map<String, String> row = toRow(headers, rows.get(i));
                String urlA = row.getOrDefault("URL A", "");
                String urlB = row.getOrDefault("URL B", "");
                String payload = row.getOrDefault("Payload (JSON)", "");

                List<String> errors = new ArrayList<>();
                if (urlA.isEmpty()) errors.add("URL A is empty");
                if (urlB.isEmpty()) errors.add("URL B is empty");
                if (!urlA.isEmpty() && !isValidUrl(urlA)) errors.add("URL A is not a valid URL");
                if (!urlB.isEmpty() && !isValidUrl(urlB)) errors.add("URL B is not a valid URL");
                if (!payload.isEmpty() && !isJson(payload)) errors.add("Payload is not valid JSON");

                if (errors.isEmpty()) {
                    validRows++;
                } else {
                    Map<String, Object> invalid = new LinkedHashMap<>();
                    invalid.put("row", i);
                    invalid.put("reason", String.join("; ", errors));
                    invalidRows.add(invalid);
                }
            }
        }

        if (validRows > 0) {
            batchStatus.put(batchId, "completed");
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("batchId", batchId);
        result.put("totalRows", Math.max(0, rows.size() - 1));
        result.put("validRows", validRows);
        result.put("invalidRows", invalidRows);
        return result;
    }

    public Map<String, Object> status(String batchId) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (batchStatus.containsKey(batchId)) {
            result.put("status", batchStatus.get(batchId));
            result.put("progress", "100%");
        } else {
            result.put("status", "not_found");
            result.put("progress", "0%");
        }
        return result;
    }

    private Map<String, String> toRow(List<String> headers, List<String> values) {
        Map<String, String> row = new LinkedHashMap<>();
        for (int i = 0; i < headers.size(); i++) {
            row.put(headers.get(i).trim(), i < values.size() ? values.get(i).trim() : "");
        }
        return row;
    }

    // RFC 4180 style: a quoted field may contain commas, newlines and "" escaped quotes.
    private List<List<String>> parseRows(String content) {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < content.length() && content.charAt(i + 1) == '"') {
                        field.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    field.append(c);
                }
            } else if (c == '"') {
                inQuotes = true;
            } else if (c == ',') {
                row.add(field.toString());
                field.setLength(0);
            } else if (c == '\n') {
                row.add(field.toString());
                field.setLength(0);
                rows.add(row);
                row = new ArrayList<>();
            } else if (c != '\r') {
                field.append(c);
            }
        }
        if (field.length() > 0 || !row.isEmpty()) {
            row.add(field.toString());
            rows.add(row);
        }

        return rows;
    }

    private boolean isValidUrl(String value) {
        try {
            return URI.create(value).isAbsolute();
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isJson(String value) {
        try {
            mapper.readTree(value);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
