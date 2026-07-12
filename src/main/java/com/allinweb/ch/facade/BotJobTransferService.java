package com.allinweb.ch.facade;

import com.allinweb.ch.model.BotJobTransferResult;
import com.allinweb.ch.util.ARPropertyEnum;
import com.allinweb.ch.util.ARPropertyManager;
import com.allinweb.ch.util.ErrorMessage;
import com.google.common.base.Strings;
import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Comparator;
import java.util.regex.Pattern;

/** Headless Bot Job import/export coordinator used by the React details toolbar. */
public final class BotJobTransferService {

    private static final BotJobTransferService INSTANCE = new BotJobTransferService();

    private final ARPropertyManager properties = ARPropertyManager.getInstance();
    private final PerformDataBase database = PerformDataBase.getInstance();
    private final PerformBackup backup = PerformBackup.getInstance();
    private final PerformLists lists = PerformLists.getInstance();
    private final PerformDBEngine engine = PerformDBEngine.getInstance();

    private static final DateTimeFormatter EXPORT_TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyy_MM_dd_HHmmss_SSS");
    private static final DateTimeFormatter RESTORE_DATE = DateTimeFormatter.ofPattern("yyyy_MM_dd");

    private BotJobTransferService() {}

    public static BotJobTransferService getInstance() {
        return INSTANCE;
    }

