package com.allinweb.ch.facade;

import com.allinweb.ch.driver.ARWebDriver;
import com.allinweb.ch.model.ElementDTO;
import com.allinweb.ch.util.ARPropertyEnum;
import com.allinweb.ch.util.ARPropertyManager;
import com.allinweb.ch.util.ErrorMessage;
import com.allinweb.ch.util.JsScanResultDTO;
import com.allinweb.ch.util.PageDiagnosticDumper;
import com.allinweb.ch.util.PageOcrDumper;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Slf4j
public class PerformListElements {
    private static final Logger logOperations = LoggerFactory.getLogger("com.allinweb.operations");
    private Gson gson = new GsonBuilder().create();

    private static final PerformLists performLists = PerformLists.getInstance();

    protected static volatile PerformListElements instance;

    /** Flag to log only the first searchListAsync injection per bot-job run. */
    private volatile boolean loggedFirstCall = false;

    /**
     * Reset the first-call log flag. Call this at the start of each bot-job execution
     * so the first searchListAsync injection is logged again.
     */
    public void resetFirstCallLog() {
        this.loggedFirstCall = false;
    }

    /**
     * Cached searchListAsync bundle. Null until the first call to dynamicLoadElementsDTO().
     * Loaded lazily so a missing file does NOT crash the JVM at startup -
     * the error surfaces only when a scan is actually triggered.
     *
     * Loaded from the filesystem path defined by PATH_PLUGINS in ARWeb.config:
     *   {path_plugins}/searchListAsync/build/searchListAsync.min.js
     *
     * To rebuild the bundle:
     *   cd {path_plugins}/searchListAsync
     *   npx esbuild index.js --bundle --minify --outfile=build/searchListAsync.min.js
     */
    private static volatile String jsSearchListAsync = null;

    /** Relative path within the plugins folder */
    private static final boolean useNoEncrypted = false;

    public static final String SEARCH_LIST_ASYNC_RELATIVE_PATH = "searchListAsync/searchListAsync.min.enc";
    public static final String SEARCH_LIST_ASYNC_RELATIVE_PATH_MIN = "searchListAsync/build/searchListAsync.min.js";
    public static final String SEARCH_LIST_ASYNC_RELATIVE_PATH_ORIG_MIN =
            "searchListAsync/build/script-search-in-use-list-async.min.js";
    public static final String SEARCH_LIST_ASYNC_RELATIVE_PATH_NOT_MIN =
            "searchListAsync/build/script-search-in-use-list-async.js";
    public static final String SEARCH_LIST_ASYNC_RELATIVE_PATH_MANUAL =
            "searchListAsync/build/script-search-in-use-list-async-manual.js";

    /**
     * Loads (and caches) the minified searchListAsync bundle from the PATH_PLUGINS folder.
     * Thread-safe via double-checked locking on jsSearchListAsync.
     *
     * @throws PerformPreLoad.PluginLoadException if the config property or file is missing.
     */
    private static String getJsSearchListAsync() {
        if (jsSearchListAsync == null) {
            synchronized (PerformListElements.class) {
                if (jsSearchListAsync == null) {
                    jsSearchListAsync = EncryptedPluginLoader.getInstance()
                            .loadPlugin(
                                    useNoEncrypted
                                            ? SEARCH_LIST_ASYNC_RELATIVE_PATH_MIN
                                            : SEARCH_LIST_ASYNC_RELATIVE_PATH);
                    log.info(
                            "PerformListElements - searchListAsync script loaded from plugins folder ({} chars)",
                            jsSearchListAsync.length());
                }
            }
        }
        return jsSearchListAsync;
    }

    /** Clear cache so the script reloads from disk on next injection. */
    public static void reloadScript() {
        synchronized (PerformListElements.class) {
            jsSearchListAsync = null;
            log.info("PerformListElements - searchListAsync cache cleared");
        }
    }

