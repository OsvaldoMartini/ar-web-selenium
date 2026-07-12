package com.allinweb.ch.runner;

import com.allinweb.ch.util.ARPropertyEnum;
import com.allinweb.ch.util.ARPropertyManager;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.sqlite.SQLiteConnection;
import org.sqlite.core.Codes;

/**
 * Creates a write-isolated test snapshot from the BancaStato production reference files.
 *
 * <p>The source config is never loaded by {@link ARPropertyManager}: that class persists version
 * and build values while loading. Instead, this fixture reads it as plain properties, writes a
 * sanitized config under the JUnit temporary directory, and activates only that copy. SQLite's
 * serialization API supplies a transactionally consistent database image without opening the
 * source for writes.
 */
final class BancaStatoIsolatedFixture implements AutoCloseable {

    static final Path DEFAULT_PRODUCTION_ROOT = Path.of("D:\\Projects\\ARWebBancaStato");
    static final Path DEFAULT_SOURCE_CONFIG = DEFAULT_PRODUCTION_ROOT.resolve("Config-4.2").resolve("ARWeb.config");
    static final Path DEFAULT_SOURCE_DATABASE = DEFAULT_PRODUCTION_ROOT.resolve("ARWeb").resolve("database.db");

    private static final String SOURCE_CONFIG_PROPERTY = "bancastato.source.config";
    private static final String SOURCE_DATABASE_PROPERTY = "bancastato.source.database";
    private static final String ACTIVE_CONFIG_PROPERTY = "ARWebConfig";
    private static final List<String> SAFE_SOURCE_KEYS = List.of(
            ARPropertyEnum.BROWSER.getValue(),
            ARPropertyEnum.LOG_LEVEL.getValue(),
            ARPropertyEnum.MAX_LOG_SIZE.getValue(),
            ARPropertyEnum.NAVIGATION_TIME.getValue(),
            ARPropertyEnum.WEBDRIVER_PAGE_UPDATE_TIMEOUT_SEC.getValue(),
            ARPropertyEnum.WEBDRIVER_INTERACTION_TIMEOUT_SEC.getValue(),
            ARPropertyEnum.INSTRUCTION_STOP_SECONDS.getValue(),
            ARPropertyEnum.OCR_ENGINE.getValue());

    private final Path sourceConfig;
    private final Path sourceDatabase;
    private final Path fixtureRoot;
    private final Path configFile;
    private final Path databaseFolder;
    private final Path databaseFile;
    private final String sourceConfigHash;
    private final String sourceDatabaseHash;
    private final Map<Path, String> sourceDatabaseCompanionHashes;

    private ARPropertyManager activatedManager;
    private Properties previousProperties;
    private String previousConfigurationFileName;
    private String previousConfigSystemProperty;
    private PrintStream previousOut;
    private PrintStream previousErr;

    private BancaStatoIsolatedFixture(
            Path sourceConfig,
            Path sourceDatabase,
            Path fixtureRoot,
            Path configFile,
            Path databaseFolder,
            Path databaseFile,
            String sourceConfigHash,
            String sourceDatabaseHash,
            Map<Path, String> sourceDatabaseCompanionHashes) {
        this.sourceConfig = sourceConfig;
        this.sourceDatabase = sourceDatabase;
        this.fixtureRoot = fixtureRoot;
        this.configFile = configFile;
        this.databaseFolder = databaseFolder;
        this.databaseFile = databaseFile;
        this.sourceConfigHash = sourceConfigHash;
        this.sourceDatabaseHash = sourceDatabaseHash;
        this.sourceDatabaseCompanionHashes = sourceDatabaseCompanionHashes;
    }

