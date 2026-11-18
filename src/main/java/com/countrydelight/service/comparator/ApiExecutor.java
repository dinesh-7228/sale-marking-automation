package com.countrydelight.service.comparator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.TextNode;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/** Calls one of the two APIs being compared and records status, body, latency and the equivalent cURL. */
@Component
public class ApiExecutor {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private final ObjectMapper mapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(TIMEOUT)
            .build();

    public ApiExecutor(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public Map<String, Object> execute(JsonNode request) {
        long start = System.currentTimeMillis();
        Map<String, Object> result = new LinkedHashMap<>();

        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(requestUrl(request))
                    .timeout(TIMEOUT)
                    .method(request.path("method").asText("GET"), body(request));

            JsonNode headers = request.path("headers");
            if (headers.isObject()) {
                Iterator<Map.Entry<String, JsonNode>> fields = headers.fields();
                while (fields.hasNext()) {
                    Map.Entry<String, JsonNode> header = fields.next();
                    // HttpClient computes this itself and rejects a stale value.
                    if (!"content-length".equalsIgnoreCase(header.getKey())) {
                        builder.header(header.getKey(), header.getValue().asText());
                    }
                }
            }

            HttpResponse<String> response = httpClient.send(builder.build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            result.put("status", response.statusCode());
            result.put("responseBody", parseBody(response.body()));
            result.put("responseTimeMs", System.currentTimeMillis() - start);
        } catch (Exception e) {
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("error", e.getClass().getSimpleName());
            error.put("message", e.getMessage() == null ? "Request failed" : e.getMessage());
            result.put("status", 0);
            result.put("responseBody", error);
            result.put("responseTimeMs", System.currentTimeMillis() - start);
        }

        result.put("curl", curl(request));
        return result;
    }

    private HttpRequest.BodyPublisher body(JsonNode request) {
        JsonNode payload = request.get("payload");
        if (payload == null || payload.isNull()) {
            return HttpRequest.BodyPublishers.noBody();
        }
        String raw = payload.isTextual() ? payload.asText() : payload.toString();
        return HttpRequest.BodyPublishers.ofString(raw, StandardCharsets.UTF_8);
    }

    // GET requests carry their payload as query parameters, like the original executor did.
    private URI requestUrl(JsonNode request) {
        String url = request.path("url").asText();
        JsonNode payload = request.get("payload");
        if (payload != null && payload.isObject() && "GET".equalsIgnoreCase(request.path("method").asText())) {
            StringBuilder query = new StringBuilder();
            Iterator<Map.Entry<String, JsonNode>> fields = payload.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                if (query.length() > 0) query.append('&');
                query.append(URLEncoder.encode(field.getKey(), StandardCharsets.UTF_8))
                        .append('=')
                        .append(URLEncoder.encode(field.getValue().asText(), StandardCharsets.UTF_8));
            }
            if (query.length() > 0) {
                url += (url.contains("?") ? "&" : "?") + query;
            }
        }
        return URI.create(url);
    }

    private JsonNode parseBody(String raw) {
        if (raw == null || raw.isBlank()) {
            return TextNode.valueOf("");
        }
        try {
            return mapper.readTree(raw);
        } catch (Exception e) {
            return TextNode.valueOf(raw);
        }
    }

    private String curl(JsonNode request) {
        StringBuilder curl = new StringBuilder("curl -X ")
                .append(request.path("method").asText("GET"))
                .append(" '").append(escapeShell(requestUrl(request).toString())).append("'");

        JsonNode headers = request.path("headers");
        if (headers.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = headers.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> header = fields.next();
                curl.append(" \\\n  -H '")
                        .append(escapeShell(header.getKey() + ": " + header.getValue().asText()))
                        .append("'");
            }
        }

        JsonNode payload = request.get("payload");
        if (payload != null && !payload.isNull()) {
            String raw = payload.isTextual() ? payload.asText() : payload.toString();
            curl.append(" \\\n  -d '").append(escapeShell(raw)).append("'");
        }

        return curl.toString();
    }

    private String escapeShell(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("'", "'\\''")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }
}
