package com.allinweb.ch.facade;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Canonical Java-owned definitions for commands exposed to the React editor. */
public final class CommandRegistry {
    private static final Map<String, String> ALIASES = Map.of(
            "HOLD", "H",
            "SCREEN", "P",
            "QUIT", "Q");
    private static final Set<String> SPECIAL_ROWS = Set.of("ELSEIF", "ELSE", "ENDIF", "NEXT ROW");
    private static final Map<String, Definition> DEFINITIONS = definitions();
    private static final Set<String> EDITOR_TARGETABLE = Set.of(
            "GET", "SET", "CK", "PDF CHECK", "CSV CHECK", "E", "IF", "ELSEIF", "GOTO", "EXCEL GOTO", "LOOP",
            "REFRESH_LOOP", "REFRESH", "NEXT_ENTER", "SWIPE_UP", "SWIPE_DOWN", "H", "PAUSE", "Q", "P");
    private static final Set<String> NO_EDITOR_CONFIGURATION = Set.of(
            "GET", "SET", "EXCEL GOTO", "REFRESH", "NEXT_ENTER", "PAUSE", "Q", "P");
    private static final Set<String> PRODUCES_VARIABLE_VALUE = Set.of("GET", "SET", "E");
    private CommandRegistry() {}

    public static String canonicalize(String action) {
        if (action == null) return "";
        String base = action.split(":", 2)[0].trim().toUpperCase(Locale.ROOT);
        return ALIASES.getOrDefault(base, base);
    }

    public static boolean isSpecialAction(String action) {
        String canonical = canonicalize(action);
        return DEFINITIONS.containsKey(canonical) || SPECIAL_ROWS.contains(canonical);
    }

    /**
     * Instructions whose action is not a registered command or structural row are Web Elements.
     * BACK is an executable neutral command even though it is intentionally absent from the
     * editor catalog.
     */
    public static boolean isWebElementAction(String action) {
        String canonical = canonicalize(action);
        return !isSpecialAction(canonical) && !"BACK".equals(canonical);
    }

    public static boolean isCommand(String action) {
        return DEFINITIONS.containsKey(canonicalize(action));
    }

    public static boolean isEditableCommand(String action) {
        String canonical = canonicalize(action);
        return DEFINITIONS.containsKey(canonical) && !"IF".equals(canonical);
    }

    /** Commands the Command Editor may transform an instruction into. */
    public static boolean isEditorTargetable(String action) {
        return EDITOR_TARGETABLE.contains(canonicalize(action));
    }

    /** Commands with no intrinsic editor configuration (NONE configuration kind on the wire). */
    public static boolean hasNoEditorConfiguration(String action) {
        return NO_EDITOR_CONFIGURATION.contains(canonicalize(action));
    }

    /** Commands whose instruction row carries a variable_id binding. */
    public static boolean usesVariableBinding(String action) {
        return requires(action, "variable");
    }

    /** Commands that produce a runtime variable value (variable producer_instruction_id owners). */
    public static boolean producesVariableValue(String action) {
        return PRODUCES_VARIABLE_VALUE.contains(canonicalize(action));
    }

    /** Client-facing label for a command code ("LOOP" -> "Loop"), or the canonical code itself. */
    public static String label(String action) {
        Definition definition = DEFINITIONS.get(canonicalize(action));
        return definition == null ? canonicalize(action) : definition.label();
    }

    /** A transformed command always carries the canonical client-facing target label. */
    public static String transformedName(String targetAction) {
        return label(targetAction);
    }

    /**
     * Returns whether a GOTO command owns a real cross-block navigation edge.
     *
     * <p>A same-block GOTO still has an ordinary parent family: when that family moves to another
     * block, {@code parent_block_id} must move with it. Keeping this predicate here prevents
     * closure, validation, and persistence from using subtly different action-only checks.
     */
    public static boolean isCrossBlockNavigation(
            String action, Integer parentBlockId, Integer instructionBlockId) {
        String canonical = canonicalize(action);
        return ("GOTO".equals(canonical) || "EXCEL GOTO".equals(canonical))
                && parentBlockId != null
                && instructionBlockId != null
                && !parentBlockId.equals(instructionBlockId);
    }

