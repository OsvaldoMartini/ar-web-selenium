package com.allinweb.ch.facade.execution;

import com.allinweb.ch.facade.execution.ExecutionPreflightResult.Issue;
import com.allinweb.ch.facade.execution.ExecutionPreflightResult.IssueCode;
import com.allinweb.ch.facade.execution.ExecutionPreflightResult.IssueDisposition;
import com.allinweb.ch.facade.execution.ExecutionPreflightResult.RelationshipKind;
import com.allinweb.ch.facade.execution.ExecutionPreflightResult.Severity;
import com.allinweb.ch.facade.scanner.prelaunch.ScannerExecutionPreflightMonitor;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Stable WebSocket representation of one authoritative execution-preflight observation.
 *
 * <p>Execution remains in warning-only mode. The issue sample is bounded so a damaged graph cannot
 * produce an unbounded toolbar response; {@link #totalIssues()} preserves the authoritative count.
 */
public record ExecutionPreflightReport(
        String enforcement,
        String status,
        String outcome,
        String stage,
        OwnerReport owner,
        RunScopeReport runScope,
        Long graphVersion,
        String contentRevision,
        List<Integer> reachableBlockIds,
        List<Integer> reachableInstructionIds,
        int totalIssues,
        int variableDiagnosticCount,
        int structuralStartFailureCount,
        List<IssueReport> issues,
        String unavailableReason) {

    public static final String WARNING_ENFORCEMENT = "WARN";
    public static final int ISSUE_REPORT_LIMIT = 25;
    private static final Set<String> LEGACY_STATUSES =
            Set.of("READY", "WOULD_BLOCK", "UNAVAILABLE");
    private static final Set<String> OUTCOMES =
            Set.of("READY", "WARN", "BLOCKED", "UNAVAILABLE");

    public ExecutionPreflightReport {
        enforcement = Objects.requireNonNull(enforcement, "enforcement");
        status = Objects.requireNonNull(status, "status");
        outcome = Objects.requireNonNull(outcome, "outcome");
        stage = Objects.requireNonNull(stage, "stage");
        reachableBlockIds =
                List.copyOf(Objects.requireNonNull(reachableBlockIds, "reachableBlockIds"));
        reachableInstructionIds =
                List.copyOf(Objects.requireNonNull(
                        reachableInstructionIds, "reachableInstructionIds"));
        issues = List.copyOf(Objects.requireNonNull(issues, "issues"));
        if (!LEGACY_STATUSES.contains(status)) {
            throw new IllegalArgumentException(
                    "status must remain one of the legacy values " + LEGACY_STATUSES);
        }
        if (!OUTCOMES.contains(outcome)) {
            throw new IllegalArgumentException("Unsupported preflight outcome " + outcome);
        }
        if (totalIssues < issues.size()) {
            throw new IllegalArgumentException(
                    "totalIssues cannot be smaller than the reported issue sample");
        }
        if (variableDiagnosticCount < 0 || structuralStartFailureCount < 0) {
            throw new IllegalArgumentException("Issue disposition counts cannot be negative");
        }
        if (variableDiagnosticCount + structuralStartFailureCount != totalIssues) {
            throw new IllegalArgumentException(
                    "Issue disposition counts must equal totalIssues");
        }
    }

    /**
     * Compatibility constructor for callers compiled against the original transport shape.
     *
     * <p>The legacy {@code status} remains unchanged. The richer outcome and disposition counts are
     * inferred conservatively from the supplied issue sample.
     */
    public ExecutionPreflightReport(
            String enforcement,
            String status,
            String stage,
            OwnerReport owner,
            RunScopeReport runScope,
            Long graphVersion,
            String contentRevision,
            List<Integer> reachableBlockIds,
            List<Integer> reachableInstructionIds,
            int totalIssues,
            List<IssueReport> issues,
            String unavailableReason) {
        this(
                enforcement,
                status,
                legacyOutcome(status, totalIssues, issues),
                stage,
                owner,
                runScope,
                graphVersion,
                contentRevision,
                reachableBlockIds,
                reachableInstructionIds,
                totalIssues,
                legacyVariableDiagnosticCount(status, totalIssues, issues),
                legacyStructuralStartFailureCount(status, totalIssues, issues),
                issues,
                unavailableReason);
    }

    public static ExecutionPreflightReport from(
            ScannerExecutionPreflightMonitor.Observation observation) {
        Objects.requireNonNull(observation, "observation");
        ExecutionPreflightResult result = observation.result();
        if (result == null) {
            return new ExecutionPreflightReport(
                    WARNING_ENFORCEMENT,
                    "UNAVAILABLE",
                    "UNAVAILABLE",
                    observation.stage(),
                    null,
                    null,
                    null,
                    null,
                    List.of(),
                    List.of(),
                    0,
                    0,
                    0,
                    List.of(),
                    observation.unavailableReason());
        }

        List<IssueReport> issues = result.issues().stream()
                .limit(ISSUE_REPORT_LIMIT)
                .map(IssueReport::from)
                .toList();
        int variableDiagnosticCount = (int) result.issues().stream()
                .filter(issue -> issue.disposition() == IssueDisposition.VARIABLE_DIAGNOSTIC)
                .count();
        int structuralStartFailureCount =
                result.issues().size() - variableDiagnosticCount;
        return new ExecutionPreflightReport(
                WARNING_ENFORCEMENT,
                legacyStatus(result.status()),
                result.status().name(),
                observation.stage(),
                new OwnerReport(result.owner().homeBankingId(), result.owner().botJobId()),
                new RunScopeReport(
                        result.runScope().kind().name(),
                        result.runScope().selectedBlockId()),
                observation.graphVersion().isPresent()
                        ? observation.graphVersion().getAsLong()
                        : null,
                observation.contentRevision(),
                result.reachableBlockIds(),
                result.reachableInstructionIds(),
                result.issues().size(),
                variableDiagnosticCount,
                structuralStartFailureCount,
                issues,
                null);
    }

    private static String legacyStatus(ExecutionPreflightResult.Status status) {
        return switch (Objects.requireNonNull(status, "status")) {
            case READY -> "READY";
            case WARN, BLOCKED -> "WOULD_BLOCK";
        };
    }

    private static String legacyOutcome(
            String status, int totalIssues, List<IssueReport> issues) {
        if ("UNAVAILABLE".equals(status)) {
            return "UNAVAILABLE";
        }
        int structural = legacyStructuralStartFailureCount(status, totalIssues, issues);
        if (structural > 0) {
            return "BLOCKED";
        }
        return legacyVariableDiagnosticCount(status, totalIssues, issues) > 0
                ? "WARN"
                : "READY";
    }

    private static int legacyVariableDiagnosticCount(
            String status, int totalIssues, List<IssueReport> issues) {
        Objects.requireNonNull(issues, "issues");
        if ("UNAVAILABLE".equals(status) || totalIssues == 0) {
            return 0;
        }
        long sampledVariableIssues = issues.stream()
                .filter(issue -> IssueDisposition.VARIABLE_DIAGNOSTIC.name()
                        .equals(issue.disposition()))
                .count();
        if (!issues.isEmpty() && sampledVariableIssues == issues.size()) {
            return totalIssues;
        }
        return Math.toIntExact(sampledVariableIssues);
    }

    private static int legacyStructuralStartFailureCount(
            String status, int totalIssues, List<IssueReport> issues) {
        if ("UNAVAILABLE".equals(status) || totalIssues == 0) {
            return 0;
        }
        return totalIssues - legacyVariableDiagnosticCount(status, totalIssues, issues);
    }

    public record OwnerReport(int homeBankingId, int botJobId) {}

    public record RunScopeReport(String kind, Integer selectedBlockId) {
        public RunScopeReport {
            kind = Objects.requireNonNull(kind, "kind");
        }
    }

    public record IssueReport(
            String code,
            String kind,
            String severity,
            String disposition,
            Integer blockId,
            Integer instructionId,
            String message) {

        public IssueReport {
            code = Objects.requireNonNull(code, "code");
            kind = Objects.requireNonNull(kind, "kind");
            severity = Objects.requireNonNull(severity, "severity");
            disposition = Objects.requireNonNull(disposition, "disposition");
            message = Objects.requireNonNull(message, "message");
        }

        /** Compatibility constructor for the original five-field issue transport. */
        public IssueReport(
                String code,
                String kind,
                Integer blockId,
                Integer instructionId,
                String message) {
            this(
                    code,
                    kind,
                    inferredDisposition(code, kind) == IssueDisposition.VARIABLE_DIAGNOSTIC
                            ? Severity.WARNING.name()
                            : Severity.BLOCKING.name(),
                    inferredDisposition(code, kind).name(),
                    blockId,
                    instructionId,
                    message);
        }

        private static IssueReport from(Issue issue) {
            return new IssueReport(
                    issue.code().name(),
                    issue.kind().name(),
                    issue.severity().name(),
                    issue.disposition().name(),
                    issue.blockId(),
                    issue.instructionId(),
                    issue.message());
        }

        private static IssueDisposition inferredDisposition(String code, String kind) {
            try {
                return ExecutionPreflightResult.dispositionFor(
                        IssueCode.valueOf(code), RelationshipKind.valueOf(kind));
            } catch (IllegalArgumentException | NullPointerException ignored) {
                return IssueDisposition.STRUCTURAL_START_FAILURE;
            }
        }
    }
}
