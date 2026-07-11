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

    public static boolean isCommand(String action) {
        return DEFINITIONS.containsKey(canonicalize(action));
    }

    public static boolean requires(String action, String field) {
        Definition definition = DEFINITIONS.get(canonicalize(action));
        return definition != null && definition.fields().contains(field);
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
        add(definitions, "SET", "Set Value", "variable", "webField", "variable");
        add(definitions, "GET", "Get Value", "variable", "webField", "variable");
        add(definitions, "CK", "Check Value", "variable", "webField", "variable", "operator");
        add(definitions, "PDF CHECK", "PDF Check", "variable", "webField", "variable", "operator");
        add(definitions, "CSV CHECK", "CSV Check", "variable", "webField", "variable", "operator");
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
        definitions.put(code, new Definition(code, label, target, List.of(fields)));
    }

    private record Definition(String code, String label, String target, List<String> fields) {
        private JsonObject toJson() {
            JsonObject json = new JsonObject();
            json.addProperty("code", code);
            json.addProperty("label", label);
            json.addProperty("target", target);
            JsonArray fieldDefinitions = new JsonArray();
            fields.forEach(fieldDefinitions::add);
            json.add("fields", fieldDefinitions);
            return json;
        }
    }
}