    public static boolean requires(String action, String field) {
        Definition definition = DEFINITIONS.get(canonicalize(action));
        return definition != null && definition.fields().contains(field);
    }

    public static boolean supportsTag(String action, String tagName) {
        Definition definition = DEFINITIONS.get(canonicalize(action));
        if (definition == null) return false;
        if (definition.allowedTags().isEmpty()) return true;
        String normalized = tagName == null ? "" : tagName.trim().toLowerCase(Locale.ROOT);
        return definition.allowedTags().contains(normalized);
    }

    public static Set<String> allowedVariableTypes(String action) {
        Definition definition = DEFINITIONS.get(canonicalize(action));
        return definition == null ? Set.of() : definition.allowedVariableTypes();
    }

    public static boolean supportsVariableType(String action, String variableType) {
        return variableType != null && allowedVariableTypes(action).contains(variableType);
    }

    public static int defaultHold(String action) {
        return "H".equals(canonicalize(action)) ? 5 : 1;
    }

    public static JsonArray catalog() {
        JsonArray commands = new JsonArray();
        DEFINITIONS.values().forEach(definition -> commands.add(definition.toJson()));
        return commands;
    }

    private static Map<String, Definition> definitions() {
        Map<String, Definition> definitions = new LinkedHashMap<>();
        addWithTags(definitions, "SET", "Set Value", "variable", List.of("input", "select", "textarea"), "webField", "variable");
        add(definitions, "GET", "Get Value", "variable", "webField", "variable");
        add(definitions, "CK", "Check Value", "variable", "variable", "operator");
        add(definitions, "PDF CHECK", "PDF Check", "variable", "variable", "operator");
        add(definitions, "CSV CHECK", "CSV Check", "variable", "variable", "operator");
        add(definitions, "E", "Extract Field", "variable", "webField", "variable");
        add(definitions, "IF", "IF", "none");
        add(definitions, "GOTO", "GOTO", "block", "block", "count");
        add(definitions, "EXCEL GOTO", "Excel GOTO", "block", "block");
        add(definitions, "LOOP", "Loop", "number", "webField", "interval", "count");
        add(definitions, "REFRESH_LOOP", "Refresh Loop", "number", "webField", "interval", "count");
        add(definitions, "REFRESH", "Refresh", "none");
        add(definitions, "NEXT_ENTER", "Next / Enter", "none");
        add(definitions, "SWIPE_UP", "Swipe Up", "number", "count");
        add(definitions, "SWIPE_DOWN", "Swipe Down", "number", "count");
        add(definitions, "H", "Wait", "number", "hold");
        add(definitions, "PAUSE", "Pause", "none");
        add(definitions, "Q", "Close Browser", "none");
        add(definitions, "P", "Screenshot", "none");
        return Collections.unmodifiableMap(definitions);
    }

    private static void add(
            Map<String, Definition> definitions, String code, String label, String target, String... fields) {
        Set<String> variableTypes = List.of(fields).contains("variable")
                ? Set.of("$String", "#Numeric") : Set.of();
        definitions.put(code, new Definition(code, label, target, List.of(fields), List.of(), variableTypes));
    }

    private static void addWithTags(Map<String, Definition> definitions, String code, String label, String target,
            List<String> allowedTags, String... fields) {
        Set<String> variableTypes = List.of(fields).contains("variable")
                ? Set.of("$String", "#Numeric") : Set.of();
        definitions.put(code, new Definition(code, label, target, List.of(fields), allowedTags, variableTypes));
    }

    private record Definition(String code, String label, String target, List<String> fields, List<String> allowedTags,
            Set<String> allowedVariableTypes) {
        private JsonObject toJson() {
            JsonObject json = new JsonObject();
            json.addProperty("code", code);
            json.addProperty("label", label);
            json.addProperty("target", target);
            JsonArray fieldDefinitions = new JsonArray();
            fields.forEach(fieldDefinitions::add);
            json.add("fields", fieldDefinitions);
            JsonArray tags = new JsonArray();
            allowedTags.forEach(tags::add);
            json.add("allowedTags", tags);
            JsonArray variableTypes = new JsonArray();
            allowedVariableTypes.forEach(variableTypes::add);
            json.add("allowedVariableTypes", variableTypes);
            return json;
        }
    }
}
