package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.allinweb.ch.model.ReferenceLoadDTO;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TestIdLocatorContractTest {
    @Test
    void ranksStandardTestIdsInOneDeterministicOrder() {
        assertEquals(
                List.of("[data-testid=\"primary\"]", "[test-id=\"fallback\"]"),
                TestIdLocatorContract.selectors(Map.of(
                        "test-id", "fallback",
                        "data-testid", "primary")));
    }

    @Test
    void acceptsOnlyAnExplicitlyConfiguredClientTestIdAttribute() {
        assertEquals(
                List.of("[qa-hook=\"save-action\"]"),
                TestIdLocatorContract.selectors(Map.of(
                        TestIdLocatorContract.ATTRIBUTE_NAME_METADATA, "qa-hook",
                        "qa-hook", "save-action",
                        "title", "not-a-test-id")));
        assertEquals(List.of(), TestIdLocatorContract.selectors(Map.of("qa-hook", "save-action")));
    }

    @Test
    void reconstructsClientTestIdFromPersistedInstructionReferences() {
        ReferenceLoadDTO marker = reference("AttrData:automation.test-id.attribute", "martini-id");
        ReferenceLoadDTO value = reference("AttrData:martini-id", "continue");
        ReferenceLoadDTO ordinary = reference("AttrData:role", "button");
        assertEquals(
                List.of("[martini-id=\"continue\"]"),
                TestIdLocatorContract.selectorsFromReferences(List.of(marker, value, ordinary)));
    }

    private static ReferenceLoadDTO reference(String type, String value) {
        ReferenceLoadDTO reference = new ReferenceLoadDTO();
        reference.setReferenceType(type);
        reference.setValue(value);
        return reference;
    }
}
