package com.allinweb.ch.facade.execution.v2;

import com.allinweb.ch.facade.CommandRegistry;
import com.allinweb.ch.facade.RuntimeElementHealingService;
import com.allinweb.ch.facade.RuntimeElementHealingService.Preparation;
import com.allinweb.ch.facade.execution.SmokeTestIntegrationSnapshotRepository.InstructionSnapshot;
import com.allinweb.ch.facade.execution.SmokeTestIntegrationSnapshotRepository.Plan;
import com.allinweb.ch.facade.execution.v2.ExecutionRuntimeHttpClient.RuntimeRun;
import com.allinweb.ch.facade.execution.v2.ExecutionV2Contracts.AuthorizedGrantFacts;
import com.allinweb.ch.facade.execution.v2.ExecutionV2Contracts.IssuedGrant;
import com.google.gson.JsonObject;
import java.net.URI;
import java.time.Duration;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/** Owns one Java-authorized lifecycle for an isolated Execution V2 Node run. */
public final class ExecutionRuntimeRunCoordinator {
    private static final Duration READY_TIMEOUT = Duration.ofSeconds(45);
    private static final long READY_POLL_MILLIS = 200L;

    private final GrantPort grants;
    private final RuntimePort runtime;
    private final HealingPort healing;
    private final ExecutionRuntimeActionFactory actions;
    private final TimePort time;

    ExecutionRuntimeRunCoordinator(
            GrantPort grants,
            RuntimePort runtime,
            HealingPort healing,
            ExecutionRuntimeActionFactory actions,
            TimePort time) {
        this.grants = Objects.requireNonNull(grants, "Execution V2 grant port is required");
        this.runtime = Objects.requireNonNull(runtime, "Execution V2 runtime port is required");
        this.healing = Objects.requireNonNull(healing, "Execution V2 healing port is required");
        this.actions = Objects.requireNonNull(actions, "Execution V2 action factory is required");
        this.time = Objects.requireNonNull(time, "Execution V2 time port is required");
    }

    /** Missing grant configuration keeps V2 unavailable without affecting the V1 runtime. */
    public static Optional<ExecutionRuntimeRunCoordinator> configured() {
        return ExecutionRuntimeGrantConfiguration.fromEnvironment().map(configuration -> {
            ExecutionRuntimeHttpClient client = new ExecutionRuntimeHttpClient(
                    ExecutionRuntimeClientConfiguration.fromEnvironment(System.getenv()));
            return new ExecutionRuntimeRunCoordinator(
                    new ExecutionRuntimeGrantService(configuration)::issue,
                    new DefaultRuntimePort(client),
                    new HealingPort() {
                        private final RuntimeElementHealingService delegate =
                                RuntimeElementHealingService.getInstance();

                        @Override
                        public Preparation prepare(
                                Integer homeBankingId,
                                Integer botJobId,
                                String pageKey,
                                com.allinweb.ch.model.InstructionLoad instruction) {
                            return delegate.prepareByPageKey(
                                    homeBankingId, botJobId, pageKey, instruction);
                        }

                        @Override
                        public boolean save(
                                int homeBankingId,
                                int botJobId,
                                String pageKey,
                                long scannedElementId,
                                String xpath) {
                            return delegate.saveApprovedRuntimeLocator(
                                    homeBankingId, botJobId, pageKey, scannedElementId, xpath);
                        }
                    },
                    new ExecutionRuntimeActionFactory(),
                    new SystemTimePort());
        });
    }

    public Run start(AuthorizedGrantFacts facts, Plan plan) {
        Objects.requireNonNull(facts, "Execution V2 authorized facts are required");
        Objects.requireNonNull(plan, "Execution V2 frozen plan is required");
        requirePlanAuthority(facts, plan);
        IssuedGrant grant = grants.issue(facts);
        Authority authority = runtime.reserve(grant);
        boolean ready = false;
        try {
            JsonObject snapshot = runtime.start(
                    authority,
                    new ExecutionRuntimeHttpClient.StartFacts(
                            URI.create(plan.environment().url()), false,
                            browserChannel(plan.environment().browserType())));
            awaitReady(authority, snapshot);
            ready = true;
            return new Run(grant.runId(), facts, plan, authority);
        } finally {
            if (!ready) cleanup(authority);
        }
    }

    public JsonObject action(Run run, long sequence, int instructionId, String inputValue) {
        Run current = Objects.requireNonNull(run, "Execution V2 run is required");
        synchronized (current) {
            requireOpen(current);
            InstructionSnapshot instruction = current.plan.instruction(instructionId);
            if (instruction == null) {
                throw new IllegalArgumentException("Instruction is outside the frozen V2 plan");
            }
            return executePhysical(current, sequence, instruction.id(), instruction, null, inputValue);
        }
    }