    // Private constructor to prevent instantiation
    private PerformListElements() {}

    public static PerformListElements getInstance() {
        if (instance == null) {
            synchronized (PerformListElements.class) {
                if (instance == null) {
                    instance = new PerformListElements();
                }
            }
        }
        return instance;
    }

    /**
     * Injects the searchListAsync bundle into the current browser page via
     * Selenium's executeAsyncScript.
     *
     * Argument mapping (matches index.js IIFE parameter order):
     *   arguments[0]  searchTerms        - String[] filter tags
     *   arguments[1]  searchHiddenFields - boolean
     *   arguments[2]  port               - WebSocket server port (unused, kept for alignment)
     *   arguments[3]  sessionId          - UUID string
     *   arguments[4]  destination        - target session ID for routing
     *   arguments[5]  operationId        - operation label string
     *   arguments[6]  homeBankingId      - int
     *   arguments[7]  botJobId           - int
     *   arguments[8]  extendedRules      - List&lt;String&gt; optional Match rules
     *                                       (tagPrefix:, tagSuffix:, attr:, attrPrefix:)
     *                                       from the new "Match rules:" field in
     *                                       ARScannedElementPane. Empty list = no extra
     *                                       rules (identical to legacy behaviour).
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
        return dynamicLoadElementsDTO(
                driver,
                dataArray,
                searchHiddenFields,
                port,
                sessionId,
                destination,
                operationId,
                homeBankingId,
                botJobId,
                Collections.emptyList());
    }

    public ErrorMessage dynamicLoadElementsDTO(
            ARWebDriver arWebDriver,
            WebDriver driver,
            String[] dataArray,
            boolean searchHiddenFields,
            int port,
            String sessionId,
            String destination,
            String operationId,
            int homeBankingId,
            int botJobId) {
        return dynamicLoadElementsDTO(
                arWebDriver,
                driver,
                dataArray,
                searchHiddenFields,
                port,
                sessionId,
                destination,
                operationId,
                homeBankingId,
                botJobId,
                Collections.emptyList());
    }

    /** Overload accepting extended "Match rules:" entries. */
    public ErrorMessage dynamicLoadElementsDTO(
            WebDriver driver,
            String[] dataArray,
            boolean searchHiddenFields,
            int port,
            String sessionId,
            String destination,
            String operationId,
            int homeBankingId,
            int botJobId,
            List<String> extendedRules) {
        return runScan(
                        driver,
                        dataArray,
                        searchHiddenFields,
                        port,
                        sessionId,
                        destination,
                        operationId,
                        homeBankingId,
                        botJobId,
                        extendedRules)
                .error;
    }

    /** Overload accepting ARWebDriver so the scanner can use Playwright when enabled. */
    public ErrorMessage dynamicLoadElementsDTO(
            ARWebDriver arWebDriver,
            WebDriver driver,
            String[] dataArray,
            boolean searchHiddenFields,
            int port,
            String sessionId,
            String destination,
            String operationId,
            int homeBankingId,
            int botJobId,
            List<String> extendedRules) {
        return runScan(
                        arWebDriver,
                        driver,
                        dataArray,
                        searchHiddenFields,
                        port,
                        sessionId,
                        destination,
                        operationId,
                        homeBankingId,
                        botJobId,
                        extendedRules)
                .error;
    }

    /**
     * Same scan as {@link #dynamicLoadElementsDTO}, but also returns the parsed
     * element list so callers can forward it via WebSocket without re-running the JS.
     *
     * Side effects are identical: resets and repopulates {@code performLists}
     * target-element cache on success.
     */
    public ScanResult scanElements(
            WebDriver driver,
            String[] dataArray,
            boolean searchHiddenFields,
            int port,
            String sessionId,
            String destination,
            String operationId,
            int homeBankingId,
            int botJobId) {
        return scanElements(
                driver,
                dataArray,
                searchHiddenFields,
                port,
                sessionId,
                destination,
                operationId,
                homeBankingId,
                botJobId,
                Collections.emptyList());
    }

