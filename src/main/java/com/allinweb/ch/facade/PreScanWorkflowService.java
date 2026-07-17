package com.allinweb.ch.facade;

import com.allinweb.ch.model.AttributeData;
import com.allinweb.ch.model.ElementDTO;
import com.allinweb.ch.model.FieldData;
import com.allinweb.ch.model.InstructionLoad;
import com.allinweb.ch.model.OcrConfig;
import com.allinweb.ch.model.ScannerWorkspaceOperations;
import com.allinweb.ch.util.PageDiagnosticDumper;
import com.allinweb.ch.util.PageOcrDumper;
import com.google.common.base.Strings;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;

/** Presentation-neutral orchestration for the Bot Job Details Pre Scan workflow. */
@Slf4j
public final class PreScanWorkflowService {

    private final BrowserPort browser;
    private final DiagnosticsPort diagnostics;
    private final InstructionPort instructions;

    public PreScanWorkflowService() {
        this(new SessionBrowserPort(new PreScanBrowserSession()), new DefaultDiagnosticsPort(),
                element -> PreScanApplyService.getInstance().buildTestInstruction(element));
    }

    PreScanWorkflowService(BrowserPort browser, DiagnosticsPort diagnostics, InstructionPort instructions) {
        this.browser = browser;
        this.diagnostics = diagnostics;
        this.instructions = instructions;
    }

    public void refresh(Context context, Sink sink) {
        require(context, sink);
        if (Strings.isNullOrEmpty(context.endpointUrl())) {
            sink.status("failed", "No endpoint URL selected.", 0);
            return;
        }
        try {
            if (!browser.isOpen()) {
                openBrowser(context, sink);
            } else {
                sink.status("running", "Refreshing browser page...", 0);
                browser.reload();
            }
            sink.status("done", "Web page refreshed. Run Page Scanner to update the grid.", 0);
        } catch (Exception error) {
            log.error("PRE SCAN refresh failed", error);
            sink.status("failed", message(error), 0);
        }
    }

    public void scan(Context context, String searchTerms, boolean searchHidden, Sink sink) {
        require(context, sink);
        if (!browser.tryBeginScan()) {
            sink.status("running", "A pre-scan is already in progress...", 0);
            return;
        }
        try {
            if (Strings.isNullOrEmpty(context.endpointUrl())) {
                sink.status("failed", "No endpoint URL selected.", 0);
                return;
            }
            sink.status("waiting", "Starting pre-scan for " + context.endpointUrl(), 0);
            sink.reset();
            openBrowser(context, sink);
            sink.status("waiting", "Loading the Page - waiting to settle...", 0);
            long settledMs = browser.waitForPageSettled(15_000);
            log.info("PRE SCAN - page settled after {} ms", settledMs);
            sink.status("running", "web elements...", 0);
            List<ElementDTO> elements = keepActionableElements(
                    browser.scanElements(searchTermsArray(searchTerms), searchHidden));
            diagnostics.resolveNames(context, browser, elements, sink);
            diagnostics.persist(context, elements);
            sink.elements(elements);
            int count = elements == null ? 0 : elements.size();
            sink.status(
                    count == 0 ? "empty" : "done",
                    count == 0 ? "No elements found." : "Found " + count + " web element(s).",
                    count);
        } catch (Exception error) {
            log.error("PRE SCAN failed", error);
            sink.status("failed", message(error), 0);
            sink.failure(message(error));
        } finally {
            browser.finishScan();
        }
    }

    public void testElement(ElementDTO element, String testType, Sink sink) {
        if (sink == null) throw new IllegalArgumentException("A Pre Scan result sink is required");
        if (!browser.isOpen()) {
            sink.status("failed", "No pre-scan browser open. Run the Page Scanner first.", 0);
            return;
        }
        if (element == null) return;
        boolean click = ScannerWorkspaceOperations.TEST_CLICK_DTO.equals(testType);
        String actionLabel = click ? "Test Click" : "Test Input";
        String label = label(element);
        try {
            InstructionLoad instruction = instructions.build(element);
            if (instruction == null) {
                sink.status("failed", actionLabel + " failed - cannot map element: " + label, 0);
                return;
            }
            sink.status("running", actionLabel + " - " + label, 0);
            boolean passed = click
                    ? browser.click(instruction)
                    : browser.fill(
                            instruction,
                            new FieldData(
                                    instruction.getName(),
                                    Strings.isNullOrEmpty(element.getDefaultValue())
                                            ? "abc"
                                            : element.getDefaultValue()));
            sink.status(
                    passed ? "done" : "failed",
                    actionLabel + (passed ? " passed - " : " failed - ") + label,
                    0);
        } catch (Exception error) {
            log.error("PRE SCAN {} failed for '{}'", actionLabel, label, error);
            sink.status("failed", actionLabel + " failed - " + message(error), 0);
        }
    }

