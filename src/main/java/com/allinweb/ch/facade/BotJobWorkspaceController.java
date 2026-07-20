package com.allinweb.ch.facade;

import com.allinweb.ch.model.BotJobDetailsRequest;
import com.allinweb.ch.model.BotJobDetailsState;
import com.allinweb.ch.model.BotJobToolbarAction;
import com.allinweb.ch.model.BotJobToolbarActionResult;
import com.allinweb.ch.model.BotJobWorkspaceAction;
import com.allinweb.ch.model.BotJobWorkspaceActionResult;
import com.allinweb.ch.model.SplitDTO;
import com.google.gson.JsonObject;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/** Pane-free socket entry point for the currently active Bot Job workspace host. */
public final class BotJobWorkspaceController {

    private static final BotJobWorkspaceController INSTANCE = new BotJobWorkspaceController();
    private final AtomicLong generations = new AtomicLong();
    private final AtomicReference<Registration> active = new AtomicReference<>();

    BotJobWorkspaceController() {}

    public static BotJobWorkspaceController getInstance() { return INSTANCE; }

    public long activate(HostPort host) {
        if (host == null) throw new IllegalArgumentException("A Bot Job workspace host is required");
        long generation = generations.incrementAndGet();
        active.set(new Registration(generation, host));
        return generation;
    }

    public void deactivate(long generation) {
        active.updateAndGet(current -> current != null && current.generation() == generation ? null : current);
    }

    public CompletableFuture<BotJobWorkspaceActionResult> workspaceAction(
            BotJobWorkspaceAction action, int botJobId) {
        return host().workspaceAction(action, botJobId);
    }

    public CompletableFuture<BotJobToolbarActionResult> toolbarAction(
            BotJobToolbarAction action, BotJobDetailsRequest request) {
        return host().toolbarAction(action, request);
    }

    public CompletableFuture<Void> applyMetadata(BotJobDetailsState state) {
        return host().applyMetadata(state);
    }

    public void preScanCommand(String type, JsonObject body) { host().preScanCommand(type, body); }

    public void preScanElementTest(SplitDTO payload, String type) { host().preScanElementTest(payload, type); }

    /** Returns the immutable scan context for the active Bot Job after validating its identity. */
    public PreScanWorkflowService.Context pageScannerContext(int botJobId) {
        return host().pageScannerContext(botJobId);
    }

    /** Publishes the initial empty grid/block snapshot to one detached Page Scanner transport. */
    public void pageScannerBootstrap(
            String workspaceSessionId, PreScanWorkflowService.Context context) {
        host().pageScannerBootstrap(workspaceSessionId, context);
    }

    /** Executes a scan/refresh/clear command for one authoritative detached transport. */
    public void pageScannerCommand(
            String type,
            JsonObject body,
            String workspaceSessionId,
            PreScanWorkflowService.Context context) {
        host().pageScannerCommand(type, body, workspaceSessionId, context);
    }

    /** Executes Test Click/Input against the detached workspace's isolated Playwright page. */
    public void pageScannerElementTest(
            SplitDTO payload,
            String type,
            String workspaceSessionId,
            PreScanWorkflowService.Context context) {
        host().pageScannerElementTest(payload, type, workspaceSessionId, context);
    }

    /** Releases only the isolated browser resources owned by the detached Page Scanner. */
    public void closePageScanner(String workspaceSessionId) {
        host().closePageScanner(workspaceSessionId);
    }

    private HostPort host() {
        Registration registration = active.get();
        if (registration == null) throw new IllegalStateException("Bot Job Details workspace is not open");
        return registration.host();
    }

    private record Registration(long generation, HostPort host) {}

    public interface HostPort {
        CompletableFuture<BotJobWorkspaceActionResult> workspaceAction(BotJobWorkspaceAction action, int botJobId);
        CompletableFuture<BotJobToolbarActionResult> toolbarAction(
                BotJobToolbarAction action, BotJobDetailsRequest request);
        CompletableFuture<Void> applyMetadata(BotJobDetailsState state);
        void preScanCommand(String type, JsonObject body);
        void preScanElementTest(SplitDTO payload, String type);

        default PreScanWorkflowService.Context pageScannerContext(int botJobId) {
            throw new IllegalStateException("Detached Page Scanner is not available");
        }

        default void pageScannerBootstrap(
                String workspaceSessionId, PreScanWorkflowService.Context context) {
            throw new IllegalStateException("Detached Page Scanner is not available");
        }

        default void pageScannerCommand(
                String type,
                JsonObject body,
                String workspaceSessionId,
                PreScanWorkflowService.Context context) {
            preScanCommand(type, body);
        }

        default void pageScannerElementTest(
                SplitDTO payload,
                String type,
                String workspaceSessionId,
                PreScanWorkflowService.Context context) {
            preScanElementTest(payload, type);
        }

        default void closePageScanner(String workspaceSessionId) {
            // Optional for compatibility hosts that do not own a detached scanner browser.
        }
    }
}