    public ScanResult scanElements(
            ARWebDriver arWebDriver,
            WebDriver driver,
            String[] dataArray,
            boolean searchHiddenFields,
            int port,
            String sessionId,
            String destination,
            String operationId,
            int homeBankingId,
            int botJobId) {
        return scanElements(
                arWebDriver,
                driver,
                dataArray,
                searchHiddenFields,
                port,
                sessionId,
                destination,
                operationId,
                homeBankingId,
                botJobId,
                Collections.emptyList());
    }

    /** Overload accepting extended "Match rules:" entries. */
    public ScanResult scanElements(
            WebDriver driver,
            String[] dataArray,
            boolean searchHiddenFields,
            int port,
            String sessionId,
            String destination,
            String operationId,
            int homeBankingId,
            int botJobId,
            List<String> extendedRules) {
        return runScan(
                driver,
                dataArray,
                searchHiddenFields,
                port,
                sessionId,
                destination,
                operationId,
                homeBankingId,
                botJobId,
                extendedRules);
    }

    /** Overload accepting ARWebDriver so the scanner can use Playwright when enabled. */
    public ScanResult scanElements(
            ARWebDriver arWebDriver,
            WebDriver driver,
            String[] dataArray,
            boolean searchHiddenFields,
            int port,
            String sessionId,
            String destination,
            String operationId,
            int homeBankingId,
            int botJobId,
            List<String> extendedRules) {
        return runScan(
                arWebDriver,
                driver,
                dataArray,
                searchHiddenFields,
                port,
                sessionId,
                destination,
                operationId,
                homeBankingId,
                botJobId,
                extendedRules);
    }

    private ScanResult runScan(
            WebDriver driver,
            String[] dataArray,
            boolean searchHiddenFields,
            int port,
            String sessionId,
            String destination,
            String operationId,
            int homeBankingId,
            int botJobId,
            List<String> extendedRules) {
        return runScan(
                null,
                driver,
                dataArray,
                searchHiddenFields,
                port,
                sessionId,
                destination,
                operationId,
                homeBankingId,
                botJobId,
                extendedRules);
    }

