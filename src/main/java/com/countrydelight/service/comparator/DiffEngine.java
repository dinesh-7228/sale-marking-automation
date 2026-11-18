package com.countrydelight.service.comparator;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Structural JSON diff. Port of the NestJS DiffEngineService, so the comparator
 * UI (and its report) keeps the same diff types and paths.
 */
@Component
public class DiffEngine {

    public List<Map<String, Object>> compare(JsonNode a, JsonNode b, String labelA, String labelB) {
        List<Map<String, Object>> diffs = new ArrayList<>();
        walk(a, b, "", diffs, labelA, labelB);
        return diffs;
    }

    private void walk(JsonNode a, JsonNode b, String path, List<Map<String, Object>> diffs,
                      String labelA, String labelB) {
        if (a == null && b == null) {
            return;
        }
        if (a == null || b == null) {
            if (a == null) {
                diffs.add(diff(path, "MISSING_KEY", null, b, false, labelA, null,
                        "Key at '" + path + "' is missing in " + labelA + "."));
            } else {
                diffs.add(diff(path, "MISSING_KEY", a, null, false, labelB, null,
                        "Key at '" + path + "' is missing in " + labelB + "."));
            }
            return;
        }

        if (a.isNull() || b.isNull()) {
            if (a.isNull() != b.isNull()) {
                diffs.add(diff(path, "MISSING_VALUE", a, b, false, null, null, null));
            }
            return;
        }

        String typeA = type(a);
        String typeB = type(b);

        if (!typeA.equals(typeB)) {
            if (isFormattingTypeDifference(a, b)) {
                diffs.add(diff(path, "FORMATTING", a, b, true, null, null, null));
            } else {
                diffs.add(diff(path, "TYPE_MISMATCH", a, b, false, null, null, null));
            }
            return;
        }

        if ("object".equals(typeA)) {
            compareObjects(a, b, path, diffs, labelA, labelB);
            return;
        }
        if ("array".equals(typeA)) {
            compareArrays(a, b, path, diffs, labelA, labelB);
            return;
        }

        if (!primitiveEquals(a, b)) {
            if ("number".equals(typeA) && isFormattingNumberDifference(a, b)) {
                diffs.add(diff(path, "FORMATTING", a, b, true, null, null, null));
            } else {
                diffs.add(diff(path, "VALUE_MISMATCH", a, b, false, null, null, null));
            }
        }
    }

    private void compareObjects(JsonNode objA, JsonNode objB, String path, List<Map<String, Object>> diffs,
                                String labelA, String labelB) {
        Set<String> keys = new LinkedHashSet<>();
        objA.fieldNames().forEachRemaining(keys::add);
        objB.fieldNames().forEachRemaining(keys::add);

        for (String key : keys) {
            String childPath = path.isEmpty() ? "/" + key : path + "/" + key;

            if (!objA.has(key)) {
                diffs.add(diff(childPath, "MISSING_KEY", null, objB.get(key), false, labelA, objB,
                        "Key '" + key + "' is missing in " + labelA + "."));
            } else if (!objB.has(key)) {
                diffs.add(diff(childPath, "MISSING_KEY", objA.get(key), null, false, labelB, objA,
                        "Key '" + key + "' is missing in " + labelB + "."));
            } else {
                walk(objA.get(key), objB.get(key), childPath, diffs, labelA, labelB);
            }
        }
    }

    private void compareArrays(JsonNode arrA, JsonNode arrB, String path, List<Map<String, Object>> diffs,
                               String labelA, String labelB) {
        int maxLen = Math.max(arrA.size(), arrB.size());

        for (int i = 0; i < maxLen; i++) {
            String childPath = path + "/" + i;

            if (i >= arrA.size()) {
                diffs.add(diff(childPath, "MISSING_KEY", null, arrB.get(i), false, labelA, null,
                        "Array index " + i + " is missing in " + labelA + "."));
            } else if (i >= arrB.size()) {
                diffs.add(diff(childPath, "MISSING_KEY", arrA.get(i), null, false, labelB, null,
                        "Array index " + i + " is missing in " + labelB + "."));
            } else {
                walk(arrA.get(i), arrB.get(i), childPath, diffs, labelA, labelB);
            }
        }
    }

    private Map<String, Object> diff(String path, String type, JsonNode valueA, JsonNode valueB,
                                     boolean ignorable, String missingIn, JsonNode context, String note) {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("path", path);
        d.put("type", type);
        if (valueA != null) {
            d.put("valueA", valueA);
        }
        if (valueB != null) {
            d.put("valueB", valueB);
        }
        d.put("ignorable", ignorable);
        if (missingIn != null) {
            d.put("missingIn", missingIn);
        }
        if (context != null) {
            d.put("contextJson", context);
        }
        if (note != null) {
            d.put("note", note);
        }
        return d;
    }

    private String type(JsonNode node) {
        if (node.isObject()) return "object";
        if (node.isArray()) return "array";
        if (node.isTextual()) return "string";
        if (node.isNumber()) return "number";
        if (node.isBoolean()) return "boolean";
        return "null";
    }

    private boolean primitiveEquals(JsonNode a, JsonNode b) {
        if (a.isNumber() && b.isNumber()) {
            return a.decimalValue().compareTo(b.decimalValue()) == 0;
        }
        if (a.isTextual() && b.isTextual()) {
            return a.asText().equals(b.asText());
        }
        if (a.isBoolean() && b.isBoolean()) {
            return a.asBoolean() == b.asBoolean();
        }
        return a.equals(b);
    }

    private boolean isFormattingTypeDifference(JsonNode a, JsonNode b) {
        if (a.isNumber() && b.isTextual()) {
            return isNumeric(b.asText()) && numberText(a).equals(b.asText());
        }
        if (a.isTextual() && b.isNumber()) {
            return isNumeric(a.asText()) && a.asText().equals(numberText(b));
        }
        if (a.isTextual() && b.isTextual()) {
            return a.asText().trim().equals(b.asText().trim());
        }
        return false;
    }

    // 2 vs 2.0 is a formatting difference; anything larger than 10 decimal places is real.
    private boolean isFormattingNumberDifference(JsonNode a, JsonNode b) {
        return a.decimalValue().subtract(b.decimalValue()).abs()
                .compareTo(new BigDecimal("0.0000000001")) < 0;
    }

    private String numberText(JsonNode node) {
        BigDecimal value = node.decimalValue();
        if (value.stripTrailingZeros().scale() <= 0) {
            return value.toBigInteger().toString();
        }
        return value.stripTrailingZeros().toPlainString();
    }

    private boolean isNumeric(String text) {
        try {
            new BigDecimal(text.trim());
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
