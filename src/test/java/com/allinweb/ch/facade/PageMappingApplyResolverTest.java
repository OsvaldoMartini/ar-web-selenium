package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.allinweb.ch.db.ScannedElementRepository;
import com.allinweb.ch.model.ElementDTO;
import com.allinweb.ch.model.InstructionLoad;
import com.allinweb.ch.util.ARPropertyEnum;
import com.allinweb.ch.util.ARPropertyManager;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PageMappingApplyResolverTest {

    private static final Gson JSON = new Gson();
    private static final String PAGE_KEY = "bank-login-page";
    private static final String LAST_SCANNED_AT = "2026-08-07T10:00:00Z";
    private static final String CAPTURED_AT = "2026-08-07T10:00:01Z";
    private static final String SCAN_ID = "00000000-0000-0000-0000-000000000001";

    @TempDir Path temporaryDirectory;
    private Path snapshotRoot;
    private String databaseUrl;

    @BeforeEach
    void prepare() throws Exception {
        ARPropertyManager.getInstance()
                .getProperties()
                .setProperty(ARPropertyEnum.PATH_DB.getValue(), temporaryDirectory.toString());
        snapshotRoot = temporaryDirectory.resolve("Scanned");
        Files.createDirectories(snapshotRoot);
        databaseUrl = "jdbc:sqlite:" + temporaryDirectory.resolve("mapping.db");
        try (Connection connection = DriverManager.getConnection(databaseUrl);
                Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE scanned_element ("
                    + "id INTEGER PRIMARY KEY,home_banking_id INTEGER,bot_job_id INTEGER,"
                    + "home_url_id INTEGER,page_url TEXT,page_key TEXT,element_hash TEXT,"
                    + "tag_name TEXT,type_element TEXT,defined_name TEXT,client_named TEXT,"
                    + "some_text TEXT,x_path TEXT,custom_x_path TEXT,css_selector TEXT,"
                    + "attrib_id TEXT,attrib_name TEXT,coordinates TEXT,iframe_xpath TEXT,"
                    + "shadow_host TEXT,shadow_root TEXT,attribute_data TEXT,ocr_text TEXT,"
                    + "ocr_match_quality TEXT,ocr_confidence REAL,scan_count INTEGER,"
                    + "first_scanned_at TEXT,last_scanned_at TEXT)");
            statement.execute("CREATE TABLE page_scan_snapshot ("
                    + "scan_id TEXT PRIMARY KEY,home_banking_id INTEGER,bot_job_id INTEGER,"
                    + "home_url_id INTEGER,page_key TEXT,page_url TEXT,captured_at TEXT,"
                    + "element_count INTEGER,artifact_path TEXT,manifest_sha256 TEXT,"
                    + "status TEXT,pinned INTEGER DEFAULT 0)");
        }
    }

    @Test
    void reloadsAuthoritativeRegistryFieldsAndIgnoresArtifactPresentationFields()
            throws Exception {
        Fixture fixture = seedCurrentCapture();
        PageMappingApplyResolver resolver = new PageMappingApplyResolver(snapshotRoot);

        InstructionLoad resolved;
        try (Connection connection = DriverManager.getConnection(databaseUrl)) {
            resolved = resolver.resolve(connection, 2, 5, 10, 1, fixture.reference());
        }

        assertEquals("authoritative_registry_name", resolved.getName());
        assertEquals("//button[@id='continue']", resolved.getXpath());
        assertEquals("C", resolved.getActions());
    }

    @Test
    void rejectsSpoofedOwnerEvenWhenTheCaptureIdAndElementIdExist() throws Exception {
        Fixture fixture = seedCurrentCapture();
        PageMappingApplyResolver resolver = new PageMappingApplyResolver(snapshotRoot);

        try (Connection connection = DriverManager.getConnection(databaseUrl)) {
            assertThrows(
                    PageMappingApplyResolver.Refused.class,
                    () -> resolver.resolve(connection, 9, 5, 10, 1, fixture.reference()));
        }
    }

    @Test
    void rejectsEveryStaleRegistryRevisionField() throws Exception {
        Fixture fixture = seedCurrentCapture();
        PageMappingApplyResolver resolver = new PageMappingApplyResolver(snapshotRoot);
        PageMappingInstructionReference reference = fixture.reference();

        try (Connection connection = DriverManager.getConnection(databaseUrl)) {
            assertThrows(
                    PageMappingApplyResolver.Refused.class,
                    () -> resolver.resolve(
                            connection,
                            2,
                            5,
                            10,
                            1,
                            new PageMappingInstructionReference(
                                    reference.captureId(),
                                    reference.pageKey(),
                                    reference.scannedElementId(),
                                    "f".repeat(64),
                                    reference.expectedLastScannedAt(),
                                    reference.expectedScanCount())));
            assertThrows(
                    PageMappingApplyResolver.Refused.class,
                    () -> resolver.resolve(
                            connection,
                            2,
                            5,
                            10,
                            1,
                            new PageMappingInstructionReference(
                                    reference.captureId(),
                                    reference.pageKey(),
                                    reference.scannedElementId(),
                                    reference.elementHash(),
                                    "2026-08-07T09:00:00Z",
                                    reference.expectedScanCount())));
            assertThrows(
                    PageMappingApplyResolver.Refused.class,
                    () -> resolver.resolve(
                            connection,
                            2,
                            5,
                            10,
                            1,
                            new PageMappingInstructionReference(
                                    reference.captureId(),
                                    reference.pageKey(),
                                    reference.scannedElementId(),
                                    reference.elementHash(),
                                    reference.expectedLastScannedAt(),
                                    reference.expectedScanCount() + 1)));
        }
    }

    @Test
    void rejectsDeletedRegistryRow() throws Exception {
        Fixture fixture = seedCurrentCapture();
        try (Connection connection = DriverManager.getConnection(databaseUrl);
                Statement statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM scanned_element WHERE id=41");
        }

        try (Connection connection = DriverManager.getConnection(databaseUrl)) {
            assertThrows(
                    PageMappingApplyResolver.Refused.class,
                    () -> new PageMappingApplyResolver(snapshotRoot)
                            .resolve(connection, 2, 5, 10, 1, fixture.reference()));
        }
    }

    @Test
    void acceptsOlderReadyCaptureWhenLocatorIdentityStillExistsInNewestCapture()
            throws Exception {
        Fixture fixture = seedCurrentCapture();
        writeCapture(
                "00000000-0000-0000-0000-000000000002",
                "2026-08-07T10:01:00Z",
                fixture.artifactElement());

        try (Connection connection = DriverManager.getConnection(databaseUrl)) {
            InstructionLoad resolved = new PageMappingApplyResolver(snapshotRoot)
                    .resolve(connection, 2, 5, 10, 1, fixture.reference());
            assertEquals("authoritative_registry_name", resolved.getName());
        }
    }

    @Test
    void rejectsOlderCaptureWhenLocatorIdentityIsAbsentFromNewestCapture()
            throws Exception {
        Fixture fixture = seedCurrentCapture();
        writeCapture(
                "00000000-0000-0000-0000-000000000002",
                "2026-08-07T10:01:00Z",
                element("//button[@id='replacement']", "replacement"));

        try (Connection connection = DriverManager.getConnection(databaseUrl)) {
            assertThrows(
                    PageMappingApplyResolver.Refused.class,
                    () -> new PageMappingApplyResolver(snapshotRoot)
                            .resolve(connection, 2, 5, 10, 1, fixture.reference()));
        }
    }

    @Test
    void choosesNewestReadyCaptureByInstantInsteadOfLexicalTimestampOrder()
            throws Exception {
        Fixture fixture = seedCurrentCapture();
        rewriteCapture(
                SCAN_ID,
                "2026-08-07T10:00:01.1Z",
                fixture.artifactElement());
        writeCapture(
                "00000000-0000-0000-0000-000000000002",
                "2026-08-07T10:00:01.12Z",
                element("//button[@id='replacement']", "replacement"));

        try (Connection connection = DriverManager.getConnection(databaseUrl)) {
            assertThrows(
                    PageMappingApplyResolver.Refused.class,
                    () -> new PageMappingApplyResolver(snapshotRoot)
                            .resolve(connection, 2, 5, 10, 1, fixture.reference()));
        }
    }

    @Test
    void failsClosedWhenAnyReadyCaptureHasMalformedTimestamp() throws Exception {
        Fixture fixture = seedCurrentCapture();
        writeCapture(
                "00000000-0000-0000-0000-000000000002",
                "not-an-instant",
                fixture.artifactElement());

        try (Connection connection = DriverManager.getConnection(databaseUrl)) {
            assertThrows(
                    PageMappingApplyResolver.Refused.class,
                    () -> new PageMappingApplyResolver(snapshotRoot)
                            .resolve(connection, 2, 5, 10, 1, fixture.reference()));
        }
    }

    @Test
    void rejectsHashThatDoesNotBelongToTheSelectedCapture() throws Exception {
        Fixture fixture = seedCurrentCapture();
        ElementDTO different = element("//button[@id='different']", "different_artifact");
        rewriteCapture(SCAN_ID, CAPTURED_AT, different);

        try (Connection connection = DriverManager.getConnection(databaseUrl)) {
            assertThrows(
                    PageMappingApplyResolver.Refused.class,
                    () -> new PageMappingApplyResolver(snapshotRoot)
                            .resolve(connection, 2, 5, 10, 1, fixture.reference()));
        }
    }

    @Test
    void rejectsArtifactPathOutsideTheCaptureOwnerTree() throws Exception {
        Fixture fixture = seedCurrentCapture();
        try (Connection connection = DriverManager.getConnection(databaseUrl);
                PreparedStatement update = connection.prepareStatement(
                        "UPDATE page_scan_snapshot SET artifact_path=? WHERE scan_id=?")) {
            update.setString(
                    1,
                    "org-9/bot-job-5/" + PAGE_KEY + "/capture-" + SCAN_ID);
            update.setString(2, SCAN_ID);
            update.executeUpdate();
        }

        try (Connection connection = DriverManager.getConnection(databaseUrl)) {
            assertThrows(
                    PageMappingApplyResolver.Refused.class,
                    () -> new PageMappingApplyResolver(snapshotRoot)
                            .resolve(connection, 2, 5, 10, 1, fixture.reference()));
        }
    }

    private Fixture seedCurrentCapture() throws Exception {
        ElementDTO artifact = element(
                "//button[@id='continue']", "untrusted_artifact_name");
        String hash = ScannedElementRepository.pageScopedHash(PAGE_KEY, artifact);
        try (Connection connection = DriverManager.getConnection(databaseUrl);
                PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO scanned_element ("
                                + "id,home_banking_id,bot_job_id,page_url,page_key,element_hash,"
                                + "tag_name,type_element,defined_name,client_named,some_text,x_path,"
                                + "css_selector,scan_count,first_scanned_at,last_scanned_at)"
                                + " VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)")) {
            int index = 1;
            statement.setLong(index++, 41L);
            statement.setInt(index++, 2);
            statement.setInt(index++, 5);
            statement.setString(index++, "https://example.invalid/login");
            statement.setString(index++, PAGE_KEY);
            statement.setString(index++, hash);
            statement.setString(index++, "button");
            statement.setString(index++, "button");
            statement.setString(index++, "authoritative_registry_name");
            statement.setString(index++, null);
            statement.setString(index++, "Continue from registry");
            statement.setString(index++, artifact.getXPath());
            statement.setString(index++, "#continue");
            statement.setInt(index++, 3);
            statement.setString(index++, "2026-08-07T08:00:00Z");
            statement.setString(index, LAST_SCANNED_AT);
            statement.executeUpdate();
        }
        writeCapture(SCAN_ID, CAPTURED_AT, artifact);
        return new Fixture(
                artifact,
                new PageMappingInstructionReference(
                        SCAN_ID, PAGE_KEY, 41L, hash, LAST_SCANNED_AT, 3));
    }

    private void rewriteCapture(String scanId, String capturedAt, ElementDTO element)
            throws Exception {
        try (Connection connection = DriverManager.getConnection(databaseUrl);
                PreparedStatement delete = connection.prepareStatement(
                        "DELETE FROM page_scan_snapshot WHERE scan_id=?")) {
            delete.setString(1, scanId);
            delete.executeUpdate();
        }
        Path folder = captureFolder(scanId);
        Files.deleteIfExists(folder.resolve("manifest.json"));
        Files.deleteIfExists(folder.resolve("elements.json"));
        Files.deleteIfExists(folder);
        writeCapture(scanId, capturedAt, element);
    }

    private void writeCapture(String scanId, String capturedAt, ElementDTO element)
            throws Exception {
        Path folder = captureFolder(scanId);
        Files.createDirectories(folder);
        byte[] elementsBytes = JSON.toJson(List.of(element)).getBytes(StandardCharsets.UTF_8);
        Files.write(folder.resolve("elements.json"), elementsBytes);

        JsonObject manifest = new JsonObject();
        manifest.addProperty("format", "page-scan-snapshot-v1");
        manifest.addProperty("scanId", scanId);
        manifest.addProperty("capturedAt", capturedAt);
        JsonObject owner = new JsonObject();
        owner.addProperty("homeBankingId", 2);
        owner.addProperty("botJobId", 5);
        manifest.add("owner", owner);
        JsonObject page = new JsonObject();
        page.addProperty("pageKey", PAGE_KEY);
        manifest.add("page", page);
        manifest.addProperty("elementCount", 1);
        JsonObject files = new JsonObject();
        files.addProperty("elements.json", sha256(elementsBytes));
        manifest.add("files", files);
        byte[] manifestBytes = JSON.toJson(manifest).getBytes(StandardCharsets.UTF_8);
        Files.write(folder.resolve("manifest.json"), manifestBytes);
        PageScanSnapshotFileSecurity.secureExistingRoot(snapshotRoot);

        try (Connection connection = DriverManager.getConnection(databaseUrl);
                PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO page_scan_snapshot ("
                                + "scan_id,home_banking_id,bot_job_id,page_key,captured_at,"
                                + "element_count,artifact_path,manifest_sha256,status)"
                                + " VALUES(?,?,?,?,?,?,?,?,?)")) {
            statement.setString(1, scanId);
            statement.setInt(2, 2);
            statement.setInt(3, 5);
            statement.setString(4, PAGE_KEY);
            statement.setString(5, capturedAt);
            statement.setInt(6, 1);
            statement.setString(
                    7, snapshotRoot.relativize(folder).toString().replace('\\', '/'));
            statement.setString(8, sha256(manifestBytes));
            statement.setString(9, "READY");
            statement.executeUpdate();
        }
    }

    private static ElementDTO element(String xpath, String definedName) {
        ElementDTO element = new ElementDTO();
        element.setTagName("button");
        element.setTypeElement("button");
        element.setXPath(xpath);
        element.setCssSelector("#continue");
        element.setSomeText("Continue");
        element.setDefinedName(definedName);
        return element;
    }

    private Path captureFolder(String scanId) {
        return snapshotRoot
                .resolve("org-2")
                .resolve("bot-job-5")
                .resolve(PAGE_KEY)
                .resolve("capture-" + scanId);
    }

    private static String sha256(byte[] content) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(content);
        StringBuilder output = new StringBuilder(64);
        for (byte value : digest) {
            output.append(String.format(Locale.ROOT, "%02x", value & 0xff));
        }
        return output.toString();
    }

    private record Fixture(
            ElementDTO artifactElement, PageMappingInstructionReference reference) {}
}
