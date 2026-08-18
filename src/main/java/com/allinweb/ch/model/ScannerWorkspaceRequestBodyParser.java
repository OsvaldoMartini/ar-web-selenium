package com.allinweb.ch.model;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

final class ScannerWorkspaceRequestBodyParser {

    private ScannerWorkspaceRequestBodyParser() {}

    static JsonObject parse(JsonObject envelope) {
        if (!envelope.has("body") || envelope.get("body").isJsonNull()) {
            throw new IllegalArgumentException("Scanner request body is required");
        }
        JsonElement bodyElement = envelope.get("body");
        if (bodyElement.isJsonObject()) {
            return bodyElement.getAsJsonObject().deepCopy();
        }
        if (bodyElement.isJsonPrimitive() && bodyElement.getAsJsonPrimitive().isString()) {
            try {
                JsonElement parsed = JsonParser.parseString(bodyElement.getAsString());
                if (parsed.isJsonObject()) {
                    return parsed.getAsJsonObject();
                }
            } catch (RuntimeException invalidJson) {
                throw new IllegalArgumentException("Scanner request body must be valid JSON", invalidJson);
            }
        }
        throw new IllegalArgumentException("Scanner request body must be a JSON object");
    }
}
