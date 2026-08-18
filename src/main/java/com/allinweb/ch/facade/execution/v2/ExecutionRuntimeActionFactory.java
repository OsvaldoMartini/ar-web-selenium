package com.allinweb.ch.facade.execution.v2;

import com.allinweb.ch.facade.CommandRegistry;
import com.allinweb.ch.facade.RuntimeElementHealingService.Preparation;
import com.allinweb.ch.facade.RuntimeElementHealingService.RegistryCandidate;
import com.allinweb.ch.facade.TestIdLocatorContract;
import com.allinweb.ch.facade.execution.SmokeTestIntegrationSnapshotRepository.InstructionSnapshot;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Builds one strict Node action DTO exclusively from server-frozen execution facts. */
public final class ExecutionRuntimeActionFactory {
    private static final int MAX_SELECTORS = 32;
    private static final int MAX_CANDIDATES = 100;
    private static final int MAX_SELECTOR_LENGTH = 2_048;
    private static final int MAX_NAME_LENGTH = 512;
    private static final int MAX_INPUT_LENGTH = 1_048_576;

    public JsonObject create(
            long sequence,
            InstructionSnapshot instruction,
            Preparation preparation,
            String inputValue) {
        return createDelegated(
                sequence,
                instruction.id(),
                physicalAction(instruction.action()),
                instruction,
                preparation,
                inputValue);
    }

    JsonObject createDelegated(
            long sequence,
            int requestInstructionId,
            String requestedAction,
            InstructionSnapshot target,
            Preparation preparation,
            String inputValue) {
        Objects.requireNonNull(target, "Frozen target instruction is required");
        Objects.requireNonNull(preparation, "Runtime healing preparation is required");
        if (requestInstructionId <= 0
                || sequence <= 0
                || sequence > ExecutionV2Contracts.MAX_JAVASCRIPT_SAFE_INTEGER) {
            throw new IllegalArgumentException("Execution V2 action sequence is invalid");
        }
        if (!preparation.ready()
                || preparation.homeBankingId() != target.owner().homeBankingId()
                || preparation.botJobId() != target.owner().botJobId()) {
            throw new IllegalArgumentException("Execution V2 action owner preparation is invalid");
        }

        String action = normalizedPhysicalAction(requestedAction);
        if ("INPUT".equals(action)) {
            if (inputValue == null || inputValue.length() > MAX_INPUT_LENGTH) {
                throw new IllegalArgumentException("Execution V2 input value is invalid");
            }
        } else if (inputValue != null) {
            throw new IllegalArgumentException("Execution V2 non-input action has an input value");
        }

        JsonObject request = new JsonObject();
        request.addProperty("instructionId", requestInstructionId);
        request.addProperty("sequence", sequence);
        request.addProperty("action", action);
        request.addProperty("pageKey", preparation.pageKey());
        request.add("authoredSelectors", selectorsWithTestIds(
                TestIdLocatorContract.selectorsFromSnapshots(target.references()),
                target.xpath(), target.cssSelector()));
        request.add("registryCandidates", candidates(preparation));
        optional(request, "canonicalName", target.name(), MAX_NAME_LENGTH);
        optional(request, "clientName", target.clientNamed(), MAX_NAME_LENGTH);
        optional(request, "expectedTag", normalizedTag(target.tagName()), 32);
        optional(request, "iframeXPath", target.iframeXpath(), MAX_SELECTOR_LENGTH);
        optional(request, "shadowHost", target.shadowHost(), MAX_SELECTOR_LENGTH);
        optional(request, "shadowRoot", target.shadowRoot(), MAX_SELECTOR_LENGTH);
        if ("INPUT".equals(action)) {
            request.addProperty("inputValue", inputValue);
            String flags = value(target.forceCoordinates()).toUpperCase(Locale.ROOT);
            request.addProperty("pressEnter", flags.contains("E"));
            request.addProperty("pressTab", flags.contains("T"));
        }
        return request;
    }

    JsonObject createRecovery(
            long sequence,
            JsonObject original,
            JsonObject candidate) {
        String action = original != null && original.has("action")
                ? original.get("action").getAsString() : "";
        String input = original != null && original.has("inputValue")
                ? original.get("inputValue").getAsString() : null;
        return createRecovery(sequence, original, candidate, action, input);
    }

    JsonObject createRecovery(
            long sequence,
            JsonObject original,
            JsonObject candidate,
            String requestedAction,
            String inputValue) {
        if (sequence <= 0 || sequence > ExecutionV2Contracts.MAX_JAVASCRIPT_SAFE_INTEGER
                || original == null || candidate == null) {
            throw new IllegalArgumentException("Execution V2 recovery request is invalid");
        }
        String xpath = candidate.has("newXPath") ? candidate.get("newXPath").getAsString() : "";
        String css = candidate.has("newCss") ? candidate.get("newCss").getAsString() : "";
        JsonArray selectors = selectorsWithTestIds(
                TestIdLocatorContract.selectors(candidateAttributes(candidate)), xpath, css);
        if (selectors.isEmpty()) {
            throw new IllegalArgumentException("Execution V2 recovery candidate has no locator");
        }
        JsonObject request = original.deepCopy();
        request.addProperty("sequence", sequence);
        String action = normalizedPhysicalAction(requestedAction);
        request.addProperty("action", action);
        if ("INPUT".equals(action)) {
            if (inputValue == null || inputValue.length() > MAX_INPUT_LENGTH) {
                throw new IllegalArgumentException("Execution V2 recovery input value is invalid");
            }
            request.addProperty("inputValue", inputValue);
        } else {
            request.remove("inputValue");
            request.remove("pressEnter");
            request.remove("pressTab");
        }
        request.add("authoredSelectors", selectors);
        request.add("registryCandidates", new JsonArray());
        if (candidate.has("tag")) {
            optional(request, "expectedTag", normalizedTag(candidate.get("tag").getAsString()), 32);
        }
        return request;
    }

