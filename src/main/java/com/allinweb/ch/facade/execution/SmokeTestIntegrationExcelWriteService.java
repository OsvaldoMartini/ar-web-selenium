package com.allinweb.ch.facade.execution;

import com.allinweb.ch.facade.ExcelExportTarget;
import com.allinweb.ch.facade.PerformDataBase;
import com.allinweb.ch.facade.execution.SmokeTestIntegrationSnapshotRepository.Owner;
import com.allinweb.ch.facade.execution.SmokeTestIntegrationSnapshotRepository.Plan;
import com.allinweb.ch.model.SmokeTestIntegrationContracts.ExcelWriteRequest;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Minimal Java adapter for one React-finalized ExcelWrite artifact. */
public final class SmokeTestIntegrationExcelWriteService {
    private static final PerformDataBase DATABASE = PerformDataBase.getInstance();
    private final ConnectionPort connections;

    public SmokeTestIntegrationExcelWriteService() {
        this(DATABASE::getConnection);
    }

    SmokeTestIntegrationExcelWriteService(ConnectionPort connections) {
        this.connections = java.util.Objects.requireNonNull(connections);
    }

    public Result save(Plan plan, ExcelWriteRequest request) throws Exception {
        Owner owner = plan.owner();
        Set<Integer> planIds = new HashSet<>();
        plan.instructions().forEach(row -> planIds.add(row.id()));
        if (!planIds.containsAll(request.instructionIds())) {
            throw new IllegalArgumentException("ExcelWrite contains an instruction outside the frozen run.");
        }
        ExcelExportTarget target = ExcelExportTarget.decode(request.outputFile())
                .orElseThrow(() -> new IllegalArgumentException("ExcelWrite target is missing."));
        if (!target.delimiter().equals(request.delimiter())) {
            throw new IllegalArgumentException("ExcelWrite delimiter does not match its typed target.");
        }
        validateConfigurations(owner, request, target);
        byte[] artifact;
        try { artifact = Base64.getDecoder().decode(request.contentBase64()); }
        catch (IllegalArgumentException malformed) { throw new IllegalArgumentException("ExcelWrite artifact is not valid Base64.", malformed); }
        if (artifact.length != request.byteLength() || !sha256(artifact).equals(request.sha256())) {
            throw new IllegalArgumentException("ExcelWrite artifact length or checksum does not match.");
        }
        Path finalTarget = target.path();
        Path directory = finalTarget.getParent();
        if (directory == null || !Files.isDirectory(directory) || !Files.isWritable(directory)) {
            throw new IllegalArgumentException("ExcelWrite target directory is not writable.");
        }
        Path artifactTarget;
        if ("CSV".equals(request.artifactKind())) {
            artifactTarget = ".csv".equals(target.fileType()) ? finalTarget : siblingCsv(finalTarget);
        } else {
            if (!".xlsx".equals(target.fileType())) {
                throw new IllegalArgumentException("An XLSX artifact requires an XLSX instruction target.");
            }
            artifactTarget = finalTarget;
        }
        ExcelWriteFileWriteCoordinator.run(artifactTarget, () -> atomicWriteUnchecked(artifactTarget, artifact));
        return new Result(request.sha256(), request.revision(),
                request.artifactKind() + " artifact was saved atomically.");
    }

    private void validateConfigurations(Owner owner, ExcelWriteRequest request, ExcelExportTarget target)
            throws Exception {
        String sql = "SELECT i.id,c.output_file,c.output_column,s.variable_id"
                + " FROM instruction i JOIN instruction_variable_command_config c"
                + " ON c.home_banking_id=? AND c.bot_job_id=i.bot_job_id AND c.instruction_id=i.id"
                + " JOIN instruction_variable_slot s ON s.home_banking_id=c.home_banking_id"
                + " AND s.bot_job_id=c.bot_job_id AND s.instruction_id=i.id AND UPPER(s.slot)='READ'"
                + " WHERE i.bot_job_id=? AND UPPER(TRIM(i.actions)) IN ('E','EXCELWRITE')";
        Set<Integer> expected = new HashSet<>(request.instructionIds());
        Set<Integer> found = new HashSet<>();
        Set<String> columns = new HashSet<>();
        try (Connection connection = connections.open(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, owner.homeBankingId()); statement.setInt(2, owner.botJobId());
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    int id = rows.getInt("id"); if (!expected.contains(id)) continue;
                    ExcelExportTarget configured = ExcelExportTarget.decode(rows.getString("output_file")).orElseThrow();
                    if (!configured.path().equals(target.path()) || !configured.delimiter().equals(target.delimiter())) {
                        throw new IllegalArgumentException("ExcelWrite target changed after Integration started.");
                    }
                    String column = rows.getString("output_column");
                    if (column == null || column.isBlank()) throw new IllegalArgumentException("ExcelWrite column is missing.");
                    columns.add(column.trim()); found.add(id);
                }
            }
        }
        if (!found.equals(expected) || !new HashSet<>(request.columns()).equals(columns)) {
            throw new IllegalArgumentException("ExcelWrite instructions or columns changed after Integration started.");
        }
    }

    private static Path siblingCsv(Path xlsx) {
        String name = xlsx.getFileName().toString(); int dot = name.lastIndexOf('.');
        return xlsx.resolveSibling((dot > 0 ? name.substring(0, dot) : name) + ".csv");
    }

    private static String sha256(byte[] bytes) throws Exception { return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
    private static void atomicWrite(Path target, byte[] bytes) throws Exception {
        Path temporary = Files.createTempFile(target.getParent(), ".arweb-excelwrite-", ".tmp");
        try { Files.write(temporary, bytes); try { Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException unsupported) { Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING); } }
        finally { Files.deleteIfExists(temporary); }
    }
    private static void atomicWriteUnchecked(Path target, byte[] bytes) {
        try { atomicWrite(target, bytes); }
        catch (Exception failure) { throw new IllegalStateException("Unable to atomically save ExcelWrite artifact.", failure); }
    }
    @FunctionalInterface
    interface ConnectionPort {
        Connection open() throws Exception;
    }
    public record Result(String sha256, long revision, String message) {}
}
