package com.allinweb.ch.facade.execution.v2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.allinweb.ch.facade.RuntimeElementHealingService.Preparation;
import com.allinweb.ch.facade.RuntimeElementHealingService.RegistryCandidate;
import com.allinweb.ch.facade.RuntimeElementHealingService.Status;
import com.allinweb.ch.facade.execution.SmokeTestIntegrationSnapshotRepository.BlockSnapshot;
import com.allinweb.ch.facade.execution.SmokeTestIntegrationSnapshotRepository.InstructionSnapshot;
import com.allinweb.ch.facade.execution.SmokeTestIntegrationSnapshotRepository.Owner;
import com.allinweb.ch.facade.execution.SmokeTestIntegrationSnapshotRepository.ReferenceSnapshot;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ExecutionRuntimeActionFactoryTest {
    private static final String PAGE_KEY = "url-v1:" + "a".repeat(64);
    private final ExecutionRuntimeActionFactory factory = new ExecutionRuntimeActionFactory();

    @Test
    void buildsStrictInputFactsFromFrozenInstructionAndOwnerRegistry() {
        InstructionSnapshot instruction = instruction("I", "ET", "input");
        RegistryCandidate candidate = new RegistryCandidate(
                41L,
                "input",
                "text",
                "//*[@id='login']",
                "//*[@data-testid='login']",
                "input#login",
                "login",
                "username",
                "",
                "",
                "",
                "",
                Map.of());
        Preparation preparation = new Preparation(
                Status.READY, 13, 29, PAGE_KEY, List.of(candidate), List.of(), List.of());

        JsonObject request = factory.create(1L, instruction, preparation, "secret-value");

        assertEquals("INPUT", request.get("action").getAsString());
        assertEquals(PAGE_KEY, request.get("pageKey").getAsString());
        assertEquals(2, request.getAsJsonArray("authoredSelectors").size());
        assertEquals(1, request.getAsJsonArray("registryCandidates").size());
        JsonObject registry = request.getAsJsonArray("registryCandidates").get(0).getAsJsonObject();
        assertEquals("LOCATOR", registry.get("tier").getAsString());
        assertEquals(3, registry.getAsJsonArray("selectors").size());
        assertEquals("secret-value", request.get("inputValue").getAsString());
        assertEquals(true, request.get("pressEnter").getAsBoolean());
        assertEquals(true, request.get("pressTab").getAsBoolean());
        assertFalse(request.toString().contains("username"));
    }

    @Test
    void rejectsOwnerMismatchAndCandidateOverflowBeforeRuntimeSubmission() {
        InstructionSnapshot click = instruction("C", "", "button");
        Preparation wrongOwner = new Preparation(
                Status.READY, 2, 29, PAGE_KEY, List.of(), List.of(), List.of());
        assertThrows(
                IllegalArgumentException.class,
                () -> factory.create(1L, click, wrongOwner, null));

        List<RegistryCandidate> candidates = new ArrayList<>();
        for (int index = 1; index <= 101; index++) {
            candidates.add(new RegistryCandidate(
                    index, "button", "button", "//button[" + index + "]", "", "", "", "",
                    "", "", "", "", Map.of()));
        }
        Preparation overflow = new Preparation(
                Status.READY, 13, 29, PAGE_KEY, candidates, List.of(), List.of());
        assertThrows(
                IllegalArgumentException.class,
                () -> factory.create(1L, click, overflow, null));
    }

    @Test
    void sendsStandardAndClientTestIdsBeforeXpathForAuthoredAndRegistryTargets() {
        InstructionSnapshot instruction = instruction(
                "C", "", "button", List.of(
                        new ReferenceSnapshot(1, 1733, "AttrData:automation.test-id.attribute", "qa-hook"),
                        new ReferenceSnapshot(2, 1733, "AttrData:qa-hook", "continue")));
        RegistryCandidate candidate = new RegistryCandidate(
                41L, "button", "button", "//button[2]", "//button[@name='next']", "button.next",
                "", "", "", "", "", "", Map.of(
                        "data-testid", "registry-next"));
        JsonObject request = factory.create(1L, instruction, new Preparation(
                Status.READY, 13, 29, PAGE_KEY, List.of(candidate), List.of(), List.of()), null);

        assertEquals("[qa-hook=\"continue\"]", request.getAsJsonArray("authoredSelectors").get(0).getAsString());
        assertEquals("//*[@id='login']", request.getAsJsonArray("authoredSelectors").get(1).getAsString());
        JsonObject registry = request.getAsJsonArray("registryCandidates").get(0).getAsJsonObject();
        assertEquals("[data-testid=\"registry-next\"]", registry.getAsJsonArray("selectors").get(0).getAsString());
        assertEquals("//button[@name='next']", registry.getAsJsonArray("selectors").get(1).getAsString());
    }

    @Test
    void recoveryPreservesConfiguredTestIdBeforeFreshXpathAndCss() {
        JsonObject original = factory.create(
                1L,
                instruction("I", "ET", "input"),
                new Preparation(Status.READY, 13, 29, PAGE_KEY, List.of(), List.of(), List.of()),
                "temporary-value");
        JsonObject candidate = new JsonObject();
        candidate.addProperty("newXPath", "//button[@id='fresh']");
        candidate.addProperty("newCss", "button#fresh");
        candidate.addProperty("tag", "button");
        JsonObject attributes = new JsonObject();
        attributes.addProperty("automation.test-id.attribute", "qa-hook");
        attributes.addProperty("qa-hook", "fresh-next");
        attributes.addProperty("title", "not-a-test-id");
        candidate.add("newStableAttributes", attributes);

        JsonObject request = factory.createRecovery(2L, original, candidate, "CLICK", null);

        assertEquals("CLICK", request.get("action").getAsString());
        assertFalse(request.has("inputValue"));
        assertFalse(request.has("pressEnter"));
        assertFalse(request.has("pressTab"));
        assertEquals("[qa-hook=\"fresh-next\"]",
                request.getAsJsonArray("authoredSelectors").get(0).getAsString());
        assertEquals("//button[@id='fresh']",
                request.getAsJsonArray("authoredSelectors").get(1).getAsString());
        assertEquals("button#fresh",
                request.getAsJsonArray("authoredSelectors").get(2).getAsString());
        assertEquals(0, request.getAsJsonArray("registryCandidates").size());
    }

    private static InstructionSnapshot instruction(
            String action, String forceCoordinates, String tagName) {
        return instruction(action, forceCoordinates, tagName, List.of());
    }

    private static InstructionSnapshot instruction(
            String action, String forceCoordinates, String tagName, List<ReferenceSnapshot> references) {
        Owner owner = new Owner(13, 29);
        BlockSnapshot block = new BlockSnapshot(7, 1, "Login", "", null, "", true, 0);
        return new InstructionSnapshot(
                owner,
                "Lloyds",
                "",
                block,
                1733,
                1,
                action,
                "log_in",
                "Login",
                "",
                "//*[@id='login']",
                "",
                forceCoordinates,
                "",
                tagName,
                "",
                "",
                "input#login",
                "",
                "",
                false,
                false,
                null,
                null,
                false,
                false,
                true,
                null,
                null,
                references,
                Map.of());
    }
}