    private static String normalizedPhysicalAction(String raw) {
        String action = value(raw).trim().toUpperCase(Locale.ROOT);
        if (!Set.of("CLICK", "INPUT", "OUTPUT").contains(action)) {
            throw new IllegalArgumentException("Execution V2 delegated action is invalid");
        }
        return action;
    }

    private static String physicalAction(String raw) {
        String action = CommandRegistry.canonicalize(raw);
        return switch (action) {
            case "C" -> "CLICK";
            case "I" -> "INPUT";
            case "O" -> "OUTPUT";
            default -> throw new IllegalArgumentException(
                    "The frozen instruction is not a V2 physical action");
        };
    }

    private static JsonArray candidates(Preparation preparation) {
        int count = preparation.registryCandidateCount();
        if (count > MAX_CANDIDATES) {
            throw new IllegalArgumentException("Execution V2 registry candidate limit exceeded");
        }
        JsonArray result = new JsonArray();
        addCandidates(result, preparation.locatorCandidates(), "LOCATOR");
        addCandidates(result, preparation.canonicalCandidates(), "CANONICAL");
        addCandidates(result, preparation.aliasCandidates(), "ALIAS");
        addCandidates(result, preparation.reviewCandidates(), "REVIEW");
        return result;
    }

    private static void addCandidates(
            JsonArray target, List<RegistryCandidate> candidates, String tier) {
        for (RegistryCandidate candidate : candidates) {
            JsonObject value = new JsonObject();
            value.addProperty("candidateId", candidate.scannedElementId());
            value.addProperty("tier", tier);
            value.add("selectors", selectorsWithTestIds(
                    TestIdLocatorContract.selectors(candidate.attributes()),
                    candidate.customXPath(), candidate.xpath(), candidate.cssSelector()));
            optional(value, "expectedTag", normalizedTag(candidate.tagName()), 32);
            optional(value, "iframeXPath", candidate.iframeXpath(), MAX_SELECTOR_LENGTH);
            optional(value, "shadowHost", candidate.shadowHost(), MAX_SELECTOR_LENGTH);
            optional(value, "shadowRoot", candidate.shadowRoot(), MAX_SELECTOR_LENGTH);
            optional(value, "canonicalName", candidate.canonicalName(), MAX_NAME_LENGTH);
            optional(value, "clientName", candidate.clientName(), MAX_NAME_LENGTH);
            optional(value, "ocrName", candidate.ocrName(), MAX_NAME_LENGTH);
            optional(value, "previousPageKey", candidate.pageKey(), 71);
            optional(value, "xpath", candidate.xpath(), MAX_SELECTOR_LENGTH);
            optional(value, "customXPath", candidate.customXPath(), MAX_SELECTOR_LENGTH);
            optional(value, "cssSelector", candidate.cssSelector(), MAX_SELECTOR_LENGTH);
            optional(value, "expectedType", candidate.typeElement(), 80);
            String role = candidate.attributes().get("role");
            optional(value, "expectedRole", role, 80);
            JsonObject attributes = new JsonObject();
            candidate.attributes().forEach(attributes::addProperty);
            value.add("stableAttributes", attributes);
            target.add(value);
        }
    }

    private static JsonArray selectors(String... candidates) {
        Set<String> unique = new LinkedHashSet<>();
        for (String candidate : candidates) {
            String selector = value(candidate).trim();
            if (selector.isEmpty()) continue;
            if (selector.length() > MAX_SELECTOR_LENGTH) {
                throw new IllegalArgumentException("Execution V2 selector is too long");
            }
            unique.add(selector);
        }
        if (unique.size() > MAX_SELECTORS) {
            throw new IllegalArgumentException("Execution V2 selector limit exceeded");
        }
        JsonArray result = new JsonArray();
        unique.forEach(result::add);
        return result;
    }

    private static JsonArray selectorsWithTestIds(List<String> testIds, String... candidates) {
        List<String> values = new java.util.ArrayList<>(testIds == null ? List.of() : testIds);
        values.addAll(List.of(candidates));
        return selectors(values.toArray(String[]::new));
    }

    private static Map<String, String> candidateAttributes(JsonObject candidate) {
        if (!candidate.has("newStableAttributes") || !candidate.get("newStableAttributes").isJsonObject()) {
            return Map.of();
        }
        Map<String, String> result = new LinkedHashMap<>();
        candidate.getAsJsonObject("newStableAttributes").entrySet().forEach(entry -> {
            if (entry.getValue().isJsonPrimitive() && entry.getValue().getAsJsonPrimitive().isString()) {
                result.put(entry.getKey(), entry.getValue().getAsString());
            }
        });
        return result;
    }

    private static String normalizedTag(String raw) {
        String tag = value(raw).trim().toLowerCase(Locale.ROOT);
        if (!tag.isEmpty() && !tag.matches("[a-z][a-z0-9-]{0,31}")) {
            throw new IllegalArgumentException("Execution V2 expected tag is invalid");
        }
        return tag;
    }

    private static void optional(JsonObject target, String name, String raw, int maximum) {
        String value = value(raw).trim();
        if (value.isEmpty()) return;
        if (value.length() > maximum) {
            throw new IllegalArgumentException("Execution V2 " + name + " is too long");
        }
        target.addProperty(name, value);
    }

    private static String value(String value) {
        return value == null ? "" : value;
    }
}
