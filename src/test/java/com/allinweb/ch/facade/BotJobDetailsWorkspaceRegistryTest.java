package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.allinweb.ch.model.BotJobLoadDTO;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BotJobDetailsWorkspaceRegistryTest {

    private BotJobDetailsWorkspaceRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new BotJobDetailsWorkspaceRegistry();
        BotJobLoadDTO job = new BotJobLoadDTO();
        job.setId(42);
        job.setName("Payments");
        job.setDescription("Payment flow");
        job.setPriority("Web App");
        job.setHomeBankingId(7);
        job.setHomeUrlId(8);
        registry.activate(job, false);
    }

    @Test
    void commitsPersistenceBeforePublishingTheNextRevision() {
        BotJobDetailsWorkspaceRegistry.Snapshot before = registry.require(42);
        AtomicBoolean persistenceCalled = new AtomicBoolean();

        BotJobDetailsWorkspaceRegistry.MetadataCommit<String> commit = registry.commitMetadata(
                42,
                before.metadataRevision(),
                "Payments QA",
                "Updated",
                9,
                () -> {
                    persistenceCalled.set(true);
                    assertEquals(before.revision(), registry.require(42).revision());
                    return null;
                });

        assertTrue(persistenceCalled.get());
        assertTrue(commit.committed());
        assertTrue(commit.snapshot().revision() > before.revision());
        assertEquals("Payments QA", registry.require(42).name());
        assertEquals(9, registry.require(42).homeUrlId());
    }

    @Test
    void persistenceFailureAndStaleRevisionLeaveSnapshotUnchanged() {
        BotJobDetailsWorkspaceRegistry.Snapshot before = registry.require(42);
        BotJobDetailsWorkspaceRegistry.MetadataCommit<String> failed = registry.commitMetadata(
                42,
                before.metadataRevision(),
                "Payments QA",
                "Updated",
                9,
                () -> "database rejected update");

        assertFalse(failed.committed());
        assertEquals("database rejected update", failed.persistenceError());
        assertEquals(before, registry.require(42));

        AtomicBoolean persistenceAfterWorkspaceAction = new AtomicBoolean();
        registry.updateWorkspace(42, "components", true);
        BotJobDetailsWorkspaceRegistry.MetadataCommit<String> committed = registry.commitMetadata(
                42,
                before.metadataRevision(),
                "Fresh metadata",
                "Fresh",
                8,
                () -> {
                    persistenceAfterWorkspaceAction.set(true);
                    return null;
                });
        assertTrue(committed.committed());
        assertTrue(persistenceAfterWorkspaceAction.get());

        AtomicBoolean stalePersistenceCalled = new AtomicBoolean();
        assertThrows(
                BotJobDetailsWorkspaceRegistry.RevisionConflictException.class,
                () -> registry.commitMetadata(
                        42,
                        before.metadataRevision(),
                        "Stale",
                        "Stale",
                        8,
                        () -> {
                            stalePersistenceCalled.set(true);
                            return null;
                        }));
        assertFalse(stalePersistenceCalled.get());
    }

    @Test
    void workspaceMutationRequiresAndRetainsTheExactActiveEpoch() {
        BotJobDetailsWorkspaceRegistry.Snapshot workspace = registry.require(42);

        String result = registry.commitWorkspaceMutation(
                42,
                workspace.workspaceEpoch(),
                () -> {
                    assertEquals(workspace, registry.require(42, workspace.workspaceEpoch()));
                    return "committed";
                });

        assertEquals("committed", result);
        registry.close(42);
        assertThrows(
                IllegalArgumentException.class,
                () -> registry.commitWorkspaceMutation(42, workspace.workspaceEpoch(), () -> "stale"));
    }

    @Test
    void executionTransitionsAdvanceStateWithoutInvalidatingMetadata() {
        BotJobDetailsWorkspaceRegistry.Snapshot before = registry.require(42);

        BotJobDetailsWorkspaceRegistry.Snapshot running = registry.updateExecutionState(42, "RUNNING");

        assertTrue(running.revision() > before.revision());
        assertEquals(before.metadataRevision(), running.metadataRevision());
        assertEquals("RUNNING", running.executionState());
    }

    @Test
    void reopeningTheSameJobInvalidatesThePreviousWorkspaceEpoch() {
        BotJobDetailsWorkspaceRegistry.Snapshot first = registry.require(42);
        registry.close(42);

        BotJobLoadDTO reopened = new BotJobLoadDTO();
        reopened.setId(42);
        reopened.setName("Payments");
        BotJobDetailsWorkspaceRegistry.Snapshot second = registry.activate(reopened, false);

        assertTrue(second.workspaceEpoch() > first.workspaceEpoch());
        assertThrows(
                IllegalArgumentException.class,
                () -> registry.require(42, first.workspaceEpoch()));
        assertEquals(second, registry.require(42, second.workspaceEpoch()));
    }

    @Test
    void stopDuringStartupIsPromptAndTerminalStateCannotBeOverwritten() {
        BotJobDetailsWorkspaceRegistry.Snapshot workspace = registry.require(42);
        BotJobDetailsWorkspaceRegistry.ExecutionAttempt attempt =
                registry.beginTestRun(42, workspace.workspaceEpoch());

        BotJobDetailsWorkspaceRegistry.StopDecision firstStop =
                registry.requestTestRunStop(42, workspace.workspaceEpoch());
        BotJobDetailsWorkspaceRegistry.StopDecision repeatedStop =
                registry.requestTestRunStop(42, workspace.workspaceEpoch());

        assertTrue(firstStop.accepted());
        assertFalse(firstStop.alreadyRequested());
        assertEquals("STARTING", firstStop.previousState());
        assertTrue(repeatedStop.accepted());
        assertTrue(repeatedStop.alreadyRequested());
        assertFalse(registry.markTestRunRunning(attempt));
        assertTrue(registry.finishTestRun(attempt, "INTERRUPTED"));
        assertFalse(registry.finishTestRun(attempt, "PASSED"));
        assertEquals("INTERRUPTED", registry.require(42).executionState());
    }

    @Test
    void failedOwnedStopCanRestoreThePreviousStateForRetry() {
        BotJobDetailsWorkspaceRegistry.Snapshot workspace = registry.require(42);
        BotJobDetailsWorkspaceRegistry.ExecutionAttempt attempt =
                registry.beginTestRun(42, workspace.workspaceEpoch());
        assertTrue(registry.markTestRunRunning(attempt));
        BotJobDetailsWorkspaceRegistry.StopDecision stop =
                registry.requestTestRunStop(42, workspace.workspaceEpoch());

        assertEquals("RUNNING", stop.previousState());
        assertTrue(registry.restoreTestRunAfterStopFailure(attempt, stop.previousState()));
        assertEquals("RUNNING", registry.require(42).executionState());
        assertFalse(registry.restoreTestRunAfterStopFailure(attempt, "RUNNING"));
        assertFalse(registry.restoreTestRunAfterStopFailure(
                new BotJobDetailsWorkspaceRegistry.ExecutionAttempt(42, workspace.workspaceEpoch(), 999),
                "RUNNING"));
    }

    @Test
    void anOldExecutionAttemptCannotFinishANewerRun() {
        long workspaceEpoch = registry.require(42).workspaceEpoch();
        BotJobDetailsWorkspaceRegistry.ExecutionAttempt first = registry.beginTestRun(42, workspaceEpoch);
        assertTrue(registry.finishTestRun(first, "PASSED"));
        BotJobDetailsWorkspaceRegistry.ExecutionAttempt second = registry.beginTestRun(42, workspaceEpoch);

        assertFalse(registry.finishTestRun(first, "FAILED"));
        assertEquals(second.attemptId(), registry.require(42).executionAttemptId());
        assertEquals("STARTING", registry.require(42).executionState());
    }

    @Test
    void naturalCompletionPublishesPassedAndRejectsIdleAsATerminalState() {
        long workspaceEpoch = registry.require(42).workspaceEpoch();
        BotJobDetailsWorkspaceRegistry.ExecutionAttempt attempt = registry.beginTestRun(42, workspaceEpoch);

        assertTrue(registry.finishTestRun(attempt, "PASSED"));
        assertEquals("PASSED", registry.require(42).executionState());

        BotJobDetailsWorkspaceRegistry.ExecutionAttempt next = registry.beginTestRun(42, workspaceEpoch);
        assertThrows(IllegalArgumentException.class, () -> registry.finishTestRun(next, "IDLE"));
    }
}
