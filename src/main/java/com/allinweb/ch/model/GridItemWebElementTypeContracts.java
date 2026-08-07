package com.allinweb.ch.model;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Locale;
import java.util.regex.Pattern;

/** Minimal versioned contract for changing one persisted GridItem Web Element execution type. */
public final class GridItemWebElementTypeContracts {
    public static final int CONTRACT_VERSION = 1;
    public static final String REQUEST = "gridItem.webElementType.update";
    public static final String RESPONSE = REQUEST + "Response";

    private static final int MAX_REQUEST_ID = 200;
    private static final Pattern SHA_256 = Pattern.compile("[0-9a-fA-F]{64}");

    private GridItemWebElementTypeContracts() {}

    public enum WebElementType {
        INPUT,
        OUTPUT,
        CLICK
    }

    public record Request(
            int contractVersion,
            String requestId,
            int homeBankingId,
            int botJobId,
            int instructionId,
            long workspaceEpoch,
            long baseGraphVersion,
            String graphRevision,
            WebElementType expectedType,
            WebElementType replacementType) {
        public Request {
            if (contractVersion != CONTRACT_VERSION) {
                throw new IllegalArgumentException(
                        "GridItem Web Element type contractVersion must be " + CONTRACT_VERSION);
            }
            requestId = requiredText(requestId, "requestId", MAX_REQUEST_ID);
            positive(homeBankingId, "homeBankingId");
            positive(botJobId, "botJobId");
            positive(instructionId, "instructionId");
            if (workspaceEpoch <= 0L) {
                throw new IllegalArgumentException(
                        "GridItem Web Element type workspaceEpoch must be positive");
            }
            if (baseGraphVersion < 0L) {
                throw new IllegalArgumentException(
                        "GridItem Web Element type baseGraphVersion cannot be negative");
            }
            graphRevision = requiredText(graphRevision, "graphRevision", 64);
            if (!SHA_256.matcher(graphRevision).matches()) {
                throw new IllegalArgumentException(
                        "GridItem Web Element type graphRevision must be a SHA-256 value");
            }
            graphRevision = graphRevision.toLowerCase(Locale.ROOT);
            if (expectedType == null || replacementType == null) {
                throw new IllegalArgumentException(
                        "GridItem Web Element expectedType and replacementType are required");
            }
        }
    }

    public static Request parse(JsonObject body) {
        if (body == null) {
            throw new IllegalArgumentException("GridItem Web Element type body is required");
        }
        return new Request(
                requiredInt(body, "contractVersion"),
                requiredString(body, "requestId"),
                requiredInt(body, "homeBankingId"),
                requiredInt(body, "botJobId"),
                requiredInt(body, "instructionId"),
                requiredLong(body, "workspaceEpoch"),
                requiredLong(body, "baseGraphVersion"),
                requiredString(body, "graphRevision"),
                requiredType(body, "expectedType"),
                requiredType(body, "replacementType"));
    }

    private static WebElementType requiredType(JsonObject body, String field) {
        String value = requiredString(body, field).trim().toUpperCase(Locale.ROOT);
        try {
            return WebElementType.valueOf(value);
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException(
                    "GridItem Web Element " + field + " must be INPUT, OUTPUT, or CLICK");
        }
    }

    private static int requiredInt(JsonObject body, String field) {
        JsonElement value = body.get(field);
        if (value == null || value.isJsonNull()) {
            throw new IllegalArgumentException("GridItem Web Element " + field + " is required");
        }
        try {
            return value.getAsInt();
        } catch (RuntimeException invalid) {
            throw new IllegalArgumentException(
                    "GridItem Web Element " + field + " must be an integer");
        }
    }

    private static long requiredLong(JsonObject body, String field) {
        JsonElement value = body.get(field);
        if (value == null || value.isJsonNull()) {
            throw new IllegalArgumentException("GridItem Web Element " + field + " is required");
        }
        try {
            return value.getAsLong();
        } catch (RuntimeException invalid) {
            throw new IllegalArgumentException(
                    "GridItem Web Element " + field + " must be an integer");
        }
    }

    private static String requiredString(JsonObject body, String field) {
        JsonElement value = body.get(field);
        if (value == null || value.isJsonNull()) {
            throw new IllegalArgumentException("GridItem Web Element " + field + " is required");
        }
        try {
            String text = value.getAsString();
            if (text == null || text.isBlank()) {
                throw new IllegalArgumentException(
                        "GridItem Web Element " + field + " is required");
            }
            return text;
        } catch (IllegalArgumentException invalid) {
            throw invalid;
        } catch (RuntimeException invalid) {
            throw new IllegalArgumentException(
                    "GridItem Web Element " + field + " must be text");
        }
    }

    private static String requiredText(String value, String field, int maximum) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("GridItem Web Element " + field + " is required");
        }
        String trimmed = value.trim();
        if (trimmed.length() > maximum) {
            throw new IllegalArgumentException(
                    "GridItem Web Element " + field + " is too long");
        }
        return trimmed;
    }

    private static void positive(int value, String field) {
        if (value <= 0) {
            throw new IllegalArgumentException(
                    "GridItem Web Element " + field + " must be positive");
        }
    }
}