    public boolean isOpen() {
        return browser.isOpen();
    }

    public boolean isRunning() {
        return browser.isScanRunning();
    }

    public void shutdown() {
        browser.shutdown();
    }

    static String[] searchTermsArray(String searchTerms) {
        if (Strings.isNullOrEmpty(searchTerms)) {
            return new String[] {"input", "textarea", "button", "a", "select", "label"};
        }
        return Arrays.stream(searchTerms.split(","))
                .map(String::trim)
                .filter(term -> !term.isEmpty())
                .toArray(String[]::new);
    }

    static List<ElementDTO> keepActionableElements(List<ElementDTO> elements) {
        if (elements == null || elements.isEmpty()) return elements;
        List<ElementDTO> actionable = new ArrayList<>();
        for (ElementDTO element : elements) {
            String type = element == null
                    ? ""
                    : Objects.toString(element.getTypeElement(), "").toLowerCase(Locale.ROOT);
            if (type.equals("input") || type.equals("button") || type.equals("output") || type.equals("label")) {
                actionable.add(element);
            }
        }
        return actionable;
    }

    private void openBrowser(Context context, Sink sink) {
        if (!browser.isOpen()) {
            sink.status("waiting", "Loading the Page - Opening isolated browser...", 0);
            browser.ensureOpen(context.browserType(), context.endpointUrl(), context.optionsConfig());
            return;
        }
        try {
            log.info("PRE SCAN - scanning current isolated browser page {}", browser.currentUrl());
        } catch (Exception ignored) {
            // The active page can still be scanned when its URL cannot be read.
        }
        sink.status("waiting", "Loading the Page - Using current browser page...", 0);
    }

    private static String label(ElementDTO element) {
        if (!Strings.isNullOrEmpty(element.getClientNamed())) return element.getClientNamed();
        if (!Strings.isNullOrEmpty(element.getDefinedName())) return element.getDefinedName();
        if (!Strings.isNullOrEmpty(element.getSomeText())) return element.getSomeText();
        return Objects.toString(element.getTagName(), "");
    }

    private static String message(Exception error) {
        return Objects.toString(error == null ? null : error.getMessage(), "Pre Scan failed");
    }

    private static void require(Context context, Sink sink) {
        if (context == null) throw new IllegalArgumentException("A Pre Scan context is required");
        if (sink == null) throw new IllegalArgumentException("A Pre Scan result sink is required");
    }

    public record Context(
            int botJobId,
            String botJobName,
            int homeBankingId,
            Integer homeUrlId,
            String endpointUrl,
            String browserType,
            String optionsConfig,
            String jsonPath) {}

    public interface Sink {
        void status(String status, String message, int elementCount);

        void reset();

        void elements(List<ElementDTO> elements);

        void failure(String message);
    }

    interface BrowserPort {
        boolean tryBeginScan();
        void finishScan();
        boolean isScanRunning();
        boolean isOpen();
        void ensureOpen(String browserType, String endpointUrl, String optionsConfig);
        void reload();
        String currentUrl();
        long waitForPageSettled(long maxWaitMs);
        List<ElementDTO> scanElements(String[] searchTerms, boolean includeHidden);
        boolean click(InstructionLoad instruction);
        boolean fill(InstructionLoad instruction, FieldData data);
        com.allinweb.ch.driver.ARPlaywrightDriver playwrightDriver();
        void shutdown();
    }

    interface DiagnosticsPort {
        void resolveNames(Context context, BrowserPort browser, List<ElementDTO> elements, Sink sink);
        void persist(Context context, List<ElementDTO> elements);
    }

