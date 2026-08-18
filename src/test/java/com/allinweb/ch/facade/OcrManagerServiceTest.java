package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OcrManagerServiceTest {
    @Test
    void canonicalSchemaPublishesTypedMetadataForEveryParameter() {
        List<Map<String, Object>> parameters = OcrManagerService.canonicalParameters();
        assertFalse(parameters.isEmpty());
        for (Map<String, Object> parameter : parameters) {
            assertNotNull(parameter.get("category"));
            assertNotNull(parameter.get("name"));
            assertNotNull(parameter.get("valueType"));
            assertTrue(parameter.containsKey("value"));
            assertTrue(parameter.containsKey("description"));
            assertTrue(parameter.containsKey("options"));
        }
    }
}
