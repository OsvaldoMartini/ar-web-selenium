package com.allinweb.ch.facade.scanner.prelaunch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.allinweb.ch.facade.execution.ExecutionPreflightContentRevisionService;
import com.allinweb.ch.facade.execution.ExecutionPreflightReport;
import com.allinweb.ch.facade.execution.ExecutionPreflightResult;
import com.allinweb.ch.facade.execution.ExecutionPreflightSnapshot;
import com.allinweb.ch.facade.execution.ExecutionPreflightSnapshot.BlockFact;
import com.allinweb.ch.facade.execution.ExecutionPreflightSnapshot.InstructionFact;
import com.allinweb.ch.facade.execution.ExecutionPreflightSnapshot.Owner;
import com.allinweb.ch.facade.execution.ExecutionPreflightSnapshotRepository;
import com.allinweb.ch.facade.execution.ExecutionRelationshipPreflightService;
import com.allinweb.ch.facade.execution.RunScope;
import com.allinweb.ch.model.BotJobLoadDTO;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;
import org.junit.jupiter.api.Test;

class ScannerExecutionPreflightMonitorTest {

    @Test
    void recordsBoundedWouldBlockIssuesAndAlwaysAllowsShadowExecution() {
        RecordingOperations operations = new RecordingOperations();
        ScannerExecutionPreflightMonitor monitor = monitor(
                owner -> new ExecutionPreflightSnapshotRepository.LoadedSnapshot(
                        brokenSnapshot(owner),
                        OptionalLong.of(12L)),
                operations);

        ScannerExecutionPreflightMonitor.Observation observation =
                monitor.observe("TEST_RUN", botJob(), RunScope.all());

        assertEquals(
                ScannerExecutionPreflightMonitor.Status.WOULD_BLOCK,
                observation.status());
        assertTrue(observation.allowExecution());
        assertEquals(12L, observation.graphVersion().getAsLong());
        assertEquals(7, observation.result().issues().size());
        ExecutionPreflightReport report = ExecutionPreflightReport.from(observation);
        assertEquals("WARN", report.enforcement());
        assertEquals("WOULD_BLOCK", report.status());
        assertEquals("BLOCKED", report.outcome());
        assertEquals("TEST_RUN", report.stage());
        assertEquals(2, report.owner().homeBankingId());
        assertEquals(5, report.owner().botJobId());
        assertEquals("ALL", report.runScope().kind());
        assertNull(report.runScope().selectedBlockId());
        assertEquals(12L, report.graphVersion());
        assertEquals(7, report.totalIssues());
        assertEquals(0, report.variableDiagnosticCount());
        assertEquals(7, report.structuralStartFailureCount());
        assertEquals(7, report.issues().size());
        assertEquals("MISSING_LOOP_ANCHOR", report.issues().get(0).code());
        assertEquals("BLOCKING", report.issues().get(0).severity());
        assertEquals(
                "STRUCTURAL_START_FAILURE",
                report.issues().get(0).disposition());
        assertEquals(
                7,
                operations.warnings.size(),
                "summary + five issues + one remainder line");
        assertTrue(operations.warnings.get(0).contains("allowExecution=true"));
        assertTrue(operations.warnings.get(6).contains("remainingIssues=2"));
    }

    @Test
    void recordsVariableOnlyDiagnosticsAsWarnWithoutBlockingExecution() {
        RecordingOperations operations = new RecordingOperations();
        ScannerExecutionPreflightMonitor monitor = monitor(
                owner -> new ExecutionPreflightSnapshotRepository.LoadedSnapshot(
                        variableWarningSnapshot(owner),
                        OptionalLong.of(13L)),
                operations);

        ScannerExecutionPreflightMonitor.Observation observation =
                monitor.observe("LAUNCH", botJob(), RunScope.all());

        assertEquals(ScannerExecutionPreflightMonitor.Status.WARN, observation.status());
        assertTrue(observation.allowExecution());
        assertEquals(ExecutionPreflightResult.Status.WARN, observation.result().status());
        ExecutionPreflightReport report = ExecutionPreflightReport.from(observation);
        assertEquals("WOULD_BLOCK", report.status(), "legacy status remains stable");
        assertEquals("WARN", report.outcome());
        assertEquals(2, report.totalIssues());
        assertEquals(2, report.variableDiagnosticCount());
        assertEquals(0, report.structuralStartFailureCount());
        assertTrue(report.issues().stream()
                .allMatch(issue -> "WARNING".equals(issue.severity())));
        assertTrue(report.issues().stream()
                .allMatch(issue -> "VARIABLE_DIAGNOSTIC".equals(issue.disposition())));
        assertTrue(operations.warnings.get(0).contains("status=WARN"));
        assertTrue(operations.warnings.get(0).contains("allowExecution=true"));
    }

