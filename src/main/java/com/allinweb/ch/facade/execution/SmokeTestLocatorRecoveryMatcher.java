package com.allinweb.ch.facade.execution;

import com.allinweb.ch.facade.RuntimeElementHealingService.Preparation;
import com.allinweb.ch.facade.RuntimeElementHealingService.RegistryCandidate;
import com.allinweb.ch.model.AttributeData;
import com.allinweb.ch.model.ElementDTO;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Compares one frozen owner/page registry with the fresh result of the normal Page Scanner. */
public final class SmokeTestLocatorRecoveryMatcher {
    private static final Gson JSON = new Gson();
    private static final int MAX_REGISTRY = 100;
    private static final int MAX_SCANNED = 2_000;
    private static final int MAX_RESULTS = 25;
    private static final Set<String> STABLE_ATTRIBUTES = Set.of(
            "id", "name", "role", "type", "title", "placeholder",
            "aria-label", "aria-labelledby", "data-testid", "data-test", "data-qa");

    private SmokeTestLocatorRecoveryMatcher() {}

    public static JsonArray match(
            Preparation frozen,
            String instructionName,
            String instructionClientName,
            String expectedAction,
            String currentPageKey,
            List<ElementDTO> scannedElements) {
        JsonArray result = new JsonArray();
        if (frozen == null || !frozen.ready() || currentPageKey == null
                || currentPageKey.isBlank() || scannedElements == null) {
            return result;
        }
        Map<Long, RegistryCandidate> registry = new LinkedHashMap<>();
        for (List<RegistryCandidate> tier : List.of(
                frozen.locatorCandidates(), frozen.canonicalCandidates(),
                frozen.aliasCandidates(), frozen.reviewCandidates())) {
            for (RegistryCandidate candidate : tier) {
                registry.putIfAbsent(candidate.scannedElementId(), candidate);
                if (registry.size() > MAX_REGISTRY) return result;
            }
        }
        List<Scored> matches = new ArrayList<>();
        int scannedCount = Math.min(scannedElements.size(), MAX_SCANNED);
        for (int index = 0; index < scannedCount; index++) {
            ElementDTO live = scannedElements.get(index);
            if (live == null || (text(live.getXPath()).isBlank()
                    && text(live.getCssSelector()).isBlank())) continue;
            for (RegistryCandidate saved : registry.values()) {
                Score score = score(saved, live, instructionName, instructionClientName);
                if (score.confidence < .35d) continue;
                JsonObject row = row(saved, live, expectedAction, currentPageKey, score);
                matches.add(new Scored(score.confidence, saved.scannedElementId(), row));
            }
        }
        matches.stream()
                .sorted(Comparator.comparingDouble(Scored::confidence).reversed()
                        .thenComparingLong(Scored::registryId)
                        .thenComparing(value -> value.row().get("recoveryCandidateId").getAsString()))
                .limit(MAX_RESULTS)
                .forEach(value -> result.add(value.row()));
        return result;
    }

    private static Score score(
            RegistryCandidate saved,
            ElementDTO live,
            String instructionName,
            String instructionClientName) {
        Set<String> expectedNames = normalizedNames(
                saved.clientName(), saved.canonicalName(), saved.ocrName(),
                instructionClientName, instructionName);
        Set<String> liveNames = normalizedNames(
                live.getClientNamed(), live.getDefinedName(), live.getSomeText(),
                live.getNameLabel(), live.getNameField(), attribute(live, "scanned-text"));
        boolean exactName = expectedNames.stream().anyMatch(liveNames::contains);
        boolean partialName = expectedNames.stream().anyMatch(left -> liveNames.stream().anyMatch(right ->
                left.length() >= 4 && right.length() >= 4
                        && (left.contains(right) || right.contains(left))));
        boolean tagMatch = text(saved.tagName()).isBlank()
                || normalize(saved.tagName()).equals(normalize(live.getTagName()));
        boolean typeMatch = text(saved.typeElement()).isBlank()
                || normalize(saved.typeElement()).equals(normalize(live.getTypeElement()));
        String savedRole = text(saved.attributes().get("role"));
        boolean roleMatch = savedRole.isBlank()
                || normalize(savedRole).equals(normalize(attribute(live, "role")));
        Boolean stableMatch = stableMatch(saved.attributes(), stableAttributes(live));
        double confidence = 0d;
        List<String> reasons = new ArrayList<>();
        if (exactName) { confidence += .55d; reasons.add("Exact saved/OCR name match"); }
        else if (partialName) { confidence += .30d; reasons.add("Partial normalized name match"); }
        if (!text(saved.tagName()).isBlank() && tagMatch) { confidence += .15d; reasons.add("Compatible tag"); }
        if (!text(saved.typeElement()).isBlank() && typeMatch) { confidence += .10d; reasons.add("Compatible type"); }
        if (!savedRole.isBlank() && roleMatch) { confidence += .10d; reasons.add("Compatible role"); }
        if (Boolean.TRUE.equals(stableMatch)) { confidence += .10d; reasons.add("Stable attribute match"); }
        List<String> warnings = new ArrayList<>();
        if (!exactName) warnings.add("Name is not an exact match");
        if (!tagMatch || !typeMatch || !roleMatch) warnings.add("Element semantics changed");
        return new Score(Math.min(1d, Math.round(confidence * 100d) / 100d),
                List.copyOf(reasons), List.copyOf(warnings), stableMatch);
    }

