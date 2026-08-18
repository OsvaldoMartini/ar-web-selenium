package com.allinweb.ch.facade;

import com.allinweb.ch.facade.execution.SmokeTestIntegrationSnapshotRepository.ReferenceSnapshot;
import com.allinweb.ch.model.ReferenceLoadDTO;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** One bounded contract for standard and client-configured automation Test ID attributes. */
public final class TestIdLocatorContract {
    public static final String ATTRIBUTE_NAME_METADATA = "automation.test-id.attribute";
    private static final List<String> STANDARD =
            List.of("data-testid", "data-test-id", "test-id", "data-cy", "data-qa");

    private TestIdLocatorContract() {}

    public static List<String> selectorsFromReferences(List<ReferenceLoadDTO> references) {
        Map<String, String> values = new LinkedHashMap<>();
        if (references != null) for (ReferenceLoadDTO reference : references) {
            if (reference != null) addReference(values, reference.getReferenceType(), reference.getValue());
        }
        return selectors(values);
    }

    public static List<String> selectorsFromSnapshots(List<ReferenceSnapshot> references) {
        Map<String, String> values = new LinkedHashMap<>();
        if (references != null) for (ReferenceSnapshot reference : references) {
            if (reference != null) addReference(values, reference.type(), reference.value());
        }
        return selectors(values);
    }

    public static List<String> selectors(Map<String, String> attributes) {
        Map<String, String> normalized = normalized(attributes);
        Set<String> names = new LinkedHashSet<>(STANDARD);
        String configured = normalized.get(ATTRIBUTE_NAME_METADATA);
        if (isSafeAttributeName(configured)) names.add(configured);
        List<String> selectors = new ArrayList<>();
        for (String name : names) {
            String value = normalized.get(name);
            if (value != null) {
                selectors.add("[" + name + "=\"" + cssAttribute(value.trim()) + "\"]");
            }
        }
        return List.copyOf(selectors);
    }

    public static Map<String, String> testIdValues(Map<String, String> attributes) {
        Map<String, String> normalized = normalized(attributes);
        Map<String, String> result = new LinkedHashMap<>();
        for (String name : STANDARD) addValue(result, name, normalized.get(name));
        String configured = normalized.get(ATTRIBUTE_NAME_METADATA);
        if (isSafeAttributeName(configured)) addValue(result, configured, normalized.get(configured));
        return Map.copyOf(result);
    }

    public static boolean isStandard(String name) {
        return name != null && STANDARD.contains(name.trim().toLowerCase(Locale.ROOT));
    }

    public static boolean isSafeAttributeName(String name) {
        return name != null && name.matches("[a-z_:][a-z0-9_.:-]{0,127}");
    }

    private static void addReference(Map<String, String> values, String rawType, String rawValue) {
        if (rawType == null || rawValue == null || rawValue.isBlank()) return;
        String type = rawType.trim().toLowerCase(Locale.ROOT);
        String name = type.startsWith("attrdata:") ? type.substring("attrdata:".length()) : type;
        if ((type.startsWith("attrdata:") && isSafeAttributeName(name))
                || isStandard(name) || ATTRIBUTE_NAME_METADATA.equals(name)) {
            values.putIfAbsent(name, rawValue.trim());
        }
    }

    private static Map<String, String> normalized(Map<String, String> attributes) {
        Map<String, String> result = new LinkedHashMap<>();
        if (attributes == null) return result;
        attributes.forEach((rawName, rawValue) -> {
            if (rawName == null || rawValue == null || rawValue.isBlank()) return;
            String name = rawName.trim().toLowerCase(Locale.ROOT);
            if (isSafeAttributeName(name)) result.putIfAbsent(name, rawValue.trim());
        });
        return result;
    }

    private static void addValue(Map<String, String> target, String name, String value) {
        if (value != null) target.putIfAbsent(name, value.trim());
    }

    private static String cssAttribute(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