    @FunctionalInterface
    interface InstructionPort {
        InstructionLoad build(ElementDTO element);
    }

    private static final class SessionBrowserPort implements BrowserPort {
        private final PreScanBrowserSession session;
        private SessionBrowserPort(PreScanBrowserSession session) { this.session = session; }
        public boolean tryBeginScan() { return session.tryBeginScan(); }
        public void finishScan() { session.finishScan(); }
        public boolean isScanRunning() { return session.isScanRunning(); }
        public boolean isOpen() { return session.isOpen(); }
        public void ensureOpen(String browserType, String endpointUrl, String optionsConfig) {
            session.ensureOpen(browserType, endpointUrl, optionsConfig);
        }
        public void reload() { session.reload(); }
        public String currentUrl() { return session.currentUrl(); }
        public long waitForPageSettled(long maxWaitMs) { return session.waitForPageSettled(maxWaitMs); }
        public List<ElementDTO> scanElements(String[] terms, boolean hidden) { return session.scanElements(terms, hidden); }
        public boolean click(InstructionLoad instruction) { return session.click(instruction); }
        public boolean fill(InstructionLoad instruction, FieldData data) { return session.fill(instruction, data); }
        public com.allinweb.ch.driver.ARPlaywrightDriver playwrightDriver() { return session.playwrightDriver(); }
        public void shutdown() { session.shutdown(); }
    }

    private static final class DefaultDiagnosticsPort implements DiagnosticsPort {
        private final com.allinweb.ch.facade.PerformMessage messages = com.allinweb.ch.facade.PerformMessage.getInstance();

        @Override
        public void resolveNames(Context context, BrowserPort browser, List<ElementDTO> elements, Sink sink) {
            if (elements == null || elements.isEmpty()) return;
            try {
                sink.status("running", "Resolving element names (OCR)...", elements.size());
                ElementDTO[] values = elements.toArray(new ElementDTO[0]);
                PageDiagnosticDumper.dumpRectsFromElements(browser.playwrightDriver(), values, context.jsonPath(), "page-BJ");
                PageOcrDumper.runAndDump(
                        browser.playwrightDriver(), values, context.jsonPath(), "page-BJ",
                        context.homeBankingId(), context.homeUrlId());
                String[] scanned = new String[values.length];
                for (int i = 0; i < values.length; i++) scanned[i] = values[i] == null ? null : values[i].getSomeText();
                OcrConfig config = OcrConfigService.getInstance()
                        .resolveFor(context.homeBankingId(), context.homeUrlId());
                ElementTextResolver.resolveAll(
                        values,
                        Paths.get(context.jsonPath(), PageDiagnosticDumper.SUBFOLDER, "ocr-correlation-HP.json"),
                        config);
                for (int i = 0; i < values.length; i++) {
                    ElementDTO element = values[i];
                    if (element == null || Strings.isNullOrEmpty(scanned[i]) || scanned[i].equals(element.getSomeText())) continue;
                    AttributeData[] existing = element.getAttributeData() == null ? new AttributeData[0] : element.getAttributeData();
                    AttributeData[] updated = Arrays.copyOf(existing, existing.length + 1);
                    updated[existing.length] = new AttributeData("scanned-text", scanned[i]);
                    element.setAttributeData(updated);
                }
            } catch (Exception error) {
                log.warn("PRE SCAN - OCR name resolution failed (non-fatal): {}", error.getMessage());
            }
        }

        @Override
        public void persist(Context context, List<ElementDTO> elements) {
            if (elements == null || elements.isEmpty()) return;
            try {
                ElementDTO[] values = elements.toArray(new ElementDTO[0]);
                messages.outputJsonElementDTO(
                        values, List.of("optional", "blockMarked", "editMode"), "elementDTO-PS-BJ", context.jsonPath());
                messages.outputJsonElementDTO(
                        values,
                        List.of("optional", "blockMarked", "editMode", "id", "attributeData", "typeElement",
                                "customXPath", "shadowRoot", "nestedShadow", "searchAttributeValue",
                                "attributeType", "attributeValue"),
                        "AI-ElementDTO-PS-BJ",
                        context.jsonPath());
            } catch (Exception error) {
                log.warn("PRE SCAN - failed to persist element JSON: {}", error.getMessage());
            }
        }
    }
}
