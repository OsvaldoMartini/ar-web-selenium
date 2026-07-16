package com.allinweb.ch.facade;

import com.allinweb.ch.driver.ARWebDriver;
import java.util.Collections;
import java.util.List;
import org.openqa.selenium.WebDriver;

/** Owns page-scanner term resolution and backend scan invocation. */
public final class ScannerPageScanService {
    private final ScannerPort scanner;

    public ScannerPageScanService(PerformListElements scanner) {
        this(new PerformListElementsScannerPort(scanner));
    }

    ScannerPageScanService(ScannerPort scanner) {
        this.scanner = scanner;
    }

    public String[] terms(String searchTerms, String[] selectedProfileTerms, String[] fallbackTerms) {
        if (searchTerms != null && !searchTerms.trim().isEmpty()) {
            return searchTerms.split("\\s*,\\s*");
        }
        if (selectedProfileTerms != null && selectedProfileTerms.length > 0) {
            return selectedProfileTerms;
        }
        return fallbackTerms == null ? new String[0] : fallbackTerms;
    }

    public Request standardRequest(
            String searchTerms,
            String[] selectedProfileTerms,
            String[] fallbackTerms,
            int port,
            int homeBankingId,
            int botJobId,
            List<String> extendedRules) {
        return new Request(
                terms(searchTerms, selectedProfileTerms, fallbackTerms),
                false,
                port,
                ScannerSearchRoute.standardPageScanner(),
                homeBankingId,
                botJobId,
                extendedRules == null ? Collections.emptyList() : extendedRules);
    }

    public PerformListElements.ScanResult scan(ARWebDriver arWebDriver, WebDriver driver, Request request) {
        List<String> rules = request.extendedRules() == null ? Collections.emptyList() : request.extendedRules();
        return scanner.scanElements(
                arWebDriver,
                driver,
                request.terms(),
                request.searchHiddenFields(),
                request.port(),
                request.route().sourceSessionId(),
                request.route().destinationSessionId(),
                request.route().operationId(),
                request.homeBankingId(),
                request.botJobId(),
                rules);
    }

    public record Request(
            String[] terms,
            boolean searchHiddenFields,
            int port,
            ScannerSearchRoute route,
            int homeBankingId,
            int botJobId,
            List<String> extendedRules) {}

    interface ScannerPort {
        PerformListElements.ScanResult scanElements(
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
                List<String> extendedRules);
    }

    private static final class PerformListElementsScannerPort implements ScannerPort {
        private final PerformListElements scanner;

        private PerformListElementsScannerPort(PerformListElements scanner) {
            this.scanner = scanner;
        }

        @Override
        public PerformListElements.ScanResult scanElements(
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
            return scanner.scanElements(
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
    }
}
