package com.allinweb.ch.facade;

import com.allinweb.ch.db.ScannedElementRepository;
import com.allinweb.ch.model.AttributeData;
import com.allinweb.ch.model.ElementDTO;
import com.allinweb.ch.model.InstructionLoad;
import com.allinweb.ch.model.ScannedElement;
import com.allinweb.ch.util.ARPropertyEnum;
import com.allinweb.ch.util.ARPropertyManager;
import com.allinweb.ch.util.PageDiagnosticDumper;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;

/**
 * Resolves a staged Page Mapping reference to an instruction from server-owned state.
 *
 * <p>The caller supplies an already-open Apply transaction. The selected immutable capture, the
 * current scanned-element registry row, and the newest READY capture are verified before the
 * authoritative registry row is converted. Artifact element data is used only to prove capture
 * membership; it never supplies the instruction fields.
 */
public final class PageMappingApplyResolver {

    private static final Gson JSON = new Gson();
    private static final char[] HEX = "0123456789abcdef".toCharArray();
    private static final long MAX_MANIFEST_BYTES = 1_000_000L;
    private static final long MAX_ELEMENTS_BYTES = 32_000_000L;

    private final Path snapshotRoot;
    private final PreScanApplyService preScanApplyService;

    static PageMappingApplyResolver configured() {
        String pathDb = ARPropertyManager.getInstance().getProperty(ARPropertyEnum.PATH_DB);
        Path root = pathDb == null || pathDb.isBlank()
                ? null
                : Path.of(pathDb)
                        .resolve(PageDiagnosticDumper.SUBFOLDER)
                        .resolve("Scanned");
        return new PageMappingApplyResolver(root, PreScanApplyService.getInstance());
    }

    PageMappingApplyResolver(Path snapshotRoot) {
        this(snapshotRoot, PreScanApplyService.getInstance());
    }

    PageMappingApplyResolver(Path snapshotRoot, PreScanApplyService preScanApplyService) {
        this.snapshotRoot = snapshotRoot == null ? null : snapshotRoot.toAbsolutePath().normalize();
        this.preScanApplyService = Objects.requireNonNull(preScanApplyService);
    }

    InstructionLoad resolve(
            Connection connection,
            int homeBankingId,
            int botJobId,
            int targetBlockId,
            int targetOrder,
            PageMappingInstructionReference reference)
            throws SQLException, Refused {
        if (reference == null || !reference.valid()) {
            throw new Refused("A Page Mapping row has an invalid revision reference.");
        }

        Capture selected = loadSelectedCapture(
                connection, homeBankingId, botJobId, reference);
        Capture newest = loadNewestReadyCapture(
                connection, homeBankingId, botJobId, reference.pageKey());

        ScannedElement registry = ScannedElementRepository.loadExact(
                        connection,
                        homeBankingId,
                        botJobId,
                        reference.pageKey(),
                        reference.scannedElementId())
                .orElseThrow(() -> new Refused(
                        "The selected Page Mapping element no longer exists. Reload Page Mappings and add it again."));
        validateRegistryRevision(registry, reference);
        proveCaptureMembership(selected, homeBankingId, botJobId, reference);
        if (!selected.scanId().equals(newest.scanId())) {
            // History selection is supported, but only while the same locator identity is still
            // present in the newest READY observation for this exact owner/page.
            proveCaptureMembership(newest, homeBankingId, botJobId, reference);
        }

        ElementDTO authoritative = toElement(registry);
        if (!reference.elementHash().equalsIgnoreCase(
                ScannedElementRepository.pageScopedHash(
                        reference.pageKey(), authoritative))) {
            throw new Refused(
                    "The authoritative Page Mapping locator identity is inconsistent.");
        }
        InstructionLoad instruction = preScanApplyService.buildMemoryListInstruction(
                authoritative, botJobId, targetBlockId, targetOrder);
        if (instruction == null) {
            throw new Refused(
                    "The authoritative Page Mapping element could not be converted to an instruction.");
        }
        if (instruction.getParentId() != null
                || instruction.getParentBlockId() != null
                || instruction.getVariableId() != null) {
            throw new Refused(
                    "The authoritative Page Mapping element contains unsupported command relationships.");
        }
        return instruction;
    }

