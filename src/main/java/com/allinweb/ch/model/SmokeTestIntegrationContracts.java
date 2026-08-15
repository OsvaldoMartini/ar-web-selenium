package com.allinweb.ch.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Versioned, fail-closed transport contract for the detached Smoke Test Playwright integration.
 *
 * <p>Only identity, scope and execution intent cross the WebSocket. Browser configuration,
 * locators, instruction actions, parent relationships and variable slots are deliberately absent:
 * the backend must load those facts from its owner-scoped database snapshot. Parsers accept either
 * the already-extracted request body or the normal AR Web envelope whose {@code body} is a JSON
 * object or a JSON string.
 */
public final class SmokeTestIntegrationContracts {
    public static final int CONTRACT_VERSION = 1;

    public static final String START = "smokeTest.integration.start";
    public static final String REFRESH = "smokeTest.integration.refresh";
    public static final String STEP = "smokeTest.integration.step";
    public static final String RECOVER = "smokeTest.integration.recover";
    public static final String EXCEL_WRITE = "smokeTest.integration.excelWrite";
    public static final String STOP = "smokeTest.integration.stop";
    public static final String FORCE_STOP = "smokeTest.integration.forceStop";
    public static final String FINISH = "smokeTest.integration.finish";
    public static final String RUNTIME_STATUS = "smokeTest.integration.runtimeStatus";
    public static final String RUNTIME_CONTROL = "smokeTest.integration.runtimeControl";
    public static final String RUNTIME_INSTANCES = "smokeTest.integration.runtimeInstances";
    public static final String RUNTIME_INSTANCE_CONTROL = "smokeTest.integration.runtimeInstanceControl";
    public static final String START_RESPONSE = START + "Response";
    public static final String REFRESH_RESPONSE = REFRESH + "Response";
    public static final String STEP_RESPONSE = STEP + "Response";
    public static final String RECOVER_RESPONSE = RECOVER + "Response";
    public static final String EXCEL_WRITE_RESPONSE = EXCEL_WRITE + "Response";
    public static final String STOP_RESPONSE = STOP + "Response";
    public static final String FORCE_STOP_RESPONSE = FORCE_STOP + "Response";
    public static final String FINISH_RESPONSE = FINISH + "Response";
    public static final String RUNTIME_STATUS_RESPONSE = RUNTIME_STATUS + "Response";
    public static final String RUNTIME_CONTROL_RESPONSE = RUNTIME_CONTROL + "Response";
    public static final String RUNTIME_INSTANCES_RESPONSE = RUNTIME_INSTANCES + "Response";
    public static final String RUNTIME_INSTANCE_CONTROL_RESPONSE = RUNTIME_INSTANCE_CONTROL + "Response";

    private static final int MAX_CORRELATION_LENGTH = 200;
    private static final int MAX_EPOCH_LENGTH = 256;
    private static final Pattern SHA_256 = Pattern.compile("[0-9a-fA-F]{64}");
    private static final Pattern INTEGER = Pattern.compile("-?[0-9]+");

    private SmokeTestIntegrationContracts() {}

    public enum ScopeKind {
        ALL,
        BLOCKS
    }

    public enum ExcelMode {
        REAL,
        SYNTHETIC
    }

    /** Authoritative start behavior for the selected Bot Job Playwright page. */
    public enum PagePolicy {
        PRESERVE_ACTIVE,
        RELOAD_SELECTED
    }

    /** Whole-run physical runtime. A run cannot change this value after START. */
    public enum RuntimeMode {
        JAVA_V1,
        TYPESCRIPT_PLAYWRIGHT_V2
    }

    public enum RunStatus {
        STARTED,
        RUNNING,
        STOPPING,
        STOPPED,
        FINISHED,
        FAILED,
        REJECTED
    }

    public enum StepDisposition {
        PHYSICAL,
        LOGICAL_ONLY,
        INACTIVE,
        UNSUPPORTED
    }

    public enum StepStatus {
        PASSED,
        WARNING,
        FAILED,
        SKIPPED
    }

    public enum RecoveryDecision {
        USE_ONCE,
        USE_AND_SAVE,
        BYPASS,
        CANCEL
    }

    /** Explicit runtime state; VALUE with an empty String remains different from VOID. */
    public enum RuntimeValueState {
        VALUE,
        VOID
    }

