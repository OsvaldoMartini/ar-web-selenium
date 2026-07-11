package com.allinweb.ch.facade;

import com.allinweb.ch.model.InstructionLoad;
import com.google.gson.JsonObject;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Set;

/** Builds legacy operation strings from typed React command fields. */
public final class CommandOperationCodec {
    private final PerformDataBase database = PerformDataBase.getInstance();

    public String encode(JsonObject body, String action) throws SQLException {
        Relation relation = relation(body);
        String operator = string(body, "operator", "=");
        String count = boundedNumber(body, "count", "1");
        String interval = boundedNumber(body, "interval", "1");

        return switch (action) {
            case "SET" -> relation.webFieldName + ":" + emptyValue(relation.variableValue);
            case "GET" -> relation.webFieldName + ":" + relation.typedVariableName;
            case "CK", "PDF CHECK", "CSV CHECK" ->
                    relation.typedVariableName + ":" + operator + ":" + emptyValue(relation.variableValue);
            case "E" -> relation.typedVariableName;
            case "LOOP", "REFRESH_LOOP" -> interval + ":" + count;
            case "GOTO" -> count;
            case "EXCEL GOTO" -> "1";
            case "IF" -> "IF";
            case "SWIPE_UP", "SWIPE_DOWN" -> count;
            case "NEXT_ENTER", "REFRESH", "PAUSE", "Q", "P", "H" -> "";
            default -> throw new IllegalArgumentException("Unsupported command action: " + action);
        };
    }

    public JsonObject decode(InstructionLoad instruction) {
        JsonObject draft = new JsonObject();
        String action = CommandRegistry.canonicalize(instruction.getActions());
        String operation = instruction.getOperation() == null ? "" : instruction.getOperation();
        String[] parts = operation.split(":", -1);
        draft.addProperty("action", action);
        draft.addProperty("name", instruction.getName());
        addNullable(draft, "parentId", instruction.getParentId());
        addNullable(draft, "variableId", instruction.getVariableId());
        addNullable(draft, "parentBlockId", instruction.getParentBlockId());
        draft.addProperty("hold", positive(instruction.getOnHoldSeconds(), "H".equals(action) ? 5 : 1));
        draft.addProperty("operator", isCheck(action) && parts.length > 1 ? parts[1] : "=");
        draft.addProperty("interval", isLoop(action) && parts.length > 0 ? positive(parts[0], 1) : 1);
        draft.addProperty("count", count(action, parts));
        return draft;
    }

    private static int count(String action, String[] parts) {
        if (isLoop(action)) return parts.length > 1 ? positive(parts[1], 1) : 1;
        if (Set.of("GOTO", "SWIPE_UP", "SWIPE_DOWN").contains(action)) {
            return parts.length > 0 ? positive(parts[0], 1) : 1;
        }
        return 1;
    }

    private static boolean isCheck(String action) {
        return Set.of("CK", "PDF CHECK", "CSV CHECK").contains(action);
    }

    private static boolean isLoop(String action) {
        return Set.of("LOOP", "REFRESH_LOOP").contains(action);
    }

    private static int positive(Integer value, int fallback) {
        return value != null && value > 0 ? value : fallback;
    }

    private static int positive(String value, int fallback) {
        try {
            int parsed = Integer.parseInt(value);
            return parsed > 0 ? parsed : fallback;
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static void addNullable(JsonObject target, String key, Integer value) {
        if (value != null && value > 0) target.addProperty(key, value);
    }

    private Relation relation(JsonObject body) throws SQLException {
        Integer parentId = nullableInteger(body, "parentId");
        Integer variableId = nullableInteger(body, "variableId");
        boolean component = "componentTasks".equals(string(body, "targetSessionId", "botJobTasks"));
        String instructionTable = component ? "component_instruction" : "instruction";
        String variableTable = component ? "component_variable" : "variable";
        String webFieldName = "";
        String variableName = "";
        String variableType = "$String";
        String variableValue = "$EMPTY";

        if (parentId != null) {
            try (PreparedStatement statement = database.getConnection()
                    .prepareStatement("SELECT name FROM " + instructionTable + " WHERE id=?")) {
                statement.setInt(1, parentId);
                try (ResultSet result = statement.executeQuery()) {
                    if (result.next()) webFieldName = result.getString("name");
                }
            }
        }
        if (variableId != null) {
            try (PreparedStatement statement = database.getConnection()
                    .prepareStatement("SELECT type,name,value FROM " + variableTable + " WHERE id=?")) {
                statement.setInt(1, variableId);
                try (ResultSet result = statement.executeQuery()) {
                    if (result.next()) {
                        variableType = result.getString("type");
                        variableName = result.getString("name");
                        variableValue = result.getString("value");
                    }
                }
            }
        }
        String prefix = "#Numeric".equals(variableType) ? "#" : "$";
        return new Relation(webFieldName, prefix + variableName, variableValue);
    }

    private static String emptyValue(String value) {
        return value == null || value.isBlank() ? "$EMPTY" : value;
    }

    private static String boundedNumber(JsonObject body, String key, String fallback) {
        int value;
        try {
            value = body != null && body.has(key) ? body.get(key).getAsInt() : Integer.parseInt(fallback);
        } catch (Exception ignored) {
            value = Integer.parseInt(fallback);
        }
        if (value < 1 || value > 9999) throw new IllegalArgumentException(key + " must be between 1 and 9999.");
        return String.valueOf(value);
    }

    private static String string(JsonObject body, String key, String fallback) {
        return body != null && body.has(key) && !body.get(key).isJsonNull() ? body.get(key).getAsString() : fallback;
    }

    private static Integer nullableInteger(JsonObject body, String key) {
        return body != null && body.has(key) && !body.get(key).isJsonNull() ? body.get(key).getAsInt() : null;
    }

    private record Relation(String webFieldName, String typedVariableName, String variableValue) {}
}
