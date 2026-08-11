package com.allinweb.ch.facade;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Builds the read-only scanned-page tree for one authorized Home Banking owner. */
public final class PageMappingsScanInventoryService {

    public Inventory load(Connection connection, int homeBankingId) throws SQLException {
        Objects.requireNonNull(connection, "connection");
        if (homeBankingId <= 0) {
            throw new IllegalArgumentException("A valid Home Banking owner is required.");
        }

        String organizationName = organizationName(connection, homeBankingId);
        Map<Integer, JobBuilder> jobs = botJobs(connection, homeBankingId);
        String aggregateSql = "SELECT bot_job_id,page_key,COUNT(*) AS element_count,"
                + "MAX(id) AS sample_id,MAX(last_scanned_at) AS last_scanned_at "
                + "FROM scanned_element WHERE home_banking_id=? "
                + "AND bot_job_id IS NOT NULL AND page_key IS NOT NULL "
                + "GROUP BY bot_job_id,page_key ORDER BY bot_job_id,page_key";
        try (PreparedStatement statement = connection.prepareStatement(aggregateSql)) {
            statement.setInt(1, homeBankingId);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    int botJobId = rows.getInt("bot_job_id");
                    JobBuilder job = jobs.get(botJobId);
                    if (job == null) continue;
                    String pageKey = rows.getString("page_key");
                    if (pageKey == null || pageKey.isBlank()) continue;
                    job.pages.add(new PageBuilder(
                            pageKey,
                            rows.getInt("element_count"),
                            rows.getLong("sample_id"),
                            value(rows.getString("last_scanned_at"))));
                }
            }
        }

        String pageSql = "SELECT page_url FROM scanned_element "
                + "WHERE id=? AND home_banking_id=? AND bot_job_id=?";
        try (PreparedStatement statement = connection.prepareStatement(pageSql)) {
            for (JobBuilder job : jobs.values()) {
                for (PageBuilder page : job.pages) {
                    statement.setLong(1, page.sampleId);
                    statement.setInt(2, homeBankingId);
                    statement.setInt(3, job.botJobId);
                    try (ResultSet rows = statement.executeQuery()) {
                        if (rows.next()) {
                            page.pageUrl = PageScanUrlRedactor.redact(rows.getString("page_url"));
                        }
                    }
                }
            }
        }

        List<Job> populatedJobs = new ArrayList<>();
        int totalElements = 0;
        int totalPages = 0;
        for (JobBuilder job : jobs.values()) {
            if (job.pages.isEmpty()) continue;
            List<Page> pages = new ArrayList<>();
            int jobElements = 0;
            for (PageBuilder page : job.pages) {
                pages.add(new Page(
                        page.pageKey,
                        page.pageUrl,
                        page.elementCount,
                        page.lastScannedAt));
                jobElements += page.elementCount;
            }
            populatedJobs.add(new Job(
                    job.botJobId,
                    job.botJobName,
                    jobElements,
                    pages.size(),
                    List.copyOf(pages)));
            totalElements += jobElements;
            totalPages += pages.size();
        }
        return new Inventory(
                homeBankingId,
                organizationName,
                totalElements,
                populatedJobs.size(),
                totalPages,
                List.copyOf(populatedJobs));
    }

    private static String organizationName(Connection connection, int homeBankingId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT name FROM home_banking WHERE id=?")) {
            statement.setInt(1, homeBankingId);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    throw new SQLException("The Page Mappings organization was not found.");
                }
                String name = rows.getString("name");
                return name == null || name.isBlank()
                        ? "Home Banking #" + homeBankingId
                        : name.trim();
            }
        }
    }

    private static Map<Integer, JobBuilder> botJobs(Connection connection, int homeBankingId)
            throws SQLException {
        Map<Integer, JobBuilder> jobs = new LinkedHashMap<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT id,name FROM bot_job WHERE home_banking_id=? ORDER BY id")) {
            statement.setInt(1, homeBankingId);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    int botJobId = rows.getInt("id");
                    String name = rows.getString("name");
                    jobs.put(botJobId, new JobBuilder(
                            botJobId,
                            name == null || name.isBlank() ? "Bot Job #" + botJobId : name.trim()));
                }
            }
        }
        return jobs;
    }

    private static String value(String input) {
        return input == null ? "" : input;
    }

    public record Inventory(
            int homeBankingId,
            String organizationName,
            int totalElements,
            int totalBotJobs,
            int totalPages,
            List<Job> jobs) {}

    public record Job(
            int botJobId,
            String botJobName,
            int elementCount,
            int pageCount,
            List<Page> pages) {}

    public record Page(
            String pageKey,
            String pageUrl,
            int elementCount,
            String lastScannedAt) {}

    private static final class JobBuilder {
        private final int botJobId;
        private final String botJobName;
        private final List<PageBuilder> pages = new ArrayList<>();

        private JobBuilder(int botJobId, String botJobName) {
            this.botJobId = botJobId;
            this.botJobName = botJobName;
        }
    }

    private static final class PageBuilder {
        private final String pageKey;
        private final int elementCount;
        private final long sampleId;
        private final String lastScannedAt;
        private String pageUrl = "arweb://redacted-page";

        private PageBuilder(
                String pageKey,
                int elementCount,
                long sampleId,
                String lastScannedAt) {
            this.pageKey = pageKey;
            this.elementCount = elementCount;
            this.sampleId = sampleId;
            this.lastScannedAt = lastScannedAt;
        }
    }
}
