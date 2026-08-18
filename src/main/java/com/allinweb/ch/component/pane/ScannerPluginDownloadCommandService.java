package com.allinweb.ch.component.pane;

import com.allinweb.ch.facade.scanner.plugin.ScannerPluginAlertAdapter;
import com.allinweb.ch.facade.ScannerDialogPublisher;
import com.allinweb.ch.model.PluginDTO;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BiConsumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class ScannerPluginDownloadCommandService {

    private static final ScannerPluginDownloadCommandService INSTANCE =
            new ScannerPluginDownloadCommandService();
    private static final String SINGLE_PROGRESS_ID = "plugin-download";
    private static final String BATCH_PROGRESS_ID = "plugin-download-batch";

    private final Gson gson = new Gson();
    private final ScannerPluginAlertAdapter alerts = new ScannerPluginAlertAdapter();
    private final ScannerDialogPublisher dialogs = ScannerDialogPublisher.getInstance();
    private final ExecutorService executor = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "plugin-download-thread");
        thread.setDaemon(true);
        return thread;
    });

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
        startSingleDownload(downloadUrl, fileName, pluginName, pluginsDir);

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

        startBatchDownload(plugins, normalizeBase(serverBase), pathPlugins);

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

    private void startSingleDownload(String downloadUrl, String fileName, String pluginName, Path pluginsDir) {
        dialogs.progress(SINGLE_PROGRESS_ID, "Downloading: " + pluginName, "Downloading " + pluginName + "...", 0, 0, 0);
        CompletableFuture
                .supplyAsync(() -> downloadSingle(downloadUrl, fileName, pluginName, pluginsDir), executor)
                .whenComplete((summary, throwable) -> {
                    dialogs.closeProgress(SINGLE_PROGRESS_ID);
                    if (throwable != null) {
                        log.error("UpdatePlugins - failed", throwable);
                        alerts.error("Download failed", messageOf(throwable));
                        return;
                    }
                    alerts.information("Download complete", summary + "\nDestination: " + pluginsDir);
                });
    }

    private void startBatchDownload(List<PluginDTO> plugins, String serverBase, String pathPlugins) {
        Path pluginsDir = Paths.get(pathPlugins);
        dialogs.progress(BATCH_PROGRESS_ID, "Downloading Plugins", "Starting...", 0, 0, plugins.size());
        CompletableFuture
                .supplyAsync(() -> downloadBatch(plugins, serverBase, pluginsDir, this::publishBatchCounter), executor)
                .whenComplete((count, throwable) -> {
                    dialogs.closeProgress(BATCH_PROGRESS_ID);
                    if (throwable != null) {
                        log.error("PluginDownload - failed", throwable);
                        alerts.error("Download failed", messageOf(throwable));
                        return;
                    }
                    alerts.information(
                            "Download complete",
                            count + " of " + plugins.size() + " plugin(s) downloaded and extracted to:\n" + pathPlugins);
                    log.info("PluginDownload - finished: {}/{} plugins", count, plugins.size());
                });
    }

    private String downloadSingle(String downloadUrl, String fileName, String pluginName, Path pluginsDir) {
        try {
            log.info("UpdatePlugins - downloading: {}", downloadUrl);

            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(15))
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();

            HttpRequest getRequest = HttpRequest.newBuilder()
                    .uri(URI.create(downloadUrl))
                    .timeout(Duration.ofSeconds(120))
                    .build();

            HttpResponse<InputStream> getResponse = client.send(getRequest, HttpResponse.BodyHandlers.ofInputStream());

            int statusCode = getResponse.statusCode();
            String contentType = getResponse.headers().firstValue("Content-Type").orElse("unknown");
            long contentLength = getResponse
                    .headers()
                    .firstValue("Content-Length")
                    .map(Long::parseLong)
                    .orElse(-1L);

            log.info(
                    "UpdatePlugins - HTTP {} | Content-Type: {} | Content-Length: {}",
                    statusCode,
                    contentType,
                    contentLength);

            if (statusCode != 200) {
                throw new IOException("HTTP " + statusCode + " for: " + downloadUrl);
            }
            if (contentType.contains("text/html")) {
                throw new IOException("Server returned HTML instead of ZIP.\n"
                        + "File '" + fileName + "' may not exist on the server.\n"
                        + "URL: " + downloadUrl);
            }

            publishSingleProgress(pluginName, "Downloading " + pluginName + "...", 0);
            Path tempZip = Files.createTempFile("ar-plugin-", ".zip");
            long totalRead = 0;

            try (InputStream body = getResponse.body();
                    OutputStream out = Files.newOutputStream(tempZip)) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = body.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                    totalRead += bytesRead;
                    double progress = contentLength > 0 ? totalRead / (double) contentLength : -1;
                    publishSingleProgress(
                            pluginName,
                            String.format(
                                    "Downloading %s... %s / %s",
                                    pluginName,
                                    formatBytes(totalRead),
                                    contentLength > 0 ? formatBytes(contentLength) : "unknown"),
                            progress);
                }
                out.flush();
            }

            long fileSize = Files.size(tempZip);
            log.info("UpdatePlugins - downloaded {} to: {}", formatBytes(fileSize), tempZip);
            validateZip(downloadUrl, tempZip, fileSize);

            publishSingleProgress(pluginName, "Extracting " + pluginName + "...", -1);
            Files.createDirectories(pluginsDir);

            ExtractResult extractResult = extractPluginZip(tempZip, pluginsDir);
            Files.deleteIfExists(tempZip);

            if (extractResult.fileCount() == 0) {
                throw new IOException("ZIP was valid but contained 0 files.");
            }

            saveMetadata(pluginsDir.resolve(".plugins-meta"), pluginName, fileName, downloadUrl, extractResult.fileCount());

            String summary = pluginName + ": " + extractResult.fileCount() + " files, " + extractResult.dirCount() + " dirs";
            publishSingleProgress(pluginName, "Done! " + summary, 1);
            log.info("UpdatePlugins - SUCCESS: {}", summary);
            return summary;
        } catch (Exception e) {
            throw new PluginDownloadException(e);
        }
    }

    private int downloadBatch(
            List<PluginDTO> plugins,
            String serverBase,
            Path pluginsDir,
            BiConsumer<Integer, Integer> counterUpdate) {
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(15))
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();

            Files.createDirectories(pluginsDir);
            int successCount = 0;

            for (int i = 0; i < plugins.size(); i++) {
                PluginDTO plugin = plugins.get(i);
                String zipUrl = serverBase + plugin.getDownloadUrl();
                counterUpdate.accept(i + 1, plugins.size());
                publishBatchProgress(
                        "Downloading " + plugin.getName() + "...", progress(i, plugins.size()), i + 1, plugins.size());

                log.info("PluginDownload - GET {}", zipUrl);

                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(zipUrl))
                        .timeout(Duration.ofSeconds(60))
                        .build();

                HttpResponse<InputStream> resp = client.send(req, HttpResponse.BodyHandlers.ofInputStream());

                if (resp.statusCode() != 200) {
                    log.warn("PluginDownload - HTTP {} for {}", resp.statusCode(), zipUrl);
                    publishBatchProgress(
                            "Skipped " + plugin.getName() + " (HTTP " + resp.statusCode() + ")",
                            progress(i + 1, plugins.size()),
                            i + 1,
                            plugins.size());
                    continue;
                }

                Path tempZip = Files.createTempFile("ar-plugin-" + plugin.getId() + "-", ".zip");
                try (InputStream body = resp.body();
                        OutputStream out = Files.newOutputStream(tempZip)) {
                    body.transferTo(out);
                }

                publishBatchProgress(
                        "Extracting " + plugin.getName() + "...", -1, i + 1, plugins.size());
                extractPluginZip(tempZip, pluginsDir);
                Files.deleteIfExists(tempZip);
                successCount++;
                publishBatchProgress(
                        "Installed " + plugin.getName(), progress(i + 1, plugins.size()), i + 1, plugins.size());
                log.info("PluginDownload - installed: {}", plugin.getName());
            }

            return successCount;
        } catch (Exception e) {
            throw new PluginDownloadException(e);
        }
    }

    private void publishSingleProgress(String pluginName, String message, double progress) {
        dialogs.progress(SINGLE_PROGRESS_ID, "Downloading: " + pluginName, message, progress, 0, 0);
    }

    private void publishBatchCounter(int current, int total) {
        dialogs.progress(BATCH_PROGRESS_ID, "Downloading Plugins", current + " / " + total, -1, current, total);
    }

    private void publishBatchProgress(String message, double progress, int current, int total) {
        dialogs.progress(BATCH_PROGRESS_ID, "Downloading Plugins", message, progress, current, total);
    }

    private static double progress(int current, int total) {
        return total <= 0 ? 0 : current / (double) total;
    }

    private static ExtractResult extractPluginZip(Path tempZip, Path pluginsDir) throws IOException {
        int fileCount = 0;
        int dirCount = 0;
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
                    dirCount++;
                } else {
                    Files.createDirectories(target.getParent());
                    try (OutputStream fileOut = Files.newOutputStream(target)) {
                        byte[] buf = new byte[8192];
                        int len;
                        while ((len = zis.read(buf)) != -1) {
                            fileOut.write(buf, 0, len);
                        }
                    }
                    fileCount++;
                }
                zis.closeEntry();
            }
        }
        return new ExtractResult(fileCount, dirCount);
    }

    private static void validateZip(String downloadUrl, Path tempZip, long fileSize) throws IOException {
        if (fileSize == 0) {
            Files.deleteIfExists(tempZip);
            throw new IOException("Downloaded file is empty (0 bytes): " + downloadUrl);
        }
        try (InputStream check = Files.newInputStream(tempZip)) {
            int b1 = check.read();
            int b2 = check.read();
            if (b1 != 0x50 || b2 != 0x4B) {
                String preview = Files.readString(tempZip, StandardCharsets.UTF_8);
                if (preview.length() > 200) preview = preview.substring(0, 200);
                Files.deleteIfExists(tempZip);
                throw new IOException("Not a valid ZIP file.\nFirst 200 chars: " + preview);
            }
        }
        log.info("UpdatePlugins - ZIP validated OK");
    }

    private static void saveMetadata(
            Path metaFile, String pluginName, String fileName, String downloadUrl, int fileCount) throws IOException {
        Properties meta = new Properties();
        meta.setProperty("lastPlugin", pluginName);
        meta.setProperty("lastFile", fileName);
        meta.setProperty("url", downloadUrl);
        meta.setProperty("updated", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        meta.setProperty("files", String.valueOf(fileCount));
        try (OutputStream metaOut = Files.newOutputStream(metaFile)) {
            meta.store(metaOut, "AR Web Plugin Update Metadata");
        }
    }

    private static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
    }

    private static String messageOf(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.toString() : current.getMessage();
    }

    private record ExtractResult(int fileCount, int dirCount) {}

    private static final class PluginDownloadException extends RuntimeException {
        private PluginDownloadException(Throwable cause) {
            super(cause);
        }
    }
}
