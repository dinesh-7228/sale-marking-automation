package com.countrydelight.service.comparator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiffEngineTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final DiffEngine engine = new DiffEngine();

    private List<Map<String, Object>> diff(String jsonA, String jsonB) throws Exception {
        JsonNode a = mapper.readTree(jsonA);
        JsonNode b = mapper.readTree(jsonB);
        return engine.compare(a, b, "API A", "API B");
    }

    @Test
    void identicalResponsesProduceNoDiffs() throws Exception {
        assertTrue(diff("{\"id\":1,\"name\":\"x\"}", "{\"id\":1,\"name\":\"x\"}").isEmpty());
    }

    @Test
    void missingKeyIsReportedWithTheSideItIsMissingFrom() throws Exception {
        List<Map<String, Object>> diffs = diff("{\"id\":1}", "{\"id\":1,\"name\":\"x\"}");

        assertEquals(1, diffs.size());
        assertEquals("/name", diffs.get(0).get("path"));
        assertEquals("MISSING_KEY", diffs.get(0).get("type"));
        assertEquals("API A", diffs.get(0).get("missingIn"));
        assertTrue(diffs.get(0).containsKey("contextJson"));
    }

    @Test
    void numericStringIsTreatedAsFormatting() throws Exception {
        List<Map<String, Object>> diffs = diff("{\"price\":2}", "{\"price\":\"2\"}");

        assertEquals(1, diffs.size());
        assertEquals("FORMATTING", diffs.get(0).get("type"));
        assertEquals(true, diffs.get(0).get("ignorable"));
    }

    @Test
    void tinyNumberDifferenceIsFormattingButRealOneIsNot() throws Exception {
        assertEquals("FORMATTING", diff("{\"v\":2.0}", "{\"v\":2.00000000001}").get(0).get("type"));
        assertEquals("VALUE_MISMATCH", diff("{\"v\":2}", "{\"v\":3}").get(0).get("type"));
    }

    @Test
    void nonNumericTypeDifferenceIsCritical() throws Exception {
        List<Map<String, Object>> diffs = diff("{\"v\":true}", "{\"v\":\"true\"}");

        assertEquals("TYPE_MISMATCH", diffs.get(0).get("type"));
        assertEquals(false, diffs.get(0).get("ignorable"));
    }

    @Test
    void nestedAndArrayPathsUseSlashNotation() throws Exception {
        List<Map<String, Object>> diffs = diff(
                "{\"a\":{\"b\":1},\"list\":[1,2]}",
                "{\"a\":{\"b\":2},\"list\":[1]}");

        assertEquals("/a/b", diffs.get(0).get("path"));
        assertEquals("VALUE_MISMATCH", diffs.get(0).get("type"));
        assertEquals("/list/1", diffs.get(1).get("path"));
        assertEquals("API B", diffs.get(1).get("missingIn"));
    }

    @Test
    void nullVersusValueIsReported() throws Exception {
        List<Map<String, Object>> diffs = diff("{\"v\":null}", "{\"v\":\"x\"}");

        assertEquals(1, diffs.size());
        assertEquals("MISSING_VALUE", diffs.get(0).get("type"));
    }
}
