package com.allinweb.ch.testsupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class GoldenInstructionGraphFixtureTest {
    private static final String RESOURCE =
            "/fixtures/instruction-graph/golden-instruction-graphs-v1.json";

    private static final Set<String> EXPECTED_CASES = Set.of(
            "simple_parent_child",
            "get_multiple_consumers",
            "loop_with_positional_body",
            "if_else_endif_family",
            "goto_excel_goto_chain",
            "ownerless_memory_variable",
            "component_check_payment_complex");

    private static final Set<String> FORBIDDEN_DATA_KEYS = Set.of(
            "xpath",
            "coordinates",
            "cssSelector",
            "iframeXpath",
            "operation",
            "defaultValue",
            "value",
            "url",
            "password",
            "secret");

    @Test
    void fixtureIsVersionedSanitizedAndStructurallyClosed() {
        JsonObject root = load();

        assertEquals(1, root.get("fixtureVersion").getAsInt());
        assertSanitization(root.getAsJsonObject("sanitization"));
        assertFrozenMemoryContract(root.getAsJsonObject("frozenMemoryContract"));
        assertNoForbiddenDataKeys(root);

        JsonArray cases = root.getAsJsonArray("cases");
        assertEquals(7, cases.size());

        Set<String> names = new LinkedHashSet<>();
        for (JsonElement element : cases) {
            JsonObject fixture = element.getAsJsonObject();
            names.add(fixture.get("name").getAsString());
            assertClosedFixture(fixture);
        }
        assertEquals(EXPECTED_CASES, names);
    }

    @Test
    void ownerlessMemoryAndComplexComponentFactsRemainExplicit() {
        Map<String, JsonObject> cases = casesByName(load());

        JsonObject ownerless = cases.get("ownerless_memory_variable");
        assertNotNull(ownerless);
        JsonObject ownerlessVariable = ownerless.getAsJsonArray("variables").get(0).getAsJsonObject();
        assertTrue(ownerlessVariable.get("ownerInstructionId").isJsonNull());
        assertEquals(
                "MISSING_OWNER",
                ownerless
                        .getAsJsonObject("expectations")
                        .getAsJsonObject("baselineV2")
                        .get("relationshipState")
                        .getAsString());

        JsonObject complex = cases.get("component_check_payment_complex");
        assertNotNull(complex);
        assertEquals(15, complex.getAsJsonArray("instructions").size());
        assertEquals(2, complex.getAsJsonArray("variables").size());
        assertEquals(25, complex.getAsJsonArray("references").size());

        Map<Integer, Integer> ownerCounts = new HashMap<>();
        for (JsonElement element : complex.getAsJsonArray("variables")) {
            int owner = element.getAsJsonObject().get("ownerInstructionId").getAsInt();
            ownerCounts.merge(owner, 1, Integer::sum);
        }
        assertEquals(2, ownerCounts.get(7204));
    }

    private JsonObject load() {
        try (InputStreamReader reader = new InputStreamReader(
                getClass().getResourceAsStream(RESOURCE), StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (Exception error) {
            throw new AssertionError("Could not load " + RESOURCE, error);
        }
    }

    private Map<String, JsonObject> casesByName(JsonObject root) {
        Map<String, JsonObject> cases = new HashMap<>();
        for (JsonElement element : root.getAsJsonArray("cases")) {
            JsonObject fixture = element.getAsJsonObject();
            cases.put(fixture.get("name").getAsString(), fixture);
        }
        return cases;
    }

    private void assertSanitization(JsonObject sanitization) {
        assertTrue(sanitization.get("syntheticOwners").getAsBoolean());
        assertTrue(sanitization.get("syntheticIds").getAsBoolean());
        assertFalse(sanitization.get("containsCustomerNames").getAsBoolean());
        assertFalse(sanitization.get("containsLocators").getAsBoolean());
        assertFalse(sanitization.get("containsConfiguredValues").getAsBoolean());
    }

    private void assertFrozenMemoryContract(JsonObject contract) {
        assertEquals("REACT_VISIBLE_GRAPH", contract.get("selectionPlanner").getAsString());
        assertTrue(contract.get("blockSelectionCreatesOneTypedItem").getAsBoolean());
        assertTrue(contract.get("applyCreatesFreshIds").getAsBoolean());
        assertTrue(contract.get("applyLeavesSourceUnchanged").getAsBoolean());
        assertTrue(contract.get("applyRemapsExplicitLinks").getAsBoolean());
        assertTrue(contract.get("reorderMovesDependencyGroupsAtomically").getAsBoolean());
        assertTrue(contract.get("clearOccursAfterAcknowledgedSuccessOnly").getAsBoolean());
    }

    private void assertNoForbiddenDataKeys(JsonElement element) {
        if (element.isJsonArray()) {
            for (JsonElement item : element.getAsJsonArray()) {
                assertNoForbiddenDataKeys(item);
            }
            return;
        }
        if (!element.isJsonObject()) {
            return;
        }
        for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
            assertFalse(
                    FORBIDDEN_DATA_KEYS.contains(entry.getKey()),
                    () -> "Golden fixture must not contain customer data field: " + entry.getKey());
            assertNoForbiddenDataKeys(entry.getValue());
        }
    }

    private void assertClosedFixture(JsonObject fixture) {
        JsonObject workspace = fixture.getAsJsonObject("workspace");
        assertTrue(workspace.get("organizationId").getAsInt() >= 9000);
        assertTrue(workspace.get("ownerId").getAsInt() >= 1000);

        Set<Integer> blockIds = ids(fixture.getAsJsonArray("blocks"));
        Set<Integer> instructionIds = ids(fixture.getAsJsonArray("instructions"));
        Set<Integer> variableIds = ids(fixture.getAsJsonArray("variables"));
        Set<Integer> referenceIds = ids(fixture.getAsJsonArray("references"));

        assertEquals(fixture.getAsJsonArray("blocks").size(), blockIds.size());
        assertEquals(fixture.getAsJsonArray("instructions").size(), instructionIds.size());
        assertEquals(fixture.getAsJsonArray("variables").size(), variableIds.size());
        assertEquals(fixture.getAsJsonArray("references").size(), referenceIds.size());

        Map<Integer, Set<Integer>> ordersByBlock = new HashMap<>();
        for (JsonElement element : fixture.getAsJsonArray("instructions")) {
            JsonObject instruction = element.getAsJsonObject();
            int blockId = instruction.get("blockId").getAsInt();
            assertTrue(blockIds.contains(blockId));
            nullableIdTargets(instruction.get("parentId"), instructionIds);
            nullableIdTargets(instruction.get("parentBlockId"), blockIds);
            nullableIdTargets(instruction.get("variableId"), variableIds);
            assertTrue(ordersByBlock
                    .computeIfAbsent(blockId, ignored -> new HashSet<>())
                    .add(instruction.get("order").getAsInt()));
        }

        for (JsonElement element : fixture.getAsJsonArray("variables")) {
            nullableIdTargets(element.getAsJsonObject().get("ownerInstructionId"), instructionIds);
        }
        for (JsonElement element : fixture.getAsJsonArray("references")) {
            assertTrue(instructionIds.contains(
                    element.getAsJsonObject().get("instructionId").getAsInt()));
        }

        JsonObject selections = fixture
                .getAsJsonObject("expectations")
                .getAsJsonObject("memorySelections");
        for (Map.Entry<String, JsonElement> selection : selections.entrySet()) {
            assertFalse(selection.getValue().getAsJsonArray().isEmpty());
            for (JsonElement selectedId : selection.getValue().getAsJsonArray()) {
                assertTrue(
                        instructionIds.contains(selectedId.getAsInt()),
                        () -> fixture.get("name").getAsString()
                                + " selection "
                                + selection.getKey()
                                + " references an unknown instruction");
            }
        }
    }

    private Set<Integer> ids(JsonArray rows) {
        Set<Integer> ids = new HashSet<>();
        for (JsonElement element : rows) {
            ids.add(element.getAsJsonObject().get("id").getAsInt());
        }
        return ids;
    }

    private void nullableIdTargets(JsonElement candidate, Set<Integer> validIds) {
        if (candidate != null && !candidate.isJsonNull()) {
            assertTrue(validIds.contains(candidate.getAsInt()));
        }
    }
}
