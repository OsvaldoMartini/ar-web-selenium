package com.allinweb.ch.facade.execution.v2;

import com.allinweb.ch.facade.CommandRegistry;
import com.allinweb.ch.facade.RuntimeElementHealingService;
import com.allinweb.ch.facade.RuntimeElementHealingService.Preparation;
import com.allinweb.ch.facade.execution.SmokeTestIntegrationSnapshotRepository.InstructionSnapshot;
import com.allinweb.ch.facade.execution.SmokeTestIntegrationSnapshotRepository.Plan;
import com.allinweb.ch.facade.execution.v2.ExecutionRuntimeHttpClient.RuntimeRun;
import com.allinweb.ch.facade.execution.v2.ExecutionV2Contracts.AuthorizedGrantFacts;
import com.allinweb.ch.facade.execution.v2.ExecutionV2Contracts.IssuedGrant;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.net.URI;
import java.time.Duration;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.Executors;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;

/** Owns one Java-authorized lifecycle for an isolated Execution V2 Node run. */
@Slf4j
public final class ExecutionRuntimeRunCoordinator {
    private static final org.slf4j.Logger executionTrace =
            org.slf4j.LoggerFactory.getLogger("com.allinweb.smoke.execution");
    private static final Duration READY_TIMEOUT = Duration.ofSeconds(45);
    private static final long READY_POLL_MILLIS = 200L;
    private static final long KEEP_ALIVE_INTERVAL_SECONDS = 15L;
    private static final int MAX_KEEP_ALIVE_THREADS = 5;
    private static final Pattern BROWSER_OPTION_MARKER = Pattern.compile(
            "(?i)#?(?:argument|arg|proxy):");
    private static final KeepAlivePort NO_KEEP_ALIVE = task -> () -> {};

    private final GrantPort grants;
    private final RuntimePort runtime;
    private final HealingPort healing;
    private final ExecutionRuntimeActionFactory actions;
    private final TimePort time;
    private final KeepAlivePort keepAlive;
    private final ConcurrentMap<String, Run> openRuns = new ConcurrentHashMap<>();

    ExecutionRuntimeRunCoordinator(
            GrantPort grants,
            RuntimePort runtime,
            HealingPort healing,
            ExecutionRuntimeActionFactory actions,
            TimePort time) {
        this(grants, runtime, healing, actions, time, NO_KEEP_ALIVE);
    }

    ExecutionRuntimeRunCoordinator(
            GrantPort grants,
            RuntimePort runtime,
            HealingPort healing,
            ExecutionRuntimeActionFactory actions,
            TimePort time,
            KeepAlivePort keepAlive) {
        this.grants = Objects.requireNonNull(grants, "Execution V2 grant port is required");
        this.runtime = Objects.requireNonNull(runtime, "Execution V2 runtime port is required");
        this.healing = Objects.requireNonNull(healing, "Execution V2 healing port is required");
        this.actions = Objects.requireNonNull(actions, "Execution V2 action factory is required");
        this.time = Objects.requireNonNull(time, "Execution V2 time port is required");
        this.keepAlive = Objects.requireNonNull(keepAlive, "Execution V2 keep-alive port is required");
    }

    /** Missing grant configuration keeps V2 unavailable without affecting the V1 runtime. */
    public static Optional<ExecutionRuntimeRunCoordinator> configured() {
        return ExecutionRuntimeGrantConfiguration.fromEnvironment().map(configuration ->
                create(configuration, ExecutionRuntimeClientConfiguration.fromEnvironment()));
    }

    /** Builds the coordinator from process-supervisor-owned local credentials. */
    public static ExecutionRuntimeRunCoordinator create(
            ExecutionRuntimeGrantConfiguration grantConfiguration,
            ExecutionRuntimeClientConfiguration clientConfiguration) {
        Objects.requireNonNull(grantConfiguration, "Execution V2 grant configuration is required");
        Objects.requireNonNull(clientConfiguration, "Execution V2 client configuration is required");
        ExecutionRuntimeHttpClient client = new ExecutionRuntimeHttpClient(clientConfiguration);
        return new ExecutionRuntimeRunCoordinator(
                    new ExecutionRuntimeGrantService(grantConfiguration)::issue,
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
                    new SystemTimePort(),
                    SystemKeepAlivePort.INSTANCE);
    }