    public JsonObject get(Run run, long sequence, int commandId) {
        return delegatedAction(run, sequence, commandId, "GET", "OUTPUT", null);
    }

    public JsonObject set(Run run, long sequence, int commandId, String value) {
        if (value == null) throw new IllegalArgumentException("Execution V2 SET value is required");
        return delegatedAction(run, sequence, commandId, "SET", "INPUT", value);
    }

    private JsonObject delegatedAction(
            Run run,
            long sequence,
            int commandId,
            String expectedCommand,
            String physicalAction,
            String inputValue) {
        Run current = Objects.requireNonNull(run, "Execution V2 run is required");
        synchronized (current) {
            requireOpen(current);
            InstructionSnapshot command = current.plan.instruction(commandId);
            if (command == null
                    || !expectedCommand.equals(CommandRegistry.canonicalize(command.action()))) {
                throw new IllegalArgumentException("Execution V2 delegated command is invalid");
            }
            InstructionSnapshot target = requireParent(current.plan, command);
            return executePhysical(
                    current, sequence, command.id(), target, physicalAction, inputValue);
        }
    }

    private JsonObject executePhysical(
            Run current,
            long callerSequence,
            int requestInstructionId,
            InstructionSnapshot target,
            String delegatedAction,
            String inputValue) {
        if (callerSequence <= 0
                || callerSequence > ExecutionV2Contracts.MAX_JAVASCRIPT_SAFE_INTEGER) {
            throw new IllegalArgumentException("Execution V2 caller sequence is invalid");
        }
        String pageKey = runtime.pageIdentity(current.authority);
        Preparation preparation = healing.prepare(
                current.facts.homeBankingId(),
                current.facts.botJobId(),
                pageKey,
                target.toInstructionLoad());
        long physicalSequence = current.nextPhysicalSequence;
        if (physicalSequence > ExecutionV2Contracts.MAX_JAVASCRIPT_SAFE_INTEGER) {
            throw new IllegalStateException("Execution V2 physical sequence limit exceeded");
        }
        JsonObject request = delegatedAction == null
                ? actions.create(physicalSequence, target, preparation, inputValue)
                : actions.createDelegated(
                        physicalSequence,
                        requestInstructionId,
                        delegatedAction,
                        target,
                        preparation,
                        inputValue);
        current.nextPhysicalSequence++;
        try {
            JsonObject result = runtime.action(current.authority, request);
            rememberRecovery(current, request, result);
            return result;
        } catch (RuntimeException unknownOutcome) {
            current.actionOutcomeUnknown = true;
            throw unknownOutcome;
        }
    }

    public JsonObject recover(Run run, int instructionId, String recoveryCandidateId, boolean save) {
        Run current = Objects.requireNonNull(run, "Execution V2 run is required");
        synchronized (current) {
            requireOpen(current);
            PendingRecovery pending = current.pendingRecovery;
            if (pending == null
                    || pending.instructionId != instructionId
                    || recoveryCandidateId == null
                    || !recoveryCandidateId.matches("[0-9a-f]{64}")) {
                throw new IllegalArgumentException("Execution V2 recovery is not pending");
            }
            JsonObject candidate = null;
            for (var value : pending.candidates) {
                if (value.isJsonObject()
                        && recoveryCandidateId.equals(value.getAsJsonObject()
                                .get("recoveryCandidateId").getAsString())) {
                    candidate = value.getAsJsonObject();
                    break;
                }
            }
            if (candidate == null) {
                throw new IllegalArgumentException("Execution V2 recovery candidate is stale");
            }
            long sequence = current.nextPhysicalSequence++;
            JsonObject request = actions.createRecovery(sequence, pending.originalRequest, candidate);
            JsonObject result;
            try {
                result = runtime.action(current.authority, request);
            } catch (RuntimeException unknownOutcome) {
                current.actionOutcomeUnknown = true;
                throw unknownOutcome;
            }
            if (!isSuccessful(result)) return result;
            current.pendingRecovery = null;
            boolean saveFailed = false;
            if (save) {
                long scannedElementId = candidate.get("registryCandidateId").getAsLong();
                String xpath = candidate.get("newXPath").getAsString();
                if (!healing.save(
                        current.facts.homeBankingId(),
                        current.facts.botJobId(),
                        pending.pageKey,
                        scannedElementId,
                        xpath)) {
                    saveFailed = true;
                }
            }
            JsonObject completed = result.deepCopy();
            completed.add("recoveryCandidate", candidate.deepCopy());
            completed.addProperty("locatorSaved", save && !saveFailed);
            if (saveFailed) completed.addProperty("recoverySaveFailed", true);
            return completed;
        }
    }

