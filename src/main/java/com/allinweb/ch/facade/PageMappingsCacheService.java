package com.allinweb.ch.facade;

import com.allinweb.ch.driver.ARPlaywrightDriver;
import com.allinweb.ch.driver.ARWebDriver;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;

/** Compares the active Playwright view with immutable Page Mappings captures. */
@Slf4j
public final class PageMappingsCacheService {

    private PageMappingsCacheService() {}

    public static CacheState inspect(
            Connection connection, int homeBankingId, int botJobId) {
        Objects.requireNonNull(connection, "connection");
        if (homeBankingId <= 0 || botJobId <= 0) {
            return CacheState.unavailable("Page Mappings requires an active Bot Job owner.");
        }
        try {
            if (!PageScanSnapshotStore.hasViewFingerprintColumn(connection)) {
                return CacheState.migrationRequired();
            }
            ARPlaywrightDriver browser = ARWebDriver.getInstance().currentPlaywrightDriver();
            if (browser == null || !browser.isOpen()) {
                return CacheState.historicalOnly();
            }
            PageViewFingerprintService.Observation live =
                    PageViewFingerprintService.observe(browser);
            if (!live.cacheable()) {
                return CacheState.unsupported(live);
            }
            LatestCaptures captures = latestCaptures(
                    connection, homeBankingId, botJobId, live.page().pageKey());
            if (captures.currentPage() == null) {
                if (captures.overall() == null) {
                    return CacheState.noCapture(live);
                }
                return CacheState.pageChanged(live, captures.overall());
            }
            Capture latest = captures.currentPage();
            if (latest.viewFingerprint().isBlank()) {
                return CacheState.unknown(live, latest);
            }
            if (latest.viewFingerprint().equals(live.fingerprint())) {
                return CacheState.current(live, latest);
            }
            return CacheState.changed(live, latest);
        } catch (Exception failure) {
            log.warn(
                    "Page Mappings live comparison failed for homeBankingId={} botJobId={}",
                    homeBankingId,
                    botJobId,
                    failure);
            return CacheState.unavailable("Live mapping comparison is unavailable.");
        }
    }

    private static LatestCaptures latestCaptures(
            Connection connection,
            int homeBankingId,
            int botJobId,
            String currentPageKey)
            throws Exception {
        Capture overall = null;
        Capture currentPage = null;
        String sql = "SELECT scan_id, page_key, captured_at, view_fingerprint "
                + "FROM page_scan_snapshot WHERE home_banking_id = ? AND bot_job_id = ? "
                + "AND status = 'READY'";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, homeBankingId);
            statement.setInt(2, botJobId);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    Capture candidate = capture(rows);
                    if (candidate == null) continue;
                    if (newer(candidate, overall)) overall = candidate;
                    if (candidate.pageKey().equals(currentPageKey)
                            && newer(candidate, currentPage)) {
                        currentPage = candidate;
                    }
                }
            }
        }
        return new LatestCaptures(overall, currentPage);
    }

    private static Capture capture(ResultSet rows) {
        try {
            String scanId = Objects.toString(rows.getString("scan_id"), "").trim();
            String pageKey = Objects.toString(rows.getString("page_key"), "").trim();
            String capturedAt = Objects.toString(rows.getString("captured_at"), "").trim();
            if (scanId.isEmpty() || pageKey.isEmpty() || capturedAt.isEmpty()) return null;
            return new Capture(
                    scanId,
                    pageKey,
                    Instant.parse(capturedAt),
                    Objects.toString(rows.getString("view_fingerprint"), "").trim());
        } catch (Exception invalidRow) {
            return null;
        }
    }

    private static boolean newer(Capture candidate, Capture current) {
        if (current == null) return true;
        int compared = candidate.capturedAt().compareTo(current.capturedAt());
        return compared > 0
                || (compared == 0 && candidate.scanId().compareTo(current.scanId()) > 0);
    }

    private record Capture(
            String scanId, String pageKey, Instant capturedAt, String viewFingerprint) {}

    private record LatestCaptures(Capture overall, Capture currentPage) {}

    public record CacheState(
            String state,
            String message,
            boolean browserAvailable,
            String livePageKey,
            String livePageUrl,
            int liveNodeCount,
            String reusableScanId,
            String comparedScanId) {

        private static CacheState current(
                PageViewFingerprintService.Observation live, Capture capture) {
            return of(
                    "CURRENT",
                    "Saved mapping current - use the existing capture or rescan.",
                    live,
                    capture.scanId(),
                    capture.scanId());
        }

        private static CacheState changed(
                PageViewFingerprintService.Observation live, Capture capture) {
            return of(
                    "CHANGED",
                    "The live page structure changed. Rescan is recommended.",
                    live,
                    "",
                    capture.scanId());
        }

        private static CacheState pageChanged(
                PageViewFingerprintService.Observation live, Capture capture) {
            return of(
                    "PAGE_CHANGED",
                    "The active page has no matching capture. Rescan the current page.",
                    live,
                    "",
                    capture.scanId());
        }

        private static CacheState unknown(
                PageViewFingerprintService.Observation live, Capture capture) {
            return of(
                    "STALE",
                    "This capture predates structural fingerprints. Rescan to refresh it.",
                    live,
                    "",
                    capture.scanId());
        }

        private static CacheState unsupported(
                PageViewFingerprintService.Observation live) {
            return of(
                    "UNSUPPORTED",
                    live.diagnostic().isBlank()
                            ? "This live page requires a fresh scan."
                            : live.diagnostic(),
                    live,
                    "",
                    "");
        }

        public CacheState artifactStale() {
            return new CacheState(
                    "STALE",
                    "The saved mapping artifact is unavailable or failed integrity checks. Rescan the page.",
                    browserAvailable,
                    livePageKey,
                    livePageUrl,
                    liveNodeCount,
                    "",
                    comparedScanId);
        }

        private static CacheState noCapture(PageViewFingerprintService.Observation live) {
            return of(
                    "NO_CAPTURE",
                    "No saved mapping exists for the active page. Run Rescan.",
                    live,
                    "",
                    "");
        }

        private static CacheState of(
                String state,
                String message,
                PageViewFingerprintService.Observation live,
                String reusableScanId,
                String comparedScanId) {
            return new CacheState(
                    state,
                    message,
                    true,
                    live.page().pageKey(),
                    PageScanUrlRedactor.redact(live.page().actualUrl()),
                    live.nodeCount(),
                    reusableScanId,
                    comparedScanId);
        }

        private static CacheState historicalOnly() {
            return new CacheState(
                    "HISTORICAL_ONLY",
                    "No active Playwright page is available. Historical mappings remain available.",
                    false,
                    "",
                    "",
                    0,
                    "",
                    "");
        }

        private static CacheState migrationRequired() {
            return new CacheState(
                    "MIGRATION_REQUIRED",
                    "Page fingerprint storage is not active. Apply the pending Page Mappings migration.",
                    false,
                    "",
                    "",
                    0,
                    "",
                    "");
        }

        private static CacheState unavailable(String message) {
            return new CacheState(
                    "UNAVAILABLE",
                    message == null || message.isBlank()
                            ? "Live mapping comparison is unavailable."
                            : message,
                    false,
                    "",
                    "",
                    0,
                    "",
                    "");
        }
    }
}
