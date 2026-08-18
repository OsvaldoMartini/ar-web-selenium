package com.allinweb.ch.facade.scanner.prelaunch;

import com.allinweb.ch.facade.execution.ExecutionPreflightContentRevisionService;
import com.allinweb.ch.facade.execution.ExecutionPreflightResult;
import com.allinweb.ch.facade.execution.ExecutionPreflightSnapshot;
import com.allinweb.ch.facade.execution.ExecutionPreflightSnapshot.Owner;
import com.allinweb.ch.facade.execution.ExecutionPreflightSnapshotRepository;
import com.allinweb.ch.facade.execution.ExecutionRelationshipPreflightService;
import com.allinweb.ch.facade.execution.RunScope;
import com.allinweb.ch.model.BotJobLoadDTO;
import java.util.Objects;
import java.util.OptionalLong;

/**
 * Warning-only observation of execution relationship health.
 *
 * <p>The monitor intentionally never blocks execution. It records the exact owner, requested run
 * scope, loaded-content revision, and a bounded issue sample. Variable diagnostics are reported as
 * WARN; structural start failures retain the legacy WOULD_BLOCK observation name for compatibility.
 */
public final class ScannerExecutionPreflightMonitor {
    private static final int ISSUE_LOG_LIMIT = 5;

    private final SnapshotPort snapshots;
    private final ExecutionRelationshipPreflightService preflightService;
    private final ExecutionPreflightContentRevisionService revisionService;
    private final Operations operations;

    public ScannerExecutionPreflightMonitor(
            ExecutionPreflightSnapshotRepository snapshotRepository,
            Operations operations) {
        this(
                snapshotRepository::load,
                new ExecutionRelationshipPreflightService(),
                new ExecutionPreflightContentRevisionService(),
                operations);
    }

    ScannerExecutionPreflightMonitor(
            SnapshotPort snapshots,
            ExecutionRelationshipPreflightService preflightService,
            ExecutionPreflightContentRevisionService revisionService,
            Operations operations) {
        this.snapshots = Objects.requireNonNull(snapshots, "snapshots");
        this.preflightService = Objects.requireNonNull(preflightService, "preflightService");
        this.revisionService = Objects.requireNonNull(revisionService, "revisionService");
        this.operations = Objects.requireNonNull(operations, "operations");
    }

    public Observation observe(
            String stage,
            BotJobLoadDTO botJob,
            RunScope runScope) {
        String normalizedStage =
                stage == null || stage.isBlank() ? "EXECUTION_PREPARATION" : stage.trim();
        try {
            Owner owner = owner(botJob);
            ExecutionPreflightSnapshotRepository.LoadedSnapshot loaded =
                    snapshots.load(owner);
            ExecutionPreflightSnapshot snapshot = loaded.snapshot();
            ExecutionPreflightResult result = preflightService.preflight(snapshot, runScope);
            String contentRevision = revisionService.revision(snapshot);
            Observation observation =
                    Observation.observed(
                            normalizedStage,
                            contentRevision,
                            loaded.graphVersion(),
                            result);
            record(observation);
            return observation;
        } catch (Exception error) {
            Observation observation = Observation.unavailable(normalizedStage, safeMessage(error));
            operations.warn(
                    "EXECUTION_PREFLIGHT_SHADOW stage={} status=UNAVAILABLE allowExecution=true reason={}",
                    normalizedStage,
                    safeMessage(error));
            return observation;
        }
    }

    private Owner owner(BotJobLoadDTO botJob) {
        if (botJob == null
                || botJob.getHomeBankingId() == null
                || botJob.getHomeBankingId() <= 0) {
            throw new IllegalArgumentException(
                    "Execution preflight requires a positive homeBankingId");
        }
        Integer botJobId =
                botJob.getId() != null && botJob.getId() > 0
                        ? botJob.getId()
                        : botJob.getBotJobId();
        if (botJobId == null || botJobId <= 0) {
            throw new IllegalArgumentException(
                    "Execution preflight requires a positive botJobId");
        }
        return new Owner(botJob.getHomeBankingId(), botJobId);
    }

    private void record(Observation observation) {
        ExecutionPreflightResult result = observation.result();
        String status = observation.status().name();
        String message =
                "EXECUTION_PREFLIGHT_SHADOW stage={} status={} allowExecution=true"
                        + " homeBankingId={} botJobId={} runScope={} selectedBlockId={}"
                        + " graphVersion={} contentRevision={} reachableBlocks={}"
                        + " reachableRows={} issues={}";
        Object[] values = {
            observation.stage(),
            status,
            result.owner().homeBankingId(),
            result.owner().botJobId(),
            result.runScope().kind(),
            result.runScope().selectedBlockId(),
            observation.graphVersion().isPresent()
                    ? observation.graphVersion().getAsLong()
                    : "UNAVAILABLE",
            observation.contentRevision(),
            result.reachableBlockIds().size(),
            result.reachableInstructionIds().size(),
            result.issues().size()
        };
        if (result.clean()) {
            operations.info(message, values);
            return;
        }

        operations.warn(message, values);
        result.issues().stream()
                .limit(ISSUE_LOG_LIMIT)
                .forEach(issue -> operations.warn(
                        "EXECUTION_PREFLIGHT_SHADOW_ISSUE stage={} code={} blockId={}"
                                + " instructionId={} severity={} disposition={} message={}",
                        observation.stage(),
                        issue.code(),
                        issue.blockId(),
                        issue.instructionId(),
                        issue.severity(),
                        issue.disposition(),
                        issue.message()));
        if (result.issues().size() > ISSUE_LOG_LIMIT) {
            operations.warn(
                    "EXECUTION_PREFLIGHT_SHADOW_ISSUE stage={} remainingIssues={}",
                    observation.stage(),
                    result.issues().size() - ISSUE_LOG_LIMIT);
        }
    }

    private static String safeMessage(Throwable error) {
        String message = error.getMessage();
        return message == null || message.isBlank()
                ? error.getClass().getSimpleName()
                : message;
    }

    public record Observation(
            Status status,
            String stage,
            String contentRevision,
            OptionalLong graphVersion,
            ExecutionPreflightResult result,
            String unavailableReason) {

        private static Observation observed(
                String stage,
                String contentRevision,
                OptionalLong graphVersion,
                ExecutionPreflightResult result) {
            return new Observation(
                    switch (result.status()) {
                        case READY -> Status.READY;
                        case WARN -> Status.WARN;
                        case BLOCKED -> Status.WOULD_BLOCK;
                    },
                    stage,
                    contentRevision,
                    graphVersion,
                    result,
                    null);
        }

        private static Observation unavailable(String stage, String reason) {
            return new Observation(
                    Status.UNAVAILABLE,
                    stage,
                    null,
                    OptionalLong.empty(),
                    null,
                    reason);
        }

        /** Shadow mode always permits execution, including when the observation is unavailable. */
        public boolean allowExecution() {
            return true;
        }
    }

    public enum Status {
        READY,
        WARN,
        WOULD_BLOCK,
        UNAVAILABLE
    }

    public interface Operations {
        void info(String message, Object... args);

        void warn(String message, Object... args);
    }

    @FunctionalInterface
    interface SnapshotPort {
        ExecutionPreflightSnapshotRepository.LoadedSnapshot load(Owner owner)
                throws Exception;
    }
}
