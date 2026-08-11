package com.allinweb.ch.facade.execution.v2;

import com.allinweb.ch.facade.CommandRegistry;
import com.allinweb.ch.facade.RuntimeElementHealingService.Preparation;
import com.allinweb.ch.facade.RuntimeElementHealingService.RegistryCandidate;
import com.allinweb.ch.facade.execution.SmokeTestIntegrationSnapshotRepository.InstructionSnapshot;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
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
        Objects.requireNonNull(instruction, "Frozen instruction is required");
        Objects.requireNonNull(preparation, "Runtime healing preparation is required");
        if (sequence <= 0 || sequence > ExecutionV2Contracts.MAX_JAVASCRIPT_SAFE_INTEGER) {
            throw new IllegalArgumentException("Execution V2 action sequence is invalid");
        }
        if (!preparation.ready()
                || preparation.homeBankingId() != instruction.owner().homeBankingId()
                || preparation.botJobId() != instruction.owner().botJobId()) {
            throw new IllegalArgumentException("Execution V2 action owner preparation is invalid");
        }

        String action = physicalAction(instruction.action());
        if ("INPUT".equals(action)) {
            if (inputValue == null || inputValue.length() > MAX_INPUT_LENGTH) {
                throw new IllegalArgumentException("Execution V2 input value is invalid");
            }
        } else if (inputValue != null) {
            throw new IllegalArgumentException("Execution V2 non-input action has an input value");
        }

        JsonObject request = new JsonObject();
        request.addProperty("instructionId", instruction.id());
        request.addProperty("sequence", sequence);
        request.addProperty("action", action);
        request.addProperty("pageKey", preparation.pageKey());
        request.add("authoredSelectors", selectors(
                instruction.xpath(), instruction.cssSelector()));
        request.add("registryCandidates", candidates(preparation));
        optional(request, "canonicalName", instruction.name(), MAX_NAME_LENGTH);
        optional(request, "clientName", instruction.clientNamed(), MAX_NAME_LENGTH);
        optional(request, "expectedTag", normalizedTag(instruction.tagName()), 32);
        optional(request, "iframeXPath", instruction.iframeXpath(), MAX_SELECTOR_LENGTH);
        optional(request, "shadowHost", instruction.shadowHost(), MAX_SELECTOR_LENGTH);
        optional(request, "shadowRoot", instruction.shadowRoot(), MAX_SELECTOR_LENGTH);
        if ("INPUT".equals(action)) {
            request.addProperty("inputValue", inputValue);
            String flags = value(instruction.forceCoordinates()).toUpperCase(Locale.ROOT);
            request.addProperty("pressEnter", flags.contains("E"));
            request.addProperty("pressTab", flags.contains("T"));
        }
        return request;
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
        return result;
    }

    private static void addCandidates(
            JsonArray target, List<RegistryCandidate> candidates, String tier) {
        for (RegistryCandidate candidate : candidates) {
            JsonObject value = new JsonObject();
            value.addProperty("candidateId", candidate.scannedElementId());
            value.addProperty("tier", tier);
            value.add("selectors", selectors(
                    candidate.customXPath(), candidate.xpath(), candidate.cssSelector()));
            optional(value, "expectedTag", normalizedTag(candidate.tagName()), 32);
            optional(value, "iframeXPath", candidate.iframeXpath(), MAX_SELECTOR_LENGTH);
            optional(value, "shadowHost", candidate.shadowHost(), MAX_SELECTOR_LENGTH);
            optional(value, "shadowRoot", candidate.shadowRoot(), MAX_SELECTOR_LENGTH);
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
