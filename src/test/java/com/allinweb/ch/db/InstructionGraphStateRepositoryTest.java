package com.allinweb.ch.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.allinweb.ch.db.InstructionGraphStateRepository.AdvanceResult;
import com.allinweb.ch.db.InstructionGraphStateRepository.AdvanceStatus;
import com.allinweb.ch.db.InstructionGraphStateRepository.GraphState;
import com.allinweb.ch.db.InstructionGraphStateRepository.OwnerKey;
import com.allinweb.ch.db.migrations.M20260729_InstructionGraphState;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class InstructionGraphStateRepositoryTest {

    private final InstructionGraphStateRepository repository =
            new InstructionGraphStateRepository();

    @TempDir
    Path tempDir;

    @Test
    void loadOrCreateIsIdempotent() throws Exception {
        try (Connection connection = database()) {
            OwnerKey owner = OwnerKey.botJob(2, 5);

            GraphState first = repository.loadOrCreate(connection, owner);
            GraphState second = repository.loadOrCreate(connection, owner);

            assertEquals(0L, first.version());
            assertEquals(first, second);
            assertEquals(1, rowCount(connection));
        }
    }

    @Test
    void compareAndSetAllowsExactlyOneWinnerForTheSameSnapshot() throws Exception {
        String url = fileDatabaseUrl("one-winner.db");
        try (Connection bootstrap = DriverManager.getConnection(url)) {
            migrate(bootstrap);
            repository.loadOrCreate(bootstrap, OwnerKey.botJob(2, 5));
        }

        try (Connection firstConnection = DriverManager.getConnection(url);
                Connection secondConnection = DriverManager.getConnection(url)) {
            GraphState firstSnapshot =
                    repository.load(firstConnection, OwnerKey.botJob(2, 5)).orElseThrow();
            GraphState secondSnapshot =
                    repository.load(secondConnection, OwnerKey.botJob(2, 5)).orElseThrow();
            assertEquals(firstSnapshot, secondSnapshot);

            AdvanceResult first = repository.compareAndSetIncrement(
                    firstConnection,
                    firstSnapshot.owner(),
                    firstSnapshot.version());
            AdvanceResult second = repository.compareAndSetIncrement(
                    secondConnection,
                    secondSnapshot.owner(),
                    secondSnapshot.version());

            assertTrue(first.advanced());
            assertEquals(1L, first.state().version());
            assertFalse(second.advanced());
            assertEquals(AdvanceStatus.STALE, second.status());
            assertEquals(1L, second.state().version());
        }
    }

    @Test
    void staleAndMissingVersionsAreRefusedWithoutWriting() throws Exception {
        try (Connection connection = database()) {
            OwnerKey existing = OwnerKey.botJob(2, 5);
            repository.loadOrCreate(connection, existing);

            AdvanceResult stale =
                    repository.compareAndSetIncrement(connection, existing, 7L);
            AdvanceResult missing = repository.compareAndSetIncrement(
                    connection,
                    OwnerKey.botJob(2, 6),
                    0L);

            assertEquals(AdvanceStatus.STALE, stale.status());
            assertEquals(0L, stale.state().version());
            assertEquals(AdvanceStatus.MISSING, missing.status());
            assertEquals(0L, repository.load(connection, existing).orElseThrow().version());
            assertTrue(repository.load(connection, OwnerKey.botJob(2, 6)).isEmpty());
        }
    }

    @Test
    void botJobAndComponentOwnersRemainSeparatedEvenWhenTheirNumericIdsMatch()
            throws Exception {
        try (Connection connection = database()) {
            OwnerKey botJob = OwnerKey.botJob(2, 2);
            OwnerKey component = OwnerKey.component(2);
            repository.loadOrCreate(connection, botJob);
            repository.loadOrCreate(connection, component);

            assertTrue(repository.compareAndSetIncrement(connection, botJob, 0L).advanced());

            assertEquals(1L, repository.load(connection, botJob).orElseThrow().version());
            assertEquals(0L, repository.load(connection, component).orElseThrow().version());
            assertEquals(2, rowCount(connection));
        }
    }

    @Test
    void callerOwnsCommitRollbackAndAutoCommitForCreateAdvanceAndCleanup() throws Exception {
        String url = fileDatabaseUrl("caller-transaction.db");
        OwnerKey persisted = OwnerKey.botJob(2, 5);
        OwnerKey transientOwner = OwnerKey.component(2);
        try (Connection bootstrap = DriverManager.getConnection(url)) {
            migrate(bootstrap);
            repository.loadOrCreate(bootstrap, persisted);
        }

        try (Connection connection = DriverManager.getConnection(url)) {
            connection.setAutoCommit(false);

            repository.loadOrCreate(connection, transientOwner);
            assertTrue(repository.compareAndSetIncrement(connection, persisted, 0L).advanced());
            assertEquals(1, repository.cleanup(connection, persisted));
            assertFalse(connection.getAutoCommit());

            connection.rollback();
            assertFalse(connection.getAutoCommit());
            assertTrue(repository.load(connection, transientOwner).isEmpty());
            assertEquals(0L, repository.load(connection, persisted).orElseThrow().version());
        }
    }

    @Test
    void cleanupDeletesOnlyTheRequestedOwner() throws Exception {
        try (Connection connection = database()) {
            OwnerKey botJob = OwnerKey.botJob(2, 2);
            OwnerKey component = OwnerKey.component(2);
            repository.loadOrCreate(connection, botJob);
            repository.loadOrCreate(connection, component);

            assertEquals(1, repository.cleanup(connection, botJob));
            assertEquals(0, repository.cleanup(connection, botJob));
            assertTrue(repository.load(connection, botJob).isEmpty());
            assertTrue(repository.load(connection, component).isPresent());
        }
    }

    private Connection database() throws Exception {
        Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:");
        migrate(connection);
        return connection;
    }

    private void migrate(Connection connection) throws Exception {
        new M20260729_InstructionGraphState().apply(connection, "TEXT");
    }

    private String fileDatabaseUrl(String name) {
        return "jdbc:sqlite:" + tempDir.resolve(name);
    }

    private int rowCount(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement();
                ResultSet rows =
                        statement.executeQuery("SELECT COUNT(*) FROM instruction_graph_state")) {
            return rows.next() ? rows.getInt(1) : -1;
        }
    }
}