    /** Opens an owner-scoped lease over a browser parked by a stopped V2 run. */
    public ScannerSession openScanner(AuthorizedGrantFacts facts) {
        IssuedGrant grant = grants.issue(Objects.requireNonNull(facts, "V2 scanner authority is required"));
        ScannerAuthority authority = runtime.openScanner(grant);
        executionTrace.info(
                "phase=V2_SCANNER_OPENED hb={} bot={} workspaceEpoch={}",
                facts.homeBankingId(), facts.botJobId(), facts.workspaceEpoch());
        return new ScannerSession(runtime, authority, facts.homeBankingId(), facts.botJobId());
    }

    /** Opens a serialized scanner view over the exact V2 run paused in Locator Recovery. */
    public ScannerSession openRecoveryScanner(AuthorizedGrantFacts facts, String runId) {
        Objects.requireNonNull(facts, "V2 recovery scanner authority is required");
        Run current = openRuns.get(runId);
        if (current == null) throw new IllegalStateException("Execution V2 recovery run is unavailable");
        synchronized (current) {
            requireOpen(current);
            if (current.pendingRecovery == null
                    || current.facts.homeBankingId() != facts.homeBankingId()
                    || current.facts.botJobId() != facts.botJobId()
                    || current.facts.workspaceEpoch() != facts.workspaceEpoch()) {
                throw new IllegalStateException("Execution V2 recovery scanner owner is stale");
            }
        }
        executionTrace.info(
                "phase=V2_RECOVERY_SCANNER_OPENED runId={} hb={} bot={} workspaceEpoch={}",
                runId, facts.homeBankingId(), facts.botJobId(), facts.workspaceEpoch());
        return new ScannerSession(runtime, current, facts.homeBankingId(), facts.botJobId());
    }

    public static final class ScannerSession implements AutoCloseable {
        private final RuntimePort runtime;
        private final ScannerAuthority authority;
        private final Run recoveryRun;
        private final int homeBankingId;
        private final int botJobId;
        private final AtomicBoolean closed = new AtomicBoolean();
        private final java.util.concurrent.atomic.AtomicLong sequence =
                new java.util.concurrent.atomic.AtomicLong();

        private ScannerSession(
                RuntimePort runtime, ScannerAuthority authority, int homeBankingId, int botJobId) {
            this.runtime = runtime;
            this.authority = authority;
            this.recoveryRun = null;
            this.homeBankingId = homeBankingId;
            this.botJobId = botJobId;
        }

        private ScannerSession(RuntimePort runtime, Run recoveryRun, int homeBankingId, int botJobId) {
            this.runtime = runtime;
            this.authority = null;
            this.recoveryRun = recoveryRun;
            this.homeBankingId = homeBankingId;
            this.botJobId = botJobId;
        }

        public JsonElement exchange(JsonObject request) {
            if (closed.get()) throw new IllegalStateException("Execution V2 scanner is closed");
            String operation = request != null && request.has("operation")
                    ? request.get("operation").getAsString() : "invalid";
            long currentSequence = sequence.incrementAndGet();
            long started = System.nanoTime();
            executionTrace.info(
                    "phase=V2_SCANNER_RPC_STARTED hb={} bot={} sequence={} operation={}",
                    homeBankingId, botJobId, currentSequence, operation);
            try {
                JsonElement response;
                if (recoveryRun == null) {
                    response = runtime.scanner(authority, request);
                } else {
                    synchronized (recoveryRun) {
                        requireOpen(recoveryRun);
                        if (recoveryRun.pendingRecovery == null) {
                            throw new IllegalStateException("Execution V2 locator recovery is no longer pending");
                        }
                        response = runtime.recoveryScanner(recoveryRun.authority, request);
                    }
                }
                executionTrace.info(
                        "phase=V2_SCANNER_RPC_COMPLETED hb={} bot={} sequence={} operation={} durationMs={}",
                        homeBankingId, botJobId, currentSequence, operation,
                        Math.max(0L, (System.nanoTime() - started) / 1_000_000L));
                return response;
            } catch (RuntimeException failure) {
                executionTrace.error(
                        "phase=V2_SCANNER_RPC_FAILED hb={} bot={} sequence={} operation={} failureType={} durationMs={}",
                        homeBankingId, botJobId, currentSequence, operation,
                        failure.getClass().getSimpleName(),
                        Math.max(0L, (System.nanoTime() - started) / 1_000_000L));
                throw failure;
            }
        }

