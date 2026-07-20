package com.allinweb.ch.runner;

import static java.nio.file.LinkOption.NOFOLLOW_LINKS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import com.allinweb.ch.facade.BotJobTransferFolderResolver;
import com.allinweb.ch.facade.BotJobTransferPathRegistry;
import com.allinweb.ch.facade.BotJobTransferService;
import com.allinweb.ch.facade.PerformDataBase;
import com.allinweb.ch.facade.PerformLists;
import com.allinweb.ch.model.BotJobTransferResult;
import com.allinweb.ch.util.ARPropertyEnum;
import com.allinweb.ch.util.ARPropertyManager;
import java.io.File;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Five real Export -> Import round trips for selected BancaStato Bot Jobs in the same
 * organization.
 *
 * <p>The production-test config and database remain read-only. Every invocation uses an isolated
 * SQLite snapshot, writes one backup to the configured BancaStato Export folder, verifies the
 * imported graph, then hash-checks and removes only that exact test-created backup.
 *
 * <pre>
 * mvn -Dtest=BancaStatoBotJobTransferRoundTripIT -DbancastatoTransferIT=true test
 * </pre>
 */
@Isolated("Mutates ARPropertyManager, PerformLists, and the configured Export folder")
@EnabledIfSystemProperty(named = "bancastatoTransferIT", matches = "true")
class BancaStatoBotJobTransferRoundTripIT {

    private static final Path SOURCE_CONFIG =
            Path.of("D:\\Projects\\ARWebBancaStato\\Config-4.2\\ARWeb.config");
    private static final Path SOURCE_DATABASE =
            Path.of("D:\\Projects\\ARWebBancaStato\\ARWeb\\database.db");
    private static final Path EXPORT_FOLDER =
            Path.of("D:\\Projects\\ARWebBancaStato\\ARWeb\\Export");
    private static final DateTimeFormatter BACKUP_DATE = DateTimeFormatter.ofPattern("yyyy_MM_dd");

    private final ARPropertyManager properties = ARPropertyManager.getInstance();
    private final PerformDataBase database = PerformDataBase.getInstance();
    private final PerformLists lists = PerformLists.getInstance();
    private final BotJobTransferService transfers = BotJobTransferService.getInstance();
    private final BotJobTransferPathRegistry selectedPaths = BotJobTransferPathRegistry.getInstance();

    @TempDir
    Path temporaryDirectory;

    private BancaStatoIsolatedFixture fixture;
    private String sessionId;
    private Path exportFolderReal;
    private Map<Path, FileFingerprint> baselineExports;
    private Path createdBackup;
    private FileFingerprint createdBackupFingerprint;

    @BeforeEach
    void activateProductionShapedFixture() throws Exception {
        assertTrue(Files.isRegularFile(SOURCE_CONFIG), "BancaStato production-test config is missing");
        assertTrue(Files.isRegularFile(SOURCE_DATABASE), "BancaStato production-test database is missing");
        assertTrue(Files.isDirectory(EXPORT_FOLDER), "BancaStato Export folder is missing");

        exportFolderReal = EXPORT_FOLDER.toRealPath();
        baselineExports = fingerprintExportFiles(exportFolderReal);
        fixture = BancaStatoIsolatedFixture.create(temporaryDirectory);
        fixture.activate(properties);
        // Override only the sanitized in-memory test configuration. The source config is never
        // loaded through ARPropertyManager and is hash-verified by the fixture on close.
        properties.getProperties().setProperty(ARPropertyEnum.PATH_EXPORT.getValue(), exportFolderReal.toString());
        sessionId = "bancastato-transfer-" + UUID.randomUUID();
    }

    @AfterEach
    void removeOnlyTheVerifiedTestBackupAndRestoreState() throws Exception {
        try {
            removeCreatedBackupSafely();
            assertBaselineExportsUnchanged();
        } finally {
            selectedPaths.clearSession(sessionId);
            lists.clearAllLists();
            if (fixture != null) fixture.close();
        }
    }