    @Test
    void unavailableSnapshotFailsOpenAndIsRecorded() {
        RecordingOperations operations = new RecordingOperations();
        ScannerExecutionPreflightMonitor monitor = monitor(
                owner -> {
                    throw new IllegalStateException("database unavailable");
                },
                operations);

        ScannerExecutionPreflightMonitor.Observation observation =
                monitor.observe("LAUNCH", botJob(), RunScope.all());

        assertEquals(
                ScannerExecutionPreflightMonitor.Status.UNAVAILABLE,
                observation.status());
        assertTrue(observation.allowExecution());
        ExecutionPreflightReport report = ExecutionPreflightReport.from(observation);
        assertEquals("WARN", report.enforcement());
        assertEquals("UNAVAILABLE", report.status());
        assertEquals("UNAVAILABLE", report.outcome());
        assertNull(report.owner());
        assertNull(report.runScope());
        assertNull(report.graphVersion());
        assertNull(report.contentRevision());
        assertEquals(0, report.totalIssues());
        assertEquals(0, report.variableDiagnosticCount());
        assertEquals(0, report.structuralStartFailureCount());
        assertTrue(report.issues().isEmpty());
        assertEquals("database unavailable", report.unavailableReason());
        assertEquals(1, operations.warnings.size());
        assertTrue(operations.warnings.get(0).contains("database unavailable"));
    }

    @Test
    void boundsTransportIssueSampleWhilePreservingAuthoritativeTotal() {
        RecordingOperations operations = new RecordingOperations();
        ScannerExecutionPreflightMonitor monitor = monitor(
                owner -> new ExecutionPreflightSnapshotRepository.LoadedSnapshot(
                        brokenSnapshot(owner, 30),
                        OptionalLong.empty()),
                operations);

        ExecutionPreflightReport report = ExecutionPreflightReport.from(
                monitor.observe("TEST_RUN", botJob(), RunScope.one(10)));

        assertEquals(30, report.totalIssues());
        assertEquals("BLOCKED", report.outcome());
        assertEquals(0, report.variableDiagnosticCount());
        assertEquals(30, report.structuralStartFailureCount());
        assertEquals(ExecutionPreflightReport.ISSUE_REPORT_LIMIT, report.issues().size());
        assertEquals("ONE", report.runScope().kind());
        assertEquals(10, report.runScope().selectedBlockId());
        assertNull(report.graphVersion());
    }

    private ScannerExecutionPreflightMonitor monitor(
            ScannerExecutionPreflightMonitor.SnapshotPort snapshots,
            RecordingOperations operations) {
        return new ScannerExecutionPreflightMonitor(
                snapshots,
                new ExecutionRelationshipPreflightService(),
                new ExecutionPreflightContentRevisionService(),
                operations);
    }

    private ExecutionPreflightSnapshot brokenSnapshot(Owner owner) {
        return brokenSnapshot(owner, 7);
    }

    private ExecutionPreflightSnapshot brokenSnapshot(Owner owner, int rowCount) {
        List<InstructionFact> rows = new ArrayList<>();
        for (int id = 1; id <= rowCount; id++) {
            rows.add(new InstructionFact(
                    id,
                    10,
                    id,
                    "LOOP",
                    null,
                    true,
                    null,
                    null,
                    null));
        }
        return new ExecutionPreflightSnapshot(
                owner,
                List.of(new BlockFact(10, 1, true)),
                rows,
                List.of());
    }

    private ExecutionPreflightSnapshot variableWarningSnapshot(Owner owner) {
        return new ExecutionPreflightSnapshot(
                owner,
                List.of(new BlockFact(10, 1, true)),
                List.of(new InstructionFact(
                        1,
                        10,
                        1,
                        "GET",
                        null,
                        true,
                        null,
                        null,
                        null)),
                List.of());
    }

    private BotJobLoadDTO botJob() {
        BotJobLoadDTO botJob = new BotJobLoadDTO();
        botJob.setId(5);
        botJob.setHomeBankingId(2);
        return botJob;
    }

    private static final class RecordingOperations
            implements ScannerExecutionPreflightMonitor.Operations {
        private final List<String> infos = new ArrayList<>();
        private final List<String> warnings = new ArrayList<>();

        @Override
        public void info(String message, Object... args) {
            infos.add(format(message, args));
        }

        @Override
        public void warn(String message, Object... args) {
            warnings.add(format(message, args));
        }

        private String format(String message, Object... args) {
            String formatted = message;
            for (Object arg : args) {
                formatted = formatted.replaceFirst(
                        "\\{}",
                        java.util.regex.Matcher.quoteReplacement(String.valueOf(arg)));
            }
            return formatted;
        }
    }
}