        @Override
        public void close() {
            if (!closed.compareAndSet(false, true)) return;
            if (recoveryRun != null) {
                executionTrace.info(
                        "phase=V2_RECOVERY_SCANNER_CLOSED runId={} hb={} bot={}",
                        recoveryRun.runId, homeBankingId, botJobId);
                return;
            }
            try {
                runtime.closeScanner(authority);
                executionTrace.info("phase=V2_SCANNER_CLOSED hb={} bot={}", homeBankingId, botJobId);
            } catch (RuntimeException failure) {
                executionTrace.error(
                        "phase=V2_SCANNER_CLOSE_FAILED hb={} bot={} failureType={}",
                        homeBankingId, botJobId, failure.getClass().getSimpleName());
                throw failure;
            }
        }
    }

    public Run start(AuthorizedGrantFacts facts, Plan plan) {
        Objects.requireNonNull(facts, "Execution V2 authorized facts are required");
        Objects.requireNonNull(plan, "Execution V2 frozen plan is required");
        requirePlanAuthority(facts, plan);
        IssuedGrant grant = grants.issue(facts);
        String channel = browserChannel(plan.environment().browserType());
        long started = System.nanoTime();
        Authority authority = null;
        boolean ready = false;
        try {
            BrowserArguments browserOptions;
            try {
                browserOptions = browserArgumentFacts(plan.environment().optionsConfig());
            } catch (IllegalArgumentException invalidOptions) {
                executionTrace.warn(
                        "phase=V2_BROWSER_OPTIONS_REJECTED runId={} homeBankingId={} botJobId={} configLength={} code={}",
                        grant.runId(), facts.homeBankingId(), facts.botJobId(),
                        plan.environment().optionsConfig().length(), failureCode(invalidOptions));
                throw invalidOptions;
            }
            java.util.List<String> arguments = browserOptions.arguments();
            executionTrace.info(
                    "phase=V2_BROWSER_OPTIONS_PARSED runId={} homeBankingId={} botJobId={} argumentCount={} normalizedSingleHyphen={} activeMarkers={} ignoredMarkers={}",
                    grant.runId(), facts.homeBankingId(), facts.botJobId(), arguments.size(),
                    browserOptions.normalizedSingleHyphen(), browserOptions.activeMarkers(),
                    browserOptions.ignoredMarkers());
            executionTrace.info(
                    "phase=V2_RUN_START_REQUESTED runId={} homeBankingId={} botJobId={} channel={} argumentCount={} headless=false",
                    grant.runId(), facts.homeBankingId(), facts.botJobId(), channel, arguments.size());
            authority = runtime.reserve(grant);
            executionTrace.debug("phase=V2_RUN_RESERVED runId={}", grant.runId());
            JsonObject snapshot = runtime.start(
                    authority,
                    new ExecutionRuntimeHttpClient.StartFacts(
                            URI.create(plan.environment().url()), false,
                            channel, arguments));
            awaitReady(authority, snapshot);
            Run run = new Run(grant.runId(), facts, plan, authority);
            run.keepAliveLease = keepAlive.start(() -> renewLease(run));
            openRuns.put(run.runId, run);
            ready = true;
            executionTrace.info(
                    "phase=V2_RUN_READY runId={} homeBankingId={} botJobId={} durationMs={}",
                    grant.runId(), facts.homeBankingId(), facts.botJobId(), elapsedMillis(started));
            return run;
        } catch (RuntimeException failure) {
            executionTrace.warn(
                    "phase=V2_RUN_START_FAILED runId={} homeBankingId={} botJobId={} code={} durationMs={}",
                    grant.runId(), facts.homeBankingId(), facts.botJobId(), failureCode(failure),
                    elapsedMillis(started));
            throw failure;
        } finally {
            if (!ready && authority != null) {
                executionTrace.debug("phase=V2_RUN_START_CLEANUP runId={}", grant.runId());
                cleanup(authority);
            }
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
        long started = System.nanoTime();
        String action = request.get("action").getAsString();
        int registryCandidateCount = request.has("registryCandidates")
                && request.get("registryCandidates").isJsonArray()
                ? request.getAsJsonArray("registryCandidates").size()
                : 0;
        executionTrace.info(
                "phase=V2_ACTION_DISPATCH runId={} instructionId={} callerSequence={} physicalSequence={} action={} registryCandidateCount={}",
                current.runId, requestInstructionId, callerSequence, physicalSequence, action,
                registryCandidateCount);
        try {
            JsonObject result = runtime.action(current.authority, request);
            rememberRecovery(current, request, result);
            JsonObject diagnostic = result != null
                    && result.has("diagnostic")
                    && result.get("diagnostic").isJsonObject()
                    ? result.getAsJsonObject("diagnostic")
                    : new JsonObject();
            executionTrace.info(
                    "phase=V2_ACTION_SETTLED runId={} instructionId={} physicalSequence={} action={} ok={} code={} stage={} registryCandidateCount={} liveCandidateCount={} physicalAttempts={} frameValidated={} shadowValidated={} tagValidated={} actionValidated={} recoveryPending={} recoveryCandidateCount={} durationMs={}",
                    current.runId, requestInstructionId, physicalSequence, action,
                    safeBoolean(result, "ok"),
                    safeDiagnosticText(diagnostic, "code", "DIAGNOSTIC_UNAVAILABLE"),
                    safeDiagnosticText(diagnostic, "stage", "STAGE_UNAVAILABLE"),
                    safeDiagnosticInt(diagnostic, "registryCandidateCount"),
                    safeDiagnosticInt(diagnostic, "liveCandidateCount"),
                    safeDiagnosticInt(diagnostic, "physicalAttempts"),
                    safeBoolean(diagnostic, "frameValidated"),
                    safeBoolean(diagnostic, "shadowValidated"),
                    safeBoolean(diagnostic, "tagValidated"),
                    safeBoolean(diagnostic, "actionValidated"),
                    current.pendingRecovery != null,
                    current.pendingRecovery == null ? 0 : current.pendingRecovery.candidates.size(),
                    elapsedMillis(started));
            return result;
        } catch (RuntimeException unknownOutcome) {
            current.actionOutcomeUnknown = true;
            executionTrace.warn(
                    "phase=V2_ACTION_FAILED runId={} instructionId={} physicalSequence={} action={} code={} registryCandidateCount={} durationMs={}",
                    current.runId, requestInstructionId, physicalSequence, action,
                    failureCode(unknownOutcome), registryCandidateCount, elapsedMillis(started));
            throw unknownOutcome;
        }
    }

    public JsonObject recover(Run run, int instructionId, String recoveryCandidateId, boolean save) {
        Run current = Objects.requireNonNull(run, "Execution V2 run is required");
        synchronized (current) {
            PendingRecovery pending = current.pendingRecovery;
            String action = pending != null && pending.originalRequest.has("action")
                    ? pending.originalRequest.get("action").getAsString() : "CLICK";
            String input = pending != null && pending.originalRequest.has("inputValue")
                    ? pending.originalRequest.get("inputValue").getAsString() : null;
            return recover(current, instructionId, recoveryCandidateId, save, action, input);
        }
    }

    public JsonObject recover(
            Run run,
            int instructionId,
            String recoveryCandidateId,
            boolean save,
            String requestedAction,
            String requestedInput) {
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
            long scannedElementId = 0L;
            String xpath = "";
            String savePageKey = "";
            if (save) {
                scannedElementId = requiredPositiveLong(candidate, "registryCandidateId");
                xpath = requiredText(candidate, "newXPath", 2_048);
                savePageKey = candidate.has("previousPageIdentity")
                        ? requiredPageKey(candidate, "previousPageIdentity")
                        : pending.pageKey;
            }
            long sequence = current.nextPhysicalSequence++;
            JsonObject request = actions.createRecovery(
                    sequence, pending.originalRequest, candidate, requestedAction, requestedInput);
            long started = System.nanoTime();
            executionTrace.info(
                    "phase=V2_RECOVERY_DISPATCH runId={} instructionId={} physicalSequence={} saveRequested={} candidateCount={}",
                    current.runId, instructionId, sequence, save, pending.candidates.size());
            JsonObject result;
            try {
                result = runtime.action(current.authority, request);
            } catch (RuntimeException unknownOutcome) {
                current.actionOutcomeUnknown = true;
                executionTrace.warn(
                        "phase=V2_RECOVERY_FAILED runId={} instructionId={} physicalSequence={} saveRequested={} code={} durationMs={}",
                        current.runId, instructionId, sequence, save, failureCode(unknownOutcome),
                        elapsedMillis(started));
                throw unknownOutcome;
            }
            if (!isSuccessful(result)) {
                executionTrace.info(
                        "phase=V2_RECOVERY_REFUSED runId={} instructionId={} physicalSequence={} saveRequested={} durationMs={}",
                        current.runId, instructionId, sequence, save, elapsedMillis(started));
                return result;
            }
            current.pendingRecovery = null;
            boolean saveFailed = false;
            if (save) {
                if (!healing.save(
                        current.facts.homeBankingId(),
                        current.facts.botJobId(),
                        savePageKey,
                        scannedElementId,
                        xpath)) {
                    saveFailed = true;
                }
            }
            JsonObject completed = result.deepCopy();
            completed.add("recoveryCandidate", candidate.deepCopy());
            completed.addProperty("locatorSaved", save && !saveFailed);
            if (saveFailed) completed.addProperty("recoverySaveFailed", true);
            executionTrace.info(
                    "phase=V2_RECOVERY_SETTLED runId={} instructionId={} physicalSequence={} saveRequested={} locatorSaved={} durationMs={}",
                    current.runId, instructionId, sequence, save, save && !saveFailed,
                    elapsedMillis(started));
            return completed;
        }
    }

