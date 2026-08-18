package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.allinweb.ch.facade.execution.SmokeTestIntegrationSnapshotRepository.ReferenceSnapshot;
import com.allinweb.ch.model.ReferenceLoadDTO;
import java.util.ArrayList;
import java.util.LinkedHashMap;
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

    @Test
    void toleratesMissingMalformedAndDuplicatePersistedReferences() {
        List<ReferenceLoadDTO> references = new ArrayList<>();
        references.add(null);
        references.add(reference(null, "ignored"));
        references.add(reference("AttrData:data-testid", null));
        references.add(reference("AttrData:data-testid", "  "));
        references.add(reference("DATA-TESTID", " first "));
        references.add(reference("AttrData:data-testid", "second"));
        references.add(reference("AttrData:INVALID ATTRIBUTE", "ignored"));

        assertEquals(List.of("[data-testid=\"first\"]"),
                TestIdLocatorContract.selectorsFromReferences(references));
        assertEquals(List.of(), TestIdLocatorContract.selectorsFromReferences(null));
    }

    @Test
    void reconstructsStandardAndConfiguredIdsFromFrozenSnapshots() {
        List<ReferenceSnapshot> references = new ArrayList<>();
        references.add(null);
        references.add(new ReferenceSnapshot(1, 7, "automation.test-id.attribute", "qa-hook"));
        references.add(new ReferenceSnapshot(2, 7, "AttrData:qa-hook", "next"));
        references.add(new ReferenceSnapshot(3, 7, "data-cy", "continue"));

        assertEquals(
                List.of("[data-cy=\"continue\"]", "[qa-hook=\"next\"]"),
                TestIdLocatorContract.selectorsFromSnapshots(references));
        assertEquals(List.of(), TestIdLocatorContract.selectorsFromSnapshots(null));
    }

    @Test
    void normalizesValuesEscapesCssAndRejectsUndeclaredOrUnsafeAttributes() {
        Map<String, String> attributes = new LinkedHashMap<>();
        attributes.put(null, "ignored");
        attributes.put("title", null);
        attributes.put("role", "  ");
        attributes.put(" DATA-TEST-ID ", " path\\\"quoted ");
        attributes.put("INVALID ATTRIBUTE", "ignored");
        attributes.put(TestIdLocatorContract.ATTRIBUTE_NAME_METADATA, "qa-hook");
        attributes.put("qa-hook", " save ");

        assertEquals(
                List.of("[data-test-id=\"path\\\\\\\"quoted\"]", "[qa-hook=\"save\"]"),
                TestIdLocatorContract.selectors(attributes));
        assertEquals(Map.of("data-test-id", "path\\\"quoted", "qa-hook", "save"),
                TestIdLocatorContract.testIdValues(attributes));
        assertEquals(List.of(), TestIdLocatorContract.selectors(null));
        assertEquals(Map.of(), TestIdLocatorContract.testIdValues(null));
    }

    @Test
    void validatesStandardAndClientAttributeNamesAtTheContractBoundary() {
        assertTrue(TestIdLocatorContract.isStandard(" DATA-QA "));
        assertFalse(TestIdLocatorContract.isStandard(null));
        assertFalse(TestIdLocatorContract.isStandard("qa-hook"));

        assertTrue(TestIdLocatorContract.isSafeAttributeName("qa-hook:variant_1.value"));
        assertTrue(TestIdLocatorContract.isSafeAttributeName("a" + "1".repeat(127)));
        assertFalse(TestIdLocatorContract.isSafeAttributeName(null));
        assertFalse(TestIdLocatorContract.isSafeAttributeName("Data-QA"));
        assertFalse(TestIdLocatorContract.isSafeAttributeName("1data-qa"));
        assertFalse(TestIdLocatorContract.isSafeAttributeName("a" + "1".repeat(128)));
    }

    private static ReferenceLoadDTO reference(String type, String value) {
        ReferenceLoadDTO reference = new ReferenceLoadDTO();
        reference.setReferenceType(type);
        reference.setValue(value);
        return reference;
    }
}
