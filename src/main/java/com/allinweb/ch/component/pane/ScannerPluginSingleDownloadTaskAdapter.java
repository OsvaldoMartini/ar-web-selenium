package com.allinweb.ch.component.pane;

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
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javafx.concurrent.Task;
import lombok.extern.slf4j.Slf4j;

@Slf4j
final class ScannerPluginSingleDownloadTaskAdapter {

    Task<String> build(String downloadUrl, String fileName, String pluginName, Path pluginsDir) {
        return new Task<>() {
            @Override
            protected String call() throws Exception {
                log.info("UpdatePlugins - downloading: {}", downloadUrl);

                HttpClient client = HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(15))
                        .followRedirects(HttpClient.Redirect.NORMAL)
                        .build();

                HttpRequest getRequest = HttpRequest.newBuilder()
                        .uri(URI.create(downloadUrl))
                        .timeout(Duration.ofSeconds(120))
                        .build();

                HttpResponse<InputStream> getResponse =
                        client.send(getRequest, HttpResponse.BodyHandlers.ofInputStream());

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

                updateMessage("Downloading " + pluginName + "...");
                Path tempZip = Files.createTempFile("ar-plugin-", ".zip");
                long totalRead = 0;

                try (InputStream body = getResponse.body();
                        OutputStream out = Files.newOutputStream(tempZip)) {
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    while ((bytesRead = body.read(buffer)) != -1) {
                        out.write(buffer, 0, bytesRead);
                        totalRead += bytesRead;
                        if (contentLength > 0) updateProgress(totalRead, contentLength);
                        updateMessage(String.format(
                                "Downloading %s... %s / %s",
                                pluginName,
                                formatBytes(totalRead),
                                contentLength > 0 ? formatBytes(contentLength) : "unknown"));
                    }
                    out.flush();
                }

                long fileSize = Files.size(tempZip);
                log.info("UpdatePlugins - downloaded {} to: {}", formatBytes(fileSize), tempZip);

                validateZip(downloadUrl, tempZip, fileSize);

                updateMessage("Extracting " + pluginName + "...");
                updateProgress(-1, -1);
                Files.createDirectories(pluginsDir);

                int fileCount = 0;
                int dirCount = 0;
                try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(tempZip), StandardCharsets.UTF_8)) {
                    ZipEntry entry;
                    while ((entry = zis.getNextEntry()) != null) {
                        Path target = pluginsDir.resolve(entry.getName()).normalize();
                        if (!target.startsWith(pluginsDir)) {
                            log.warn("UpdatePlugins - SKIPPED zip-slip: {}", entry.getName());
                            continue;
                        }
                        if (entry.isDirectory()) {
                            Files.createDirectories(target);
                            dirCount++;
                            log.info("UpdatePlugins - DIR:  {}", target);
                        } else {
                            Files.createDirectories(target.getParent());
                            long entrySize = 0;
                            try (OutputStream fileOut = Files.newOutputStream(target)) {
                                byte[] buf = new byte[8192];
                                int len;
                                while ((len = zis.read(buf)) != -1) {
                                    fileOut.write(buf, 0, len);
                                    entrySize += len;
                                }
                            }
                            fileCount++;
                            log.info("UpdatePlugins - FILE: {} ({})", target, formatBytes(entrySize));
                        }
                        zis.closeEntry();
                    }
                }

                Files.deleteIfExists(tempZip);

                if (fileCount == 0) {
                    throw new IOException("ZIP was valid but contained 0 files.");
                }

                saveMetadata(pluginsDir.resolve(".plugins-meta"), pluginName, fileName, downloadUrl, fileCount);

                String summary = pluginName + ": " + fileCount + " files, " + dirCount + " dirs";
                updateMessage("Done! " + summary);
                updateProgress(1, 1);
                log.info("UpdatePlugins - SUCCESS: {}", summary);
                return summary;
            }
        };
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
}
