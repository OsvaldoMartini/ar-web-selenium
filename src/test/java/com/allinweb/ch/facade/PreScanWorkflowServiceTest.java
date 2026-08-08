package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.allinweb.ch.db.ScannedPageIdentity;
import com.allinweb.ch.driver.ARPlaywrightDriver;
import com.allinweb.ch.model.ElementDTO;
import com.allinweb.ch.model.FieldData;
import com.allinweb.ch.model.InstructionLoad;
import com.allinweb.ch.model.ScannerWorkspaceOperations;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class PreScanWorkflowServiceTest {

    @Test
    void scanUsesSafeDefaultsFiltersNoiseAndPublishesTerminalCount() {
        FakeBrowser browser = new FakeBrowser();
        browser.elements = List.of(element("input"), element("div"), element("button"));
        FakeDiagnostics diagnostics = new FakeDiagnostics();
        FakeSink sink = new FakeSink();
        PreScanWorkflowService service = new PreScanWorkflowService(browser, diagnostics, element -> new InstructionLoad());

        service.scan(context("https://example.test"), "", false, sink);

        assertArrayEquals(
                new String[] {"input", "textarea", "button", "a", "select", "label"},
                browser.searchTerms);
        assertEquals(2, sink.elements.size());
        assertEquals(2, diagnostics.resolvedCount);
        assertEquals(2, diagnostics.persistedCount);
        assertEquals("https://example.test", diagnostics.persistedPage.actualUrl());
        assertTrue(sink.statuses.contains("done:Found 2 web element(s).:2"));
        assertFalse(browser.running);
    }

    @Test
    void scanPersistsTheLiveBrowserPageInsteadOfTheConfiguredEndpoint() {
        FakeBrowser browser = new FakeBrowser();
        browser.currentUrl =
                "https://www.inlinea.ch/bscch/wb/ui/trading/forex/new?account=42#/order";
        browser.elements = List.of(element("button"));
        FakeDiagnostics diagnostics = new FakeDiagnostics();
        FakeSink sink = new FakeSink();
        PreScanWorkflowService service =
                new PreScanWorkflowService(browser, diagnostics, element -> null);
        String configuredEndpoint =
                "https://www.inlinea.ch/auth/ui/app/auth/flow/web-app/password";

        service.scan(
                context(configuredEndpoint),
                "button",
                false,
                sink);

        assertEquals(1, diagnostics.persistedCount);
        assertEquals(browser.currentUrl, diagnostics.persistedPage.actualUrl());
        assertNotEquals(configuredEndpoint, diagnostics.persistedPage.actualUrl());
        assertEquals(
                browser.currentUrl,
                diagnostics.persistedPage.normalizedUrl());
        assertTrue(diagnostics.persistedPage.pageKey().matches("url-v1:[0-9a-f]{64}"));
        assertTrue(sink.statuses.contains("done:Found 1 web element(s).:1"));
    }

    @Test
    void scanRefusesPersistenceWhenTheBrowserPageChangesDuringTheScan() {
        FakeBrowser browser = new FakeBrowser();
        browser.currentUrl =
                "https://www.inlinea.ch/bscch/wb/ui/trading/forex/new";
        browser.currentUrlAfterFirstRead =
                "https://www.inlinea.ch/bscch/wb/ui/payments/new";
        browser.elements = List.of(element("button"));
        FakeDiagnostics diagnostics = new FakeDiagnostics();
        FakeSink sink = new FakeSink();
        PreScanWorkflowService service =
                new PreScanWorkflowService(browser, diagnostics, element -> null);

        service.scan(context("https://www.inlinea.ch/auth/ui/app/auth/flow/web-app/password"),
                "button", false, sink);

        String message =
                "The browser page changed during Page Scanner. Scan the current page again.";
        assertEquals(0, diagnostics.persistedCount);
        assertNull(diagnostics.persistedPage);
        assertTrue(sink.statuses.contains("failed:" + message + ":0"));
        assertEquals(List.of(message), sink.failures);
        assertFalse(browser.running);
    }

    @Test
    void missingEndpointAndOverlapAreRejectedWithoutOpeningBrowser() {
        FakeBrowser browser = new FakeBrowser();
        FakeSink sink = new FakeSink();
        PreScanWorkflowService service = new PreScanWorkflowService(browser, new FakeDiagnostics(), element -> null);

        service.scan(context(""), "input", false, sink);
        assertTrue(sink.statuses.contains("failed:No endpoint URL selected.:0"));
        assertFalse(browser.open);
        assertFalse(browser.running);

        browser.running = true;
        service.scan(context("https://example.test"), "input", false, sink);
        assertTrue(sink.statuses.contains("running:A pre-scan is already in progress...:0"));
    }

    @Test
    void scanFailureReportsPresentationErrorAndAlwaysReleasesLease() {
        FakeBrowser browser = new FakeBrowser();
        browser.scanFailure = new IllegalStateException("scan failed");
        FakeSink sink = new FakeSink();
        PreScanWorkflowService service = new PreScanWorkflowService(browser, new FakeDiagnostics(), element -> null);

        service.scan(context("https://example.test"), "input", true, sink);

        assertTrue(sink.statuses.contains("failed:scan failed:0"));
        assertEquals(List.of("scan failed"), sink.failures);
        assertFalse(browser.running);
    }

    @Test
    void refreshReusesOpenBrowserAndElementTestsUseExpectedFallbackValue() {
        FakeBrowser browser = new FakeBrowser();
        browser.open = true;
        FakeSink sink = new FakeSink();
        InstructionLoad instruction = new InstructionLoad();
        instruction.setName("Email");
        PreScanWorkflowService service = new PreScanWorkflowService(browser, new FakeDiagnostics(), element -> instruction);

        service.refresh(context("https://example.test"), sink);
        ElementDTO input = element("input");
        input.setSomeText("Email");
        service.testElement(input, ScannerWorkspaceOperations.TEST_INPUT_DTO, sink);
        service.testElement(input, ScannerWorkspaceOperations.TEST_CLICK_DTO, sink);

        assertEquals(1, browser.reloadCalls);
        assertEquals("abc", browser.fillData.getValue());
        assertEquals(0, browser.clickCalls);
        assertEquals(1, browser.clickOnceCalls);
        assertEquals(0, browser.fillCalls);
        assertEquals(1, browser.fillOnceCalls);
        assertTrue(sink.statuses.contains("done:Test Input passed - Email:0"));
        assertTrue(sink.statuses.contains("done:Test Click passed - Email:0"));
    }

    @Test
    void elementTestsReuseTheOpenPageWithoutOpeningOrReloadingIt() {
        FakeBrowser browser = new FakeBrowser();
        browser.open = true;
        FakeSink sink = new FakeSink();
        InstructionLoad instruction = new InstructionLoad();
        instruction.setName("Email");
        PreScanWorkflowService service = new PreScanWorkflowService(browser, new FakeDiagnostics(), element -> instruction);
        ElementDTO input = element("input");

        service.testElement(input, ScannerWorkspaceOperations.TEST_CLICK_DTO, sink);
        service.testElement(input, ScannerWorkspaceOperations.TEST_INPUT_DTO, sink);

        assertEquals(0, browser.ensureOpenCalls);
        assertEquals(0, browser.reloadCalls);
        assertEquals(0, browser.clickCalls);
        assertEquals(1, browser.clickOnceCalls);
        assertEquals(0, browser.fillCalls);
        assertEquals(1, browser.fillOnceCalls);
        assertEquals("abc", browser.fillData.getValue());
    }

    private static PreScanWorkflowService.Context context(String endpoint) {
        return new PreScanWorkflowService.Context(42, "Payments", 7, 8, endpoint, "chromium", "", "build/test");
    }

    private static ElementDTO element(String type) {
        ElementDTO element = new ElementDTO();
        element.setTypeElement(type);
        return element;
    }

    private static final class FakeSink implements PreScanWorkflowService.Sink {
        private final List<String> statuses = new ArrayList<>();
        private final List<String> failures = new ArrayList<>();
        private List<ElementDTO> elements = List.of();
        public void status(String status, String message, int count) { statuses.add(status + ":" + message + ":" + count); }
        public void reset() { elements = List.of(); }
        public void elements(List<ElementDTO> values) { elements = values; }
        public void failure(String message) { failures.add(message); }
    }

    private static final class FakeDiagnostics implements PreScanWorkflowService.DiagnosticsPort {
        private int resolvedCount;
        private int persistedCount;
        private ScannedPageIdentity persistedPage;
        public void resolveNames(PreScanWorkflowService.Context context, PreScanWorkflowService.BrowserPort browser,
                List<ElementDTO> elements, PreScanWorkflowService.Sink sink) {
            resolvedCount = elements == null ? 0 : elements.size();
        }
        public void persist(
                PreScanWorkflowService.Context context,
                ScannedPageIdentity page,
                List<ElementDTO> elements,
                PreScanWorkflowService.BrowserPort browser) {
            persistedPage = page;
            persistedCount = elements == null ? 0 : elements.size();
        }
    }

    private static final class FakeBrowser implements PreScanWorkflowService.BrowserPort {
        private boolean running;
        private boolean open;
        private int ensureOpenCalls;
        private int reloadCalls;
        private int clickCalls;
        private int clickOnceCalls;
        private int fillCalls;
        private int fillOnceCalls;
        private List<ElementDTO> elements = List.of();
        private RuntimeException scanFailure;
        private String[] searchTerms;
        private FieldData fillData;
        private String currentUrl = "https://example.test";
        private String currentUrlAfterFirstRead;
        private int currentUrlCalls;
        public boolean tryBeginScan() { if (running) return false; running = true; return true; }
        public void finishScan() { running = false; }
        public boolean isScanRunning() { return running; }
        public boolean isOpen() { return open; }
        public void ensureOpen(String browserType, String endpointUrl, String optionsConfig) {
            ensureOpenCalls++;
            open = true;
        }
        public void reload() { reloadCalls++; }
        public String currentUrl() {
            currentUrlCalls++;
            return currentUrlAfterFirstRead != null && currentUrlCalls > 1
                    ? currentUrlAfterFirstRead
                    : currentUrl;
        }
        public long waitForPageSettled(long maxWaitMs) { return 10; }
        public List<ElementDTO> scanElements(String[] terms, boolean hidden) {
            searchTerms = terms;
            if (scanFailure != null) throw scanFailure;
            return elements;
        }
        public boolean click(InstructionLoad instruction) { clickCalls++; return true; }
        public boolean clickOnce(InstructionLoad instruction) { clickOnceCalls++; return true; }
        public boolean fill(InstructionLoad instruction, FieldData data) { fillCalls++; fillData = data; return true; }
        public boolean fillOnce(InstructionLoad instruction, FieldData data) { fillOnceCalls++; fillData = data; return true; }
        public ARPlaywrightDriver playwrightDriver() { throw new UnsupportedOperationException(); }
        public void shutdown() { open = false; running = false; }
    }
}