    private Capture loadSelectedCapture(
            Connection connection,
            int homeBankingId,
            int botJobId,
            PageMappingInstructionReference reference)
            throws SQLException, Refused {
        String sql = "SELECT scan_id, page_key, captured_at, element_count, artifact_path,"
                + " manifest_sha256 FROM page_scan_snapshot"
                + " WHERE scan_id = ? AND home_banking_id = ? AND bot_job_id = ?"
                + " AND page_key = ? AND status = 'READY'";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, reference.captureId());
            statement.setInt(2, homeBankingId);
            statement.setInt(3, botJobId);
            statement.setString(4, reference.pageKey());
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    throw new Refused(
                            "The selected Page Mapping capture is unavailable for this Bot Job.");
                }
                Capture capture = new Capture(
                        rows.getString("scan_id"),
                        rows.getString("page_key"),
                        rows.getString("captured_at"),
                        rows.getInt("element_count"),
                        rows.getString("artifact_path"),
                        rows.getString("manifest_sha256"));
                if (rows.next()) {
                    throw new Refused("The selected Page Mapping capture is ambiguous.");
                }
                return capture;
            }
        }
    }

    private Capture loadNewestReadyCapture(
            Connection connection, int homeBankingId, int botJobId, String pageKey)
            throws SQLException, Refused {
        String sql = "SELECT scan_id, page_key, captured_at, element_count, artifact_path,"
                + " manifest_sha256 FROM page_scan_snapshot"
                + " WHERE home_banking_id = ? AND bot_job_id = ? AND page_key = ?"
                + " AND status = 'READY' ORDER BY captured_at DESC, scan_id DESC";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, homeBankingId);
            statement.setInt(2, botJobId);
            statement.setString(3, pageKey);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    throw new Refused("No current Page Mapping capture exists for this page.");
                }
                return new Capture(
                        rows.getString("scan_id"),
                        rows.getString("page_key"),
                        rows.getString("captured_at"),
                        rows.getInt("element_count"),
                        rows.getString("artifact_path"),
                        rows.getString("manifest_sha256"));
            }
        }
    }

    private void validateRegistryRevision(
            ScannedElement registry, PageMappingInstructionReference reference) throws Refused {
        if (!reference.elementHash().equalsIgnoreCase(value(registry.getElementHash()))
                || reference.expectedScanCount() != registry.getScanCount()
                || !reference.expectedLastScannedAt().equals(value(registry.getLastScannedAt()))) {
            throw new Refused(
                    "The selected Page Mapping element changed after it was staged. Reload Page Mappings and add it again.");
        }
    }

    private void proveCaptureMembership(
            Capture capture,
            int homeBankingId,
            int botJobId,
            PageMappingInstructionReference reference)
            throws Refused {
        try {
            Path folder = captureFolder(
                    capture, homeBankingId, botJobId);
            byte[] manifestBytes = readRegularFile(folder.resolve("manifest.json"), MAX_MANIFEST_BYTES);
            if (!sha256(manifestBytes).equalsIgnoreCase(value(capture.manifestSha256()))) {
                throw new Refused("The selected Page Mapping capture failed integrity verification.");
            }
            JsonObject manifest = JsonParser.parseString(
                            new String(manifestBytes, StandardCharsets.UTF_8))
                    .getAsJsonObject();
            validateManifest(manifest, capture, homeBankingId, botJobId);

            byte[] elementsBytes = readRegularFile(folder.resolve("elements.json"), MAX_ELEMENTS_BYTES);
            JsonObject files = object(manifest, "files");
            if (files == null
                    || !sha256(elementsBytes)
                            .equalsIgnoreCase(string(files, "elements.json"))) {
                throw new Refused("The selected Page Mapping element artifact failed integrity verification.");
            }
            JsonElement parsed = JsonParser.parseString(
                    new String(elementsBytes, StandardCharsets.UTF_8));
            if (!parsed.isJsonArray()) {
                throw new Refused("The selected Page Mapping element artifact is invalid.");
            }
            JsonArray elements = parsed.getAsJsonArray();
            if (capture.elementCount() != elements.size()
                    || integer(manifest, "elementCount") != elements.size()) {
                throw new Refused("The selected Page Mapping capture element count changed.");
            }
            boolean found = false;
            for (JsonElement value : elements) {
                if (value == null || !value.isJsonObject()) continue;
                ElementDTO artifactElement = JSON.fromJson(value, ElementDTO.class);
                if (reference.elementHash().equalsIgnoreCase(
                        ScannedElementRepository.pageScopedHash(
                                reference.pageKey(), artifactElement))) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                throw new Refused(
                        "The selected Page Mapping element does not belong to the selected capture.");
            }
        } catch (Refused refused) {
            throw refused;
        } catch (IOException | RuntimeException failure) {
            throw new Refused("The selected Page Mapping capture could not be verified.");
        }
    }

    private Path captureFolder(Capture capture, int homeBankingId, int botJobId)
            throws IOException, Refused {
        String artifactPath = capture.artifactPath();
        if (snapshotRoot == null || artifactPath == null || artifactPath.isBlank()) {
            throw new Refused("Page Mapping capture storage is unavailable.");
        }
        Path relative;
        try {
            relative = Path.of(artifactPath);
        } catch (RuntimeException invalid) {
            throw new Refused("The selected Page Mapping capture path is invalid.");
        }
        if (relative.isAbsolute()
                || relative.getNameCount() < 4
                || relative.normalize().getNameCount() != relative.getNameCount()) {
            throw new Refused("The selected Page Mapping capture path is invalid.");
        }
        for (Path segment : relative) {
            if ("..".equals(segment.toString()) || ".".equals(segment.toString())) {
                throw new Refused("The selected Page Mapping capture path is invalid.");
            }
        }
        if (!("org-" + homeBankingId).equals(relative.getName(0).toString())
                || !("bot-job-" + botJobId).equals(relative.getName(1).toString())
                || !relative.getFileName().toString().endsWith("-" + capture.scanId())) {
            throw new Refused("The selected Page Mapping capture path has the wrong owner.");
        }

        Path configuredRoot = snapshotRoot.toAbsolutePath().normalize();
        BasicFileAttributes rootAttributes = Files.readAttributes(
                configuredRoot, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!rootAttributes.isDirectory()
                || rootAttributes.isSymbolicLink()
                || rootAttributes.isOther()) {
            throw new Refused("The selected Page Mapping capture path is unavailable.");
        }
        Path realRoot = configuredRoot.toRealPath(LinkOption.NOFOLLOW_LINKS);
        Path current = realRoot;
        for (Path part : relative) {
            current = current.resolve(part);
            BasicFileAttributes attributes = Files.readAttributes(
                    current, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            if (!attributes.isDirectory()
                    || attributes.isSymbolicLink()
                    || attributes.isOther()) {
                throw new Refused("The selected Page Mapping capture path is unsafe.");
            }
        }
        Path realFolder = current.toRealPath();
        if (!realFolder.startsWith(realRoot)) {
            throw new Refused("The selected Page Mapping capture path is unsafe.");
        }
        return realFolder;
    }

    private byte[] readRegularFile(Path path, long maximumBytes) throws IOException, Refused {
        if (Files.isSymbolicLink(path) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new Refused("The selected Page Mapping capture artifact is unavailable.");
        }
        long size = Files.size(path);
        if (size < 0 || size > maximumBytes) {
            throw new Refused("The selected Page Mapping capture artifact is too large.");
        }
        return Files.readAllBytes(path);
    }

    private void validateManifest(
            JsonObject manifest, Capture capture, int homeBankingId, int botJobId)
            throws Refused {
        JsonObject owner = object(manifest, "owner");
        JsonObject page = object(manifest, "page");
        if (!"page-scan-snapshot-v1".equals(string(manifest, "format"))
                || !capture.scanId().equals(string(manifest, "scanId"))
                || !capture.capturedAt().equals(string(manifest, "capturedAt"))
                || owner == null
                || integer(owner, "homeBankingId") != homeBankingId
                || integer(owner, "botJobId") != botJobId
                || page == null
                || !capture.pageKey().equals(string(page, "pageKey"))) {
            throw new Refused("The selected Page Mapping capture ownership is invalid.");
        }
    }

    private ElementDTO toElement(ScannedElement source) throws Refused {
        ElementDTO element = new ElementDTO();
        element.setTypeElement(source.getTypeElement());
        element.setTagName(source.getTagName());
        element.setDefinedName(source.getDefinedName());
        element.setClientNamed(source.getClientNamed());
        element.setXPath(source.getXPath());
        element.setSomeText(source.getSomeText());
        element.setAttribId(source.getAttribId());
        element.setAttribName(source.getAttribName());
        element.setCoordinates(source.getCoordinates());
        element.setCustomXPath(source.getCustomXPath());
        element.setIFrameXPath(source.getIFrameXPath());
        element.setShadowHost(source.getShadowHost());
        element.setShadowRoot(source.getShadowRoot());
        element.setCssSelector(source.getCssSelector());
        String attributes = source.getAttributeData();
        if (attributes != null && !attributes.isBlank()) {
            try {
                element.setAttributeData(JSON.fromJson(attributes, AttributeData[].class));
            } catch (RuntimeException invalid) {
                throw new Refused("The authoritative Page Mapping attributes are invalid.");
            }
        }
        return element;
    }

    private static String sha256(byte[] bytes) throws Refused {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            StringBuilder output = new StringBuilder(64);
            for (byte value : digest.digest(bytes)) {
                output.append(HEX[(value >>> 4) & 0x0f]);
                output.append(HEX[value & 0x0f]);
            }
            return output.toString();
        } catch (Exception unavailable) {
            throw new Refused("Page Mapping integrity verification is unavailable.");
        }
    }

    private static JsonObject object(JsonObject source, String name) {
        if (source == null || !source.has(name) || !source.get(name).isJsonObject()) return null;
        return source.getAsJsonObject(name);
    }

    private static String string(JsonObject source, String name) {
        if (source == null || !source.has(name) || source.get(name).isJsonNull()) return "";
        try {
            return source.get(name).getAsString();
        } catch (RuntimeException invalid) {
            return "";
        }
    }

    private static int integer(JsonObject source, String name) {
        if (source == null || !source.has(name) || source.get(name).isJsonNull()) return -1;
        try {
            return source.get(name).getAsInt();
        } catch (RuntimeException invalid) {
            return -1;
        }
    }

    private static String value(String source) {
        return source == null ? "" : source;
    }

    record Capture(
            String scanId,
            String pageKey,
            String capturedAt,
            int elementCount,
            String artifactPath,
            String manifestSha256) {}

    static final class Refused extends Exception {
        Refused(String message) {
            super(message);
        }
    }
}