    public void cancelRecovery(Run run, int instructionId) {
        Run current = Objects.requireNonNull(run, "Execution V2 run is required");
        synchronized (current) {
            PendingRecovery pending = current.pendingRecovery;
            if (pending != null && pending.instructionId == instructionId) {
                current.pendingRecovery = null;
            }
        }
    }

    private static void rememberRecovery(Run run, JsonObject request, JsonObject result) {
        if (result == null
                || !result.has("recovery")
                || !result.get("recovery").isJsonObject()
                || !result.getAsJsonObject("recovery").has("candidates")
                || !result.getAsJsonObject("recovery").get("candidates").isJsonArray()) {
            run.pendingRecovery = null;
            return;
        }
        run.pendingRecovery = new PendingRecovery(
                request.get("instructionId").getAsInt(),
                request.get("pageKey").getAsString(),
                request.deepCopy(),
                result.getAsJsonObject("recovery").getAsJsonArray("candidates").deepCopy());
    }

    private static boolean isSuccessful(JsonObject result) {
        return result != null
                && result.has("ok")
                && result.get("ok").isJsonPrimitive()
                && result.get("ok").getAsBoolean();
    }

    private static InstructionSnapshot requireParent(Plan plan, InstructionSnapshot command) {
        if (command.parentId() == null) {
            throw new IllegalArgumentException("Execution V2 command parent is missing");
        }
        InstructionSnapshot parent = plan.instruction(command.parentId());
        if (parent == null
                || parent.block().id() != command.block().id()
                || (command.parentBlockId() != null
                        && command.parentBlockId() != parent.block().id())
                || !CommandRegistry.isWebElementAction(parent.action())) {
            throw new IllegalArgumentException("Execution V2 command parent is invalid");
        }
        return parent;
    }

    public JsonObject refresh(Run run) {
        Run current = Objects.requireNonNull(run, "Execution V2 run is required");
        synchronized (current) {
            requireOpen(current);
            return runtime.refresh(current.authority);
        }
    }

    /** Requests immediate Node-side interruption without waiting for the current action monitor. */
    public void interrupt(Run run) {
        Run current = Objects.requireNonNull(run, "Execution V2 run is required");
        if (current.closed) return;
        current.stopRequested.set(true);
        runtime.stop(current.authority);
    }

    public void close(Run run) {
        Run current = Objects.requireNonNull(run, "Execution V2 run is required");
        synchronized (current) {
            if (current.closed) return;
            current.stopRequested.set(true);
            try {
                runtime.stop(current.authority);
                runtime.release(current.authority);
                current.closed = true;
            } catch (RuntimeException failure) {
                // Do not declare the run closed or discard authority after an unknown terminal
                // outcome. The owner may retry the same exact stop/release lifecycle.
                throw failure;
            }
        }
    }

    private void awaitReady(Authority authority, JsonObject initial) {
        long deadline = time.nanoTime() + READY_TIMEOUT.toNanos();
        JsonObject snapshot = initial;
        while (true) {
            String state = state(snapshot);
            if ("READY".equals(state)) return;
            if ("FAILED".equals(state) || "STOPPED".equals(state)) {
                throw new IllegalStateException("Execution V2 runtime failed before readiness");
            }
            if (!java.util.Set.of("QUEUED", "STARTING", "LOADING_PAGE").contains(state)) {
                throw new IllegalStateException("Execution V2 runtime returned an invalid state");
            }
            if (time.nanoTime() >= deadline) {
                throw new IllegalStateException("Execution V2 runtime readiness timed out");
            }
            time.sleep(READY_POLL_MILLIS);
            snapshot = runtime.heartbeat(authority);
        }
    }

    private void cleanup(Authority authority) {
        try {
            runtime.stop(authority);
        } catch (RuntimeException ignored) {
            // The reservation may not have activated. Its signed-grant deadline remains bounded.
        }
        try {
            runtime.release(authority);
        } catch (RuntimeException ignored) {
            // An unactivated reservation is swept by the runtime after its short grant lifetime.
        }
    }

    private static void requireOpen(Run run) {
        if (run.closed) throw new IllegalStateException("Execution V2 run is closed");
        if (run.stopRequested.get()) {
            throw new IllegalStateException("Execution V2 run is stopping");
        }
        if (run.actionOutcomeUnknown) {
            throw new IllegalStateException("Execution V2 action outcome is unknown");
        }
    }

    private static void requirePlanAuthority(AuthorizedGrantFacts facts, Plan plan) {
        if (facts.organizationId() != facts.homeBankingId()
                || plan.owner().homeBankingId() != facts.homeBankingId()
                || plan.owner().botJobId() != facts.botJobId()
                || plan.environment().homeBankingId() != facts.homeBankingId()
                || plan.environment().botJobId() != facts.botJobId()
                || !plan.planRevision().equals(facts.planRevision())) {
            throw new IllegalArgumentException("Execution V2 frozen plan authority mismatch");
        }
    }

