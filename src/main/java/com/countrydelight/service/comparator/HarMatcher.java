package com.countrydelight.service.comparator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.TextNode;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Parses two HAR files and pairs JSON endpoints by method + path, so they can be compared. */
@Component
public class HarMatcher {

    private final ObjectMapper mapper;

    public HarMatcher(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public Map<String, Object> match(String harA, String harB) throws Exception {
        List<Entry> entriesA = parse(harA);
        List<Entry> entriesB = parse(harB);

        List<Map<String, Object>> matched = new ArrayList<>();
        List<Boolean> usedB = new ArrayList<>();
        for (int i = 0; i < entriesB.size(); i++) {
            usedB.add(false);
        }

        for (Entry entryA : entriesA) {
            for (int i = 0; i < entriesB.size(); i++) {
                Entry entryB = entriesB.get(i);
                if (usedB.get(i)) continue;
                if (entryB.method.equals(entryA.method) && entryB.path.equals(entryA.path)) {
                    usedB.set(i, true);
                    Map<String, Object> pair = new LinkedHashMap<>();
                    pair.put("method", entryA.method);
                    pair.put("path", entryA.path);
                    pair.put("entryIdA", entryA.id());
                    pair.put("entryIdB", entryB.id());
                    matched.add(pair);
                    break;
                }
            }
        }

        List<Map<String, Object>> unmatchedA = new ArrayList<>();
        for (Entry entryA : entriesA) {
            boolean wasMatched = matched.stream()
                    .anyMatch(pair -> entryA.id().equals(pair.get("entryIdA")));
            if (!wasMatched) unmatchedA.add(entryA.data);
        }

        List<Map<String, Object>> unmatchedB = new ArrayList<>();
        for (int i = 0; i < entriesB.size(); i++) {
            if (!usedB.get(i)) unmatchedB.add(entriesB.get(i).data);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("matchedEndpoints", matched);
        result.put("unmatchedA", unmatchedA);
        result.put("unmatchedB", unmatchedB);
        return result;
    }

    private List<Entry> parse(String rawHar) throws Exception {
        List<Entry> entries = new ArrayList<>();
        JsonNode harEntries = mapper.readTree(rawHar).path("log").path("entries");

        for (JsonNode harEntry : harEntries) {
            String mimeType = harEntry.path("response").path("content").path("mimeType").asText("");
            if (!mimeType.contains("application/json")) continue;

            String url = harEntry.path("request").path("url").asText("");
            String path = "";
            try {
                path = URI.create(url).getRawPath();
            } catch (Exception ignored) {
                // Keep the empty path; the entry still shows up in unmatched lists.
            }

            Map<String, String> headers = new LinkedHashMap<>();
            for (JsonNode header : harEntry.path("request").path("headers")) {
                headers.put(header.path("name").asText(), header.path("value").asText());
            }

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("method", harEntry.path("request").path("method").asText("GET"));
            data.put("url", url);
            data.put("path", path);
            data.put("requestHeaders", headers);
            data.put("requestPayload", parseJson(harEntry.path("request").path("postData").path("text").asText(null)));
            data.put("responseBody", parseJson(harEntry.path("response").path("content").path("text").asText(null)));
            data.put("statusCode", harEntry.path("response").path("status").asInt(0));
            data.put("timestamp", harEntry.path("startedDateTime").asText(""));

            entries.add(new Entry(entries.size(), data));
        }

        return entries;
    }

    private Object parseJson(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return mapper.readTree(text);
        } catch (Exception e) {
            return TextNode.valueOf(text);
        }
    }

    private static final class Entry {
        private final int index;
        private final Map<String, Object> data;
        private final String method;
        private final String path;

        private Entry(int index, Map<String, Object> data) {
            this.index = index;
            this.data = data;
            this.method = (String) data.get("method");
            this.path = (String) data.get("path");
        }

        private String id() {
            return method + ":" + path + ":" + index;
        }
    }
}