    /** Retains database-backed rows and installs fresh Page Scanner evidence without settling recovery. */
    public JsonObject replaceRecoveryCandidates(
            Run run, int instructionId, com.google.gson.JsonArray refreshedCandidates) {
        Run current = Objects.requireNonNull(run, "Execution V2 run is required");
        synchronized (current) {
            requireOpen(current);
            PendingRecovery pending = current.pendingRecovery;
            if (pending == null || pending.instructionId != instructionId) {
                throw new IllegalStateException("Execution V2 locator recovery is no longer pending");
            }
            com.google.gson.JsonArray currentCandidates = validatedRecoveryCandidates(
                    refreshedCandidates, "CURRENT", 25);
            com.google.gson.JsonArray merged = new com.google.gson.JsonArray();
            java.util.Set<String> ids = new java.util.LinkedHashSet<>();
            for (var value : pending.candidates) {
                if (!value.isJsonObject()) continue;
                JsonObject candidate = value.getAsJsonObject();
                if (!candidate.has("origin")
                        || !"PREVIOUS".equals(candidate.get("origin").getAsString())) continue;
                String id = candidate.get("recoveryCandidateId").getAsString();
                if (ids.add(id)) merged.add(candidate.deepCopy());
            }
            int previousCount = merged.size();
            for (var value : currentCandidates) {
                JsonObject candidate = value.getAsJsonObject();
                String id = candidate.get("recoveryCandidateId").getAsString();
                if (ids.add(id)) merged.add(candidate.deepCopy());
            }
            current.pendingRecovery = new PendingRecovery(
                    pending.instructionId, pending.pageKey, pending.originalRequest, merged);
            JsonObject recovery = new JsonObject();
            recovery.addProperty("state", "AWAITING_USER");
            recovery.add("candidates", merged.deepCopy());
            executionTrace.info(
                    "phase=V2_RECOVERY_CANDIDATES_REFRESHED runId={} instructionId={} previousCandidates={} currentCandidates={} totalCandidates={}",
                    current.runId, instructionId, previousCount, currentCandidates.size(), merged.size());
            return recovery;
        }
    }

