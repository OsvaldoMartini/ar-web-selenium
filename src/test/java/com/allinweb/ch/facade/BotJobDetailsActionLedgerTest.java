package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.allinweb.ch.model.BotJobWorkspaceAction;
import com.allinweb.ch.model.BotJobWorkspaceActionResult;
import com.allinweb.ch.model.ScannerWorkspaceSessions;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class BotJobDetailsActionLedgerTest {

    @Test
    void duplicateTransportRequestExecutesActionOnlyOnce() {
        BotJobDetailsActionLedger ledger = new BotJobDetailsActionLedger(4);
        AtomicInteger executions = new AtomicInteger();

        CompletableFuture<BotJobWorkspaceActionResult> first = ledger.executeOnce(
                ScannerWorkspaceSessions.BOT_JOB_TASKS,
                "refresh-1",
                42,
                BotJobWorkspaceAction.REFRESH,
                () -> action(executions));
        CompletableFuture<BotJobWorkspaceActionResult> duplicate = ledger.executeOnce(
                ScannerWorkspaceSessions.BOT_JOB_TASKS,
                "refresh-1",
                42,
                BotJobWorkspaceAction.REFRESH,
                () -> action(executions));

        assertSame(first, duplicate);
        assertEquals(1, executions.get());
        assertEquals(BotJobWorkspaceAction.REFRESH.name(), duplicate.join().action());
    }

    @Test
    void rejectsRequestIdReuseWithDifferentFingerprint() {
        BotJobDetailsActionLedger ledger = new BotJobDetailsActionLedger(4);
        AtomicInteger executions = new AtomicInteger();
        ledger.executeOnce(
                ScannerWorkspaceSessions.BOT_JOB_TASKS,
                "same-id",
                42,
                BotJobWorkspaceAction.REFRESH,
                () -> action(executions));

        BotJobWorkspaceActionResult conflict = ledger.executeOnce(
                        ScannerWorkspaceSessions.BOT_JOB_TASKS,
                        "same-id",
                        99,
                        BotJobWorkspaceAction.CLOSE,
                        () -> action(executions))
                .join();

        assertEquals(1, executions.get());
        assertFalse(conflict.ok());
        assertEquals(BotJobWorkspaceAction.CLOSE.name(), conflict.action());
    }

    @Test
    void neverEvictsAnInFlightRequestToEnforceTheCapacityLimit() {
        BotJobDetailsActionLedger ledger = new BotJobDetailsActionLedger(1);
        AtomicInteger executions = new AtomicInteger();
        CompletableFuture<BotJobWorkspaceActionResult> firstResult = new CompletableFuture<>();
        CompletableFuture<BotJobWorkspaceActionResult> secondResult = new CompletableFuture<>();

        CompletableFuture<BotJobWorkspaceActionResult> first = ledger.executeOnce(
                ScannerWorkspaceSessions.BOT_JOB_TASKS,
                "request-1",
                42,
                BotJobWorkspaceAction.REFRESH,
                () -> pending(executions, firstResult));
        ledger.executeOnce(
                ScannerWorkspaceSessions.BOT_JOB_TASKS,
                "request-2",
                42,
                BotJobWorkspaceAction.SHOW_COMPONENTS,
                () -> pending(executions, secondResult));
        CompletableFuture<BotJobWorkspaceActionResult> duplicate = ledger.executeOnce(
                ScannerWorkspaceSessions.BOT_JOB_TASKS,
                "request-1",
                42,
                BotJobWorkspaceAction.REFRESH,
                () -> pending(executions, new CompletableFuture<>()));

        assertSame(first, duplicate);
        assertEquals(2, executions.get());
    }

    @Test
    void scopesTheSameRequestIdToItsTransportSession() {
        BotJobDetailsActionLedger ledger = new BotJobDetailsActionLedger(4);
        AtomicInteger executions = new AtomicInteger();

        ledger.executeOnce(
                ScannerWorkspaceSessions.BOT_JOB_TASKS,
                "shared-id",
                42,
                BotJobWorkspaceAction.REFRESH,
                () -> action(executions));
        ledger.executeOnce(
                ScannerWorkspaceSessions.COMPONENT_TASKS,
                "shared-id",
                42,
                BotJobWorkspaceAction.REFRESH,
                () -> action(executions));

        assertEquals(2, executions.get());
    }

    @Test
    void retainsBoundedCompletedHistorySeparatelyFromInFlightRequests() {
        BotJobDetailsActionLedger ledger = new BotJobDetailsActionLedger(1);
        AtomicInteger executions = new AtomicInteger();
        CompletableFuture<BotJobWorkspaceActionResult> stalled = new CompletableFuture<>();
        CompletableFuture<BotJobWorkspaceActionResult> completedLater = new CompletableFuture<>();

        ledger.executeOnce(
                ScannerWorkspaceSessions.BOT_JOB_TASKS,
                "stalled",
                42,
                BotJobWorkspaceAction.REFRESH,
                () -> pending(executions, stalled));
        CompletableFuture<BotJobWorkspaceActionResult> completed = ledger.executeOnce(
                ScannerWorkspaceSessions.BOT_JOB_TASKS,
                "completed",
                42,
                BotJobWorkspaceAction.SHOW_COMPONENTS,
                () -> pending(executions, completedLater));
        completedLater.complete(BotJobWorkspaceActionResult.success(
                BotJobWorkspaceAction.SHOW_COMPONENTS, "opened", "components", true));

        CompletableFuture<BotJobWorkspaceActionResult> duplicate = ledger.executeOnce(
                ScannerWorkspaceSessions.BOT_JOB_TASKS,
                "completed",
                42,
                BotJobWorkspaceAction.SHOW_COMPONENTS,
                () -> pending(executions, new CompletableFuture<>()));

        assertSame(completed, duplicate);
        assertEquals(2, executions.get());
    }

    private CompletableFuture<BotJobWorkspaceActionResult> action(AtomicInteger executions) {
        executions.incrementAndGet();
        return CompletableFuture.completedFuture(BotJobWorkspaceActionResult.success(
                BotJobWorkspaceAction.REFRESH, "refreshed", "botJob", false));
    }

    private CompletableFuture<BotJobWorkspaceActionResult> pending(
            AtomicInteger executions, CompletableFuture<BotJobWorkspaceActionResult> result) {
        executions.incrementAndGet();
        return result;
    }
}