    @ParameterizedTest(name = "{0}: Export and Import Bot Job {1} inside organization {2}")
    @MethodSource("selectedBotJobs")
    void exportsAndImportsSelectedBotJobInsideTheSameOrganization(
            String scenario, int selectedBotJobId, int expectedOrganizationId, int expectedHomeUrlId)
            throws Exception {
        File selectedFolder = BotJobTransferFolderResolver.resolve(
                properties.getProperty(ARPropertyEnum.PATH_EXPORT));
        assertEquals(exportFolderReal, selectedFolder.toPath());
        String authorizedFolder = selectedPaths.select(sessionId, selectedBotJobId, selectedFolder);
        assertEquals(
                authorizedFolder,
                selectedPaths.require(sessionId, selectedBotJobId, selectedFolder.getAbsolutePath()));

        DatabaseSnapshot before;
        try (Connection connection = database.getConnection()) {
            before = snapshot(connection, selectedBotJobId);
        }
        assertEquals(expectedOrganizationId, before.selectedJob().homeBankingId());
        assertEquals(expectedHomeUrlId, before.selectedJob().homeUrlId());
        assertTrue(!before.graph().blocks().isEmpty(), scenario + " must contain at least one block");
        assertTrue(!before.graph().instructions().isEmpty(), scenario + " must contain at least one instruction");

        BotJobTransferResult exported = transfers.exportJob(
                before.selectedJob().homeBankingId(), selectedBotJobId, authorizedFolder);
        assertTrue(exported.ok(), exported.message());
        assertTrue(exported.fileName().startsWith(
                "backup_(BY_BOT_JOB)_sqlite_" + selectedBotJobId + "_"));

        createdBackup = exportFolderReal.resolve(exported.fileName()).toAbsolutePath().normalize();
        assertFalse(baselineExports.containsKey(createdBackup), "Export reused an existing backup file");
        createdBackupFingerprint = fingerprint(createdBackup);
        assertTrue(createdBackupFingerprint.size() > 0L);
        assertSnapshotContainsSelectedJob(before.selectedJob());

        LocalDate exportedDate = dateFromBackupName(exported.fileName(), selectedBotJobId);
        BotJobTransferResult imported = transfers.importJob(
                before.selectedJob().homeBankingId(),
                before.selectedJob().organizationName(),
                before.selectedJob().homeUrlId(),
                selectedBotJobId,
                exportedDate,
                authorizedFolder);

        assertTrue(imported.ok(), imported.message());
        assertEquals(exported.fileName(), imported.fileName(), "Import selected a stale same-day backup");

        try (Connection connection = database.getConnection()) {
            Set<Integer> afterIds = botJobIds(connection);
            Set<Integer> importedIds = new LinkedHashSet<>(afterIds);
            importedIds.removeAll(before.botJobIds());
            assertEquals(1, importedIds.size(), "Import must create exactly one Bot Job copy");
            int importedBotJobId = importedIds.iterator().next();

            SelectedJob originalAfter = selectedJob(connection, selectedBotJobId);
            assertEquals(before.selectedJob(), originalAfter, "Export/Import changed the selected source Bot Job");
            assertEquals(
                    before.graph(),
                    jobGraph(connection, selectedBotJobId),
                    "Export/Import changed the selected source Bot Job graph");

            SelectedJob importedJob = selectedJob(connection, importedBotJobId);
            assertTrue(importedJob.name().startsWith(before.selectedJob().name() + " "));
            assertEquals(before.selectedJob().description(), importedJob.description());
            assertEquals(before.selectedJob().priority(), importedJob.priority());
            assertEquals(before.selectedJob().active(), importedJob.active());
            assertEquals(before.selectedJob().homeBankingId(), importedJob.homeBankingId());
            assertEquals(before.selectedJob().homeUrlId(), importedJob.homeUrlId());
            assertEquals(before.selectedJob().organizationName(), importedJob.organizationName());
            assertEquals(
                    before.graph(),
                    jobGraph(connection, importedBotJobId),
                    "Imported blocks, instructions, variables, references, or relationships differ");

            DatabaseCounts afterCounts = databaseCounts(connection, expectedOrganizationId);
            assertEquals(before.counts().homeBanking(), afterCounts.homeBanking());
            assertEquals(before.counts().homeUrls(), afterCounts.homeUrls());
            assertEquals(before.counts().otherOrganizationJobs(), afterCounts.otherOrganizationJobs());
            assertEquals(before.counts().allJobs() + 1, afterCounts.allJobs());
            assertEquals(before.foreignKeyViolations(), foreignKeyViolations(connection));
            assertTrue(
                    lists.getQuickBotJobs().stream()
                            .anyMatch(job -> job != null
                                    && job.getId() != null
                                    && job.getId() == importedBotJobId),
                    "Imported Bot Job was not reloaded into the application list");
        }
    }