    static BancaStatoIsolatedFixture create(Path junitTempDirectory) throws Exception {
        Path sourceConfig = Path.of(System.getProperty(SOURCE_CONFIG_PROPERTY, DEFAULT_SOURCE_CONFIG.toString()))
                .toAbsolutePath()
                .normalize();
        Path sourceDatabase = Path.of(System.getProperty(SOURCE_DATABASE_PROPERTY, DEFAULT_SOURCE_DATABASE.toString()))
                .toAbsolutePath()
                .normalize();
        requireRegularFile(sourceConfig, "BancaStato source config");
        requireRegularFile(sourceDatabase, "BancaStato source database");
        refuseLiveProductionDatabaseWithCompanions(sourceDatabase);

        Path fixtureRoot = junitTempDirectory.resolve("bancastato-fixture").toAbsolutePath().normalize();
        requireOutsideProduction(fixtureRoot);
        Path configFolder = Files.createDirectories(fixtureRoot.resolve("config"));
        Path databaseFolder = Files.createDirectories(fixtureRoot.resolve("database"));
        Path configFile = configFolder.resolve("ARWeb.config");
        Path databaseFile = databaseFolder.resolve("database.db");

        String configHash = sha256(sourceConfig);
        String databaseHash = sha256(sourceDatabase);
        Map<Path, String> databaseCompanionHashes = databaseCompanionFingerprints(sourceDatabase);
        snapshotSqlite(sourceDatabase, databaseFile);
        writeSanitizedConfig(sourceConfig, configFile, fixtureRoot, databaseFolder);

        if (!configHash.equals(sha256(sourceConfig))
                || !databaseHash.equals(sha256(sourceDatabase))
                || !databaseCompanionHashes.equals(databaseCompanionFingerprints(sourceDatabase))) {
            throw new IllegalStateException("A production reference changed while its isolated fixture was created");
        }

        return new BancaStatoIsolatedFixture(
                sourceConfig,
                sourceDatabase,
                fixtureRoot,
                configFile,
                databaseFolder,
                databaseFile,
                configHash,
                databaseHash,
                databaseCompanionHashes);
    }

    void activate(ARPropertyManager manager) throws IOException {
        if (activatedManager != null) {
            throw new IllegalStateException("BancaStato fixture is already active");
        }
        activatedManager = manager;
        previousProperties = new Properties();
        previousProperties.putAll(manager.getProperties());
        previousConfigurationFileName = manager.getConfigurationFileName();
        previousConfigSystemProperty = System.getProperty(ACTIVE_CONFIG_PROPERTY);
        previousOut = System.out;
        previousErr = System.err;

        System.setProperty(ACTIVE_CONFIG_PROPERTY, configFile.toString());
        manager.setProperties(new Properties());
        manager.setConfigurationFileName(configFile.toString());
        try (FileInputStream input = new FileInputStream(configFile.toFile())) {
            manager.loadProperties(input);
        } catch (IOException | RuntimeException error) {
            restoreActivatedState();
            activatedManager = null;
            throw error;
        } finally {
            System.setOut(previousOut);
            System.setErr(previousErr);
        }
    }

    Path fixtureRoot() {
        return fixtureRoot;
    }

    Path configFile() {
        return configFile;
    }

    Path databaseFolder() {
        return databaseFolder;
    }

    Path databaseFile() {
        return databaseFile;
    }

    Path diagnosticsFolder() throws IOException {
        return Files.createDirectories(fixtureRoot.resolve("page_diagnostics"));
    }