    public JsonObject recoveryCandidate(Run run, int instructionId, String candidateId) {
        Run current = Objects.requireNonNull(run, "Execution V2 run is required");
        synchronized (current) {
            requireOpen(current);
            PendingRecovery pending = current.pendingRecovery;
            if (pending == null || pending.instructionId != instructionId) {
                throw new IllegalStateException("Execution V2 locator recovery is no longer pending");
            }
            for (var value : pending.candidates) {
                if (value.isJsonObject()
                        && value.getAsJsonObject().has("recoveryCandidateId")
                        && candidateId.equals(value.getAsJsonObject()
                                .get("recoveryCandidateId").getAsString())) {
                    return value.getAsJsonObject().deepCopy();
                }
            }
            throw new IllegalStateException("The selected Execution V2 locator candidate is stale");
        }
    }

    public void cancelRecovery(Run run, int instructionId) {
        Run current = Objects.requireNonNull(run, "Execution V2 run is required");
        synchronized (current) {
            PendingRecovery pending = current.pendingRecovery;
            if (pending != null && pending.instructionId == instructionId) {
                current.pendingRecovery = null;
                executionTrace.info(
                        "phase=V2_RECOVERY_CANCELLED runId={} instructionId={}",
                        current.runId, instructionId);
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
        com.google.gson.JsonArray candidates = result.getAsJsonObject("recovery")
                .getAsJsonArray("candidates").deepCopy();
        for (var value : candidates) {
            if (value.isJsonObject() && !value.getAsJsonObject().has("origin")) {
                value.getAsJsonObject().addProperty("origin", "PREVIOUS");
            }
        }
        result.getAsJsonObject("recovery").add("candidates", candidates.deepCopy());
        run.pendingRecovery = new PendingRecovery(
                request.get("instructionId").getAsInt(),
                request.get("pageKey").getAsString(),
                request.deepCopy(),
                candidates);
    }

    private static boolean isSuccessful(JsonObject result) {
        return result != null
                && result.has("ok")
                && result.get("ok").isJsonPrimitive()
                && result.get("ok").getAsBoolean();
    }

    private static com.google.gson.JsonArray validatedRecoveryCandidates(
            com.google.gson.JsonArray candidates, String expectedOrigin, int maximum) {
        if (candidates == null || candidates.size() > maximum) {
            throw new IllegalArgumentException("Execution V2 recovery candidate list is invalid");
        }
        com.google.gson.JsonArray result = new com.google.gson.JsonArray();
        java.util.Set<String> ids = new java.util.LinkedHashSet<>();
        for (var value : candidates) {
            if (!value.isJsonObject()) {
                throw new IllegalArgumentException("Execution V2 recovery candidate is invalid");
            }
            JsonObject candidate = value.getAsJsonObject();
            String id = requiredText(candidate, "recoveryCandidateId", 64);
            String origin = requiredText(candidate, "origin", 16);
            requiredPositiveLong(candidate, "registryCandidateId");
            requiredPageKey(candidate, "previousPageIdentity");
            requiredText(candidate, "newXPath", 2_048);
            if (!id.matches("[0-9a-f]{64}") || !expectedOrigin.equals(origin) || !ids.add(id)) {
                throw new IllegalArgumentException("Execution V2 recovery candidate is invalid");
            }
            result.add(candidate.deepCopy());
        }
        return result;
    }

    private static long requiredPositiveLong(JsonObject value, String field) {
        if (!value.has(field) || !value.get(field).isJsonPrimitive()) {
            throw new IllegalArgumentException("Execution V2 recovery candidate is invalid");
        }
        try {
            long result = value.get(field).getAsLong();
            if (result <= 0) throw new IllegalArgumentException("Execution V2 recovery candidate is invalid");
            return result;
        } catch (NumberFormatException failure) {
            throw new IllegalArgumentException("Execution V2 recovery candidate is invalid", failure);
        }
    }

    private static String requiredText(JsonObject value, String field, int maxLength) {
        if (!value.has(field) || !value.get(field).isJsonPrimitive()) {
            throw new IllegalArgumentException("Execution V2 recovery candidate is invalid");
        }
        String result = value.get(field).getAsString().trim();
        if (result.isEmpty() || result.length() > maxLength) {
            throw new IllegalArgumentException("Execution V2 recovery candidate is invalid");
        }
        return result;
    }

    private static String requiredPageKey(JsonObject value, String field) {
        String result = requiredText(value, field, 71);
        if (!result.matches("url-v1:[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Execution V2 recovery candidate page is invalid");
        }
        return result;
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
            executionTrace.info("phase=V2_REFRESH_REQUESTED runId={}", current.runId);
            JsonObject result = runtime.refresh(current.authority);
            executionTrace.info("phase=V2_REFRESH_SETTLED runId={}", current.runId);
            return result;
        }
    }

    /** Requests immediate Node-side interruption without waiting for the current action monitor. */
    public void interrupt(Run run) {
        Run current = Objects.requireNonNull(run, "Execution V2 run is required");
        if (current.closed) return;
        current.stopRequested.set(true);
        executionTrace.info("phase=V2_INTERRUPT_REQUESTED runId={}", current.runId);
        runtime.stop(current.authority);
    }

    public void close(Run run) {
        Run current = Objects.requireNonNull(run, "Execution V2 run is required");
        synchronized (current) {
            if (current.closed) return;
            current.stopRequested.set(true);
            current.keepAliveLease.close();
            executionTrace.info("phase=V2_CLOSE_REQUESTED runId={} preserveBrowser=true", current.runId);
            try {
                runtime.stop(current.authority);
                runtime.release(current.authority);
                current.closed = true;
                openRuns.remove(current.runId, current);
                executionTrace.info("phase=V2_CLOSE_SETTLED runId={} preserveBrowser=true", current.runId);
            } catch (RuntimeException failure) {
                executionTrace.warn(
                        "phase=V2_CLOSE_FAILED runId={} preserveBrowser=true code={}",
                        current.runId, failureCode(failure));
                // Do not declare the run closed or discard authority after an unknown terminal
                // outcome. The owner may retry the same exact stop/release lifecycle.
                throw failure;
            }
        }
    }

    /** Explicit Close Browser instruction. Ordinary Stop deliberately preserves the page. */
    public void closeBrowser(Run run) {
        Run current = Objects.requireNonNull(run, "Execution V2 run is required");
        synchronized (current) {
            if (current.closed) return;
            current.stopRequested.set(true);
            current.keepAliveLease.close();
            executionTrace.info(
                    "phase=V2_BROWSER_CLOSE_REQUESTED runId={} preserveBrowser=false", current.runId);
            try {
                runtime.closeBrowser(current.authority);
                executionTrace.info(
                        "phase=V2_BROWSER_CLOSE_SETTLED runId={} preserveBrowser=false", current.runId);
            } catch (RuntimeException failure) {
                executionTrace.warn(
                        "phase=V2_BROWSER_CLOSE_FAILED runId={} preserveBrowser=false code={}",
                        current.runId, failureCode(failure));
                throw failure;
            }
        }
    }

    private static long elapsedMillis(long started) {
        return Math.max(0L, (System.nanoTime() - started) / 1_000_000L);
    }

    private static String failureCode(RuntimeException failure) {
        String message = failure.getMessage();
        return message != null && message.matches("[A-Z][A-Z0-9_]{2,80}")
                ? message
                : "V2_RUNTIME_FAILURE";
    }

    private static String safeDiagnosticText(JsonObject diagnostic, String field, String fallback) {
        if (!diagnostic.has(field) || !diagnostic.get(field).isJsonPrimitive()) return fallback;
        String value = diagnostic.get(field).getAsString();
        return value.matches("[A-Z][A-Z0-9_]{2,80}") ? value : fallback;
    }

    private static int safeDiagnosticInt(JsonObject diagnostic, String field) {
        if (!diagnostic.has(field) || !diagnostic.get(field).isJsonPrimitive()) return 0;
        try {
            return Math.max(0, diagnostic.get(field).getAsInt());
        } catch (RuntimeException invalid) {
            return 0;
        }
    }

    private static boolean safeBoolean(JsonObject value, String field) {
        return value != null
                && value.has(field)
                && value.get(field).isJsonPrimitive()
                && value.getAsJsonPrimitive(field).isBoolean()
                && value.get(field).getAsBoolean();
    }

    private void renewLease(Run current) {
        synchronized (current) {
            if (current.closed || current.stopRequested.get()) return;
            try {
                runtime.heartbeat(current.authority);
                if (current.keepAliveUnhealthy.compareAndSet(true, false)) {
                    log.info("Execution V2 keep-alive recovered runId={}", current.runId);
                }
            } catch (RuntimeException failure) {
                if (current.keepAliveUnhealthy.compareAndSet(false, true)) {
                    log.warn("Execution V2 keep-alive failed runId={}", current.runId, failure);
                }
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

    /** Converts database-owned argument entries into the bounded V2 launch contract. */
    static java.util.List<String> browserArguments(String optionsConfig) {
        return browserArgumentFacts(optionsConfig).arguments();
    }

    private static BrowserArguments browserArgumentFacts(String optionsConfig) {
        if (optionsConfig == null || optionsConfig.isBlank()) {
            return new BrowserArguments(java.util.List.of(), 0, 0, 0);
        }
        java.util.List<String> arguments = new java.util.ArrayList<>();
        java.util.List<OptionMarker> markers = new java.util.ArrayList<>();
        Matcher matcher = BROWSER_OPTION_MARKER.matcher(optionsConfig);
        while (matcher.find()) {
            String marker = matcher.group();
            markers.add(new OptionMarker(
                    matcher.start(), matcher.end(), marker,
                    !marker.startsWith("#")
                            && !marker.regionMatches(true, 0, "proxy:", 0, "proxy:".length())));
        }
        int normalizedSingleHyphen = 0;
        int activeMarkers = 0;
        int ignoredMarkers = 0;
        for (int index = 0; index < markers.size(); index++) {
            OptionMarker marker = markers.get(index);
            if (!marker.activeArgument()) {
                ignoredMarkers++;
                continue;
            }
            activeMarkers++;
            int end = index + 1 < markers.size()
                    ? markers.get(index + 1).start()
                    : optionsConfig.length();
            String argument = optionsConfig.substring(marker.end(), end).trim();
            if (argument.isEmpty()) continue;
            if (argument.startsWith("-") && !argument.startsWith("--")) {
                argument = "-" + argument;
                normalizedSingleHyphen++;
            }
            arguments.add(argument);
        }
        java.util.List<String> validated = new ExecutionRuntimeHttpClient.StartFacts(
                URI.create("https://validation.invalid/"), false, null, arguments).arguments();
        return new BrowserArguments(
                validated, normalizedSingleHyphen, activeMarkers, ignoredMarkers);
    }

    private record OptionMarker(int start, int end, String marker, boolean activeArgument) {}

    private record BrowserArguments(
            java.util.List<String> arguments,
            int normalizedSingleHyphen,
            int activeMarkers,
            int ignoredMarkers) {}

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
        private KeepAliveLease keepAliveLease = () -> {};
        private final AtomicBoolean keepAliveUnhealthy = new AtomicBoolean();

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
    interface ScannerAuthority {}

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
        default JsonObject closeBrowser(Authority authority) {
            throw new UnsupportedOperationException("Close Browser is unavailable");
        }
        JsonObject release(Authority authority);
        default ScannerAuthority openScanner(IssuedGrant grant) {
            throw new UnsupportedOperationException("V2 Page Scanner is unavailable");
        }
        default JsonElement scanner(ScannerAuthority authority, JsonObject request) {
            throw new UnsupportedOperationException("V2 Page Scanner is unavailable");
        }
        default void closeScanner(ScannerAuthority authority) {
            throw new UnsupportedOperationException("V2 Page Scanner is unavailable");
        }
        default JsonElement recoveryScanner(Authority authority, JsonObject request) {
            throw new UnsupportedOperationException("V2 recovery Page Scanner is unavailable");
        }
    }

    interface KeepAlivePort {
        KeepAliveLease start(Runnable task);
    }

    interface KeepAliveLease {
        void close();
    }

    private enum SystemKeepAlivePort implements KeepAlivePort {
        INSTANCE;

        private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(
                MAX_KEEP_ALIVE_THREADS,
                runnable -> {
                    Thread thread = new Thread(runnable, "execution-v2-keep-alive");
                    thread.setDaemon(true);
                    return thread;
                });

        @Override
        public KeepAliveLease start(Runnable task) {
            var future = scheduler.scheduleWithFixedDelay(
                    task,
                    KEEP_ALIVE_INTERVAL_SECONDS,
                    KEEP_ALIVE_INTERVAL_SECONDS,
                    TimeUnit.SECONDS);
            return () -> future.cancel(false);
        }
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
    private record DefaultScannerAuthority(ExecutionRuntimeHttpClient.ScannerRun run)
            implements ScannerAuthority {}

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
        public JsonObject closeBrowser(Authority authority) {
            return client.closeBrowser(run(authority));
        }

        @Override
        public JsonObject release(Authority authority) {
            return client.release(run(authority));
        }

        @Override
        public ScannerAuthority openScanner(IssuedGrant grant) {
            return new DefaultScannerAuthority(client.openScanner(grant));
        }

        @Override
        public JsonElement scanner(ScannerAuthority authority, JsonObject request) {
            return client.scanner(scannerRun(authority), request);
        }

        @Override
        public void closeScanner(ScannerAuthority authority) {
            client.closeScanner(scannerRun(authority));
        }

        @Override
        public JsonElement recoveryScanner(Authority authority, JsonObject request) {
            return client.recoveryScanner(run(authority), request);
        }

        private static RuntimeRun run(Authority authority) {
            if (!(authority instanceof DefaultAuthority current)) {
                throw new IllegalArgumentException("Execution V2 runtime authority is invalid");
            }
            return current.run();
        }

        private static ExecutionRuntimeHttpClient.ScannerRun scannerRun(ScannerAuthority authority) {
            if (!(authority instanceof DefaultScannerAuthority current)) {
                throw new IllegalArgumentException("Execution V2 scanner authority is invalid");
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
