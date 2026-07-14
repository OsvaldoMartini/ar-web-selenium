package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.allinweb.ch.model.BotJobDetailsState;
import com.allinweb.ch.model.ElementDTO;
import com.allinweb.ch.model.ScannerWorkspaceRequest;
import com.allinweb.ch.model.ScannerWorkspaceResponse;
import com.allinweb.ch.model.ScannerWorkspaceState;
import com.allinweb.ch.model.SplitDTO;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ScannerWorkspaceServiceTest {

    @Test
    void bootstrapMapsBotJobDetailsStateToScannerState() {
        RecordingPublisher publisher = new RecordingPublisher();
        RecordingBrowser browser = new RecordingBrowser();
        RecordingExecution execution = new RecordingExecution();
        ScannerWorkspaceService service = new ScannerWorkspaceService(id -> state(), publisher, browser, execution);

        ScannerWorkspaceResponse response = service.bootstrap(request("bootstrap-1", null));

        assertTrue(response.ok());
        assertEquals("bootstrap-1", response.requestId());
        assertEquals(42, response.state().botJobId());
        assertEquals("https://bank.example", response.state().environmentUrl());
        assertEquals(1, response.state().blocks().size());
        assertEquals("OPEN", response.state().browser().state());
        assertEquals("https://active.example", response.state().browser().activeUrl());
        assertEquals("Active page", response.state().browser().activeTitle());
        assertEquals(2, response.state().browser().openTabs());
        assertTrue(response.state().browser().scannable());
        assertEquals("All - Interactive controls", response.state().focus().profile());
        assertTrue(response.state().focus().searchTerms().contains("input"));
        assertTrue(response.state().capabilities().canRefreshState());
    }

    @Test
    void refreshStateActionReturnsCorrelatedState() {
        RecordingPublisher publisher = new RecordingPublisher();
        RecordingBrowser browser = new RecordingBrowser();
        RecordingExecution execution = new RecordingExecution();
        ScannerWorkspaceService service = new ScannerWorkspaceService(id -> state(), publisher, browser, execution);

        ScannerWorkspaceResponse response = service.action(request("refresh-1", "REFRESH_STATE"));

        assertTrue(response.ok());
        assertEquals("REFRESH_STATE", response.action());
        assertEquals("refresh-1", response.requestId());
        assertEquals(9L, response.state().revision());
    }

    @Test
    void clearGridPublishesEmptySearchTermsPayload() {
        RecordingPublisher publisher = new RecordingPublisher();
        RecordingBrowser browser = new RecordingBrowser();
        RecordingExecution execution = new RecordingExecution();
        ScannerWorkspaceService service = new ScannerWorkspaceService(id -> state(), publisher, browser, execution);

        ScannerWorkspaceResponse response = service.action(request("clear-1", "CLEAR_GRID"));

        assertTrue(response.ok());
        assertEquals("CLEAR_GRID", response.action());
        assertEquals(1, publisher.calls.size());
        RecordingPublisher.Call call = publisher.calls.get(0);
        assertEquals("scannerGrid", call.sessionId);
        assertEquals(2, call.homeBankingId);
        assertEquals(42, call.payload.getBotJobId());
        assertEquals("scannerGrid", call.payload.getSessionId());
        assertEquals("searchTerms", call.payload.getOperationId());
        assertEquals(0, call.payload.getElementDetails().length);
    }

    @Test
    void refreshPageRunsBrowserOperationWithoutPublishingRows() {
        RecordingPublisher publisher = new RecordingPublisher();
        RecordingBrowser browser = new RecordingBrowser();
        RecordingExecution execution = new RecordingExecution();
        ScannerWorkspaceService service = new ScannerWorkspaceService(id -> state(), publisher, browser, execution);

        ScannerWorkspaceResponse response = service.action(request("refresh-page-1", "REFRESH_PAGE"));

        assertTrue(response.ok());
        assertEquals("REFRESH_PAGE", response.action());
        assertEquals(1, browser.refreshCalls);
        assertTrue(publisher.calls.isEmpty());
    }

    @Test
    void actionResponseReturnsStateAfterActionRuns() {
        RecordingPublisher publisher = new RecordingPublisher();
        RecordingBrowser browser = new RecordingBrowser();
        browser.browserState =
                new ScannerWorkspaceState.Browser("OPEN", "https://before.example", "Before", 1, true);
        browser.refreshedBrowserState =
                new ScannerWorkspaceState.Browser("OPEN", "https://after.example", "After", 1, true);
        RecordingExecution execution = new RecordingExecution();
        ScannerWorkspaceService service = new ScannerWorkspaceService(id -> state(), publisher, browser, execution);

        ScannerWorkspaceResponse response = service.action(request("refresh-page-fresh-state-1", "REFRESH_PAGE"));

        assertTrue(response.ok());
        assertEquals("https://after.example", response.state().browser().activeUrl());
        assertEquals("After", response.state().browser().activeTitle());
    }

    @Test
    void tabActionsRunBrowserOperationWithoutPublishingRows() {
        RecordingPublisher publisher = new RecordingPublisher();
        RecordingBrowser browser = new RecordingBrowser();
        RecordingExecution execution = new RecordingExecution();
        ScannerWorkspaceService service = new ScannerWorkspaceService(id -> state(), publisher, browser, execution);

        ScannerWorkspaceResponse previous = service.action(request("previous-tab-1", "PREVIOUS_TAB"));
        ScannerWorkspaceResponse next = service.action(request("next-tab-1", "NEXT_TAB"));

        assertTrue(previous.ok());
        assertTrue(next.ok());
        assertEquals(List.of(-1, 1), browser.tabDirections);
        assertTrue(publisher.calls.isEmpty());
    }

    @Test
    void preLaunchActionsRunExecutionOperationWithoutPublishingRows() {
        RecordingPublisher publisher = new RecordingPublisher();
        RecordingBrowser browser = new RecordingBrowser();
        RecordingExecution execution = new RecordingExecution();
        ScannerWorkspaceService service = new ScannerWorkspaceService(id -> state(), publisher, browser, execution);

        ScannerWorkspaceResponse start = service.action(request("pre-launch-1", "PRE_LAUNCH"));
        ScannerWorkspaceResponse stop = service.action(request("stop-pre-launch-1", "STOP_PRE_LAUNCH"));

        assertTrue(start.ok());
        assertTrue(stop.ok());
        assertEquals(List.of(42), execution.preLaunchBotJobIds);
        assertEquals(List.of(42), execution.stopBotJobIds);
        assertTrue(publisher.calls.isEmpty());
    }

    @Test
    void preLaunchFailureReturnsScannerActionFailure() {
        RecordingPublisher publisher = new RecordingPublisher();
        RecordingBrowser browser = new RecordingBrowser();
        RecordingExecution execution = new RecordingExecution();
        execution.failPreLaunch = true;
        ScannerWorkspaceService service = new ScannerWorkspaceService(id -> state(), publisher, browser, execution);

        ScannerWorkspaceResponse response = service.action(request("pre-launch-fail-1", "PRE_LAUNCH"));

        assertEquals(false, response.ok());
        assertEquals("PRE_LAUNCH", response.action());
        assertEquals("SCANNER_ACTION_FAILED", response.errorCode());
        assertTrue(response.message().contains("pre-launch failed"));
    }

    @Test
    void pageScannerPublishesResetAndElementChunks() {
        RecordingPublisher publisher = new RecordingPublisher();
        RecordingBrowser browser = new RecordingBrowser();
        browser.scanElements = List.of(element("Input 1"), element("Button 1"));
        RecordingExecution execution = new RecordingExecution();
        ScannerWorkspaceService service = new ScannerWorkspaceService(id -> state(), publisher, browser, execution);

        ScannerWorkspaceResponse response = service.action(request("page-scan-1", "PAGE_SCANNER"));

        assertTrue(response.ok());
        assertEquals("PAGE_SCANNER", response.action());
        assertEquals(1, browser.scanCalls);
        assertTrue(List.of(browser.lastSearchTerms).contains("input"));
        assertEquals(2, publisher.calls.size());
        assertEquals(0, publisher.calls.get(0).payload.getElementDetails().length);
        assertEquals(2, publisher.calls.get(1).payload.getElementDetails().length);
        assertEquals(25, publisher.lastChunkSize);
    }

    @Test
    void pageScannerUsesSearchTermsFromRequest() {
        RecordingPublisher publisher = new RecordingPublisher();
        RecordingBrowser browser = new RecordingBrowser();
        browser.scanElements = List.of(element("Search result"));
        RecordingExecution execution = new RecordingExecution();
        ScannerWorkspaceService service = new ScannerWorkspaceService(id -> state(), publisher, browser, execution);

        ScannerWorkspaceResponse response =
                service.action(request("page-scan-2", "PAGE_SCANNER", "input, button, [role='tab']"));

        assertTrue(response.ok());
        assertEquals(List.of("input", "button", "[role='tab']"), List.of(browser.lastSearchTerms));
    }

    private ScannerWorkspaceRequest request(String requestId, String action) {
        return request(requestId, action, null);
    }

    private ScannerWorkspaceRequest request(String requestId, String action, String searchTerms) {
        JsonObject body = new JsonObject();
        body.addProperty("requestId", requestId);
        body.addProperty("botJobId", 42);
        if (action != null) body.addProperty("action", action);
        if (searchTerms != null) body.addProperty("searchTerms", searchTerms);
        return new ScannerWorkspaceRequest("scannerGrid", requestId, 42, body);
    }

    private ElementDTO element(String name) {
        ElementDTO element = new ElementDTO();
        element.setDefinedName(name);
        element.setTagName("input");
        return element;
    }

    private BotJobDetailsState state() {
        return new BotJobDetailsState(
                9L,
                5L,
                42,
                "Apre Acconto",
                "desc",
                "Web",
                true,
                2,
                "Banca Stato",
                11,
                "Production",
                "https://bank.example",
                3,
                true,
                List.of(),
                List.of(new BotJobDetailsState.Block(100, 1, "Login", "", 1, true, 0)),
                new BotJobDetailsState.Capabilities(true, true, true, true, true, true, true, true),
                "IDLE",
                "scanner",
                false);
    }

    private static final class RecordingPublisher implements ScannerWorkspaceService.GridPublisher {
        private final List<Call> calls = new ArrayList<>();

        @Override
        public void publishSearchTerms(String sessionId, int homeBankingId, SplitDTO payload) {
            calls.add(new Call(sessionId, homeBankingId, payload));
        }

        @Override
        public void publishSearchTermsChunks(String sessionId, int homeBankingId, SplitDTO payload, int chunkSize) {
            lastChunkSize = chunkSize;
            calls.add(new Call(sessionId, homeBankingId, payload));
        }

        private int lastChunkSize;

        private record Call(String sessionId, int homeBankingId, SplitDTO payload) {}
    }

    private static final class RecordingBrowser implements ScannerWorkspaceService.BrowserOperations {
        private int refreshCalls;
        private int scanCalls;
        private String[] lastSearchTerms;
        private List<ElementDTO> scanElements = List.of();
        private final List<Integer> tabDirections = new ArrayList<>();
        private ScannerWorkspaceState.Browser browserState =
                new ScannerWorkspaceState.Browser("OPEN", "https://active.example", "Active page", 2, true);
        private ScannerWorkspaceState.Browser refreshedBrowserState;

        @Override
        public ScannerWorkspaceState.Browser browserState() {
            return browserState;
        }

        @Override
        public void refreshPage() {
            refreshCalls++;
            if (refreshedBrowserState != null) {
                browserState = refreshedBrowserState;
            }
        }

        @Override
        public void switchTab(int direction) {
            tabDirections.add(direction);
        }

        @Override
        public List<ElementDTO> scanPage(String[] searchTerms, int homeBankingId, int botJobId) {
            scanCalls++;
            lastSearchTerms = searchTerms;
            return scanElements;
        }
    }

    private static final class RecordingExecution implements ScannerWorkspaceService.ExecutionOperations {
        private final List<Integer> preLaunchBotJobIds = new ArrayList<>();
        private final List<Integer> stopBotJobIds = new ArrayList<>();
        private boolean failPreLaunch;

        @Override
        public void preLaunch(int botJobId) {
            if (failPreLaunch) {
                throw new IllegalStateException("pre-launch failed");
            }
            preLaunchBotJobIds.add(botJobId);
        }

        @Override
        public void stopPreLaunch(int botJobId) {
            stopBotJobIds.add(botJobId);
        }
    }
}
