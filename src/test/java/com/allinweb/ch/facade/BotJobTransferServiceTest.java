package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BotJobTransferServiceTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void mapsSupportedDatabaseTypesToStableFilenameSlugs() {
        assertAll(
                () -> assertEquals("sqlite", BotJobTransferService.dialectSlug("TEXT")),
                () -> assertEquals("sqlite", BotJobTransferService.dialectSlug(" sqlite ")),
                () -> assertEquals("access", BotJobTransferService.dialectSlug("Access")),
                () -> assertEquals("postgres", BotJobTransferService.dialectSlug("Postgres")),
                () -> assertEquals("postgres", BotJobTransferService.dialectSlug("PostGres")));
    }

    @Test
    void unknownMissingAndBlankDatabaseTypesUseTheSafeGenericSlug() {
        assertAll(
                () -> assertEquals("db", BotJobTransferService.dialectSlug(null)),
                () -> assertEquals("db", BotJobTransferService.dialectSlug("")),
                () -> assertEquals("db", BotJobTransferService.dialectSlug("   ")),
                () -> assertEquals("db", BotJobTransferService.dialectSlug("Oracle")));
    }

    @Test
    void buildsJobScopedAndLegacyCompatibleBackupFilenames() {
        assertAll(
                () -> assertEquals(
                        "backup_(BY_BOT_JOB)_sqlite_42_2026_07_12.sql",
                        BotJobTransferService.fileName("SQLite", 42, "2026_07_12")),
                () -> assertEquals(
                        "backup_(BY_BOT_JOB)_access_7_2026_01_02.sql",
                        BotJobTransferService.fileName("Access", 7, "2026_01_02")),
                () -> assertEquals(
                        "backup_(BY_BOT_JOB)_db_99_2026_12_31.sql",
                        BotJobTransferService.fileName("unknown", 99, "2026_12_31")),
                () -> assertEquals(
                        "backup_(BY_BOT_JOB)_sqlite_2026_07_12.sql",
                        BotJobTransferService.legacyFileName("SQLite", "2026_07_12")),
                () -> assertEquals(
                        "backup_(BY_BOT_JOB)_postgres_2026_01_02.sql",
                        BotJobTransferService.legacyFileName("Postgres", "2026_01_02")));
    }

    @Test
    void publishesCompletedExportsUnderUniqueNamesWithoutReplacingTheFirstFile() throws Exception {
        String preferred = BotJobTransferService.fileName("SQLite", 42, "2026_07_12_153045_123");
        Path firstTemporary = Files.createTempFile(temporaryDirectory, "first-", ".tmp");
        Files.writeString(firstTemporary, "first snapshot");
        File first = BotJobTransferService.moveCompletedExport(
                firstTemporary, temporaryDirectory.toFile(), preferred);

        Path secondTemporary = Files.createTempFile(temporaryDirectory, "second-", ".tmp");
        Files.writeString(secondTemporary, "second snapshot");
        File second = BotJobTransferService.moveCompletedExport(
                secondTemporary, temporaryDirectory.toFile(), preferred);

        assertAll(
                () -> assertEquals(preferred, first.getName()),
                () -> assertNotEquals(first.getName(), second.getName()),
                () -> assertEquals("first snapshot", Files.readString(first.toPath())),
                () -> assertEquals("second snapshot", Files.readString(second.toPath())));
    }

    @Test
    void selectsNewestScopedSnapshotThenExactAndLegacyFallbacks() throws Exception {
        String date = "2026_07_12";
        Path exact = Files.writeString(
                temporaryDirectory.resolve(BotJobTransferService.fileName("SQLite", 42, date)), "exact");
        Path early = Files.writeString(
                temporaryDirectory.resolve(
                        BotJobTransferService.fileName("SQLite", 42, date + "_080000_000")),
                "early");
        Path latest = Files.writeString(
                temporaryDirectory.resolve(
                        BotJobTransferService.fileName("SQLite", 42, date + "_170000_000")),
                "latest");
        Files.writeString(
                temporaryDirectory.resolve(
                        BotJobTransferService.fileName("SQLite", 99, date + "_230000_000")),
                "other job");

        assertEquals(
                latest.toFile(),
                BotJobTransferService.findImportSource(temporaryDirectory.toFile(), "SQLite", 42, date));

        Files.delete(latest);
        Files.delete(early);
        assertEquals(
                exact.toFile(),
                BotJobTransferService.findImportSource(temporaryDirectory.toFile(), "SQLite", 42, date));

        Files.delete(exact);
        Path legacy = Files.writeString(
                temporaryDirectory.resolve(BotJobTransferService.legacyFileName("SQLite", date)), "legacy");
        assertEquals(
                legacy.toFile(),
                BotJobTransferService.findImportSource(temporaryDirectory.toFile(), "SQLite", 42, date));

        Files.delete(legacy);
        assertNull(BotJobTransferService.findImportSource(temporaryDirectory.toFile(), "SQLite", 42, date));
    }
}