    public enum RuntimeVoidReason {
        NO_PRODUCER_YET,
        MISSING_BINDING,
        MISSING_PARENT,
        PRODUCER_FAILED,
        EVALUATION_FAILED,
        METADATA_UNAVAILABLE
    }

    /** One value from the backend-owned runtime snapshot frozen at Integration START. */
    public record FrozenRuntimeValue(
            RuntimeValueState state,
            String value,
            RuntimeVoidReason voidReason,
            long entryRevision) {
        public FrozenRuntimeValue {
            state = Objects.requireNonNull(state, "Frozen runtime value state is required");
            requireNonNegative(entryRevision, "runtimeSnapshot.entryRevision");
            if (state == RuntimeValueState.VALUE) {
                value = Objects.requireNonNull(value, "Frozen runtime VALUE cannot be null");
                if (voidReason != null) {
                    throw invalid(
                            "runtimeSnapshot.value",
                            "cannot contain a VOID reason when state is VALUE");
                }
            } else {
                if (value != null) {
                    throw invalid(
                            "runtimeSnapshot.value",
                            "cannot contain a value when state is VOID");
                }
                voidReason = Objects.requireNonNull(
                        voidReason, "Frozen runtime VOID reason is required");
            }
        }
    }

    /**
     * Immutable, Bot-Job-scoped runtime memory frozen by Java at Integration START.
     *
     * <p>Integer keys serialize as JSON object keys. The map intentionally contains only values
     * already authorized for the same detached Smoke Test workspace.
     */
    public record RuntimeSnapshot(
            long revision,
            boolean metadataAvailable,
            Map<Integer, FrozenRuntimeValue> values) {
        public RuntimeSnapshot {
            requireNonNegative(revision, "runtimeSnapshot.revision");
            Map<Integer, FrozenRuntimeValue> supplied = values == null ? Map.of() : values;
            Map<Integer, FrozenRuntimeValue> copy = new LinkedHashMap<>();
            for (Map.Entry<Integer, FrozenRuntimeValue> entry : supplied.entrySet()) {
                Integer variableId = entry.getKey();
                if (variableId == null || variableId <= 0) {
                    throw invalid(
                            "runtimeSnapshot.values",
                            "must use positive variable IDs");
                }
                copy.put(
                        variableId,
                        Objects.requireNonNull(
                                entry.getValue(),
                                "Frozen runtime map values cannot be null"));
            }
            values = Collections.unmodifiableMap(copy);
        }
    }

    /** Immutable selection of either every active Block or explicit active Block IDs. */
    public record Scope(ScopeKind kind, List<Integer> blockIds) {
        public Scope {
            kind = Objects.requireNonNull(kind, "Smoke integration scope kind is required");
            List<Integer> supplied = blockIds == null ? List.of() : List.copyOf(blockIds);
            Set<Integer> unique = new LinkedHashSet<>();
            for (Integer blockId : supplied) {
                if (blockId == null || blockId <= 0) {
                    throw new IllegalArgumentException(
                            "Smoke integration scope blockIds must contain positive integers");
                }
                if (!unique.add(blockId)) {
                    throw new IllegalArgumentException(
                            "Smoke integration scope blockIds must not contain duplicates");
                }
            }
            blockIds = List.copyOf(unique);
            if (kind == ScopeKind.ALL && !blockIds.isEmpty()) {
                throw new IllegalArgumentException(
                        "Smoke integration ALL scope must not contain blockIds");
            }
            if (kind == ScopeKind.BLOCKS && blockIds.isEmpty()) {
                throw new IllegalArgumentException(
                        "Smoke integration BLOCKS scope requires at least one blockId");
            }
        }

        public static Scope all() {
            return new Scope(ScopeKind.ALL, List.of());
        }

        public static Scope blocks(List<Integer> blockIds) {
            return new Scope(ScopeKind.BLOCKS, blockIds);
        }
    }

