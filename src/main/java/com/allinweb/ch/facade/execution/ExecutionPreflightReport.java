package com.allinweb.ch.facade.execution;

import com.allinweb.ch.facade.execution.ExecutionPreflightResult.Issue;
import com.allinweb.ch.facade.scanner.prelaunch.ScannerExecutionPreflightMonitor;
import java.util.List;
import java.util.Objects;

/**
 * Stable WebSocket representation of one authoritative execution-preflight observation.
 *
 * <p>Execution remains in warning-only mode. The issue sample is bounded so a damaged graph cannot
 * produce an unbounded toolbar response; {@link #totalIssues()} preserves the authoritative count.
 */
public record ExecutionPreflightReport(
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

    public static final String WARNING_ENFORCEMENT = "WARN";
    public static final int ISSUE_REPORT_LIMIT = 25;

    public ExecutionPreflightReport {
        enforcement = Objects.requireNonNull(enforcement, "enforcement");
        status = Objects.requireNonNull(status, "status");
        stage = Objects.requireNonNull(stage, "stage");
        reachableBlockIds =
                List.copyOf(Objects.requireNonNull(reachableBlockIds, "reachableBlockIds"));
        reachableInstructionIds =
                List.copyOf(Objects.requireNonNull(
                        reachableInstructionIds, "reachableInstructionIds"));
        issues = List.copyOf(Objects.requireNonNull(issues, "issues"));
        if (totalIssues < issues.size()) {
            throw new IllegalArgumentException(
                    "totalIssues cannot be smaller than the reported issue sample");
        }
    }

    public static ExecutionPreflightReport from(
            ScannerExecutionPreflightMonitor.Observation observation) {
        Objects.requireNonNull(observation, "observation");
        ExecutionPreflightResult result = observation.result();
        if (result == null) {
            return new ExecutionPreflightReport(
                    WARNING_ENFORCEMENT,
                    observation.status().name(),
                    observation.stage(),
                    null,
                    null,
                    null,
                    null,
                    List.of(),
                    List.of(),
                    0,
                    List.of(),
                    observation.unavailableReason());
        }

        List<IssueReport> issues = result.issues().stream()
                .limit(ISSUE_REPORT_LIMIT)
                .map(IssueReport::from)
                .toList();
        return new ExecutionPreflightReport(
                WARNING_ENFORCEMENT,
                observation.status().name(),
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
                issues,
                null);
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
            Integer blockId,
            Integer instructionId,
            String message) {

        public IssueReport {
            code = Objects.requireNonNull(code, "code");
            kind = Objects.requireNonNull(kind, "kind");
            message = Objects.requireNonNull(message, "message");
        }

        private static IssueReport from(Issue issue) {
            return new IssueReport(
                    issue.code().name(),
                    issue.kind().name(),
                    issue.blockId(),
                    issue.instructionId(),
                    issue.message());
        }
    }
}
