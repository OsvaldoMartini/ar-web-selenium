package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BotJobTransferPathRegistryTest {

    private static final int BOT_JOB_ID = 42;
    private static final int OTHER_BOT_JOB_ID = 43;

    private final BotJobTransferPathRegistry registry = BotJobTransferPathRegistry.getInstance();
    private String sessionId;
    private String otherSessionId;

    @TempDir
    Path tempDirectory;

    @BeforeEach
    void setUp() {
        sessionId = "transfer-test-" + UUID.randomUUID();
        otherSessionId = "transfer-test-" + UUID.randomUUID();
    }

    @AfterEach
    void tearDown() {
        registry.clear(sessionId, BOT_JOB_ID);
        registry.clear(sessionId, OTHER_BOT_JOB_ID);
        registry.clear(otherSessionId, BOT_JOB_ID);
        registry.clear(otherSessionId, OTHER_BOT_JOB_ID);
    }

    @Test
    void selectedDirectoryIsBoundToBothTransportSessionAndBotJob() throws Exception {
        Path selected = Files.createDirectory(tempDirectory.resolve("selected"));

        String canonical = registry.select(sessionId, BOT_JOB_ID, selected.toFile());

        assertEquals(selected.toRealPath().toString(), canonical);
        assertEquals(canonical, registry.require(sessionId, BOT_JOB_ID, selected.resolve(".").toString()));
        assertThrows(
                IllegalStateException.class,
                () -> registry.require(otherSessionId, BOT_JOB_ID, canonical));
        assertThrows(
                IllegalStateException.class,
                () -> registry.require(sessionId, OTHER_BOT_JOB_ID, canonical));
    }

    @Test
    void rejectsAClaimedDirectoryDifferentFromTheNativeSelection() throws Exception {
        Path selected = Files.createDirectory(tempDirectory.resolve("selected"));
        Path other = Files.createDirectory(tempDirectory.resolve("other"));
        registry.select(sessionId, BOT_JOB_ID, selected.toFile());

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> registry.require(sessionId, BOT_JOB_ID, other.toString()));

        assertEquals("Transfer folder does not match the folder selected for this session", error.getMessage());
    }

    @Test
    void separateJobsCanRetainDifferentSelectionsInTheSameSession() throws Exception {
        Path first = Files.createDirectory(tempDirectory.resolve("first"));
        Path second = Files.createDirectory(tempDirectory.resolve("second"));

        String firstCanonical = registry.select(sessionId, BOT_JOB_ID, first.toFile());
        String secondCanonical = registry.select(sessionId, OTHER_BOT_JOB_ID, second.toFile());

        assertEquals(firstCanonical, registry.require(sessionId, BOT_JOB_ID, first.toString()));
        assertEquals(secondCanonical, registry.require(sessionId, OTHER_BOT_JOB_ID, second.toString()));
    }

    @Test
    void clearRevokesTheSelectedDirectory() throws Exception {
        Path selected = Files.createDirectory(tempDirectory.resolve("selected"));
        registry.select(sessionId, BOT_JOB_ID, selected.toFile());

        registry.clear(sessionId, BOT_JOB_ID);

        assertThrows(
                IllegalStateException.class,
                () -> registry.require(sessionId, BOT_JOB_ID, selected.toString()));
    }

    @Test
    void clearingATransportSessionRevokesEveryJobGrantWithoutAffectingOtherSessions() throws Exception {
        Path first = Files.createDirectory(tempDirectory.resolve("first"));
        Path second = Files.createDirectory(tempDirectory.resolve("second"));
        registry.select(sessionId, BOT_JOB_ID, first.toFile());
        registry.select(sessionId, OTHER_BOT_JOB_ID, second.toFile());
        registry.select(otherSessionId, BOT_JOB_ID, first.toFile());

        registry.clearSession(sessionId);

        assertThrows(
                IllegalStateException.class,
                () -> registry.require(sessionId, BOT_JOB_ID, first.toString()));
        assertThrows(
                IllegalStateException.class,
                () -> registry.require(sessionId, OTHER_BOT_JOB_ID, second.toString()));
        assertEquals(first.toRealPath().toString(),
                registry.require(otherSessionId, BOT_JOB_ID, first.toString()));
    }

    @Test
    void selectionRequiresARealDirectoryAndValidScope() throws Exception {
        Path file = Files.createFile(tempDirectory.resolve("not-a-directory.txt"));

        assertThrows(
                IllegalArgumentException.class,
                () -> registry.select(" ", BOT_JOB_ID, tempDirectory.toFile()));
        assertThrows(
                IllegalArgumentException.class,
                () -> registry.select(sessionId, 0, tempDirectory.toFile()));
        assertThrows(
                IllegalArgumentException.class,
                () -> registry.select(sessionId, BOT_JOB_ID, file.toFile()));
        assertThrows(
                IllegalArgumentException.class,
                () -> registry.select(sessionId, BOT_JOB_ID, null));
    }
}