    public record StartRequest(
            int contractVersion,
            String requestId,
            String bindingEpoch,
            long workspaceEpoch,
            int homeBankingId,
            int botJobId,
            String graphRevision,
            Scope scope,
            ExcelMode excelMode,
            PagePolicy pagePolicy,
            RuntimeMode runtimeMode,
            boolean durableRuntimeWrites) {
        public StartRequest {
            requireVersion(contractVersion);
            requestId = requireBounded(requestId, "requestId", MAX_CORRELATION_LENGTH);
            bindingEpoch = requireBounded(bindingEpoch, "bindingEpoch", MAX_EPOCH_LENGTH);
            requirePositive(workspaceEpoch, "workspaceEpoch");
            requirePositive(homeBankingId, "homeBankingId");
            requirePositive(botJobId, "botJobId");
            graphRevision = requireSha256(graphRevision, "graphRevision");
            scope = Objects.requireNonNull(scope, "Smoke integration scope is required");
            excelMode = Objects.requireNonNull(excelMode, "Smoke integration excelMode is required");
            pagePolicy = Objects.requireNonNull(pagePolicy, "Smoke integration pagePolicy is required");
            runtimeMode = Objects.requireNonNull(runtimeMode, "Smoke integration runtimeMode is required");
        }
    }

    public record StepRequest(
            int contractVersion,
            String requestId,
            String runId,
            long sequence,
            int instructionId,
            int excelRowIndex,
            boolean recoveryVerificationEnabled) {
        public StepRequest {
            requireVersion(contractVersion);
            requestId = requireBounded(requestId, "requestId", MAX_CORRELATION_LENGTH);
            runId = requireBounded(runId, "runId", MAX_CORRELATION_LENGTH);
            requirePositive(sequence, "sequence");
            requirePositive(instructionId, "instructionId");
            requireNonNegative(excelRowIndex, "excelRowIndex");
        }
    }

    public record RecoveryRequest(
            int contractVersion,
            String requestId,
            String runId,
            long sequence,
            int instructionId,
            String recoveryCandidateId,
            RecoveryDecision decision) {
        public RecoveryRequest {
            requireVersion(contractVersion);
            requestId = requireBounded(requestId, "requestId", MAX_CORRELATION_LENGTH);
            runId = requireBounded(runId, "runId", MAX_CORRELATION_LENGTH);
            requirePositive(sequence, "sequence");
            requirePositive(instructionId, "instructionId");
            decision = Objects.requireNonNull(decision, "Recovery decision is required");
            recoveryCandidateId = decision == RecoveryDecision.CANCEL
                    || decision == RecoveryDecision.BYPASS
                    ? optionalBounded(recoveryCandidateId, "recoveryCandidateId", 64)
                    : requireSha256(recoveryCandidateId, "recoveryCandidateId");
        }
    }

    public record ExcelWriteRequest(
            int contractVersion,
            String requestId,
            String runId,
            String outputFile,
            String delimiter,
            List<String> columns,
            List<Integer> instructionIds,
            String artifactKind,
            String contentBase64,
            int byteLength,
            String sha256,
            long revision) {
        public ExcelWriteRequest {
            requireVersion(contractVersion);
            requestId = requireBounded(requestId, "requestId", MAX_CORRELATION_LENGTH);
            runId = requireBounded(runId, "runId", MAX_CORRELATION_LENGTH);
            outputFile = requireBounded(outputFile, "outputFile", 1_500);
            if (!",".equals(delimiter) && !"|".equals(delimiter)) {
                throw invalid("delimiter", "must be comma or pipe");
            }
            columns = requireStrings(columns, "columns", 200, 200);
            instructionIds = requirePositiveIds(instructionIds, "instructionIds", 1_000);
            artifactKind = requireBounded(artifactKind, "artifactKind", 4).toUpperCase(Locale.ROOT);
            if (!"CSV".equals(artifactKind) && !"XLSX".equals(artifactKind)) {
                throw invalid("artifactKind", "must be CSV or XLSX");
            }
            if (contentBase64 == null || contentBase64.isEmpty() || contentBase64.length() > 12_000_000) {
                throw invalid("contentBase64", "must contain at most 12,000,000 characters");
            }
            requirePositive(byteLength, "byteLength");
            sha256 = requireSha256(sha256, "sha256");
            requirePositive(revision, "revision");
        }
    }

    /** Exact owner assertions for a manual refresh of the shared Playwright page. */
    public record RefreshRequest(
            int contractVersion,
            String requestId,
            String bindingEpoch,
            long workspaceEpoch,
            int homeBankingId,
            int botJobId,
            String graphRevision) {
        public RefreshRequest {
            requireVersion(contractVersion);
            requestId = requireBounded(requestId, "requestId", MAX_CORRELATION_LENGTH);
            bindingEpoch = requireBounded(bindingEpoch, "bindingEpoch", MAX_EPOCH_LENGTH);
            requirePositive(workspaceEpoch, "workspaceEpoch");
            requirePositive(homeBankingId, "homeBankingId");
            requirePositive(botJobId, "botJobId");
            graphRevision = requireSha256(graphRevision, "graphRevision");
        }
    }

