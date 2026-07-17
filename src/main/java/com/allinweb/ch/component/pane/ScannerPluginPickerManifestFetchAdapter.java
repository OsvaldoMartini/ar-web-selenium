package com.allinweb.ch.component.pane;

import com.allinweb.ch.facade.UiThreadDispatcher;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;

@Slf4j
final class ScannerPluginPickerManifestFetchAdapter {

    Thread build(String manifestUrl, Consumer<List<String[]>> onSuccess, Consumer<Exception> onFailure) {
        return new Thread(() -> {
            try {
                List<String[]> plugins = fetchPlugins(manifestUrl);
                log.info("UpdatePlugins - manifest loaded: {} plugins available", plugins.size());
                UiThreadDispatcher.getInstance().execute(() -> onSuccess.accept(plugins));
            } catch (Exception ex) {
                log.error("UpdatePlugins - failed to fetch manifest from: {}", manifestUrl, ex);
                UiThreadDispatcher.getInstance().execute(() -> onFailure.accept(ex));
            }
        });
    }

    private static List<String[]> fetchPlugins(String manifestUrl) throws Exception {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(manifestUrl))
                .timeout(Duration.ofSeconds(15))
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());

        if (resp.statusCode() != 200) {
            throw new IOException("Server returned HTTP " + resp.statusCode() + " for: " + manifestUrl);
        }

        String jsonBody = resp.body().trim();
        if (jsonBody.startsWith("\uFEFF")) {
            jsonBody = jsonBody.substring(1);
        }
        JsonReader reader = new JsonReader(new StringReader(jsonBody));
        reader.setLenient(true);
        JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
        JsonArray pluginsArray = root.getAsJsonArray("plugins");

        if (pluginsArray == null || pluginsArray.isEmpty()) {
            throw new IOException("Manifest contains no plugins.");
        }

        List<String[]> plugins = new ArrayList<>();
        for (int i = 0; i < pluginsArray.size(); i++) {
            JsonObject p = pluginsArray.get(i).getAsJsonObject();
            plugins.add(new String[] {
                p.has("name") ? p.get("name").getAsString() : "Unknown",
                p.has("description") ? p.get("description").getAsString() : "",
                p.has("version") ? p.get("version").getAsString() : "",
                p.has("size") ? p.get("size").getAsString() : "",
                p.has("fileName") ? p.get("fileName").getAsString() : ""
            });
        }
        return plugins;
    }
}
