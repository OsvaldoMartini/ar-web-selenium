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
                before.revision(),
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
                before.revision(),
                "Payments QA",
                "Updated",
                9,
                () -> "database rejected update");

        assertFalse(failed.committed());
        assertEquals("database rejected update", failed.persistenceError());
        assertEquals(before, registry.require(42));

        AtomicBoolean stalePersistenceCalled = new AtomicBoolean();
        registry.updateWorkspace(42, "components", true);
        assertThrows(
                BotJobDetailsWorkspaceRegistry.RevisionConflictException.class,
                () -> registry.commitMetadata(
                        42,
                        before.revision(),
                        "Stale",
                        "Stale",
                        8,
                        () -> {
                            stalePersistenceCalled.set(true);
                            return null;
                        }));
        assertFalse(stalePersistenceCalled.get());
    }
}