    public record StopRequest(int contractVersion, String requestId, String runId) {
        public StopRequest {
            requireVersion(contractVersion);
            requestId = requireBounded(requestId, "requestId", MAX_CORRELATION_LENGTH);
            runId = requireBounded(runId, "runId", MAX_CORRELATION_LENGTH);
        }
    }

    /** Owner-bound emergency cancellation that remains addressable before START returns a run ID. */
    public record ForceStopRequest(
            int contractVersion,
            String requestId,
            String bindingEpoch,
            long workspaceEpoch,
            int homeBankingId,
            int botJobId,
            String graphRevision) {
        public ForceStopRequest {
            requireVersion(contractVersion);
            requestId = requireBounded(requestId, "requestId", MAX_CORRELATION_LENGTH);
            bindingEpoch = requireBounded(bindingEpoch, "bindingEpoch", MAX_EPOCH_LENGTH);
            requirePositive(workspaceEpoch, "workspaceEpoch");
            requirePositive(homeBankingId, "homeBankingId");
            requirePositive(botJobId, "botJobId");
            graphRevision = requireSha256(graphRevision, "graphRevision");
        }
    }

    public record FinishRequest(
            int contractVersion,
            String requestId,
            String runId,
            long lastSequence) {
        public FinishRequest {
            requireVersion(contractVersion);
            requestId = requireBounded(requestId, "requestId", MAX_CORRELATION_LENGTH);
            runId = requireBounded(runId, "runId", MAX_CORRELATION_LENGTH);
            requireNonNegative(lastSequence, "lastSequence");
        }
    }

    /** Backend-authored start acknowledgement; no client-authored execution facts are echoed. */
    public record StartResponse(
            int contractVersion,
            String requestId,
            String runId,
            long integrationEpoch,
            RunStatus status,
            String bindingEpoch,
            long workspaceEpoch,
            int homeBankingId,
            int botJobId,
            String graphRevision,
            String planRevision,
            String datasetMode,
            long datasetEpoch,
            long datasetRevision,
            String datasetContentRevision,
            int datasetRowCount,
            String runtimeMode,
            String pagePolicy,
            boolean durableRuntimeWrites,
            RuntimeSnapshot runtimeSnapshot,
            int blockCount,
            int instructionCount,
            String code,
            String message) {
        public StartResponse {
            requireVersion(contractVersion);
            requestId = requireBounded(requestId, "requestId", MAX_CORRELATION_LENGTH);
            runId = requireBounded(runId, "runId", MAX_CORRELATION_LENGTH);
            requirePositive(integrationEpoch, "integrationEpoch");
            status = Objects.requireNonNull(status, "Smoke integration response status is required");
            bindingEpoch = requireBounded(bindingEpoch, "bindingEpoch", MAX_EPOCH_LENGTH);
            requirePositive(workspaceEpoch, "workspaceEpoch");
            requirePositive(homeBankingId, "homeBankingId");
            requirePositive(botJobId, "botJobId");
            graphRevision = requireSha256(graphRevision, "graphRevision");
            planRevision = requireSha256(planRevision, "planRevision");
            datasetMode = requireEnumText(datasetMode, "datasetMode", ExcelMode.class);
            requirePositive(datasetEpoch, "datasetEpoch");
            requireNonNegative(datasetRevision, "datasetRevision");
            datasetContentRevision = requireSha256(
                    datasetContentRevision, "datasetContentRevision");
            requireNonNegative(datasetRowCount, "datasetRowCount");
            runtimeMode = requireEnumText(runtimeMode, "runtimeMode", RuntimeMode.class);
            pagePolicy = requireEnumText(pagePolicy, "pagePolicy", PagePolicy.class);
            runtimeSnapshot = Objects.requireNonNull(
                    runtimeSnapshot, "Smoke integration runtimeSnapshot is required");
            requireNonNegative(blockCount, "blockCount");
            requireNonNegative(instructionCount, "instructionCount");
            code = optionalBounded(code, "code", MAX_CORRELATION_LENGTH);
            message = optionalBounded(message, "message", 1_000);
        }
    }