    private static JsonObject row(
            RegistryCandidate saved,
            ElementDTO live,
            String expectedAction,
            String currentPageKey,
            Score score) {
        Map<String, String> liveAttributes = stableAttributes(live);
        JsonObject value = new JsonObject();
        String basis = "CURRENT\0" + saved.scannedElementId() + "\0" + text(live.getXPath()) + "\0"
                + text(live.getCssSelector()) + "\0" + JSON.toJson(liveAttributes);
        value.addProperty("origin", "CURRENT");
        value.addProperty("recoveryCandidateId", sha256(basis));
        value.addProperty("registryCandidateId", saved.scannedElementId());
        value.addProperty("savedCanonicalName", text(saved.canonicalName()));
        value.addProperty("savedClientName", text(saved.clientName()));
        value.addProperty("ocrMappedName", firstNonBlank(
                live.getSomeText(), live.getNameLabel(), live.getDefinedName(), saved.ocrName()));
        value.addProperty("previousXPath", text(saved.xpath()));
        value.addProperty("previousCustomXPath", text(saved.customXPath()));
        value.addProperty("previousCss", text(saved.cssSelector()));
        value.add("previousStableAttributes", JSON.toJsonTree(saved.attributes()));
        value.addProperty("newXPath", text(live.getXPath()));
        value.addProperty("newCss", text(live.getCssSelector()));
        value.add("newStableAttributes", JSON.toJsonTree(liveAttributes));
        value.addProperty("previousPageIdentity",
                text(saved.pageKey()).isBlank() ? currentPageKey : saved.pageKey());
        value.addProperty("currentPageIdentity", currentPageKey);
        value.addProperty("tag", text(live.getTagName()));
        value.addProperty("type", text(live.getTypeElement()));
        value.addProperty("role", attribute(live, "role"));
        value.addProperty("expectedAction", normalizedAction(expectedAction));
        value.addProperty("confidence", score.confidence);
        value.add("reasons", JSON.toJsonTree(score.reasons));
        value.add("ambiguityWarnings", JSON.toJsonTree(score.warnings));
        JsonObject locatorMatches = new JsonObject();
        locatorMatches.addProperty("xpath", sameLocator(saved.xpath(), live.getXPath()));
        locatorMatches.addProperty("customXPath", sameLocator(saved.customXPath(), live.getXPath()));
        locatorMatches.addProperty("css", sameLocator(saved.cssSelector(), live.getCssSelector()));
        if (score.stableMatch == null) locatorMatches.add("stableAttributes", null);
        else locatorMatches.addProperty("stableAttributes", score.stableMatch);
        locatorMatches.addProperty("frame", sameScope(saved.iframeXpath(), live.getIFrameXPath()));
        locatorMatches.addProperty("shadow", sameScope(
                text(saved.shadowHost()) + "\0" + text(saved.shadowRoot()),
                text(live.getShadowHost()) + "\0" + text(live.getShadowRoot())));
        value.add("matches", locatorMatches);
        return value;
    }

    private static Set<String> normalizedNames(String... values) {
        Set<String> result = new LinkedHashSet<>();
        for (String value : values) {
            String normalized = normalize(value);
            if (!normalized.isBlank()) result.add(normalized);
        }
        return result;
    }

    private static Map<String, String> stableAttributes(ElementDTO element) {
        Map<String, String> values = new LinkedHashMap<>();
        if (!text(element.getAttribId()).isBlank()) values.put("id", element.getAttribId().trim());
        if (!text(element.getAttribName()).isBlank()) values.put("name", element.getAttribName().trim());
        if (element.getAttributeData() != null) {
            for (AttributeData attribute : element.getAttributeData()) {
                if (attribute == null || attribute.getName() == null || attribute.getValue() == null) continue;
                String key = attribute.getName().trim().toLowerCase(Locale.ROOT);
                String value = attribute.getValue().trim();
                if (STABLE_ATTRIBUTES.contains(key) && !value.isEmpty() && value.length() <= 8_192) {
                    values.putIfAbsent(key, value);
                }
            }
        }
        return Map.copyOf(values);
    }

    private static String attribute(ElementDTO element, String name) {
        if (element == null || element.getAttributeData() == null) return "";
        for (AttributeData attribute : element.getAttributeData()) {
            if (attribute != null && name.equalsIgnoreCase(attribute.getName())) {
                return text(attribute.getValue());
            }
        }
        return "";
    }

    private static Boolean stableMatch(Map<String, String> previous, Map<String, String> current) {
        if (previous == null || previous.isEmpty()) return null;
        boolean compared = false;
        for (Map.Entry<String, String> entry : previous.entrySet()) {
            String currentValue = current.get(entry.getKey());
            if (currentValue == null) continue;
            compared = true;
            if (!normalize(entry.getValue()).equals(normalize(currentValue))) return false;
        }
        return compared ? Boolean.TRUE : null;
    }

    private static boolean sameLocator(String left, String right) {
        String a = text(left).trim();
        String b = text(right).trim();
        return !a.isEmpty() && a.equals(b);
    }

    private static boolean sameScope(String left, String right) {
        return text(left).trim().equals(text(right).trim());
    }

    private static String normalizedAction(String value) {
        String action = text(value).trim().toUpperCase(Locale.ROOT);
        return Set.of("CLICK", "INPUT", "OUTPUT").contains(action) ? action : "CLICK";
    }

    private static String normalize(String value) {
        return text(value).toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) return value.trim();
        }
        return "";
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private record Score(double confidence, List<String> reasons, List<String> warnings, Boolean stableMatch) {}
    private record Scored(double confidence, long registryId, JsonObject row) {}
}
