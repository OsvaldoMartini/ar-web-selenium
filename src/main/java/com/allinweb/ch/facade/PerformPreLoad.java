package com.allinweb.ch.facade;

import com.allinweb.ch.util.ARPropertyEnum;
import com.allinweb.ch.util.ARPropertyManager;
import com.allinweb.ch.util.ErrorMessage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;

@Slf4j
public class PerformPreLoad {

    protected static volatile PerformPreLoad instance;

    private static final ARPropertyManager arPropertyManager = ARPropertyManager.getInstance();

    /**
     * Cached scanner bundle. Null until the first call to dynamicLoadElementsDTO().
     * Loaded lazily so a missing file does NOT crash the JVM at startup —
     * the error surfaces only when a scan is actually triggered.
     *
     * Loaded from the filesystem path defined by PATH_PLUGINS in ARWeb.config:
     *   {path_plugins}/pageScanner/build/scanner.min.js
     *
     * To rebuild the bundle:
     *   cd {path_plugins}/pageScanner
     *   npx esbuild index.js --bundle --minify --outfile=build/scanner.min.js
     */
    private static volatile String jsScanner = null;

    /** Relative path within the plugins folder */
    private static final String SCANNER_RELATIVE_PATH = "pageScanner/build/scanner.min.js";

    /**
     * Loads (and caches) the minified scanner bundle from the PATH_PLUGINS folder.
     * Thread-safe via double-checked locking on jsScanner.
     *
     * @throws IllegalStateException if the config property or file is missing.
     * @throws RuntimeException      if the file cannot be read.
     */
    private static String getJsScanner() {
        if (jsScanner == null) {
            synchronized (PerformPreLoad.class) {
                if (jsScanner == null) {
                    jsScanner = loadPluginScript(SCANNER_RELATIVE_PATH);
                    log.info(
                            "PerformPreLoad — scanner script loaded from plugins folder ({} chars)",
                            jsScanner.length());
                }
            }
        }
        return jsScanner;
    }

    /**
     * Reads a plugin script from the filesystem, using the PATH_PLUGINS
     * config property as the base directory.
     *
     * @param relativePath path relative to the plugins folder (e.g. "pageScanner/build/scanner.min.js")
     * @return the file content as a UTF-8 String
     * @throws IllegalStateException if PATH_PLUGINS is not configured or the file doesn't exist
     * @throws RuntimeException      if an I/O error occurs
     */
    static String loadPluginScript(String relativePath) {
        String pluginsDir = arPropertyManager.resolvePluginsDir();

        Path scriptPath = Paths.get(pluginsDir, relativePath);

        if (!Files.exists(scriptPath)) {
            throw new IllegalStateException("Plugin script not found: " + scriptPath.toAbsolutePath()
                    + " — ensure the file exists in the plugins folder: " + pluginsDir);
        }

        try {
            return Files.readString(scriptPath, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read plugin script: " + scriptPath.toAbsolutePath(), e);
        }
    }

    // Private constructor — use getInstance()
    private PerformPreLoad() {}

    public static PerformPreLoad getInstance() {
        if (instance == null) {
            synchronized (PerformPreLoad.class) {
                if (instance == null) {
                    instance = new PerformPreLoad();
                }
            }
        }
        return instance;
    }

    /**
     * Injects the page-scanner bundle into the current browser page via
     * Selenium's JavascriptExecutor.
     *
     * Argument mapping (matches index.js IIFE parameter order):
     *   arguments[0]  searchTerms        — String[] filter tags
     *   arguments[1]  searchHiddenFields — boolean
     *   arguments[2]  port               — WebSocket server port
     *   arguments[3]  sessionId          — UUID string
     *   arguments[4]  destination        — target session ID for WS routing
     *   arguments[5]  operationId        — operation label string
     *   arguments[6]  homeBankingId      — int
     *   arguments[7]  botJobId           — int
     *
     * @return null on success, or an ErrorMessage on failure.
     */
    public ErrorMessage dynamicLoadElementsDTO(
            WebDriver driver,
            String[] dataArray,
            boolean searchHiddenFields,
            int port,
            String sessionId,
            String destination,
            String operationId,
            int homeBankingId,
            int botJobId) {

        List<String> dataList = Arrays.asList(dataArray);
        try {
            JavascriptExecutor executor = (JavascriptExecutor) driver;
            executor.executeScript(
                    getJsScanner(),
                    dataList, // arguments[0] — searchTerms
                    searchHiddenFields, // arguments[1] — searchHiddenFields
                    port, // arguments[2] — WS port
                    sessionId, // arguments[3] — sessionId
                    destination, // arguments[4] — destination
                    operationId, // arguments[5] — operationId
                    homeBankingId, // arguments[6] — homeBankingId
                    botJobId); // arguments[7] — botJobId
            return null;
        } catch (Exception error) {
            log.error("PerformPreLoad — scanner injection failed: {}", error.getMessage(), error);
            return new ErrorMessage("Error running Scanner", "Dynamic Load ElementsDTO error", error.getMessage());
        }
    }
}