    /** Correlated result for exactly one backend-authoritative instruction. */
    public record StepResponse(
            int contractVersion,
            String requestId,
            String runId,
            long integrationEpoch,
            long sequence,
            int instructionId,
            StepStatus status,
            StepDisposition disposition,
            String code,
            String message,
            boolean recoveryVerificationEnabled,
            boolean replayed) {
        public StepResponse {
            requireVersion(contractVersion);
            requestId = requireBounded(requestId, "requestId", MAX_CORRELATION_LENGTH);
            runId = requireBounded(runId, "runId", MAX_CORRELATION_LENGTH);
            requirePositive(integrationEpoch, "integrationEpoch");
            requirePositive(sequence, "sequence");
            requirePositive(instructionId, "instructionId");
            status = Objects.requireNonNull(status, "Smoke integration step status is required");
            disposition = Objects.requireNonNull(
                    disposition, "Smoke integration step disposition is required");
            code = optionalBounded(code, "code", MAX_CORRELATION_LENGTH);
            message = optionalBounded(message, "message", 1_000);
        }
    }

    /** Shared terminal response for STOP and FINISH. */
    public record TerminalResponse(
            int contractVersion,
            String requestId,
            String runId,
            long integrationEpoch,
            RunStatus status,
            long lastSequence,
            int passed,
            int warnings,
            int failed,
            int skipped,
            String code,
            String message) {
        public TerminalResponse {
            requireVersion(contractVersion);
            requestId = requireBounded(requestId, "requestId", MAX_CORRELATION_LENGTH);
            runId = requireBounded(runId, "runId", MAX_CORRELATION_LENGTH);
            requirePositive(integrationEpoch, "integrationEpoch");
            status = Objects.requireNonNull(status, "Smoke integration response status is required");
            requireNonNegative(lastSequence, "lastSequence");
            requireNonNegative(passed, "passed");
            requireNonNegative(warnings, "warnings");
            requireNonNegative(failed, "failed");
            requireNonNegative(skipped, "skipped");
            code = optionalBounded(code, "code", MAX_CORRELATION_LENGTH);
            message = optionalBounded(message, "message", 1_000);
        }
    }

    public static StartRequest parseStart(JsonObject envelopeOrBody) {
        JsonObject body = body(envelopeOrBody);
        return new StartRequest(
                requiredVersion(body),
                requiredString(body, "requestId", MAX_CORRELATION_LENGTH),
                requiredString(body, "bindingEpoch", MAX_EPOCH_LENGTH),
                requiredPositiveLong(body, "workspaceEpoch"),
                requiredPositiveInt(body, "homeBankingId"),
                requiredPositiveInt(body, "botJobId"),
                requiredSha256(body, "graphRevision"),
                parseScope(requiredObject(body, "scope")),
                requiredEnum(body, "excelMode", ExcelMode.class),
                requiredEnum(body, "pagePolicy", PagePolicy.class),
                optionalEnum(body, "runtimeMode", RuntimeMode.class, RuntimeMode.JAVA_V1),
                requiredBoolean(body, "durableRuntimeWrites"));
    }

    public static StepRequest parseStep(JsonObject envelopeOrBody) {
        JsonObject body = body(envelopeOrBody);
        return new StepRequest(
                requiredVersion(body),
                requiredString(body, "requestId", MAX_CORRELATION_LENGTH),
                requiredString(body, "runId", MAX_CORRELATION_LENGTH),
                requiredPositiveLong(body, "sequence"),
                requiredPositiveInt(body, "instructionId"),
                requiredNonNegativeInt(body, "excelRowIndex"),
                optionalBoolean(body, "recoveryVerificationEnabled", true));
    }

    public static RecoveryRequest parseRecovery(JsonObject envelopeOrBody) {
        JsonObject body = body(envelopeOrBody);
        RecoveryDecision decision = requiredEnum(body, "decision", RecoveryDecision.class);
        return new RecoveryRequest(
                requiredVersion(body),
                requiredString(body, "requestId", MAX_CORRELATION_LENGTH),
                requiredString(body, "runId", MAX_CORRELATION_LENGTH),
                requiredPositiveLong(body, "sequence"),
                requiredPositiveInt(body, "instructionId"),
                optionalString(body, "recoveryCandidateId"),
                decision);
    }