    private ScanResult runScan(
            ARWebDriver arWebDriver,
            WebDriver driver,
            String[] dataArray,
            boolean searchHiddenFields,
            int port,
            String sessionId,
            String destination,
            String operationId,
            int homeBankingId,
            int botJobId,
            List<String> extendedRules) {

        List<String> dataList = Arrays.asList(dataArray);
        List<String> rulesList = extendedRules == null ? Collections.emptyList() : extendedRules;
        try {
            if (!loggedFirstCall) {
                log.info(
                        ">> Running scanner [{}] - session={}, botJob={}",
                        arWebDriver != null && arWebDriver.isPlaywrightEnabled() ? "Playwright" : "searchListAsync",
                        sessionId,
                        botJobId);
                loggedFirstCall = true;
            }

            if (arWebDriver != null && arWebDriver.isPlaywrightEnabled()) {
                List<ElementDTO> elements =
                        arWebDriver.getPlaywrightDriver().scanElements(dataArray, searchHiddenFields);
                processScanElements(arWebDriver, driver, elements, homeBankingId, botJobId);
                return ScanResult.ofElements(elements);
            }

            PageDiagnosticDumper.dumpAll(
                    driver, ARPropertyManager.getInstance().getProperty(ARPropertyEnum.PATH_DB), "page-HP");

            JavascriptExecutor executor = (JavascriptExecutor) driver;

            driver.manage().timeouts().setScriptTimeout(java.time.Duration.ofSeconds(25));

            // searchListAsync (script-search-in-use-list-async.min.js) is an async IIFE that
            // captures the Selenium callback via `arguments[arguments.length-1]` and ends with
            //   })( arguments[0], arguments[1], ..., arguments[8] );
            // so it expects 9 positional executeAsyncScript args, not a single ctx object.
            // (Selenium appends the async callback as arguments[9] automatically.)
            //   [8] = extendedRules new Match rules: field (tagPrefix:/tagSuffix:/attr:/attrPrefix:)
            Object result = executor.executeAsyncScript(
                    getJsSearchListAsync(),
                    dataList, // searchTerms
                    searchHiddenFields, // hiddenFields
                    port, // socketPort
                    sessionId, // sessionId
                    destination, // destination
                    operationId, // operationId
                    homeBankingId, // homeBankingId
                    botJobId, // botJobId
                    rulesList); // extendedRules (tagPrefix / tagSuffix / attr / attrPrefix)

            if (result == null || !(result instanceof String)) {
                logOperations.warn("Cannot return any elements from the page");
                return ScanResult.ofError(new ErrorMessage(
                        "Dynamic Scanner Web Page",
                        "Dynamic Load ElementsDTO error",
                        "Cannot return any elements from the page"));
            }

            logOperations.info("JS async result: {}", result);

            String jsonScript = String.valueOf(result);

            JsScanResultDTO dto = gson.fromJson(jsonScript, JsScanResultDTO.class);
            List<ElementDTO> elements = dto.getElements() != null ? dto.getElements() : Collections.emptyList();

            processScanElements(arWebDriver, driver, elements, homeBankingId, botJobId);

            return ScanResult.ofElements(elements);
        } catch (PerformPreLoad.PluginLoadException ple) {
            PerformPreLoad.logPluginLoadFailure("PerformListElements", ple);
            return ScanResult.ofError(new ErrorMessage(
                    ple.getUserTitle(),
                    "Search List Async Plugin",
                    ple.getMsg1() + "\n" + (ple.getMsg2() != null ? ple.getMsg2() : "") + "\n"
                            + (ple.getMsg3() != null ? ple.getMsg3() : "")));
        } catch (Exception error) {
            return ScanResult.ofError(
                    new ErrorMessage("Error running Scanner", "Dynamic Load ElementsDTO error", error.getMessage()));
        }
    }

    /** Current page URL from whichever backend is active (Selenium or the single Playwright browser). */
    private static String currentPageUrl(ARWebDriver arWebDriver, WebDriver driver) {
        try {
            if (driver != null) {
                return driver.getCurrentUrl();
            }
            if (arWebDriver != null && arWebDriver.isPlaywrightEnabled()) {
                return arWebDriver.getPlaywrightDriver().currentUrl();
            }
        } catch (Exception ignore) {
            // best-effort
        }
        return null;
    }

