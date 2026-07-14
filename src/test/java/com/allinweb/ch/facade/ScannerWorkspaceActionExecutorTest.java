package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.allinweb.ch.model.ElementDTO;
import com.allinweb.ch.model.ScannerWorkspaceAction;
import com.allinweb.ch.model.ScannerWorkspaceRequest;
import com.allinweb.ch.model.ScannerWorkspaceState;
import com.allinweb.ch.model.SplitDTO;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class ScannerWorkspaceActionExecutorTest {

    @Test
    void pageScannerClearsGridAndPublishesChunks() {
        RecordingPublisher publisher = new RecordingPublisher();
        RecordingBrowser browser = new RecordingBrowser();
        browser.scanElements = List.of(element("Input"), element("Button"));
        RecordingExecution execution = new RecordingExecution();
        ScannerWorkspaceActionExecutor executor =
                new ScannerWorkspaceActionExecutor(publisher, browser, () -> execution);

        ScannerWorkspaceActionExecutor.Outcome outcome =
                executor.perform(ScannerWorkspaceAction.PAGE_SCANNER, request("scan-1", "PAGE_SCANNER"), state());

        assertEquals("Page scanner completed: 2 elements", outcome.message());
        assertEquals(1, browser.scanCalls);
        assertEquals(2, publisher.calls.size());
        assertEquals(0, publisher.calls.get(0).payload.getElementDetails().length);
        assertEquals(2, publisher.calls.get(1).payload.getElementDetails().length);
        assertEquals(25, publisher.lastChunkSize);
    }

    @Test
    void preLaunchUsesCurrentExecutionDelegate() {
        RecordingPublisher publisher = new RecordingPublisher();
        RecordingBrowser browser = new RecordingBrowser();
        RecordingExecution first = new RecordingExecution();
        RecordingExecution second = new RecordingExecution();
        AtomicReference<ScannerWorkspaceService.ExecutionOperations> execution = new AtomicReference<>(first);
        ScannerWorkspaceActionExecutor executor =
                new ScannerWorkspaceActionExecutor(publisher, browser, execution::get);

        executor.perform(ScannerWorkspaceAction.PRE_LAUNCH, request("pre-1", "PRE_LAUNCH"), state());
        execution.set(second);
        executor.perform(ScannerWorkspaceAction.PRE_LAUNCH, request("pre-2", "PRE_LAUNCH"), state());

        assertEquals(List.of(42), first.preLaunchBotJobIds);
        assertEquals(List.of(42), second.preLaunchBotJobIds);
    }

    @Test
    void refreshAndTabActionsDelegateToBrowser() {
        RecordingPublisher publisher = new RecordingPublisher();
        RecordingBrowser browser = new RecordingBrowser();
        RecordingExecution execution = new RecordingExecution();
        ScannerWorkspaceActionExecutor executor =
                new ScannerWorkspaceActionExecutor(publisher, browser, () -> execution);

        executor.perform(ScannerWorkspaceAction.REFRESH_PAGE, request("refresh-1", "REFRESH_PAGE"), state());
        executor.perform(ScannerWorkspaceAction.PREVIOUS_TAB, request("previous-1", "PREVIOUS_TAB"), state());
        executor.perform(ScannerWorkspaceAction.NEXT_TAB, request("next-1", "NEXT_TAB"), state());

        assertEquals(1, browser.refreshCalls);
        assertEquals(List.of(-1, 1), browser.tabDirections);
        assertEquals(0, publisher.calls.size());
    }

    private ScannerWorkspaceRequest request(String requestId, String action) {
        JsonObject body = new JsonObject();
        body.addProperty("requestId", requestId);
        body.addProperty("botJobId", 42);
        body.addProperty("action", action);
        return new ScannerWorkspaceRequest("scannerGrid", requestId, 42, body);
    }

    private ElementDTO element(String name) {
        ElementDTO element = new ElementDTO();
        element.setDefinedName(name);
        element.setTagName("input");
        return element;
    }

    private ScannerWorkspaceState state() {
        return new ScannerWorkspaceState(
                9L,
                42,
                "Apre Acconto",
                2,
                "https://bank.example",
                List.of(new ScannerWorkspaceState.Block(100, 1, "Login", true)),
                new ScannerWorkspaceState.Browser("OPEN", "https://active.example", "Active page", 2, true),
                new ScannerWorkspaceState.Focus("default", List.of("input")),
                new ScannerWorkspaceState.Ocr(true, "IDLE"),
                new ScannerWorkspaceState.Capabilities(true, true, true, true, true),
                "IDLE");
    }

    private static final class RecordingPublisher implements ScannerWorkspaceService.GridPublisher {
        private final List<Call> calls = new ArrayList<>();
        private int lastChunkSize;

        @Override
        public void publishSearchTerms(String sessionId, int homeBankingId, SplitDTO payload) {
            calls.add(new Call(sessionId, homeBankingId, payload));
        }

        @Override
        public void publishSearchTermsChunks(String sessionId, int homeBankingId, SplitDTO payload, int chunkSize) {
            lastChunkSize = chunkSize;
            calls.add(new Call(sessionId, homeBankingId, payload));
        }

        private record Call(String sessionId, int homeBankingId, SplitDTO payload) {}
    }

    private static final class RecordingBrowser implements ScannerWorkspaceService.BrowserOperations {
        private int refreshCalls;
        private int scanCalls;
        private List<ElementDTO> scanElements = List.of();
        private final List<Integer> tabDirections = new ArrayList<>();

        @Override
        public ScannerWorkspaceState.Browser browserState() {
            return new ScannerWorkspaceState.Browser("OPEN", "https://active.example", "Active page", 2, true);
        }

        @Override
        public void refreshPage() {
            refreshCalls++;
        }

        @Override
        public void switchTab(int direction) {
            tabDirections.add(direction);
        }

        @Override
        public List<ElementDTO> scanPage(String[] searchTerms, int homeBankingId, int botJobId) {
            scanCalls++;
            return scanElements;
        }
    }

    private static final class RecordingExecution implements ScannerWorkspaceService.ExecutionOperations {
        private final List<Integer> preLaunchBotJobIds = new ArrayList<>();

        @Override
        public void preLaunch(int botJobId) {
            preLaunchBotJobIds.add(botJobId);
        }

        @Override
        public void stopPreLaunch(int botJobId) {
        }
    }
}