    public static ExcelWriteRequest parseExcelWrite(JsonObject envelopeOrBody) {
        JsonObject body = body(envelopeOrBody);
        return new ExcelWriteRequest(
                requiredVersion(body),
                requiredString(body, "requestId", MAX_CORRELATION_LENGTH),
                requiredString(body, "runId", MAX_CORRELATION_LENGTH),
                requiredString(body, "outputFile", 1_500),
                requiredString(body, "delimiter", 1),
                stringArray(body, "columns"),
                positiveIntegerArray(body, "instructionIds"),
                requiredString(body, "artifactKind", 4),
                requiredRawString(body, "contentBase64", 12_000_000),
                requiredPositiveInt(body, "byteLength"),
                requiredSha256(body, "sha256"),
                requiredPositiveLong(body, "revision"));
    }

    public static RefreshRequest parseRefresh(JsonObject envelopeOrBody) {
        JsonObject body = body(envelopeOrBody);
        return new RefreshRequest(
                requiredVersion(body),
                requiredString(body, "requestId", MAX_CORRELATION_LENGTH),
                requiredString(body, "bindingEpoch", MAX_EPOCH_LENGTH),
                requiredPositiveLong(body, "workspaceEpoch"),
                requiredPositiveInt(body, "homeBankingId"),
                requiredPositiveInt(body, "botJobId"),
                requiredSha256(body, "graphRevision"));
    }

    public static StopRequest parseStop(JsonObject envelopeOrBody) {
        JsonObject body = body(envelopeOrBody);
        return new StopRequest(
                requiredVersion(body),
                requiredString(body, "requestId", MAX_CORRELATION_LENGTH),
                requiredString(body, "runId", MAX_CORRELATION_LENGTH));
    }

    public static ForceStopRequest parseForceStop(JsonObject envelopeOrBody) {
        JsonObject body = body(envelopeOrBody);
        return new ForceStopRequest(
                requiredVersion(body),
                requiredString(body, "requestId", MAX_CORRELATION_LENGTH),
                requiredString(body, "bindingEpoch", MAX_EPOCH_LENGTH),
                requiredPositiveLong(body, "workspaceEpoch"),
                requiredPositiveInt(body, "homeBankingId"),
                requiredPositiveInt(body, "botJobId"),
                requiredSha256(body, "graphRevision"));
    }

    public static FinishRequest parseFinish(JsonObject envelopeOrBody) {
        JsonObject body = body(envelopeOrBody);
        return new FinishRequest(
                requiredVersion(body),
                requiredString(body, "requestId", MAX_CORRELATION_LENGTH),
                requiredString(body, "runId", MAX_CORRELATION_LENGTH),
                requiredNonNegativeLong(body, "lastSequence"));
    }

    /** Best-effort request correlation used only to attach structured validation errors. */
    public static Correlation correlation(JsonObject envelopeOrBody) {
        try {
            JsonObject body = body(envelopeOrBody);
            return new Correlation(optionalString(body, "requestId"), optionalString(body, "runId"));
        } catch (RuntimeException ignored) {
            return new Correlation("", "");
        }
    }

    private static Scope parseScope(JsonObject source) {
        ScopeKind kind = requiredEnum(source, "kind", ScopeKind.class);
        List<Integer> ids = new ArrayList<>();
        JsonElement blockIds = source.get("blockIds");
        if (blockIds != null && !blockIds.isJsonNull()) {
            if (!blockIds.isJsonArray()) {
                throw invalid("scope.blockIds", "must be an array of positive integers");
            }
            JsonArray values = blockIds.getAsJsonArray();
            for (int index = 0; index < values.size(); index++) {
                ids.add(positiveInt(values.get(index), "scope.blockIds[" + index + "]"));
            }
        }
        return new Scope(kind, ids);
    }

