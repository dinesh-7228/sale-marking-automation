package com.countrydelight.service.comparator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads comparison history from the JSON file the comparator already used
 * ({@code data/comparator-history.json}), so an existing history file keeps working.
 */
@Component
public class HistoryStore {

    private final ObjectMapper mapper;
    private final Path file;

    public HistoryStore(ObjectMapper mapper, @Value("${app.comparator.data-dir:data}") String dataDir) {
        this.mapper = mapper;
        this.file = Path.of(dataDir, "comparator-history.json");
    }

    public Map<String, Object> findAll(int page, int limit, String startDate, String endDate,
                                       String result, String source, String endpoint) {
        List<JsonNode> records = filter(startDate, endDate, result, source, endpoint);
        records.sort(Comparator.comparing(this::createdAt).reversed());

        int from = Math.max(0, (page - 1) * limit);
        List<JsonNode> data = from >= records.size()
                ? new ArrayList<>()
                : records.subList(from, Math.min(records.size(), from + limit));

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", data);
        response.put("total", records.size());
        response.put("page", page);
        response.put("limit", limit);
        return response;
    }

    public JsonNode findOne(String id) {
        for (JsonNode record : readAll()) {
            if (id.equals(record.path("id").asText())) {
                return record;
            }
        }
        return null;
    }

    public String exportCsv(String startDate, String endDate, String result, String source) {
        List<JsonNode> records = filter(startDate, endDate, result, source, null);
        records.sort(Comparator.comparing(this::createdAt).reversed());

        StringBuilder csv = new StringBuilder(
                "ID,Timestamp,API Name,Method,URL A,URL B,cURL A,cURL B,Result,Source,Batch ID");
        for (JsonNode record : records) {
            csv.append('\n')
                    .append(csvValue(record.path("id").asText()))
                    .append(',').append(csvValue(record.path("createdAt").asText()))
                    .append(',').append(csvValue(record.path("apiName").asText()))
                    .append(',').append(csvValue(record.path("method").asText()))
                    .append(',').append(csvValue(record.path("urlA").asText()))
                    .append(',').append(csvValue(record.path("urlB").asText()))
                    .append(',').append(csvValue(record.path("curlA").asText()))
                    .append(',').append(csvValue(record.path("curlB").asText()))
                    .append(',').append(csvValue(record.path("result").asText()))
                    .append(',').append(csvValue(record.path("source").asText()))
                    .append(',').append(csvValue(record.path("batchId").asText()));
        }
        return csv.toString();
    }

    private List<JsonNode> filter(String startDate, String endDate, String result, String source,
                                  String endpoint) {
        List<JsonNode> records = new ArrayList<>();
        for (JsonNode record : readAll()) {
            if (startDate != null && !startDate.isBlank() && endDate != null && !endDate.isBlank()) {
                long createdAt = createdAt(record);
                if (createdAt < parseDate(startDate) || createdAt > parseDate(endDate)) continue;
            }
            if (result != null && !result.isBlank() && !result.equals(record.path("result").asText())) continue;
            if (source != null && !source.isBlank() && !source.equals(record.path("source").asText())) continue;
            if (endpoint != null && !endpoint.isBlank() && !record.path("urlA").asText().contains(endpoint)) continue;
            records.add(record);
        }
        return records;
    }

    private List<JsonNode> readAll() {
        if (!Files.isRegularFile(file)) {
            return new ArrayList<>();
        }
        try {
            JsonNode root = mapper.readTree(Files.readString(file));
            List<JsonNode> records = new ArrayList<>();
            if (root.isArray()) {
                root.forEach(records::add);
            }
            return records;
        } catch (IOException e) {
            return new ArrayList<>();
        }
    }

    private long createdAt(JsonNode record) {
        return parseDate(record.path("createdAt").asText());
    }

    private long parseDate(String value) {
        try {
            return Instant.parse(value).toEpochMilli();
        } catch (DateTimeParseException e) {
            return 0L;
        }
    }

    private String csvValue(String value) {
        return '"' + value.replace("\"", "\"\"") + '"';
    }
}
