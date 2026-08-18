package com.allinweb.ch.component.pane;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
final class ScannerLocalPluginInventory {

    int[] countLocalPlugins(String pluginsDir) {
        try {
            Path manifestPath = Paths.get(pluginsDir, "manifest.json");
            if (!Files.exists(manifestPath)) {
                return countPluginFolders(pluginsDir);
            }
            String json = Files.readString(manifestPath, StandardCharsets.UTF_8).trim();
            if (json.startsWith("\uFEFF")) {
                json = json.substring(1);
            }
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            JsonArray plugins = root.getAsJsonArray("plugins");
            if (plugins == null) {
                return new int[] {0, 0};
            }

            int total = plugins.size();
            int installed = 0;
            for (int i = 0; i < plugins.size(); i++) {
                JsonObject plugin = plugins.get(i).getAsJsonObject();
                String id = plugin.has("id") ? plugin.get("id").getAsString() : "";
                if (!id.isEmpty() && isInstalled(pluginsDir, id)) {
                    installed++;
                }
            }
            return new int[] {installed, total};
        } catch (Exception e) {
            log.warn("countLocalPlugins - failed to read manifest: {}", e.getMessage());
            return countPluginFolders(pluginsDir);
        }
    }

    List<String[]> readLocalRows(String pluginsDir) {
        List<String[]> pluginRows = readManifestRows(pluginsDir);
        return pluginRows.isEmpty() ? readFolderRows(pluginsDir) : pluginRows;
    }

    private List<String[]> readManifestRows(String pluginsDir) {
        List<String[]> pluginRows = new ArrayList<>();
        Path localManifest = Paths.get(pluginsDir, "manifest.json");

        try {
            if (Files.exists(localManifest)) {
                String json = Files.readString(localManifest, StandardCharsets.UTF_8).trim();
                if (json.startsWith("\uFEFF")) {
                    json = json.substring(1);
                }
                JsonObject root = JsonParser.parseString(json).getAsJsonObject();
                JsonArray plugins = root.getAsJsonArray("plugins");
                if (plugins != null) {
                    for (int i = 0; i < plugins.size(); i++) {
                        JsonObject plugin = plugins.get(i).getAsJsonObject();
                        String id = plugin.has("id") ? plugin.get("id").getAsString() : "";
                        String name = plugin.has("name") ? plugin.get("name").getAsString() : id;
                        String version = plugin.has("version") ? plugin.get("version").getAsString() : "";
                        String size = plugin.has("size") ? plugin.get("size").getAsString() : "";
                        String fileName = plugin.has("fileName") ? plugin.get("fileName").getAsString() : "";
                        boolean local = !id.isEmpty() && isInstalled(pluginsDir, id);
                        pluginRows.add(new String[] {id, name, version, size, fileName, local ? "LOCAL" : "MISSING"});
                    }
                }
            }
        } catch (Exception e) {
            log.warn("PluginUpdate - could not read local manifest: {}", e.getMessage());
        }
        return pluginRows;
    }

    private List<String[]> readFolderRows(String pluginsDir) {
        List<String[]> pluginRows = new ArrayList<>();
        try {
            Path dir = Paths.get(pluginsDir);
            if (Files.isDirectory(dir)) {
                try (var entries = Files.list(dir)) {
                    for (Path entry : entries.toList()) {
                        if (Files.isDirectory(entry)
                                && !entry.getFileName().toString().startsWith(".")) {
                            String folderName = entry.getFileName().toString();
                            pluginRows.add(new String[] {folderName, folderName, "", "", "", "LOCAL"});
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("PluginUpdate - could not scan plugins folder: {}", e.getMessage());
        }
        return pluginRows;
    }

    private int[] countPluginFolders(String pluginsDir) {
        try {
            Path dir = Paths.get(pluginsDir);
            if (!Files.isDirectory(dir)) {
                return new int[] {0, 0};
            }
            int count = 0;
            try (var entries = Files.list(dir)) {
                for (Path entry : entries.toList()) {
                    if (Files.isDirectory(entry)
                            && !entry.getFileName().toString().startsWith(".")) {
                        count++;
                    }
                }
            }
            return new int[] {count, count};
        } catch (Exception e) {
            return new int[] {0, 0};
        }
    }

    private boolean isInstalled(String pluginsDir, String pluginId) {
        Path zipFile = Paths.get(pluginsDir, pluginId + ".zip");
        if (Files.exists(zipFile)) {
            return true;
        }

        Path pluginDir = Paths.get(pluginsDir, pluginId);
        if (!Files.isDirectory(pluginDir)) {
            return false;
        }

        try (var files = Files.list(pluginDir)) {
            if (files.anyMatch(file -> {
                String name = file.toString();
                return name.endsWith(".min.enc") || name.endsWith(".min.js");
            })) {
                return true;
            }
        } catch (Exception ignored) {
        }

        Path buildDir = pluginDir.resolve("build");
        if (Files.isDirectory(buildDir)) {
            try (var files = Files.list(buildDir)) {
                if (files.anyMatch(file -> {
                    String name = file.toString();
                    return name.endsWith(".min.enc") || name.endsWith(".min.js");
                })) {
                    return true;
                }
            } catch (Exception ignored) {
            }
        }

        if (Files.exists(pluginDir.resolve("index.js"))) {
            return true;
        }
        try (var files = Files.list(pluginDir)) {
            return files.anyMatch(file -> file.toString().endsWith(".js"));
        } catch (Exception ignored) {
            return false;
        }
    }
}