    void requireIsolatedOutput(Path output) {
        Path normalized = output.toAbsolutePath().normalize();
        requireOutsideProduction(normalized);
        if (!normalized.startsWith(fixtureRoot)) {
            throw new IllegalArgumentException("Test output must remain under the isolated fixture: " + normalized);
        }
        if (Files.isSymbolicLink(normalized)) {
            throw new IllegalArgumentException("Test output may not be a symbolic link: " + normalized);
        }
        try {
            Path existingAncestor = normalized.getParent();
            while (existingAncestor != null && !Files.exists(existingAncestor)) {
                existingAncestor = existingAncestor.getParent();
            }
            if (existingAncestor == null
                    || !existingAncestor.toRealPath().startsWith(fixtureRoot.toRealPath())) {
                throw new IllegalArgumentException("Test output resolves outside the isolated fixture: " + normalized);
            }
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
    }

    void assertSourceFilesUnchanged() {
        if (!sourceConfigHash.equals(sha256(sourceConfig))) {
            throw new AssertionError("BancaStato production config changed during the test");
        }
        if (!sourceDatabaseHash.equals(sha256(sourceDatabase))) {
            throw new AssertionError("BancaStato production database changed during the test");
        }
        if (!sourceDatabaseCompanionHashes.equals(databaseCompanionFingerprints(sourceDatabase))) {
            throw new AssertionError("BancaStato production database WAL/journal companions changed during the test");
        }
    }

    @Override
    public void close() {
        try {
            assertSourceFilesUnchanged();
        } finally {
            restoreActivatedState();
            activatedManager = null;
        }
    }

    private void restoreActivatedState() {
        if (activatedManager != null) {
            activatedManager.setProperties(previousProperties);
            activatedManager.setConfigurationFileName(previousConfigurationFileName);
        }
        if (previousConfigSystemProperty == null) {
            System.clearProperty(ACTIVE_CONFIG_PROPERTY);
        } else {
            System.setProperty(ACTIVE_CONFIG_PROPERTY, previousConfigSystemProperty);
        }
        if (previousOut != null) {
            System.setOut(previousOut);
        }
        if (previousErr != null) {
            System.setErr(previousErr);
        }
    }

    private static void snapshotSqlite(Path source, Path destination) throws Exception {
        Class.forName("org.sqlite.JDBC");
        String normalizedSource = source.toString().replace('\\', '/');
        String jdbcUrl = "jdbc:sqlite:file:" + normalizedSource + "?mode=ro";
        try (Connection connection = DriverManager.getConnection(jdbcUrl)) {
            if (!(connection instanceof SQLiteConnection sqliteConnection)) {
                throw new SQLException("SQLite JDBC connection does not expose the serialization API");
            }
            int result = sqliteConnection
                    .getDatabase()
                    .backup("main", destination.toAbsolutePath().toString(), null);
            if (result != Codes.SQLITE_OK || !Files.isRegularFile(destination) || Files.size(destination) == 0) {
                throw new SQLException("SQLite online backup failed with result code " + result);
            }
        }

        try (Connection snapshot = DriverManager.getConnection("jdbc:sqlite:" + destination);
                Statement statement = snapshot.createStatement();
                ResultSet result = statement.executeQuery("PRAGMA quick_check")) {
            if (!result.next() || !"ok".equalsIgnoreCase(result.getString(1))) {
                throw new SQLException("Isolated BancaStato snapshot failed PRAGMA quick_check");
            }
        }
    }

    private static void writeSanitizedConfig(
            Path sourceConfig, Path destination, Path fixtureRoot, Path databaseFolder) throws IOException {
        Properties source = new Properties();
        try (InputStream input = Files.newInputStream(sourceConfig)) {
            source.load(input);
        }

        Properties isolated = new Properties();
        for (String key : SAFE_SOURCE_KEYS) {
            String value = source.getProperty(key);
            if (value != null && !value.isBlank()) {
                isolated.setProperty(key, value);
            }
        }

        isolated.setProperty(ARPropertyEnum.DATABASE_TYPE.getValue(), "TEXT");
        isolated.setProperty(ARPropertyEnum.PATH_DB.getValue(), databaseFolder.toString());
        isolated.setProperty(ARPropertyEnum.PATH_LOG.getValue(), createDirectory(fixtureRoot.resolve("logs")));
        isolated.setProperty(ARPropertyEnum.PATH_REPORT.getValue(), createDirectory(fixtureRoot.resolve("reports")));
        isolated.setProperty(ARPropertyEnum.PATH_EXCEL.getValue(), createDirectory(fixtureRoot.resolve("excel")));
        isolated.setProperty(ARPropertyEnum.PATH_EXPORT.getValue(), createDirectory(fixtureRoot.resolve("export")));
        isolated.setProperty(ARPropertyEnum.PATH_APPIUM.getValue(), createDirectory(fixtureRoot.resolve("appium")));
        isolated.setProperty(ARPropertyEnum.PATH_PLUGINS.getValue(), createDirectory(fixtureRoot.resolve("plugins")));
        isolated.setProperty(ARPropertyEnum.PATH_LICENSE.getValue(), createDirectory(fixtureRoot.resolve("license")));
        isolated.setProperty(ARPropertyEnum.PATH_PRIORITY.getValue(), createDirectory(fixtureRoot.resolve("priority")));
        isolated.setProperty(ARPropertyEnum.PATH_ENGINE.getValue(), fixtureRoot.resolve("Engine.jar").toString());
        isolated.setProperty(
                ARPropertyEnum.PATH_WEBDRIVER.getValue(), createDirectory(fixtureRoot.resolve("webdriver")));
        isolated.setProperty(ARPropertyEnum.PATH_OCR.getValue(), createDirectory(fixtureRoot.resolve("ocr")));
        isolated.setProperty(ARPropertyEnum.PORT_SOCKET.getValue(), Integer.toString(findFreePort()));
        isolated.setProperty(ARPropertyEnum.USE_PLAYWRIGHT.getValue(), "true");
        isolated.setProperty(ARPropertyEnum.PLAYWRIGHT_SELENIUM_FALLBACK.getValue(), "false");
        isolated.setProperty(ARPropertyEnum.DB_URL.getValue(), "");
        isolated.setProperty(ARPropertyEnum.DB_USER.getValue(), "");
        isolated.setProperty(ARPropertyEnum.DB_PWD.getValue(), "");
        isolated.setProperty(ARPropertyEnum.AI_ENDPOINT.getValue(), "");
        isolated.setProperty(ARPropertyEnum.AI_API_KEY.getValue(), "");
        isolated.setProperty(ARPropertyEnum.AI_MODEL.getValue(), "");
        isolated.setProperty("url_plugins", "");
        isolated.setProperty("path_java", createDirectory(fixtureRoot.resolve("java")));
        isolated.putIfAbsent(ARPropertyEnum.BROWSER.getValue(), "edge");
        isolated.putIfAbsent(ARPropertyEnum.LOG_LEVEL.getValue(), "INFO");
        isolated.putIfAbsent(ARPropertyEnum.NAVIGATION_TIME.getValue(), "1");
        isolated.putIfAbsent(ARPropertyEnum.WEBDRIVER_PAGE_UPDATE_TIMEOUT_SEC.getValue(), "60");
        isolated.putIfAbsent(ARPropertyEnum.WEBDRIVER_INTERACTION_TIMEOUT_SEC.getValue(), "60");
        isolated.putIfAbsent(ARPropertyEnum.INSTRUCTION_STOP_SECONDS.getValue(), "15");

        try (var output = Files.newOutputStream(destination, StandardOpenOption.CREATE_NEW)) {
            isolated.store(output, "Generated isolated BancaStato Playwright fixture; contains no production secrets");
        }
    }

    private static int findFreePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static String createDirectory(Path directory) {
        try {
            return Files.createDirectories(directory).toString();
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
    }

    private static void requireRegularFile(Path path, String label) {
        if (!Files.isRegularFile(path)) {
            throw new IllegalStateException(label + " does not exist: " + path);
        }
    }

    private static void requireOutsideProduction(Path path) {
        Path productionRoot = DEFAULT_PRODUCTION_ROOT.toAbsolutePath().normalize();
        if (path.toAbsolutePath().normalize().startsWith(productionRoot)) {
            throw new IllegalArgumentException("Tests may not write beneath the production root: " + path);
        }
    }

    private static Map<Path, String> databaseCompanionFingerprints(Path database) {
        Map<Path, String> fingerprints = new LinkedHashMap<>();
        // SQLite readers may legitimately rewrite the transient WAL shared-memory index (-shm)
        // while leaving the database and durable WAL/journal bytes unchanged. Fingerprint only
        // durable companions here; production snapshots are still refused whenever any companion,
        // including -shm, exists (see refuseLiveProductionDatabaseWithCompanions).
        for (String suffix : List.of("-wal", "-journal")) {
            Path companion = Path.of(database.toString() + suffix);
            fingerprints.put(companion, Files.isRegularFile(companion) ? sha256(companion) : "<missing>");
        }
        return Map.copyOf(fingerprints);
    }

    private static void refuseLiveProductionDatabaseWithCompanions(Path database) {
        Path productionRoot = DEFAULT_PRODUCTION_ROOT.toAbsolutePath().normalize();
        if (!database.toAbsolutePath().normalize().startsWith(productionRoot)) {
            return;
        }
        for (String suffix : List.of("-wal", "-shm", "-journal")) {
            Path companion = Path.of(database.toString() + suffix);
            if (Files.exists(companion)) {
                throw new IllegalStateException(
                        "Refusing to snapshot a live production SQLite database with companion file: " + companion);
            }
        }
    }

    private static String sha256(Path file) {
        try (InputStream input = Files.newInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                if (read > 0) {
                    digest.update(buffer, 0, read);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is unavailable", error);
        }
    }
}
