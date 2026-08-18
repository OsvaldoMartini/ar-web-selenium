package com.allinweb.ch.model;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Locale;
import java.util.regex.Pattern;

/** Versioned, minimal contract for one GridItem Playwright test action. */
public final class GridItemTestActionContracts {
    public static final int CONTRACT_VERSION = 1;
    public static final String REQUEST = "gridItem.testAction";
    public static final String RESPONSE = REQUEST + "Response";

    private static final int MAX_REQUEST_ID = 200;
    private static final Pattern SHA_256 = Pattern.compile("[0-9a-fA-F]{64}");

    private GridItemTestActionContracts() {}

    public enum Action {
        CLICK,
        INPUT
    }

    public record Request(
            int contractVersion,
            String requestId,
            int homeBankingId,
            int botJobId,
            int instructionId,
            Action action,
            int excelRowIndex,
            Long workspaceEpoch,
            Long baseGraphVersion,
            String graphRevision) {
        public Request {
            if (contractVersion != CONTRACT_VERSION) {
                throw new IllegalArgumentException(
                        "GridItem test action contractVersion must be " + CONTRACT_VERSION);
            }
            requestId = requiredText(requestId, "requestId", MAX_REQUEST_ID);
            positive(homeBankingId, "homeBankingId");
            positive(botJobId, "botJobId");
            positive(instructionId, "instructionId");
            if (action == null) {
                throw new IllegalArgumentException("GridItem test action is required");
            }
            if (excelRowIndex < 0 || excelRowIndex > 999) {
                throw new IllegalArgumentException(
                        "GridItem test excelRowIndex must be between 0 and 999");
            }
            if (workspaceEpoch != null && workspaceEpoch <= 0) {
                throw new IllegalArgumentException(
                        "GridItem test workspaceEpoch must be positive when provided");
            }
            if (baseGraphVersion != null && baseGraphVersion < 0) {
                throw new IllegalArgumentException(
                        "GridItem test baseGraphVersion cannot be negative");
            }
            if (graphRevision != null) {
                graphRevision = graphRevision.trim();
                if (graphRevision.isEmpty()) {
                    graphRevision = null;
                } else if (!SHA_256.matcher(graphRevision).matches()) {
                    throw new IllegalArgumentException(
                            "GridItem test graphRevision must be a SHA-256 value");
                } else {
                    graphRevision = graphRevision.toLowerCase(Locale.ROOT);
                }
            }
        }
    }

    public static Request parse(JsonObject body) {
        if (body == null) {
            throw new IllegalArgumentException("GridItem test action body is required");
        }
        int version = requiredInt(body, "contractVersion");
        String actionText = requiredString(body, "action");
        Action action;
        try {
            action = Action.valueOf(actionText.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException("GridItem test action must be CLICK or INPUT");
        }
        return new Request(
                version,
                requiredString(body, "requestId"),
                requiredInt(body, "homeBankingId"),
                requiredInt(body, "botJobId"),
                requiredInt(body, "instructionId"),
                action,
                optionalInt(body, "excelRowIndex", 0),
                optionalLong(body, "workspaceEpoch"),
                optionalLong(body, "baseGraphVersion"),
                optionalString(body, "graphRevision"));
    }

    private static int requiredInt(JsonObject body, String field) {
        JsonElement value = body.get(field);
        if (value == null || value.isJsonNull()) {
            throw new IllegalArgumentException("GridItem test " + field + " is required");
        }
        try {
            return value.getAsInt();
        } catch (RuntimeException invalid) {
            throw new IllegalArgumentException("GridItem test " + field + " must be an integer");
        }
    }

    private static int optionalInt(JsonObject body, String field, int fallback) {
        JsonElement value = body.get(field);
        if (value == null || value.isJsonNull()) return fallback;
        try {
            return value.getAsInt();
        } catch (RuntimeException invalid) {
            throw new IllegalArgumentException("GridItem test " + field + " must be an integer");
        }
    }

    private static Long optionalLong(JsonObject body, String field) {
        JsonElement value = body.get(field);
        if (value == null || value.isJsonNull()) return null;
        try {
            return value.getAsLong();
        } catch (RuntimeException invalid) {
            throw new IllegalArgumentException("GridItem test " + field + " must be an integer");
        }
    }

    private static String requiredString(JsonObject body, String field) {
        String value = optionalString(body, field);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("GridItem test " + field + " is required");
        }
        return value;
    }

    private static String optionalString(JsonObject body, String field) {
        JsonElement value = body.get(field);
        if (value == null || value.isJsonNull()) return null;
        try {
            return value.getAsString();
        } catch (RuntimeException invalid) {
            throw new IllegalArgumentException("GridItem test " + field + " must be text");
        }
    }

    private static String requiredText(String value, String field, int maximum) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("GridItem test " + field + " is required");
        }
        String trimmed = value.trim();
        if (trimmed.length() > maximum) {
            throw new IllegalArgumentException("GridItem test " + field + " is too long");
        }
        return trimmed;
    }

    private static void positive(int value, String field) {
        if (value <= 0) {
            throw new IllegalArgumentException("GridItem test " + field + " must be positive");
        }
    }
}
