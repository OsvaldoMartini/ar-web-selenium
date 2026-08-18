package com.allinweb.ch.facade;

import com.allinweb.ch.db.ScannedPageIdentity;
import com.allinweb.ch.driver.ARWebDriver;
import com.allinweb.ch.model.ElementDTO;
import com.allinweb.ch.util.ARPropertyEnum;
import com.allinweb.ch.util.ARPropertyManager;
import com.allinweb.ch.util.ErrorMessage;
import com.allinweb.ch.util.PageDiagnosticDumper;
import com.allinweb.ch.util.PageOcrDumper;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PerformListElements {
    private static final PerformLists performLists = PerformLists.getInstance();

    protected static volatile PerformListElements instance;

    /** Flag to log only the first Playwright page scan per bot-job run. */
    private volatile boolean loggedFirstCall = false;

    /**
     * Reset the first-call log flag. Call this at the start of each bot-job execution
     * so the first Playwright scan is logged again.
     */
    public void resetFirstCallLog() {
        this.loggedFirstCall = false;
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
     * Scans the active Playwright page and returns the parsed element list.
     *
     * Side effects are identical: resets and repopulates {@code performLists}
     * target-element cache on success.
     */
    public ScanResult scanElements(
            ARWebDriver arWebDriver,
            String[] dataArray,
            boolean searchHiddenFields,
            int homeBankingId,
            int botJobId,
            String sessionId) {
        return runScan(arWebDriver, dataArray, searchHiddenFields, homeBankingId, botJobId, sessionId);
    }

    private ScanResult runScan(
            ARWebDriver arWebDriver,
            String[] dataArray,
            boolean searchHiddenFields,
            int homeBankingId,
            int botJobId,
            String sessionId) {

        try {
            if (!loggedFirstCall) {
                log.info(">> Running scanner [Playwright] - session={}, botJob={}", sessionId, botJobId);
                loggedFirstCall = true;
            }

            if (arWebDriver == null || arWebDriver.currentPlaywrightDriver() == null) {
                return ScanResult.ofError(new ErrorMessage(
                        "Playwright Page Scanner",
                        "Browser not available",
                        "No active Playwright browser is attached"));
            }

            ScannedPageIdentity scannedPage =
                    ScannedPageIdentity.fromLiveUrl(currentPageUrl(arWebDriver));
            List<ElementDTO> elements =
                    arWebDriver.currentPlaywrightDriver().scanElements(dataArray, searchHiddenFields);
            ScannedPageIdentity afterScan =
                    ScannedPageIdentity.fromLiveUrl(currentPageUrl(arWebDriver));
            if (!scannedPage.pageKey().equals(afterScan.pageKey())) {
                return ScanResult.ofError(new ErrorMessage(
                        "Playwright Page Scanner",
                        "Page changed during scan",
                        "Scan the current browser page again"));
            }
            processScanElements(
                    arWebDriver, elements, homeBankingId, botJobId, scannedPage);
            return ScanResult.ofElements(elements);
        } catch (Exception error) {
            return ScanResult.ofError(
                    new ErrorMessage("Error running Scanner", "Playwright page scan error", error.getMessage()));
        }
    }

    private static String currentPageUrl(ARWebDriver arWebDriver) {
        try {
            return arWebDriver == null || arWebDriver.currentPlaywrightDriver() == null
                    ? null
                    : arWebDriver.currentPlaywrightDriver().currentUrl();
        } catch (Exception ignore) {
            // best-effort
        }
        return null;
    }

    private static Integer currentHomeUrlId() {
        return ScannerCurrentJobContext.getInstance().currentHomeUrlId();
    }

    private void processScanElements(
            ARWebDriver arWebDriver,
            List<ElementDTO> elements,
            int homeBankingId,
            int botJobId,
            ScannedPageIdentity scannedPage) {
        performLists.resetListElements();

        if (elements == null || elements.isEmpty()) {
            return;
        }

        boolean targetCachePopulated = false;
        try {
            ElementDTO[] asArray = elements.toArray(new ElementDTO[0]);
            String jsonPath = ARPropertyManager.getInstance().getProperty(ARPropertyEnum.PATH_DB);
            PerformMessage performMessage = PerformMessage.getInstance();

            Integer cfgHbId = homeBankingId > 0 ? homeBankingId : null;
            Integer cfgHomeUrlId = currentHomeUrlId();

            if (arWebDriver != null && arWebDriver.currentPlaywrightDriver() != null) {
                com.allinweb.ch.driver.ARPlaywrightDriver pw = arWebDriver.currentPlaywrightDriver();
                PageDiagnosticDumper.dumpRectsFromElements(pw, asArray, jsonPath, "page-HP");
                PageOcrDumper.runAndDump(pw, asArray, jsonPath, "page-HP", cfgHbId, cfgHomeUrlId);
            }

            com.allinweb.ch.model.OcrConfig resolverCfg =
                    OcrConfigService.getInstance().resolveFor(cfgHbId, cfgHomeUrlId);
            resolveAndPopulateTargetCache(
                    asArray,
                    java.nio.file.Paths.get(
                            jsonPath, com.allinweb.ch.util.PageDiagnosticDumper.SUBFOLDER, "ocr-correlation-HP.json"),
                    resolverCfg);
            targetCachePopulated = true;

            try {
                Integer hbId = homeBankingId > 0 ? homeBankingId : null;
                Integer homeUrlId = currentHomeUrlId();
                ElementLocatorRepository.getInstance().upsertOnPickBatch(asArray, hbId, homeUrlId);
            } catch (Exception locEx) {
                log.warn("Locator upsert failed (non-fatal): {}", locEx.getMessage());
            }

            // Source-of-truth registry: upsert every scanned element (OCR-corrected someText/definedName
            // already applied above) scoped by organization + Bot Job + live page, stamping
            // last_scanned_at.
            try {
                ScannedPageIdentity persistencePage =
                        ScannedPageIdentity.fromLiveUrl(currentPageUrl(arWebDriver));
                if (!scannedPage.pageKey().equals(persistencePage.pageKey())) {
                    throw new IllegalStateException(
                            "Browser page changed before scanner persistence; registry update skipped");
                }
                int[] up = PerformDataBase.getInstance()
                        .upsertScannedElements(
                                cfgHbId,
                                botJobId > 0 ? botJobId : null,
                                cfgHomeUrlId,
                                scannedPage.actualUrl(),
                                Arrays.asList(asArray));
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
        } finally {
            // Diagnostics/OCR persistence is best-effort. If it failed before resolution,
            // preserve the historical behavior of making the raw scan available to callers.
            if (!targetCachePopulated) {
                performLists.resetListElements();
                performLists.addMapElementsTarget(elements);
            }
        }
    }

    static void resolveAndPopulateTargetCache(
            ElementDTO[] elements,
            java.nio.file.Path ocrCorrelationFile,
            com.allinweb.ch.model.OcrConfig resolverConfig) {
        ElementTextResolver.resolveAll(elements, ocrCorrelationFile, resolverConfig);
        performLists.addMapElementsTarget(Arrays.asList(elements));
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