    private static JsonObject body(JsonObject envelopeOrBody) {
        if (envelopeOrBody == null) {
            throw new IllegalArgumentException("Smoke integration request is required");
        }
        if (!envelopeOrBody.has("body")) {
            return envelopeOrBody.deepCopy();
        }
        JsonElement body = envelopeOrBody.get("body");
        if (body == null || body.isJsonNull()) {
            throw new IllegalArgumentException("Smoke integration request body is required");
        }
        if (body.isJsonObject()) {
            return body.getAsJsonObject().deepCopy();
        }
        if (body.isJsonPrimitive() && body.getAsJsonPrimitive().isString()) {
            try {
                JsonElement parsed = JsonParser.parseString(body.getAsString());
                if (parsed.isJsonObject()) {
                    return parsed.getAsJsonObject();
                }
            } catch (RuntimeException invalidJson) {
                throw new IllegalArgumentException(
                        "Smoke integration request body must be valid JSON", invalidJson);
            }
        }
        throw new IllegalArgumentException(
                "Smoke integration request body must be a JSON object");
    }

    private static int requiredVersion(JsonObject body) {
        int version = requiredPositiveInt(body, "contractVersion");
        requireVersion(version);
        return version;
    }

    private static JsonObject requiredObject(JsonObject source, String field) {
        JsonElement value = source.get(field);
        if (value == null || value.isJsonNull() || !value.isJsonObject()) {
            throw invalid(field, "must be a JSON object");
        }
        return value.getAsJsonObject();
    }

    private static String requiredSha256(JsonObject source, String field) {
        return requireSha256(requiredString(source, field, 64), field);
    }

    private static String requireSha256(String value, String field) {
        String parsed = requireBounded(value, field, 64);
        if (!SHA_256.matcher(parsed).matches()) {
            throw invalid(field, "must be a 64-character SHA-256 value");
        }
        return parsed.toLowerCase(Locale.ROOT);
    }

    private static String requiredString(JsonObject source, String field, int maxLength) {
        String parsed = optionalString(source, field);
        if (parsed == null) {
            throw invalid(field, "is required");
        }
        return requireBounded(parsed, field, maxLength);
    }

