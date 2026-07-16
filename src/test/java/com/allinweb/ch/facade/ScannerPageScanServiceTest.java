package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.allinweb.ch.driver.ARWebDriver;
import com.allinweb.ch.model.ElementDTO;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.WebDriver;

class ScannerPageScanServiceTest {

    @Test
    void splitsExplicitSearchTerms() {
        ScannerPageScanService service = new ScannerPageScanService(new RecordingScanner());

        String[] terms = service.terms("button, label,input", new String[] {"selected"}, new String[] {"fallback"});

        assertArrayEquals(new String[] {"button", "label", "input"}, terms);
    }

    @Test
    void usesSelectedProfileTermsWhenSearchTextIsBlank() {
        ScannerPageScanService service = new ScannerPageScanService(new RecordingScanner());

        String[] terms = service.terms(" ", new String[] {"selected"}, new String[] {"fallback"});

        assertArrayEquals(new String[] {"selected"}, terms);
    }

    @Test
    void usesFallbackTermsWhenNoSearchTextOrProfileExists() {
        ScannerPageScanService service = new ScannerPageScanService(new RecordingScanner());

        String[] terms = service.terms(null, null, new String[] {"fallback"});

        assertArrayEquals(new String[] {"fallback"}, terms);
    }

    @Test
    void forwardsScanRequestToPerformListElements() {
        RecordingScanner scanner = new RecordingScanner();
        ScannerPageScanService service = new ScannerPageScanService(scanner);
        ScannerSearchRoute route = new ScannerSearchRoute("scannerTool", "scannerGrid", "searchTerms");
        ScannerPageScanService.Request request = new ScannerPageScanService.Request(
                new String[] {"button"},
                true,
                54525,
                route,
                7,
                42,
                List.of("attr:data-testid"));

        PerformListElements.ScanResult result = service.scan(null, null, request);

        assertSame(scanner.result, result);
        assertArrayEquals(new String[] {"button"}, scanner.terms);
        assertEquals(true, scanner.hidden);
        assertEquals(54525, scanner.port);
        assertEquals("scannerTool", scanner.sessionId);
        assertEquals("scannerGrid", scanner.destination);
        assertEquals("searchTerms", scanner.operationId);
        assertEquals(7, scanner.homeBankingId);
        assertEquals(42, scanner.botJobId);
        assertEquals(List.of("attr:data-testid"), scanner.extendedRules);
    }

    private static final class RecordingScanner implements ScannerPageScanService.ScannerPort {
        private final PerformListElements.ScanResult result =
                PerformListElements.ScanResult.ofElements(List.of(new ElementDTO()));
        private String[] terms;
        private boolean hidden;
        private int port;
        private String sessionId;
        private String destination;
        private String operationId;
        private int homeBankingId;
        private int botJobId;
        private List<String> extendedRules;

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
            this.terms = dataArray;
            this.hidden = searchHiddenFields;
            this.port = port;
            this.sessionId = sessionId;
            this.destination = destination;
            this.operationId = operationId;
            this.homeBankingId = homeBankingId;
            this.botJobId = botJobId;
            this.extendedRules = extendedRules;
            return result;
        }
    }
}
