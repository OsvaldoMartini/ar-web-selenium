package com.allinweb.ch.facade;

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

            List<ElementDTO> elements =
                    arWebDriver.currentPlaywrightDriver().scanElements(dataArray, searchHiddenFields);
            processScanElements(arWebDriver, elements, homeBankingId, botJobId);
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
            ARWebDriver arWebDriver, List<ElementDTO> elements, int homeBankingId, int botJobId) {
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
            Integer cfgHomeUrlId = currentHomeUrlId();

            if (arWebDriver != null && arWebDriver.currentPlaywrightDriver() != null) {
                com.allinweb.ch.driver.ARPlaywrightDriver pw = arWebDriver.currentPlaywrightDriver();
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
                Integer homeUrlId = currentHomeUrlId();
                ElementLocatorRepository.getInstance().upsertOnPickBatch(asArray, hbId, homeUrlId);
            } catch (Exception locEx) {
                log.warn("Locator upsert failed (non-fatal): {}", locEx.getMessage());
            }

            // Source-of-truth registry: upsert every scanned element (OCR-corrected someText/definedName
            // already applied above) scoped by organization + bot job, stamping last_scanned_at.
            try {
                String pageUrl = currentPageUrl(arWebDriver);
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
