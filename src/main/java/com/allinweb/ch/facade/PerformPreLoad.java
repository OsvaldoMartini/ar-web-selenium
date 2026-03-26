package com.allinweb.ch.facade;

import com.allinweb.ch.util.ErrorMessage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;

@Slf4j
public class PerformPreLoad {

    protected static volatile PerformPreLoad instance;
    private static JavascriptExecutor jsExecutor;

    /**
     * Cached scanner bundle. Null until the first call to dynamicLoadElementsDTO().
     * Loaded lazily so a missing build artifact does NOT crash the JVM at startup —
     * the error surfaces only when a scan is actually triggered.
     *
     * Source tree : src/main/resources/plugins/pageScanner/
     * Build output: src/main/resources/plugins/pageScanner/build/scanner.min.js
     *
     * To rebuild the bundle:
     *   cd src/main/resources/plugins/pageScanner
     *   npx esbuild index.js --bundle --minify --outfile=build/scanner.min.js
     * Or use the Maven frontend-maven-plugin target configured in pom.xml.
     */
    private static volatile String jsScanner = null;

    /**
     * Loads (and caches) the minified scanner bundle from the classpath.
     * Thread-safe via double-checked locking on jsScanner.
     *
     * @throws IllegalStateException if the build artifact is missing from the classpath.
     * @throws RuntimeException      if the resource stream cannot be read.
     */
    private static String getJsScanner() {
        if (jsScanner == null) {
            synchronized (PerformPreLoad.class) {
                if (jsScanner == null) {
                    jsScanner = loadScript("plugins/pageScanner/build/scanner.min.js");
                }
            }
        }
        return jsScanner;
    }

    private static String loadScript(String classpathPath) {
        try (InputStream is = PerformPreLoad.class
                .getResourceAsStream("/" + classpathPath)) {
            if (is == null) {
                throw new IllegalStateException(
                        "Scanner script not found on classpath: " + classpathPath
                                + " — run the pageScanner build step first: "
                                + "npx esbuild index.js --bundle --minify "
                                + "--outfile=build/scanner.min.js");
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException(
                    "Failed to load scanner script: " + classpathPath, e);
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
            jsExecutor = (JavascriptExecutor) driver;
            jsExecutor.executeScript(
                    getJsScanner(),
                    dataList,           // arguments[0] — searchTerms
                    searchHiddenFields, // arguments[1] — searchHiddenFields
                    port,               // arguments[2] — WS port
                    sessionId,          // arguments[3] — sessionId
                    destination,        // arguments[4] — destination
                    operationId,        // arguments[5] — operationId
                    homeBankingId,      // arguments[6] — homeBankingId
                    botJobId);          // arguments[7] — botJobId
            return null;
        } catch (Exception error) {
            log.error("PerformPreLoad — scanner injection failed: {}", error.getMessage(), error);
            return new ErrorMessage(
                    "Error running Scanner",
                    "Dynamic Load ElementsDTO error",
                    error.getMessage());
        }
    }
}