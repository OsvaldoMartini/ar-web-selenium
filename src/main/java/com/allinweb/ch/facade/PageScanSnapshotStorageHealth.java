package com.allinweb.ch.facade;

import com.allinweb.ch.util.ARPropertyEnum;
import com.allinweb.ch.util.ARPropertyManager;
import com.allinweb.ch.util.PageDiagnosticDumper;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;

/** Fail-closed process state for sensitive Page Mapping snapshot storage. */
@Slf4j
public final class PageScanSnapshotStorageHealth {

    private static final Object LOCK = new Object();
    private static Path initializedPathDb;
    private static String unavailableReason = "";

    private PageScanSnapshotStorageHealth() {}

    /** Records the exact configuration generation whose ACLs and journals were reconciled. */
    static void markHealthyConfiguredRoot() throws IOException {
        Path configured = configuredPathDb();
        synchronized (LOCK) {
            initializedPathDb = configured;
            unavailableReason = "";
        }
    }

    /** Blocks all snapshot access after a startup security or recovery failure. */
    static void markUnhealthy(Throwable failure) {
        String detail = failure == null ? "unknown recovery failure" : failure.getMessage();
        synchronized (LOCK) {
            unavailableReason = detail == null || detail.isBlank()
                    ? "snapshot security recovery failed"
                    : detail;
        }
        log.error("Page Mapping snapshot storage is fail-closed: {}", unavailableReason);
    }

    /** A live database/root switch cannot safely share the initialized generation. */
    static void configurationChanged() {
        synchronized (LOCK) {
            if (initializedPathDb != null) {
                unavailableReason =
                        "database or PATH_DB configuration changed after snapshot storage initialization; restart is required";
            }
        }
    }

    public static void requireHealthy() throws IOException {
        Path configured = configuredPathDb();
        synchronized (LOCK) {
            if (!unavailableReason.isBlank()) {
                throw new IOException(
                        "Page Mapping snapshot storage is unavailable until restart: "
                                + unavailableReason);
            }
            if (initializedPathDb != null && !initializedPathDb.equals(configured)) {
                unavailableReason =
                        "PATH_DB no longer matches the initialized snapshot storage generation";
                throw new IOException(
                        "Page Mapping snapshot storage is unavailable until restart: "
                                + unavailableReason);
            }
        }
    }

    static void requireHealthyContext(String diagnosticPath) throws IOException {
        requireHealthy();
        if (diagnosticPath == null || diagnosticPath.isBlank()) {
            throw new IOException("Page Mapping snapshot storage path is unavailable");
        }
        Path supplied;
        try {
            supplied = Path.of(diagnosticPath).toAbsolutePath().normalize();
        } catch (RuntimeException invalid) {
            throw new IOException("Page Mapping snapshot storage path is invalid", invalid);
        }
        Path configured = configuredPathDb();
        if (!Objects.equals(supplied, configured)) {
            markUnhealthy(new IOException(
                    "A scan attempted to use a stale PATH_DB configuration generation"));
            throw new IOException(
                    "Page Mapping snapshot storage changed while the scan was running; restart is required");
        }
    }

    public static Path configuredSnapshotRoot() throws IOException {
        requireHealthy();
        return configuredPathDb()
                .resolve(PageDiagnosticDumper.SUBFOLDER)
                .resolve("Scanned")
                .toAbsolutePath()
                .normalize();
    }

    private static Path configuredPathDb() throws IOException {
        String raw = ARPropertyManager.getInstance().getProperty(ARPropertyEnum.PATH_DB);
        if (raw == null || raw.isBlank()) {
            throw new IOException("PATH_DB is not configured for Page Mapping snapshot storage");
        }
        try {
            return Path.of(raw).toAbsolutePath().normalize();
        } catch (RuntimeException invalid) {
            throw new IOException("PATH_DB is invalid for Page Mapping snapshot storage", invalid);
        }
    }
}
