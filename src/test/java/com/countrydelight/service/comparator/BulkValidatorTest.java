package com.countrydelight.service.comparator;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BulkValidatorTest {

    private final BulkValidator validator = new BulkValidator(new ObjectMapper());

    @Test
    void quotedJsonWithCommasStaysOneField() {
        String csv = "API Name,Method,URL A,URL B,Headers A (JSON),Headers B (JSON),Payload (JSON),Ignore Formatting (Y/N)\n"
                + "Order,POST,https://qa.example.com/v1/order,https://dev.example.com/v1/order,{},{},";

        Map<String, Object> result = validator.validate(csv);

        assertEquals(1, result.get("validRows"));
        assertEquals(1, result.get("totalRows"));
    }

    @Test
    void invalidUrlAndPayloadAreReportedPerRow() {
        String csv = "API Name,Method,URL A,URL B,Headers A (JSON),Headers B (JSON),Payload (JSON),Ignore Formatting (Y/N)\n"
                + "Bad,POST,not-a-url,https://dev.example.com/v1/order,{},{},\"{bad}\",N";

        Map<String, Object> result = validator.validate(csv);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> invalidRows = (List<Map<String, Object>>) result.get("invalidRows");

        assertEquals(0, result.get("validRows"));
        assertEquals(1, invalidRows.size());
        assertEquals("URL A is not a valid URL; Payload is not valid JSON", invalidRows.get(0).get("reason"));
    }
}
