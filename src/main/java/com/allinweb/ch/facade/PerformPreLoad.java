package com.allinweb.ch.facade;

import com.allinweb.ch.util.ARPropertyEnum;
import com.allinweb.ch.util.ARPropertyManager;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PerformPreLoad {

    private static final ARPropertyManager arPropertyManager = ARPropertyManager.getInstance();

    /**
     * Clears all active plugin caches.
     * Convenience method for a single "Refresh Plugins" button.
     */
    public static void reloadAllPlugins() {
        EncryptedPluginLoader.getInstance().reloadAll();
        log.info("All plugin caches cleared - scripts will reload on next injection");
    }

    /**
     * Reads a plugin script from the filesystem, using the PATH_PLUGINS
     * config property as the base directory.
     *
     * <p>Returns the file content on success, or throws a
     * {@link PluginLoadException} with a user-friendly message describing
     * exactly what went wrong and how to fix it.</p>
     *
     * @param relativePath path relative to the plugins folder (e.g. "pageScanner/build/scanner.min.js")
     * @return the file content as a UTF-8 String
     * @throws PluginLoadException if the plugins folder or the script file cannot be resolved
     */
    public static String loadPluginScript(String relativePath) {
        // ── 1. Read the raw path_plugins property ───────────────────────────
        String configured = arPropertyManager.getProperty(ARPropertyEnum.PATH_PLUGINS);
        boolean isConfigured = configured != null && !configured.isBlank();

        // ── 2. If path_plugins is not set at all → tell the user to set it ─
        if (!isConfigured) {
            log.error("loadPluginScript - path_plugins is not set in ARWeb.config");
            throw new PluginLoadException(
                    "Plugins folder is not configured",
                    "<span style='color: #E65100; font-weight: bold;'>The property 'path_plugins' is not set in ARWeb.config.</span>",
                    "<span style='font-style: italic;'>Please open Settings and set the path_plugins property pointing to your plugins folder.</span>",
                    "<span style='color: #455A64;'>Example:  path_plugins = C:\\ARWeb\\plugins</span>");
        }

        // ── 3. path_plugins is set - check the folder exists ────────────────
        Path pluginsPath = Paths.get(configured);

        if (!Files.isDirectory(pluginsPath)) {
            log.error("loadPluginScript - path_plugins folder does not exist: {}", configured);
            throw new PluginLoadException(
                    "Plugins folder does not exist",
                    "<span style='color: #E65100; font-weight: bold;'>The folder was not found on disk:</span>  "
                            + configured,
                    "<span style='font-style: italic;'>Please verify that the folder exists and contains the plugin sub-folders (pageScanner, hoverPick, etc.).</span>",
                    "<span style='color: #455A64;'>You can change it in Settings > path_plugins.</span>");
        }

        // ── 4. Check that the script file exists inside the folder ──────────
        Path scriptPath = pluginsPath.resolve(relativePath);

        if (!Files.exists(scriptPath)) {
            String pluginName =
                    relativePath.contains("/") ? relativePath.substring(0, relativePath.indexOf('/')) : relativePath;

            log.error(
                    "loadPluginScript - Plugin script not found: {}: expected at {}",
                    pluginName,
                    scriptPath.toAbsolutePath());
            throw new PluginLoadException(
                    "Plugin script not found: " + pluginName,
                    "<span style='color: #E65100; font-weight: bold;'>File not found:</span>  "
                            + scriptPath.toAbsolutePath(),
                    "<span style='font-style: italic;'>The 'path_plugins' is set to:</span>  <b>" + configured + "</b>",
                    "<span style='color: #455A64;'>Make sure the '" + pluginName
                            + "' plugin is installed in that folder and its build output exists.</span>");
        }

        // ── 5. Read the file ────────────────────────────────────────────────
        try {
            return Files.readString(scriptPath, StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("loadPluginScript - Failed to read plugin script: {}", e.getMessage(), e);
            throw new PluginLoadException(
                    "Failed to read plugin script",
                    "<span style='color: #E65100; font-weight: bold;'>The file exists but could not be read:</span>  "
                            + scriptPath.toAbsolutePath(),
                    "<span style='font-style: italic;'>Check file permissions and ensure it is not locked by another process.</span>",
                    null,
                    e);
        }
    }

    /**
     * Exception thrown when a plugin script cannot be loaded.
     * Carries user-friendly messages suitable for display in a
     * {@code showCustomModalDialogDragWin11Timer} dialog.
     *
     * <p>The three message lines map directly to message2 / message3 / message4
     * in the dialog (message1 is the errorHeader set by the caller).</p>
     */
    static class PluginLoadException extends RuntimeException {
        private final String userTitle;
        private final String msg1;
        private final String msg2;
        private final String msg3;

        PluginLoadException(String userTitle, String msg1, String msg2, String msg3) {
            super(userTitle + " - " + msg1);
            this.userTitle = userTitle;
            this.msg1 = msg1;
            this.msg2 = msg2;
            this.msg3 = msg3;
        }

        PluginLoadException(String userTitle, String msg1, String msg2, String msg3, Throwable cause) {
            super(userTitle + " - " + msg1, cause);
            this.userTitle = userTitle;
            this.msg1 = msg1;
            this.msg2 = msg2;
            this.msg3 = msg3;
        }

        /** Short title for the dialog title bar */
        public String getUserTitle() {
            return userTitle;
        }

        /** Dialog message line 2 - what went wrong */
        public String getMsg1() {
            return msg1;
        }

        /** Dialog message line 3 - where to look / what is configured */
        public String getMsg2() {
            return msg2;
        }

        /** Dialog message line 4 - how to fix it */
        public String getMsg3() {
            return msg3;
        }
    }

    /**
     * Pretty one-block log for a {@link PluginLoadException}. No stack trace —
     * the exception carries the same info in its fields, so a clean banner is
     * enough for the operator. Use this from every caller that catches
     * {@code PluginLoadException} to keep the logs uniform.
     */
    static void logPluginLoadFailure(String caller, PluginLoadException ple) {
        String sep = "──────────────────────────────────────────────────────────────";
        String title = ple.getUserTitle();
        String m1 = ple.getMsg1() != null ? ple.getMsg1() : "";
        String m2 = ple.getMsg2() != null ? ple.getMsg2() : "";
        String m3 = ple.getMsg3() != null ? ple.getMsg3() : "";
        log.error(
                "\n┌{}\n│ [plugin load failed] {}\n│   caller : {}\n│   reason : {}\n│   detail : {}\n│   fix    : {}\n└{}",
                sep,
                title,
                caller,
                m1,
                m2,
                m3,
                sep);
    }

    // Static utility class.
    private PerformPreLoad() {}
}
