package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.allinweb.ch.model.*;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

class BotJobWorkspaceControllerTest {

    @Test
    void routesEverySocketOperationThroughActiveHost() {
        BotJobWorkspaceController controller = new BotJobWorkspaceController();
        Host host = new Host();
        controller.activate(host);
        BotJobDetailsRequest request = new BotJobDetailsRequest("session", "request-1", 42, new JsonObject());

        controller.workspaceAction(BotJobWorkspaceAction.REFRESH, 42);
        controller.toolbarAction(BotJobToolbarAction.OPEN_EXCEL, request);
        controller.applyMetadata(null);
        controller.preScanCommand(ScannerWorkspaceOperations.PRE_SCAN_PAGE, new JsonObject());
        controller.preScanElementTest(new SplitDTO(), "TEST_CLICK_DTO");

        assertEquals(List.of("workspace", "toolbar", "metadata", "command", "test"), host.calls);
    }

    @Test
    void staleGenerationCannotDeactivateNewerHost() {
        BotJobWorkspaceController controller = new BotJobWorkspaceController();
        long first = controller.activate(new Host());
        Host second = new Host();
        controller.activate(second);

        controller.deactivate(first);
        controller.preScanCommand(ScannerWorkspaceOperations.PRE_SCAN_PAGE, new JsonObject());

        assertEquals(List.of("command"), second.calls);
    }

    @Test
    void closedWorkspaceRejectsSocketOperations() {
        BotJobWorkspaceController controller = new BotJobWorkspaceController();
        long generation = controller.activate(new Host());
        controller.deactivate(generation);

        assertThrows(
                IllegalStateException.class,
                () -> controller.preScanCommand(ScannerWorkspaceOperations.PRE_SCAN_PAGE, new JsonObject()));
    }

    private static final class Host implements BotJobWorkspaceController.HostPort {
        private final List<String> calls = new ArrayList<>();
        public CompletableFuture<BotJobWorkspaceActionResult> workspaceAction(BotJobWorkspaceAction action, int id) {
            calls.add("workspace"); return CompletableFuture.completedFuture(null);
        }
        public CompletableFuture<BotJobToolbarActionResult> toolbarAction(
                BotJobToolbarAction action, BotJobDetailsRequest request) {
            calls.add("toolbar"); return CompletableFuture.completedFuture(null);
        }
        public CompletableFuture<Void> applyMetadata(BotJobDetailsState state) {
            calls.add("metadata"); return CompletableFuture.completedFuture(null);
        }
        public void preScanCommand(String type, JsonObject body) { calls.add("command"); }
        public void preScanElementTest(SplitDTO payload, String type) { calls.add("test"); }
    }
}
