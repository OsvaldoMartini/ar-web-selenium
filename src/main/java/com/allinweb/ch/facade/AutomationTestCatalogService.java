package com.allinweb.ch.facade;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/** Loads the build-time inventory of automated tests shipped with the desktop application. */
public final class AutomationTestCatalogService {
    private static final String CATALOG_RESOURCE = "/automation-tests.json";
    private static final AutomationTestCatalogService INSTANCE = new AutomationTestCatalogService();

    private final JsonObject catalog;

    private AutomationTestCatalogService() {
        catalog = loadCatalog();
    }

    public static AutomationTestCatalogService getInstance() {
        return INSTANCE;
    }

    /** Returns a defensive copy because WebSocket serialization and callers must not mutate the cache. */
    public JsonObject list() {
        return catalog.deepCopy();
    }

    private JsonObject loadCatalog() {
        try (InputStream stream = AutomationTestCatalogService.class.getResourceAsStream(CATALOG_RESOURCE)) {
            if (stream == null) {
                return unavailable("The packaged automation test catalog is missing.");
            }
            JsonObject loaded = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
                    .getAsJsonObject();
            if (!loaded.has("tests") || !loaded.get("tests").isJsonArray()) {
                return unavailable("The packaged automation test catalog is invalid.");
            }
            loaded.addProperty("ok", true);
            return loaded;
        } catch (Exception exception) {
            return unavailable("The packaged automation test catalog could not be read.");
        }
    }

    private JsonObject unavailable(String message) {
        JsonObject response = new JsonObject();
        response.addProperty("ok", false);
        response.addProperty("error", message);
        response.addProperty("schemaVersion", 1);
        response.add("sources", new JsonArray());
        response.add("tests", new JsonArray());
        return response;
    }
}