    private static Stream<Arguments> selectedBotJobs() {
        return Stream.of(
                arguments("BancaStato base flow", 2, 2, 11),
                arguments("Pagamento flow", 3, 2, 11),
                arguments("BancaStato restored flow A", 4, 2, 11),
                arguments("BancaStato restored flow B", 15, 2, 11),
                arguments("Apertura Conto flow", 20, 2, 31));
    }

    private static DatabaseSnapshot snapshot(Connection connection, int botJobId) throws SQLException {
        SelectedJob job = selectedJob(connection, botJobId);
        return new DatabaseSnapshot(
                job,
                jobGraph(connection, botJobId),
                botJobIds(connection),
                databaseCounts(connection, job.homeBankingId()),
                foreignKeyViolations(connection));
    }

    private static SelectedJob selectedJob(Connection connection, int botJobId) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement(
                "SELECT bj.id, bj.name, bj.description, bj.priority, bj.active, "
                        + "bj.home_banking_id, bj.home_url_id, hb.name AS organization_name "
                        + "FROM bot_job bj JOIN home_banking hb ON hb.id = bj.home_banking_id "
                        + "WHERE bj.id = ?")) {
            query.setInt(1, botJobId);
            try (ResultSet result = query.executeQuery()) {
                assertTrue(result.next(), "Bot Job " + botJobId + " is missing from the BancaStato database");
                SelectedJob selected = new SelectedJob(
                        result.getInt("id"),
                        result.getString("name"),
                        result.getString("description"),
                        result.getString("priority"),
                        result.getInt("active"),
                        result.getInt("home_banking_id"),
                        result.getInt("home_url_id"),
                        result.getString("organization_name"));
                assertFalse(result.next(), "Bot Job id is not unique: " + botJobId);
                return selected;
            }
        }
    }

    private static JobGraph jobGraph(Connection connection, int botJobId) throws SQLException {
        List<List<String>> blocks = rows(
                connection,
                "SELECT block_order_number, name, description, type_id, export_file, active, wait "
                        + "FROM block WHERE bot_job_id = ? ORDER BY block_order_number, id",
                botJobId);
        List<List<String>> instructions = rows(
                connection,
                "SELECT b.block_order_number, i.instruction_order_number, i.actions, i.name, i.xpath, "
                        + "i.coordinates, i.force_coordinates, i.iframe_xpath, i.tag_name, i.shadow_host, "
                        + "i.shadow_root, i.css_selector, i.description, i.operation, i.optional, "
                        + "i.block_marked, i.default_value, i.action_custom_max_wait_sec, i.on_hold_seconds, "
                        + "i.codified, i.export_to_abr, i.active, i.client_named, "
                        + "pb.block_order_number AS parent_block_order, "
                        + "pib.block_order_number AS parent_instruction_block_order, "
                        + "pi.instruction_order_number AS parent_instruction_order, pi.name AS parent_instruction_name "
                        + "FROM instruction i "
                        + "LEFT JOIN block b ON b.id = i.block_id "
                        + "LEFT JOIN block pb ON pb.id = i.parent_block_id "
                        + "LEFT JOIN instruction pi ON pi.id = i.parent_id "
                        + "LEFT JOIN block pib ON pib.id = pi.block_id "
                        + "WHERE i.bot_job_id = ? "
                        + "ORDER BY b.block_order_number, i.instruction_order_number, i.id",
                botJobId);
        List<List<String>> variables = rows(
                connection,
                "SELECT v.type, v.name, v.value, v.local_format, v.delimiter, "
                        + "b.block_order_number, i.instruction_order_number, i.name "
                        + "FROM variable v "
                        + "LEFT JOIN instruction i ON i.id = v.instruction_id "
                        + "LEFT JOIN block b ON b.id = i.block_id "
                        + "WHERE v.bot_job_id = ? ORDER BY v.name, v.id",
                botJobId);
        List<List<String>> references = rows(
                connection,
                "SELECT r.reference_type, r.value, b.block_order_number, "
                        + "i.instruction_order_number, i.name "
                        + "FROM reference r "
                        + "JOIN instruction i ON i.id = r.instruction_id "
                        + "LEFT JOIN block b ON b.id = i.block_id "
                        + "WHERE r.bot_job_id = ? "
                        + "ORDER BY b.block_order_number, i.instruction_order_number, r.reference_type, r.value, r.id",
                botJobId);
        FeatureCounts featureCounts = new FeatureCounts(
                count(connection, "SELECT COUNT(*) FROM use_case WHERE bot_job_id = ?", botJobId),
                count(connection, "SELECT COUNT(*) FROM use_case_field_mapping WHERE bot_job_id = ?", botJobId),
                count(connection, "SELECT COUNT(*) FROM flow WHERE bot_job_id = ?", botJobId),
                count(
                        connection,
                        "SELECT COUNT(*) FROM flow_step WHERE flow_id IN "
                                + "(SELECT id FROM flow WHERE bot_job_id = ?)",
                        botJobId),
                count(connection, "SELECT COUNT(*) FROM requirement WHERE bot_job_id = ?", botJobId),
                count(
                        connection,
                        "SELECT COUNT(*) FROM requirement_use_case WHERE requirement_id IN "
                                + "(SELECT id FROM requirement WHERE bot_job_id = ?)",
                        botJobId),
                count(
                        connection,
                        "SELECT COUNT(*) FROM requirement_flow WHERE requirement_id IN "
                                + "(SELECT id FROM requirement WHERE bot_job_id = ?)",
                        botJobId));
        return new JobGraph(blocks, instructions, variables, references, featureCounts);
    }

    private static List<List<String>> rows(Connection connection, String sql, Object... parameters)
            throws SQLException {
        List<List<String>> rows = new ArrayList<>();
        try (PreparedStatement query = connection.prepareStatement(sql)) {
            bind(query, parameters);
            try (ResultSet result = query.executeQuery()) {
                ResultSetMetaData metadata = result.getMetaData();
                while (result.next()) {
                    List<String> row = new ArrayList<>(metadata.getColumnCount());
                    for (int column = 1; column <= metadata.getColumnCount(); column++) {
                        row.add(result.getString(column));
                    }
                    rows.add(row);
                }
            }
        }
        return List.copyOf(rows);
    }

    private static Set<Integer> botJobIds(Connection connection) throws SQLException {
        Set<Integer> ids = new LinkedHashSet<>();
        try (Statement query = connection.createStatement();
                ResultSet result = query.executeQuery("SELECT id FROM bot_job ORDER BY id")) {
            while (result.next()) ids.add(result.getInt(1));
        }
        return Set.copyOf(ids);
    }

    private static DatabaseCounts databaseCounts(Connection connection, int homeBankingId) throws SQLException {
        return new DatabaseCounts(
                count(connection, "SELECT COUNT(*) FROM home_banking"),
                count(connection, "SELECT COUNT(*) FROM home_url"),
                count(connection, "SELECT COUNT(*) FROM bot_job"),
                count(connection, "SELECT COUNT(*) FROM bot_job WHERE home_banking_id <> ?", homeBankingId));
    }

    private static List<List<String>> foreignKeyViolations(Connection connection) throws SQLException {
        return rows(connection, "PRAGMA foreign_key_check");
    }

    private static int count(Connection connection, String sql, Object... parameters) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement(sql)) {
            bind(query, parameters);
            try (ResultSet result = query.executeQuery()) {
                assertTrue(result.next());
                return result.getInt(1);
            }
        }
    }

    private static void bind(PreparedStatement query, Object... parameters) throws SQLException {
        for (int index = 0; index < parameters.length; index++) {
            query.setObject(index + 1, parameters[index]);
        }
    }

    private void assertSnapshotContainsSelectedJob(SelectedJob source) throws Exception {
        String snapshot = Files.readString(createdBackup, Charset.forName("windows-1252"));
        assertTrue(snapshot.contains("-- TABLE: bot_job"));
        assertTrue(snapshot.contains("-- TABLE: block"));
        assertTrue(snapshot.contains("-- TABLE: instruction"));
        assertTrue(snapshot.contains("-- TABLE: reference"));
        assertTrue(snapshot.contains(source.name().replace("'", "''")));
        assertTrue(snapshot.contains("-- home_banking_id: "
                + source.homeBankingId()
                + ", bot_job_id: "
                + source.id()));
    }

    private static LocalDate dateFromBackupName(String fileName, int botJobId) {
        Pattern expected = Pattern.compile(
                "^backup_\\(BY_BOT_JOB\\)_sqlite_"
                        + botJobId
                        + "_(\\d{4}_\\d{2}_\\d{2})_\\d{6}_\\d{3}(?:_\\d+)?\\.sql$");
        Matcher matcher = expected.matcher(fileName);
        assertTrue(matcher.matches(), "Unexpected Bot Job backup filename: " + fileName);
        return LocalDate.parse(matcher.group(1), BACKUP_DATE);
    }

    private static Map<Path, FileFingerprint> fingerprintExportFiles(Path folder) throws Exception {
        Map<Path, FileFingerprint> fingerprints = new LinkedHashMap<>();
        try (Stream<Path> files = Files.list(folder)) {
            for (Path file : files.sorted().toList()) {
                if (Files.isRegularFile(file, NOFOLLOW_LINKS)) {
                    fingerprints.put(file.toAbsolutePath().normalize(), fingerprint(file));
                }
            }
        }
        return Map.copyOf(fingerprints);
    }

    private static FileFingerprint fingerprint(Path file) throws Exception {
        assertTrue(Files.isRegularFile(file, NOFOLLOW_LINKS), "Expected a regular backup file: " + file);
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (var input = Files.newInputStream(file)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                if (read > 0) digest.update(buffer, 0, read);
            }
        }
        return new FileFingerprint(Files.size(file), HexFormat.of().formatHex(digest.digest()));
    }

    private void removeCreatedBackupSafely() throws Exception {
        if (createdBackup == null) return;
        assertEquals(exportFolderReal, createdBackup.getParent().toRealPath());
        assertFalse(Files.isSymbolicLink(createdBackup), "Refusing to remove a symbolic-link backup");
        assertTrue(createdBackupFingerprint != null, "Refusing to remove an unverified backup");
        assertEquals(createdBackupFingerprint, fingerprint(createdBackup), "Test backup changed after export");
        Files.delete(createdBackup);
    }

    private void assertBaselineExportsUnchanged() throws Exception {
        for (Map.Entry<Path, FileFingerprint> baseline : baselineExports.entrySet()) {
            assertEquals(
                    baseline.getValue(),
                    fingerprint(baseline.getKey()),
                    "A pre-existing Export file changed during the test: " + baseline.getKey());
        }
    }

    private record SelectedJob(
            int id,
            String name,
            String description,
            String priority,
            int active,
            int homeBankingId,
            int homeUrlId,
            String organizationName) {}

    private record JobGraph(
            List<List<String>> blocks,
            List<List<String>> instructions,
            List<List<String>> variables,
            List<List<String>> references,
            FeatureCounts featureCounts) {}

    private record FeatureCounts(
            int useCases,
            int useCaseFieldMappings,
            int flows,
            int flowSteps,
            int requirements,
            int requirementUseCases,
            int requirementFlows) {}

    private record DatabaseCounts(int homeBanking, int homeUrls, int allJobs, int otherOrganizationJobs) {}

    private record DatabaseSnapshot(
            SelectedJob selectedJob,
            JobGraph graph,
            Set<Integer> botJobIds,
            DatabaseCounts counts,
            List<List<String>> foreignKeyViolations) {}

    private record FileFingerprint(long size, String sha256) {}
}
