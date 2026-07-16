package com.allinweb.ch.component.pane;

import com.allinweb.ch.model.PluginManifestDTO;
import com.google.gson.Gson;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;

@Slf4j
final class ScannerPluginManifestClient {

    PluginManifestDTO fetch(String manifestUrl) throws Exception {
        log.info("PluginManifest - fetching: {}", manifestUrl);

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(manifestUrl))
                .timeout(Duration.ofSeconds(15))
                .header("Accept", "application/json")
                .GET()
                .build();

        HttpResponse<String> response =
                client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        if (response.statusCode() != 200) {
            throw new IOException("HTTP " + response.statusCode() + " from: " + manifestUrl);
        }

        String body = response.body();
        if (body == null || body.isBlank()) {
            throw new IOException("Empty response from: " + manifestUrl);
        }

        log.debug(
                "PluginManifest - raw JSON ({} chars): {}",
                body.length(),
                body.substring(0, Math.min(200, body.length())));

        PluginManifestDTO manifest = new Gson().fromJson(body, PluginManifestDTO.class);

        if (manifest == null) {
            throw new IOException("Gson returned null - invalid JSON from: " + manifestUrl);
        }
        if (manifest.getPlugins() == null || manifest.getPlugins().isEmpty()) {
            throw new IOException("Manifest parsed but 'plugins' array is missing or empty.");
        }

        log.info(
                "PluginManifest - loaded {} plugins (manifest v{})",
                manifest.getPlugins().size(),
                manifest.getVersion());
        return manifest;
    }
}