    public synchronized BotJobTransferResult exportJob(int homeBankingId, int botJobId, String folder) {
        File directory = requireDirectory(folder);
        String databaseType = properties.getProperty(ARPropertyEnum.DATABASE_TYPE);
        String timestamp = LocalDateTime.now().format(EXPORT_TIMESTAMP);
        String preferredName = fileName(databaseType, botJobId, timestamp);
        Path temporary = null;
        try {
            temporary = Files.createTempFile(directory.toPath(), ".ar-bot-job-" + botJobId + '-', ".tmp");
            try (Connection connection = database.getConnection()) {
                backup.initialize(connection);
                ErrorMessage error = backup.dumpBotJobToSingleFile(
                        connection, temporary.toAbsolutePath().toString(), homeBankingId, botJobId);
                if (error != null) return BotJobTransferResult.failure(errorText("Bot Job export failed", error));
            }
            File target = moveCompletedExport(temporary, directory, preferredName);
            temporary = null;
            return BotJobTransferResult.success("Bot Job exported", target.getName());
        } catch (SQLException | IOException | RuntimeException error) {
            return BotJobTransferResult.failure("Bot Job export failed: " + safe(error.getMessage()));
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException ignored) {
                    // Best-effort cleanup; the incomplete file never has a public backup name.
                }
            }
        }
    }

    public synchronized BotJobTransferResult importJob(
            int homeBankingId,
            String organizationName,
            int homeUrlId,
            int botJobId,
            LocalDate restoreDate,
            String folder) {
        File directory = requireDirectory(folder);
        if (restoreDate == null) return BotJobTransferResult.failure("Restore date is required");
        String databaseType = properties.getProperty(ARPropertyEnum.DATABASE_TYPE);
        String date = restoreDate.format(RESTORE_DATE);
        File source = findImportSource(directory, databaseType, botJobId, date);
        if (source == null) {
            return BotJobTransferResult.failure("No Bot Job backup was found for " + restoreDate);
        }
        String fileName = source.getName();

        ErrorMessage error;
        try (Connection connection = database.getConnection()) {
            backup.initialize(connection);
            error = backup.restoreBotJobFromSingleFile(
                    connection,
                    source.getAbsolutePath(),
                    homeBankingId,
                    homeUrlId,
                    botJobId,
                    safe(organizationName));
        } catch (SQLException sqlError) {
            return BotJobTransferResult.failure("Bot Job import failed: " + safe(sqlError.getMessage()));
        }
        if (error != null) return BotJobTransferResult.failure(errorText("Bot Job import failed", error));

        lists.clearAllLists();
        error = engine.loadHomeBanking(null);
        if (error == null) error = engine.loadHomeUrls(null);
        if (error == null) error = database.loadQuickBotJobs();
        if (error != null) {
            return BotJobTransferResult.failure(errorText("Bot Job imported but application reload failed", error));
        }
        return BotJobTransferResult.success("Bot Job imported", fileName);
    }

    static String fileName(String databaseType, int botJobId, String date) {
        return "backup_(BY_BOT_JOB)_" + dialectSlug(databaseType) + '_' + botJobId + '_' + date + ".sql";
    }

    static String legacyFileName(String databaseType, String date) {
        return "backup_(BY_BOT_JOB)_" + dialectSlug(databaseType) + '_' + date + ".sql";
    }

    static File moveCompletedExport(Path completedFile, File directory, String preferredName) throws IOException {
        if (completedFile == null || !Files.isRegularFile(completedFile)) {
            throw new IOException("Completed Bot Job export is unavailable");
        }
        if (directory == null || !directory.isDirectory()) {
            throw new IOException("Transfer folder does not exist");
        }
        String stem = preferredName.endsWith(".sql")
                ? preferredName.substring(0, preferredName.length() - 4)
                : preferredName;
        for (int attempt = 1; attempt <= 10_000; attempt++) {
            String candidateName = attempt == 1 ? preferredName : stem + '_' + attempt + ".sql";
            Path target = directory.toPath().resolve(candidateName);
            if (Files.exists(target)) continue;
            try {
                moveWithoutReplacement(completedFile, target);
                return target.toFile();
            } catch (FileAlreadyExistsException collision) {
                // Another process won this name; retry with the next suffix.
            }
        }
        throw new IOException("Unable to allocate a unique Bot Job backup filename");
    }

    static File findImportSource(File directory, String databaseType, int botJobId, String date) {
        if (directory == null || !directory.isDirectory()) return null;
        String exactName = fileName(databaseType, botJobId, date);
        String stem = exactName.substring(0, exactName.length() - 4);
        Pattern scopedName = Pattern.compile(
                Pattern.quote(stem) + "(?:_\\d{6}_\\d{3}(?:_\\d+)?)?\\.sql");
        File[] scoped = directory.listFiles(file -> file.isFile() && scopedName.matcher(file.getName()).matches());
        if (scoped != null && scoped.length > 0) {
            return Arrays.stream(scoped)
                    .max(Comparator.comparing(File::getName))
                    .orElse(null);
        }
        File legacy = new File(directory, legacyFileName(databaseType, date));
        return legacy.isFile() ? legacy : null;
    }

    private static void moveWithoutReplacement(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException unsupported) {
            Files.move(source, target);
        }
    }

    static String dialectSlug(String databaseType) {
        if (databaseType == null) return "db";
        String value = databaseType.trim();
        if (value.equalsIgnoreCase("TEXT") || value.equalsIgnoreCase("SQLite")) return "sqlite";
        if (value.equalsIgnoreCase("Access")) return "access";
        if (value.equalsIgnoreCase("Postgres") || value.equalsIgnoreCase("PostGres")) return "postgres";
        return "db";
    }

    private static File requireDirectory(String path) {
        if (Strings.isNullOrEmpty(path)) throw new IllegalArgumentException("Transfer folder is required");
        File directory = new File(path);
        if (!directory.isDirectory()) throw new IllegalArgumentException("Transfer folder does not exist");
        return directory;
    }

    private static String errorText(String prefix, ErrorMessage error) {
        if (error == null) return prefix;
        String detail = !Strings.isNullOrEmpty(error.getErrorMessage())
                ? error.getErrorMessage()
                : (!Strings.isNullOrEmpty(error.getErrorHeader())
                        ? error.getErrorHeader()
                        : error.getErrorTitle());
        return Strings.isNullOrEmpty(detail) ? prefix : prefix + ": " + detail;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
