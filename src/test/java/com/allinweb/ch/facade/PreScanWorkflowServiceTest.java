package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        assertTrue(sink.statuses.contains("done:Found 2 web element(s).:2"));
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
        assertEquals(1, browser.clickCalls);
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
        assertEquals(1, browser.clickCalls);
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
        public void resolveNames(PreScanWorkflowService.Context context, PreScanWorkflowService.BrowserPort browser,
                List<ElementDTO> elements, PreScanWorkflowService.Sink sink) {
            resolvedCount = elements == null ? 0 : elements.size();
        }
        public void persist(PreScanWorkflowService.Context context, List<ElementDTO> elements) {
            persistedCount = elements == null ? 0 : elements.size();
        }
    }

    private static final class FakeBrowser implements PreScanWorkflowService.BrowserPort {
        private boolean running;
        private boolean open;
        private int ensureOpenCalls;
        private int reloadCalls;
        private int clickCalls;
        private List<ElementDTO> elements = List.of();
        private RuntimeException scanFailure;
        private String[] searchTerms;
        private FieldData fillData;
        public boolean tryBeginScan() { if (running) return false; running = true; return true; }
        public void finishScan() { running = false; }
        public boolean isScanRunning() { return running; }
        public boolean isOpen() { return open; }
        public void ensureOpen(String browserType, String endpointUrl, String optionsConfig) {
            ensureOpenCalls++;
            open = true;
        }
        public void reload() { reloadCalls++; }
        public String currentUrl() { return "https://example.test"; }
        public long waitForPageSettled(long maxWaitMs) { return 10; }
        public List<ElementDTO> scanElements(String[] terms, boolean hidden) {
            searchTerms = terms;
            if (scanFailure != null) throw scanFailure;
            return elements;
        }
        public boolean click(InstructionLoad instruction) { clickCalls++; return true; }
        public boolean fill(InstructionLoad instruction, FieldData data) { fillData = data; return true; }
        public ARPlaywrightDriver playwrightDriver() { throw new UnsupportedOperationException(); }
        public void shutdown() { open = false; running = false; }
    }
}
