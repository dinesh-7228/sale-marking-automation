package com.countrydelight.controller;

import com.countrydelight.service.comparator.ApiExecutor;
import com.countrydelight.service.comparator.BulkValidator;
import com.countrydelight.service.comparator.DiffEngine;
import com.countrydelight.service.comparator.HarMatcher;
import com.countrydelight.service.comparator.HistoryStore;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Backing API for the bundled API Comparator UI (served from /comparator/). */
@RestController
@RequestMapping("/api/v1")
public class ComparatorController {

    private final DiffEngine diffEngine;
    private final ApiExecutor apiExecutor;
    private final HarMatcher harMatcher;
    private final BulkValidator bulkValidator;
    private final HistoryStore historyStore;

    public ComparatorController(DiffEngine diffEngine, ApiExecutor apiExecutor, HarMatcher harMatcher,
                                BulkValidator bulkValidator, HistoryStore historyStore) {
        this.diffEngine = diffEngine;
        this.apiExecutor = apiExecutor;
        this.harMatcher = harMatcher;
        this.bulkValidator = bulkValidator;
        this.historyStore = historyStore;
    }

    @PostMapping("/execute-comparison")
    public Map<String, Object> executeComparison(@RequestBody JsonNode request) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("apiA", apiExecutor.execute(request.path("apiA")));
        result.put("apiB", apiExecutor.execute(request.path("apiB")));
        return result;
    }

    @PostMapping("/diff")
    public Map<String, Object> diff(@RequestBody JsonNode request) {
        List<Map<String, Object>> diffs = diffEngine.compare(
                request.get("jsonA"),
                request.get("jsonB"),
                request.path("labelA").asText("API A"),
                request.path("labelB").asText("API B"));

        List<Map<String, Object>> critical = new ArrayList<>();
        List<Map<String, Object>> ignored = new ArrayList<>();
        for (Map<String, Object> entry : diffs) {
            if (Boolean.TRUE.equals(entry.get("ignorable"))) {
                ignored.add(entry);
            } else {
                critical.add(entry);
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("diffs", diffs);
        result.put("criticalDiffs", critical);
        result.put("ignoredDiffs", ignored);
        return result;
    }

    @PostMapping("/har/upload")
    public Map<String, Object> uploadHar(@RequestParam(value = "files", required = false) MultipartFile[] files)
            throws Exception {
        if (files == null || files.length < 2) {
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("error", "Please upload exactly 2 HAR files");
            return error;
        }
        return harMatcher.match(read(files[0]), read(files[1]));
    }

    @PostMapping("/bulk/upload")
    public Map<String, Object> uploadBulk(@RequestParam(value = "file", required = false) MultipartFile file)
            throws IOException {
        if (file == null) {
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("error", "No file uploaded");
            return error;
        }
        return bulkValidator.validate(read(file));
    }

    @GetMapping("/bulk/{batchId}/status")
    public Map<String, Object> bulkStatus(@PathVariable String batchId) {
        return bulkValidator.status(batchId);
    }

    @GetMapping("/history")
    public Map<String, Object> history(@RequestParam(defaultValue = "1") int page,
                                       @RequestParam(defaultValue = "20") int limit,
                                       @RequestParam(required = false) String startDate,
                                       @RequestParam(required = false) String endDate,
                                       @RequestParam(required = false) String result,
                                       @RequestParam(required = false) String source,
                                       @RequestParam(required = false) String endpoint) {
        return historyStore.findAll(page, limit, startDate, endDate, result, source, endpoint);
    }

    @GetMapping(value = "/history/export", produces = "text/csv")
    public String historyExport(@RequestParam(required = false) String startDate,
                                @RequestParam(required = false) String endDate,
                                @RequestParam(required = false) String result,
                                @RequestParam(required = false) String source) {
        return historyStore.exportCsv(startDate, endDate, result, source);
    }

    @GetMapping("/history/{id}")
    public JsonNode historyOne(@PathVariable String id) {
        return historyStore.findOne(id);
    }

    private String read(MultipartFile file) throws IOException {
        return new String(file.getBytes(), StandardCharsets.UTF_8);
    }
}