    private static String browserChannel(String browserType) {
        String value = browserType == null ? "" : browserType.trim().toUpperCase(Locale.ROOT);
        return switch (value) {
            case "CHROME", "GOOGLE CHROME" -> "chrome";
            case "EDGE", "MICROSOFT EDGE", "MSEDGE" -> "msedge";
            case "CHROMIUM" -> "chromium";
            default -> throw new IllegalArgumentException(
                    "Execution V2 Bot Job browser type is unsupported");
        };
    }

    private static String state(JsonObject snapshot) {
        if (snapshot == null
                || !snapshot.has("state")
                || !snapshot.get("state").isJsonPrimitive()
                || !snapshot.getAsJsonPrimitive("state").isString()) {
            throw new IllegalStateException("Execution V2 runtime state is invalid");
        }
        return snapshot.get("state").getAsString();
    }

    public static final class Run {
        private final String runId;
        private final AuthorizedGrantFacts facts;
        private final Plan plan;
        private final Authority authority;
        private long nextPhysicalSequence = 1L;
        private final java.util.concurrent.atomic.AtomicBoolean stopRequested =
                new java.util.concurrent.atomic.AtomicBoolean();
        private boolean actionOutcomeUnknown;
        private volatile boolean closed;
        private PendingRecovery pendingRecovery;

        private Run(String runId, AuthorizedGrantFacts facts, Plan plan, Authority authority) {
            this.runId = runId;
            this.facts = facts;
            this.plan = plan;
            this.authority = authority;
        }

        public String runId() {
            return runId;
        }

        @Override
        public String toString() {
            return "ExecutionRuntimeRun[runId=" + runId + "]";
        }
    }

    interface Authority {}

    interface GrantPort {
        IssuedGrant issue(AuthorizedGrantFacts facts);
    }

    interface RuntimePort {
        Authority reserve(IssuedGrant grant);
        JsonObject start(Authority authority, ExecutionRuntimeHttpClient.StartFacts facts);
        JsonObject heartbeat(Authority authority);
        String pageIdentity(Authority authority);
        JsonObject action(Authority authority, JsonObject request);
        JsonObject refresh(Authority authority);
        JsonObject stop(Authority authority);
        JsonObject release(Authority authority);
    }

    interface HealingPort {
        Preparation prepare(
                Integer homeBankingId,
                Integer botJobId,
                String pageKey,
                com.allinweb.ch.model.InstructionLoad instruction);

        default boolean save(
                int homeBankingId,
                int botJobId,
                String pageKey,
                long scannedElementId,
                String xpath) {
            return false;
        }
    }

    private record PendingRecovery(
            int instructionId,
            String pageKey,
            JsonObject originalRequest,
            com.google.gson.JsonArray candidates) {}

    interface TimePort {
        long nanoTime();
        void sleep(long milliseconds);
    }

    private record DefaultAuthority(RuntimeRun run) implements Authority {}

    private static final class DefaultRuntimePort implements RuntimePort {
        private final ExecutionRuntimeHttpClient client;

        private DefaultRuntimePort(ExecutionRuntimeHttpClient client) {
            this.client = client;
        }

        @Override
        public Authority reserve(IssuedGrant grant) {
            return new DefaultAuthority(client.reserve(grant));
        }

        @Override
        public JsonObject start(Authority authority, ExecutionRuntimeHttpClient.StartFacts facts) {
            return client.start(run(authority), facts);
        }

        @Override
        public JsonObject heartbeat(Authority authority) {
            return client.heartbeat(run(authority));
        }

        @Override
        public String pageIdentity(Authority authority) {
            return client.pageIdentity(run(authority));
        }

        @Override
        public JsonObject action(Authority authority, JsonObject request) {
            return client.action(run(authority), request);
        }

        @Override
        public JsonObject refresh(Authority authority) {
            return client.refresh(run(authority));
        }

        @Override
        public JsonObject stop(Authority authority) {
            return client.stop(run(authority));
        }

        @Override
        public JsonObject release(Authority authority) {
            return client.release(run(authority));
        }

        private static RuntimeRun run(Authority authority) {
            if (!(authority instanceof DefaultAuthority current)) {
                throw new IllegalArgumentException("Execution V2 runtime authority is invalid");
            }
            return current.run();
        }
    }

    private static final class SystemTimePort implements TimePort {
        @Override
        public long nanoTime() {
            return System.nanoTime();
        }

        @Override
        public void sleep(long milliseconds) {
            try {
                Thread.sleep(milliseconds);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Execution V2 readiness wait was interrupted");
            }
        }
    }
}
