package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.allinweb.ch.model.BotJobToolbarAction;
import com.allinweb.ch.model.BotJobToolbarActionResult;
import com.allinweb.ch.model.ScannerWorkspaceSessions;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class BotJobDetailsToolbarLedgerTest {

    @Test
    void replaysTheSameFutureWithoutExecutingTheToolbarActionTwice() {
        BotJobDetailsToolbarLedger ledger = new BotJobDetailsToolbarLedger(4);
        AtomicInteger executions = new AtomicInteger();

        CompletableFuture<BotJobToolbarActionResult> first = ledger.executeOnce(
                ScannerWorkspaceSessions.BOT_JOB_TASKS,
                "request-1",
                42,
                BotJobToolbarAction.SET_NAVIGATION_TIME,
                "navigationTimeSeconds=4",
                () -> {
                    executions.incrementAndGet();
                    return CompletableFuture.completedFuture(BotJobToolbarActionResult.success(
                            BotJobToolbarAction.SET_NAVIGATION_TIME, "Navigation time updated"));
                });
        CompletableFuture<BotJobToolbarActionResult> replay = ledger.executeOnce(
                ScannerWorkspaceSessions.BOT_JOB_TASKS,
                "request-1",
                42,
                BotJobToolbarAction.SET_NAVIGATION_TIME,
                "navigationTimeSeconds=4",
                () -> {
                    executions.incrementAndGet();
                    return CompletableFuture.completedFuture(BotJobToolbarActionResult.failure(
                            BotJobToolbarAction.SET_NAVIGATION_TIME, "must not execute"));
                });

        assertSame(first, replay);
        assertEquals(1, executions.get());
        assertTrue(replay.join().ok());
        assertEquals("Navigation time updated", replay.join().message());
    }

    @Test
    void rejectsRequestIdReuseWhenAnyFingerprintInputChanges() {
        BotJobDetailsToolbarLedger ledger = new BotJobDetailsToolbarLedger(4);
        AtomicInteger executions = new AtomicInteger();

        ledger.executeOnce(
                        ScannerWorkspaceSessions.BOT_JOB_TASKS,
                        "request-2",
                        42,
                        BotJobToolbarAction.TEST_RUN,
                        "blockId=91;mode=ALL",
                        () -> {
                            executions.incrementAndGet();
                            return CompletableFuture.completedFuture(BotJobToolbarActionResult.success(
                                    BotJobToolbarAction.TEST_RUN, "TEST RUN accepted"));
                        })
                .join();

        BotJobToolbarActionResult changedPayload = ledger.executeOnce(
                        ScannerWorkspaceSessions.BOT_JOB_TASKS,
                        "request-2",
                        42,
                        BotJobToolbarAction.TEST_RUN,
                        "blockId=91;mode=ONE",
                        () -> {
                            executions.incrementAndGet();
                            return CompletableFuture.completedFuture(BotJobToolbarActionResult.success(
                                    BotJobToolbarAction.TEST_RUN, "must not execute"));
                        })
                .join();
        BotJobToolbarActionResult changedAction = ledger.executeOnce(
                        ScannerWorkspaceSessions.BOT_JOB_TASKS,
                        "request-2",
                        42,
                        BotJobToolbarAction.STOP_TEST_RUN,
                        "blockId=91;mode=ALL",
                        () -> {
                            executions.incrementAndGet();
                            return CompletableFuture.completedFuture(BotJobToolbarActionResult.success(
                                    BotJobToolbarAction.STOP_TEST_RUN, "must not execute"));
                        })
                .join();
        BotJobToolbarActionResult changedJob = ledger.executeOnce(
                        ScannerWorkspaceSessions.BOT_JOB_TASKS,
                        "request-2",
                        99,
                        BotJobToolbarAction.TEST_RUN,
                        "blockId=91;mode=ALL",
                        () -> {
                            executions.incrementAndGet();
                            return CompletableFuture.completedFuture(BotJobToolbarActionResult.success(
                                    BotJobToolbarAction.TEST_RUN, "must not execute"));
                        })
                .join();

        assertEquals(1, executions.get());
        for (BotJobToolbarActionResult conflict :
                new BotJobToolbarActionResult[] {changedPayload, changedAction, changedJob}) {
            assertFalse(conflict.ok());
            assertTrue(conflict.message().contains("requestId was reused"));
        }
        assertEquals(BotJobToolbarAction.TEST_RUN.name(), changedPayload.action());
        assertEquals(BotJobToolbarAction.STOP_TEST_RUN.name(), changedAction.action());
    }

    @Test
    void scopesTheSameRequestIdToItsTransportSession() {
        BotJobDetailsToolbarLedger ledger = new BotJobDetailsToolbarLedger(4);
        AtomicInteger executions = new AtomicInteger();

        for (String session : new String[] {
                ScannerWorkspaceSessions.BOT_JOB_TASKS,
                PreScannerGridRoute.standardSearchTerms().destinationSessionId()}) {
            BotJobToolbarActionResult result = ledger.executeOnce(
                            session,
                            "shared-request",
                            42,
                            BotJobToolbarAction.REFRESH_BLOCKS,
                            "",
                            () -> {
                                executions.incrementAndGet();
                                return CompletableFuture.completedFuture(BotJobToolbarActionResult.success(
                                        BotJobToolbarAction.REFRESH_BLOCKS, session));
                            })
                    .join();
            assertEquals(session, result.message());
        }

        assertEquals(2, executions.get());
    }

    @Test
    void rejectsInvalidCapacityAndNullOperationResult() {
        assertThrows(IllegalArgumentException.class, () -> new BotJobDetailsToolbarLedger(0));

        BotJobDetailsToolbarLedger ledger = new BotJobDetailsToolbarLedger(1);
        assertThrows(
                IllegalStateException.class,
                () -> ledger.executeOnce(
                        ScannerWorkspaceSessions.BOT_JOB_TASKS,
                        "request-null",
                        42,
                        BotJobToolbarAction.OPEN_EXCEL,
                        "",
                        () -> null));
    }
}
