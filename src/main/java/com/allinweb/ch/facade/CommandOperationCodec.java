package com.allinweb.ch.facade;

import com.allinweb.ch.model.InstructionLoad;
import com.allinweb.ch.model.ScannerWorkspaceSessions;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
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

        return encodeResolved(action, relation.webFieldName, relation.typedVariableName, relation.variableValue,
                operator, count, interval);
    }

    static String encodeResolved(String action, String webFieldName, String typedVariableName, String variableValue,
            String operator, String count, String interval) {
        return switch (action) {
            case "SET" -> webFieldName + ":" + emptyValue(variableValue);
            case "GET" -> webFieldName + ":" + typedVariableName;
            case "CK", "PDF CHECK", "CSV CHECK" ->
                    typedVariableName + ":" + operator + ":" + emptyValue(variableValue);
            case "E" -> typedVariableName;
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
        JsonArray warnings = decodeWarnings(action, operation, parts);
        draft.add("warnings", warnings);
        return draft;
    }

    private static JsonArray decodeWarnings(String action, String operation, String[] parts) {
        JsonArray warnings = new JsonArray();
        if (Set.of("SET", "GET").contains(action) && parts.length != 2) {
            warnings.add(action + " operation must contain Web Field and Variable/Value segments.");
        } else if (isCheck(action) && parts.length != 3) {
            warnings.add(action + " operation must contain Variable, Operator, and Value segments.");
        } else if ("E".equals(action) && operation.isBlank()) {
            warnings.add("Extract Field operation has no Variable segment.");
        } else if (isLoop(action) && (parts.length != 2 || !isPositive(parts[0]) || !isPositive(parts[1]))) {
            warnings.add(action + " operation must contain positive Interval and Count segments.");
        } else if (Set.of("GOTO", "SWIPE_UP", "SWIPE_DOWN").contains(action)
                && (parts.length != 1 || !isPositive(parts[0]))) {
            warnings.add(action + " operation must contain one positive Count segment.");
        } else if ("EXCEL GOTO".equals(action) && !"1".equals(operation)) {
            warnings.add("EXCEL GOTO operation is not canonical.");
        } else if ("IF".equals(action) && !"IF".equals(operation)) {
            warnings.add("IF operation is not canonical.");
        } else if (Set.of("NEXT_ENTER", "REFRESH", "PAUSE", "Q", "P", "H").contains(action)
                && !operation.isBlank()) {
            warnings.add(action + " ignores a non-empty historical operation.");
        }
        return warnings;
    }

    private static boolean isPositive(String value) {
        try {
            return Integer.parseInt(value) > 0;
        } catch (NumberFormatException ignored) {
            return false;
        }
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
        boolean component = ScannerWorkspaceSessions.COMPONENT_TASKS.equals(
                string(body, "targetSessionId", ScannerWorkspaceSessions.BOT_JOB_TASKS));
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