    private static String requiredRawString(JsonObject source, String field, int maxLength) {
        JsonElement value = required(source, field);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw invalid(field, "must be a string");
        }
        String parsed = value.getAsString();
        if (parsed.isEmpty() || parsed.length() > maxLength) {
            throw invalid(field, "has an invalid length");
        }
        return parsed;
    }

    private static List<String> stringArray(JsonObject source, String field) {
        JsonElement raw = required(source, field);
        if (!raw.isJsonArray()) throw invalid(field, "must be an array");
        List<String> values = new ArrayList<>();
        for (JsonElement value : raw.getAsJsonArray()) {
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
                throw invalid(field, "must contain strings");
            }
            values.add(value.getAsString());
        }
        return values;
    }

    private static List<Integer> positiveIntegerArray(JsonObject source, String field) {
        JsonElement raw = required(source, field);
        if (!raw.isJsonArray()) throw invalid(field, "must be an array");
        List<Integer> values = new ArrayList<>();
        for (JsonElement value : raw.getAsJsonArray()) values.add(positiveInt(value, field));
        return values;
    }

    private static List<String> requireStrings(List<String> supplied, String field, int maxItems, int maxLength) {
        if (supplied == null || supplied.isEmpty() || supplied.size() > maxItems) throw invalid(field, "has an invalid size");
        LinkedHashSet<String> values = new LinkedHashSet<>();
        for (String value : supplied) values.add(requireBounded(value, field, maxLength));
        if (values.size() != supplied.size()) throw invalid(field, "must not contain duplicates");
        return List.copyOf(values);
    }

    private static List<Integer> requirePositiveIds(List<Integer> supplied, String field, int maxItems) {
        if (supplied == null || supplied.isEmpty() || supplied.size() > maxItems) throw invalid(field, "has an invalid size");
        LinkedHashSet<Integer> values = new LinkedHashSet<>();
        for (Integer value : supplied) {
            if (value == null || value <= 0 || !values.add(value)) throw invalid(field, "must contain unique positive integers");
        }
        return List.copyOf(values);
    }

    private static String optionalString(JsonObject source, String field) {
        JsonElement value = source.get(field);
        if (value == null || value.isJsonNull()) {
            return null;
        }
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw invalid(field, "must be a string");
        }
        String parsed = value.getAsString().trim();
        return parsed.isEmpty() ? null : parsed;
    }

    private static boolean requiredBoolean(JsonObject source, String field) {
        JsonElement value = source.get(field);
        if (value == null
                || value.isJsonNull()
                || !value.isJsonPrimitive()
                || !value.getAsJsonPrimitive().isBoolean()) {
            throw invalid(field, "must be a boolean");
        }
        return value.getAsBoolean();
    }

    private static boolean optionalBoolean(JsonObject source, String field, boolean fallback) {
        if (!source.has(field) || source.get(field).isJsonNull()) return fallback;
        return requiredBoolean(source, field);
    }

    private static int requiredPositiveInt(JsonObject source, String field) {
        return positiveInt(required(source, field), field);
    }

    private static int requiredNonNegativeInt(JsonObject source, String field) {
        long parsed = strictLong(required(source, field), field);
        if (parsed < 0 || parsed > Integer.MAX_VALUE) {
            throw invalid(field, "must be a non-negative integer");
        }
        return (int) parsed;
    }

    private static long requiredPositiveLong(JsonObject source, String field) {
        long parsed = strictLong(required(source, field), field);
        if (parsed <= 0) {
            throw invalid(field, "must be a positive integer");
        }
        return parsed;
    }

    private static long requiredNonNegativeLong(JsonObject source, String field) {
        long parsed = strictLong(required(source, field), field);
        if (parsed < 0) {
            throw invalid(field, "must be a non-negative integer");
        }
        return parsed;
    }

    private static int positiveInt(JsonElement value, String field) {
        long parsed = strictLong(value, field);
        if (parsed <= 0 || parsed > Integer.MAX_VALUE) {
            throw invalid(field, "must be a positive integer");
        }
        return (int) parsed;
    }

    private static long strictLong(JsonElement value, String field) {
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw invalid(field, "must be an integer");
        }
        String raw = value.getAsString();
        if (!INTEGER.matcher(raw).matches()) {
            throw invalid(field, "must be an integer");
        }
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException outOfRange) {
            throw invalid(field, "is outside the supported integer range");
        }
    }

    private static JsonElement required(JsonObject source, String field) {
        JsonElement value = source.get(field);
        if (value == null || value.isJsonNull()) {
            throw invalid(field, "is required");
        }
        return value;
    }

    private static <E extends Enum<E>> E requiredEnum(
            JsonObject source,
            String field,
            Class<E> type) {
        String value = requiredString(source, field, 80).toUpperCase(Locale.ROOT);
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException unsupported) {
            throw invalid(field, "contains an unsupported value");
        }
    }

    private static <E extends Enum<E>> E optionalEnum(
            JsonObject source, String field, Class<E> type, E fallback) {
        String value = optionalString(source, field);
        if (value == null) return fallback;
        try {
            return Enum.valueOf(type, value.toUpperCase(Locale.ROOT));
        } catch (RuntimeException invalid) {
            throw invalid(field, "contains an unsupported value");
        }
    }

    private static <E extends Enum<E>> String requireEnumText(
            String value,
            String field,
            Class<E> type) {
        String parsed = requireBounded(value, field, 80).toUpperCase(Locale.ROOT);
        try {
            Enum.valueOf(type, parsed);
            return parsed;
        } catch (IllegalArgumentException unsupported) {
            throw invalid(field, "contains an unsupported value");
        }
    }

    private static void requireVersion(int value) {
        if (value != CONTRACT_VERSION) {
            throw new IllegalArgumentException(
                    "Smoke integration contractVersion must be " + CONTRACT_VERSION);
        }
    }

    private static String requireBounded(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            throw invalid(field, "is required");
        }
        String parsed = value.trim();
        if (parsed.length() > maxLength) {
            throw invalid(field, "is too long");
        }
        return parsed;
    }

    private static String optionalBounded(String value, String field, int maxLength) {
        if (value == null) {
            return "";
        }
        String parsed = value.trim();
        if (parsed.length() > maxLength) {
            throw invalid(field, "is too long");
        }
        return parsed;
    }

    private static void requirePositive(long value, String field) {
        if (value <= 0) {
            throw invalid(field, "must be a positive integer");
        }
    }

    private static void requireNonNegative(long value, String field) {
        if (value < 0) {
            throw invalid(field, "must be a non-negative integer");
        }
    }

    private static IllegalArgumentException invalid(String field, String reason) {
        return new IllegalArgumentException("Smoke integration " + field + " " + reason);
    }

    public record Correlation(String requestId, String runId) {
        public Correlation {
            requestId = requestId == null ? "" : requestId;
            runId = runId == null ? "" : runId;
        }
    }
}
