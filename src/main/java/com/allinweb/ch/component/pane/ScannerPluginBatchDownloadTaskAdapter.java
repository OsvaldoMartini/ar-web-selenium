package com.allinweb.ch.component.pane;

import com.allinweb.ch.model.PluginDTO;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javafx.concurrent.Task;
import lombok.extern.slf4j.Slf4j;

@Slf4j
final class ScannerPluginBatchDownloadTaskAdapter {

    Task<Integer> build(
            List<PluginDTO> plugins,
            String serverBase,
            Path pluginsDir,
            BiConsumer<Integer, Integer> counterUpdate) {
        return new Task<>() {
            @Override
            protected Integer call() throws Exception {
                HttpClient client = HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(15))
                        .followRedirects(HttpClient.Redirect.NORMAL)
                        .build();

                Files.createDirectories(pluginsDir);
                int successCount = 0;

                for (int i = 0; i < plugins.size(); i++) {
                    if (isCancelled()) break;

                    PluginDTO plugin = plugins.get(i);
                    String zipUrl = serverBase + plugin.getDownloadUrl();
                    updateMessage("Downloading " + plugin.getName() + "...");
                    counterUpdate.accept(i + 1, plugins.size());

                    log.info("PluginDownload - GET {}", zipUrl);

                    HttpRequest req = HttpRequest.newBuilder()
                            .uri(URI.create(zipUrl))
                            .timeout(Duration.ofSeconds(60))
                            .build();

                    HttpResponse<InputStream> resp = client.send(req, HttpResponse.BodyHandlers.ofInputStream());

                    if (resp.statusCode() != 200) {
                        log.warn("PluginDownload - HTTP {} for {}", resp.statusCode(), zipUrl);
                        updateMessage("Skipped " + plugin.getName() + " (HTTP " + resp.statusCode() + ")");
                        Thread.sleep(600);
                        continue;
                    }

                    Path tempZip = Files.createTempFile("ar-plugin-" + plugin.getId() + "-", ".zip");
                    try (InputStream body = resp.body();
                            OutputStream out = Files.newOutputStream(tempZip)) {
                        body.transferTo(out);
                    }

                    updateMessage("Extracting " + plugin.getName() + "...");
                    extractPluginZip(tempZip, pluginsDir);
                    Files.deleteIfExists(tempZip);
                    successCount++;
                    updateProgress(i + 1, plugins.size());
                    log.info("PluginDownload - installed: {}", plugin.getName());
                }

                return successCount;
            }
        };
    }

    private static void extractPluginZip(Path tempZip, Path pluginsDir) throws java.io.IOException {
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(tempZip), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                Path target = pluginsDir.resolve(entry.getName()).normalize();
                if (!target.startsWith(pluginsDir)) {
                    log.warn("PluginDownload - zip-slip blocked: {}", entry.getName());
                    continue;
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(target);
                } else {
                    Files.createDirectories(target.getParent());
                    try (OutputStream fo = Files.newOutputStream(target)) {
                        byte[] buf = new byte[8192];
                        int len;
                        while ((len = zis.read(buf)) != -1) {
                            fo.write(buf, 0, len);
                        }
                    }
                }
                zis.closeEntry();
            }
        }
    }
}