    private void processScanElements(
            ARWebDriver arWebDriver, WebDriver driver, List<ElementDTO> elements, int homeBankingId, int botJobId) {
        performLists.resetListElements();
        performLists.addMapElementsTarget(elements);

        if (elements == null || elements.isEmpty()) {
            return;
        }

        try {
            ElementDTO[] asArray = elements.toArray(new ElementDTO[0]);
            String jsonPath = ARPropertyManager.getInstance().getProperty(ARPropertyEnum.PATH_DB);
            PerformMessage performMessage = PerformMessage.getInstance();

            Integer cfgHbId = homeBankingId > 0 ? homeBankingId : null;
            Integer cfgHomeUrlId = null;
            try {
                com.allinweb.ch.component.scene.ARScannedElementScene scene =
                        com.allinweb.ch.component.scene.ARScannedElementScene.getInstance();
                if (scene != null && scene.getCurrentBotJob() != null) {
                    cfgHomeUrlId = scene.getCurrentBotJob().getHomeUrlId();
                }
            } catch (Throwable ignore) {
                // scene unavailable bank-level scope only
            }

            // DOM rects + OCR: Selenium when present, otherwise the single Playwright browser.
            if (driver != null) {
                PageDiagnosticDumper.dumpRectsFromElements(driver, asArray, jsonPath, "page-HP");
                PageOcrDumper.runAndDump(driver, asArray, jsonPath, "page-HP", cfgHbId, cfgHomeUrlId);
            } else if (arWebDriver != null && arWebDriver.isPlaywrightEnabled()) {
                com.allinweb.ch.driver.ARPlaywrightDriver pw = arWebDriver.getPlaywrightDriver();
                PageDiagnosticDumper.dumpRectsFromElements(pw, asArray, jsonPath, "page-HP");
                PageOcrDumper.runAndDump(pw, asArray, jsonPath, "page-HP", cfgHbId, cfgHomeUrlId);
            }

            com.allinweb.ch.model.OcrConfig resolverCfg =
                    OcrConfigService.getInstance().resolveFor(cfgHbId, cfgHomeUrlId);
            ElementTextResolver.resolveAll(
                    asArray,
                    java.nio.file.Paths.get(
                            jsonPath, com.allinweb.ch.util.PageDiagnosticDumper.SUBFOLDER, "ocr-correlation-HP.json"),
                    resolverCfg);

            try {
                Integer hbId = homeBankingId > 0 ? homeBankingId : null;
                Integer homeUrlId = null;
                try {
                    com.allinweb.ch.component.scene.ARScannedElementScene scene =
                            com.allinweb.ch.component.scene.ARScannedElementScene.getInstance();
                    if (scene != null && scene.getCurrentBotJob() != null) {
                        homeUrlId = scene.getCurrentBotJob().getHomeUrlId();
                    }
                } catch (Throwable ignore) {
                    // scene unavailable bank-level scope only
                }
                ElementLocatorRepository.getInstance().upsertOnPickBatch(asArray, hbId, homeUrlId);
            } catch (Exception locEx) {
                log.warn("Locator upsert failed (non-fatal): {}", locEx.getMessage());
            }

            // Source-of-truth registry: upsert every scanned element (OCR-corrected someText/definedName
            // already applied above) scoped by organization + bot job, stamping last_scanned_at.
            try {
                String pageUrl = currentPageUrl(arWebDriver, driver);
                int[] up = PerformDataBase.getInstance()
                        .upsertScannedElements(
                                cfgHbId, botJobId > 0 ? botJobId : null, cfgHomeUrlId, pageUrl, Arrays.asList(asArray));
                log.info("scanned_element registry — inserted={} updated={} (bot={})", up[0], up[1], botJobId);
            } catch (Exception regEx) {
                log.warn("scanned_element upsert failed (non-fatal): {}", regEx.getMessage());
            }

            List<String> excludeList = List.of("optional", "blockMarked", "editMode");
            performMessage.outputJsonElementDTO(asArray, excludeList, "elementDTO-PS", jsonPath);

            List<String> aiExcludeList = List.of(
                    "optional",
                    "blockMarked",
                    "editMode",
                    "id",
                    "attributeData",
                    "typeElement",
                    "customXPath",
                    "shadowRoot",
                    "nestedShadow",
                    "searchAttributeValue",
                    "attributeType",
                    "attributeValue");
            performMessage.outputJsonElementDTO(asArray, aiExcludeList, "AI-ElementDTO-PS", jsonPath);
        } catch (Exception jsonError) {
            log.warn("PerformListElements - failed to persist element JSON: {}", jsonError.getMessage());
        }
    }

    /** Result bundle for {@link #scanElements}: either an error or the parsed list. */
    public static final class ScanResult {
        public final ErrorMessage error;
        public final List<ElementDTO> elements;

        private ScanResult(ErrorMessage error, List<ElementDTO> elements) {
            this.error = error;
            this.elements = elements;
        }

        static ScanResult ofError(ErrorMessage error) {
            return new ScanResult(error, Collections.emptyList());
        }

        static ScanResult ofElements(List<ElementDTO> elements) {
            return new ScanResult(null, elements);
        }
    }
}
