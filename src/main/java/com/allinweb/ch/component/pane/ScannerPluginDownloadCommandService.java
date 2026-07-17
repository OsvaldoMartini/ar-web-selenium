package com.allinweb.ch.component.pane;

import com.allinweb.ch.model.PluginDTO;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public final class ScannerPluginDownloadCommandService {

    private static final ScannerPluginDownloadCommandService INSTANCE =
            new ScannerPluginDownloadCommandService();

    private final Gson gson = new Gson();
    private final ScannerPluginDownloadFlowAdapter downloadFlow = new ScannerPluginDownloadFlowAdapter();
    private final ScannerPluginAlertAdapter alerts = new ScannerPluginAlertAdapter();

    public static ScannerPluginDownloadCommandService getInstance() {
        return INSTANCE;
    }

    public JsonObject download(JsonObject body) {
        if (body == null) {
            return failure("Missing plugin download request body.");
        }

        JsonObject plugin = object(body, "plugin");
        String pluginName = firstText(body, plugin, "pluginName", "name");
        String fileName = firstText(body, plugin, "fileName", "file");
        String pluginsDirText = firstText(body, null, "pluginsDir", "pathPlugins");
        String downloadUrl = resolveDownloadUrl(body, plugin, fileName);

        if (pluginName.isBlank() || fileName.isBlank() || pluginsDirText.isBlank() || downloadUrl.isBlank()) {
            return failure("Missing plugin name, file name, plugins directory, or download URL.");
        }

        Path pluginsDir = Paths.get(pluginsDirText);
        downloadFlow.runSingle(downloadUrl, fileName, pluginName, pluginsDir, () -> {}, new PublisherNotifier());

        JsonObject response = success("Plugin download started.");
        response.addProperty("pluginName", pluginName);
        response.addProperty("fileName", fileName);
        response.addProperty("pluginsDir", pluginsDir.toString());
        return response;
    }

    public JsonObject downloadBatch(JsonObject body) {
        if (body == null) {
            return failure("Missing plugin batch download request body.");
        }

        String serverBase = text(body, "serverBase");
        String pathPlugins = firstText(body, null, "pathPlugins", "pluginsDir");
        JsonArray pluginArray = body.has("plugins") && body.get("plugins").isJsonArray()
                ? body.getAsJsonArray("plugins")
                : null;

        if (serverBase.isBlank() || pathPlugins.isBlank() || pluginArray == null || pluginArray.size() == 0) {
            return failure("Missing server base, plugins directory, or plugin list.");
        }

        List<PluginDTO> plugins = new ArrayList<>();
        pluginArray.forEach(element -> {
            if (element != null && element.isJsonObject()) {
                plugins.add(gson.fromJson(element, PluginDTO.class));
            }
        });

        if (plugins.isEmpty()) {
            return failure("Plugin list did not contain downloadable plugins.");
        }

        downloadFlow.runBatch(plugins, normalizeBase(serverBase), pathPlugins, () -> {}, new PublisherNotifier());

        JsonObject response = success("Plugin batch download started.");
        response.addProperty("count", plugins.size());
        response.addProperty("pathPlugins", pathPlugins);
        return response;
    }

    private String resolveDownloadUrl(JsonObject body, JsonObject plugin, String fileName) {
        String directUrl = firstText(body, plugin, "downloadUrl", "url");
        if (directUrl.startsWith("http://") || directUrl.startsWith("https://")) {
            return directUrl;
        }

        String serverBase = text(body, "serverBase");
        if (serverBase.isBlank()) {
            return directUrl;
        }

        if (!directUrl.isBlank()) {
            return normalizeBase(serverBase) + trimLeadingSlash(directUrl);
        }
        return normalizeBase(serverBase) + trimLeadingSlash(fileName);
    }

    private static String firstText(JsonObject primary, JsonObject secondary, String... keys) {
        for (String key : keys) {
            String value = text(primary, key);
            if (!value.isBlank()) {
                return value;
            }
            value = text(secondary, key);
            if (!value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private static JsonObject object(JsonObject body, String key) {
        return body != null && body.has(key) && body.get(key).isJsonObject()
                ? body.getAsJsonObject(key)
                : null;
    }

    private static String text(JsonObject object, String key) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) {
            return "";
        }
        return object.get(key).getAsString().trim();
    }

    private static String normalizeBase(String value) {
        return value.endsWith("/") ? value : value + "/";
    }

    private static String trimLeadingSlash(String value) {
        return value == null ? "" : value.replaceFirst("^/+", "");
    }

    private static JsonObject success(String message) {
        JsonObject response = new JsonObject();
        response.addProperty("ok", true);
        response.addProperty("message", message);
        return response;
    }

    private static JsonObject failure(String message) {
        JsonObject response = new JsonObject();
        response.addProperty("ok", false);
        response.addProperty("error", message);
        return response;
    }

    private final class PublisherNotifier implements ScannerPluginDownloadResultAdapter.PluginNotifier {
        @Override
        public void information(String header, String body) {
            alerts.information(header, body);
        }

        @Override
        public void error(String header, String body) {
            alerts.error(header, body);
        }
    }
}
