package com.allinweb.ch.facade.execution;

import com.allinweb.ch.db.ScannedPageIdentity;
import com.allinweb.ch.driver.ARPlaywrightDriver;
import com.allinweb.ch.driver.ARWebDriver;
import com.allinweb.ch.facade.CommandRegistry;
import com.allinweb.ch.facade.PlaywrightRuntimeHealingExecutor.Result;
import com.allinweb.ch.facade.RuntimeElementHealingService;
import com.allinweb.ch.facade.TestIdLocatorContract;
import com.allinweb.ch.facade.RuntimeElementHealingService.Preparation;
import com.allinweb.ch.facade.RuntimeElementHealingService.RegistryCandidate;
import com.allinweb.ch.facade.actions.RuntimeVariableValue;
import com.allinweb.ch.facade.execution.SmokeTestIntegrationSnapshotRepository.InstructionSnapshot;
import com.allinweb.ch.facade.execution.SmokeTestIntegrationSnapshotRepository.Plan;
import com.allinweb.ch.facade.execution.SmokeTestIntegrationStepExecutor.Outcome;
import com.allinweb.ch.facade.execution.SmokeTestIntegrationStepExecutor.RunVariables;
import com.allinweb.ch.model.FieldData;
import com.allinweb.ch.model.InstructionLoad;
import com.allinweb.ch.model.ReferenceLoadDTO;
import com.allinweb.ch.model.SmokeTestIntegrationContracts.StepDisposition;
import com.allinweb.ch.model.SmokeTestIntegrationContracts.StepStatus;
import com.allinweb.ch.socket.ExcelDataWorkspaceService.IntegrationDataset;
import com.allinweb.ch.util.CryptationAlgorithm;
import com.allinweb.ch.util.ExtractedData;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Owner/run/page-bound locator recovery for the shared Java V1 Playwright runtime. */
public final class SmokeTestIntegrationV1RecoveryCoordinator {
    private static final org.slf4j.Logger executionTrace =
            org.slf4j.LoggerFactory.getLogger("com.allinweb.smoke.execution");
    private static final int MAX_REGISTRY_CANDIDATES = 100;
    private static final int MAX_LIVE_CANDIDATES = 100;
    private static final int MAX_RECOVERY_CANDIDATES = 25;
    private static final int MAX_TEXT = 512;
    private static final Gson JSON = new Gson();
    private static final Set<String> RECOVERABLE_CODES =
            Set.of("TARGET_NOT_FOUND", "AMBIGUOUS_TARGET");
    private static final String LIVE_INSPECTION = """
            (action) => {
              const selector = action === 'OUTPUT'
                ? 'input,textarea,select,output,[role],p,span,div,td,th,label'
                : 'a,button,input,textarea,select,option,label,summary,[role],[onclick],[tabindex]';
              const values = [];
              const escapeCss = value => globalThis.CSS?.escape
                ? globalThis.CSS.escape(value)
                : value.replace(/[^a-zA-Z0-9_-]/g, '\\$&');
              for (const element of Array.from(document.querySelectorAll(selector)).slice(0, 100)) {
                const html = element;
                const tagName = (element.tagName || '').toLowerCase();
                const type = String(html.type || element.getAttribute('type') || '').toLowerCase();
                const role = (element.getAttribute('role') || '').toLowerCase();
                const style = window.getComputedStyle(element);
                const rect = element.getBoundingClientRect();
                const visible = style.visibility !== 'hidden' && style.display !== 'none'
                  && Number(style.opacity || 1) !== 0 && rect.width > 0 && rect.height > 0;
                const disabled = Boolean(html.disabled) || element.getAttribute('aria-disabled') === 'true';
                const readonly = Boolean(html.readOnly) || element.getAttribute('aria-readonly') === 'true';
                let actionable = false;
                if (action === 'OUTPUT') actionable = visible;
                if (action === 'INPUT') {
                  actionable = visible && !disabled && !readonly && (tagName === 'textarea'
                    || tagName === 'select' || html.isContentEditable || role === 'textbox'
                    || (tagName === 'input' && !['button','submit','reset','file','checkbox','radio','hidden','image'].includes(type)));
                }
                if (action === 'CLICK') {
                  const clickTag = ['a','button','label','summary','select','option'].includes(tagName)
                    || (tagName === 'input' && type !== 'hidden');
                  const clickRole = ['button','link','menuitem','tab','checkbox','radio','option','switch'].includes(role);
                  actionable = visible && !disabled && (clickTag || clickRole
                    || element.hasAttribute('onclick') || html.tabIndex >= 0);
                }
                const root = element.getRootNode?.();
                if (!actionable || (typeof ShadowRoot !== 'undefined' && root instanceof ShadowRoot)) continue;
                const names = [html.id, element.getAttribute('name'), element.getAttribute('aria-label'),
                  element.getAttribute('data-testid'), element.getAttribute('data-test-id'),
                  element.getAttribute('test-id'), element.getAttribute('data-cy'),
                  element.getAttribute('data-qa'), element.textContent]
                  .filter(value => typeof value === 'string').map(value => value.slice(0, 512));
                const stableAttributes = Object.fromEntries([
                  'id','name','data-testid','data-test-id','test-id','data-cy','data-qa',
                  'aria-label','role','type'
                ].map(name => [name, element.getAttribute(name) || ''])
                  .filter(entry => entry[1].length > 0 && entry[1].length <= 512));
                const parts = [];
                let cursor = element;
                while (cursor && cursor.nodeType === Node.ELEMENT_NODE) {
                  const cursorTag = cursor.tagName.toLowerCase();
                  let index = 1;
                  let sibling = cursor.previousElementSibling;
                  while (sibling) {
                    if (sibling.tagName.toLowerCase() === cursorTag) index += 1;
                    sibling = sibling.previousElementSibling;
                  }
                  parts.unshift(`${cursorTag}[${index}]`);
                  cursor = cursor.parentElement;
                }
                const id = (html.id || '').trim();
                const testId = (element.getAttribute('data-testid') || '').trim();
                const name = (element.getAttribute('name') || '').trim();
                const css = id ? `#${escapeCss(id)}`
                  : testId ? `${tagName}[data-testid="${testId.replace(/["\\]/g, '\\$&')}"]`
                  : name ? `${tagName}[name="${name.replace(/["\\]/g, '\\$&')}"]`
                  : parts.map(part => {
                    const match = /^(.*)\\[(\\d+)\\]$/.exec(part);
                    return match ? `${match[1]}:nth-of-type(${match[2]})` : part;
                  }).join(' > ');
                values.push({ names, tagName, type, role,
                  xpath: parts.length ? `/${parts.join('/')}` : '', css, stableAttributes });
              }
              return values;
            }
            """;

    private final RuntimeElementHealingService healing;
    private final Map<String, PendingRecovery> pendingByRun = new LinkedHashMap<>();

    public SmokeTestIntegrationV1RecoveryCoordinator() {
        this(RuntimeElementHealingService.getInstance());
    }

    SmokeTestIntegrationV1RecoveryCoordinator(RuntimeElementHealingService healing) {
        this.healing = Objects.requireNonNull(healing, "Runtime healing service is required");
    }

    public synchronized Outcome prepare(
            String runId,
            Plan plan,
            IntegrationDataset dataset,
            int instructionId,
            int excelRowIndex,
            RunVariables variables,
            Outcome failure) {
        pendingByRun.remove(runId);
        if (failure == null || !RECOVERABLE_CODES.contains(failure.code())) return failure;
        RecoveryTarget target = target(plan, dataset, instructionId, excelRowIndex, variables);
        if (target == null) return failure;
        ARPlaywrightDriver driver = activeDriver();
        if (driver == null) return failure;
        final String pageKey;
        final Preparation preparation;
        try {
            String currentUrl = driver.currentUrl();
            pageKey = ScannedPageIdentity.fromLiveUrl(currentUrl).pageKey();
            preparation = healing.prepare(
                    target.instruction.owner().homeBankingId(),
                    target.instruction.owner().botJobId(),
                    currentUrl,
                    target.target.toInstructionLoad());
        } catch (RuntimeException unavailable) {
            executionTrace.warn("phase=V1_RECOVERY_PREPARATION_FAILED runId={} instructionId={} failureType={}",
                    runId, instructionId, unavailable.getClass().getSimpleName());
            return failure;
        }
        if (!preparation.ready() || !pageKey.equals(preparation.pageKey())) return failure;
        boolean topDocument = !hasScope(target.target.iframeXpath())
                && !hasScope(target.target.shadowHost())
                && !hasScope(target.target.shadowRoot());
        List<RegistryCandidate> registry = topDocument
                ? registry(preparation).stream()
                        .filter(candidate -> !hasScope(candidate.iframeXpath())
                                && !hasScope(candidate.shadowHost())
                                && !hasScope(candidate.shadowRoot()))
                        .toList()
                : List.of();
        List<RecoveryCandidate> candidates;
        try {
            candidates = registry.size() > MAX_REGISTRY_CANDIDATES
                    ? List.of()
                    : candidates(driver, target, pageKey, registry);
        } catch (RuntimeException inspectionFailure) {
            executionTrace.warn("phase=V1_RECOVERY_INSPECTION_FAILED runId={} instructionId={} failureType={}",
                    runId, instructionId, inspectionFailure.getClass().getSimpleName());
            candidates = List.of();
        }
        Map<String, RecoveryCandidate> indexed = new LinkedHashMap<>();
        candidates.forEach(candidate -> indexed.put(candidate.id, candidate));
        JsonObject failedTarget = unresolvedTarget(
                target.target.toInstructionLoad(), target.action, pageKey, failure.code());
        pendingByRun.put(runId, new PendingRecovery(
                instructionId, pageKey, target, preparation, failedTarget,
                Collections.unmodifiableMap(new LinkedHashMap<>(indexed))));
        JsonObject recovery = new JsonObject();
        recovery.addProperty("state", "AWAITING_USER");
        recovery.add("failedTarget", failedTarget.deepCopy());
        JsonArray rows = new JsonArray();
        candidates.forEach(candidate -> rows.add(candidate.json.deepCopy()));
        recovery.add("candidates", rows);
        executionTrace.warn("phase=V1_RECOVERY_AWAITING_USER runId={} instructionId={} code={} registryCandidates={} reviewCandidates={}",
                runId, instructionId, failure.code(), registry.size(), candidates.size());
        return new Outcome(
                failure.status(), failure.disposition(), failure.code(), failure.message(),
                failure.runtimeVariableId(), failure.runtimeValue(), recovery);
    }

    public synchronized Outcome recover(
            String runId,
            int instructionId,
            String candidateId,
            boolean save,
            RunVariables variables) {
        PendingRecovery pending = pendingByRun.get(runId);
        String action = pending == null ? "CLICK" : pending.target.action;
        String input = pending == null ? "" : pending.target.input;
        return recover(runId, instructionId, candidateId, save, action, input, variables);
    }

    public synchronized Outcome recover(
            String runId,
            int instructionId,
            String candidateId,
            boolean save,
            String requestedAction,
            String requestedInput,
            RunVariables variables) {
        PendingRecovery pending = pendingByRun.get(runId);
        if (pending == null || pending.instructionId != instructionId) {
            return failed("V1_RECOVERY_NOT_PENDING", "This Java V1 locator recovery is no longer pending.");
        }
        RecoveryCandidate candidate = pending.candidates.get(candidateId);
        if (candidate == null) {
            return failed("V1_RECOVERY_CANDIDATE_STALE", "The selected Java V1 locator candidate is stale.");
        }
        ARPlaywrightDriver driver = activeDriver();
        if (driver == null || !samePage(driver, pending.pageKey)) {
            executionTrace.warn("phase=V1_RECOVERY_REFUSED runId={} instructionId={} code=PAGE_CONTEXT_CHANGED", runId, instructionId);
            return failed("PAGE_CONTEXT_CHANGED", "The Playwright page changed before locator recovery.");
        }
        InstructionLoad selected = selectedTarget(pending.target.target, candidate);
        Preparation exactPage = new Preparation(
                RuntimeElementHealingService.Status.READY,
                pending.preparation.homeBankingId(),
                pending.preparation.botJobId(),
                pending.pageKey,
                List.of(), List.of(), List.of(), List.of());
        Result result;
        try {
            String action = normalizedRecoveryAction(requestedAction);
            result = switch (action) {
                case "CLICK" -> driver.runtimeClick(selected, exactPage);
                case "INPUT" -> driver.runtimeInput(
                        selected, new FieldData(selected.displayKey(), Objects.toString(requestedInput, "")), exactPage);
                case "OUTPUT" -> driver.runtimeOutput(selected, exactPage);
                default -> null;
            };
        } catch (RuntimeException actionFailure) {
            executionTrace.warn("phase=V1_RECOVERY_ACTION_FAILED runId={} instructionId={} failureType={}",
                    runId, instructionId, actionFailure.getClass().getSimpleName());
            return failed("V1_RECOVERY_ACTION_FAILED", "The selected Java V1 locator action failed.");
        }
        if (result == null || !result.succeeded()) {
            String diagnostic = result == null ? "RESULT_MISSING" : result.diagnostic().code();
            executionTrace.warn("phase=V1_RECOVERY_REFUSED runId={} instructionId={} code={}",
                    runId, instructionId, diagnostic);
            return failed(diagnostic, "The selected Java V1 locator no longer resolves uniquely.");
        }
        pendingByRun.remove(runId);
        Outcome completed = completedOutput(pending.target, result, variables);
        boolean saved = false;
        if (save) {
            saved = healing.saveApprovedRuntimeLocator(
                    pending.preparation.homeBankingId(),
                    pending.preparation.botJobId(),
                    candidate.previousPageKey,
                    candidate.registryCandidateId,
                    candidate.newXPath);
            if (!saved) {
                executionTrace.warn("phase=V1_RECOVERY_SAVE_FAILED runId={} instructionId={} registryCandidateId={}",
                        runId, instructionId, candidate.registryCandidateId);
                return new Outcome(
                        StepStatus.WARNING,
                        StepDisposition.PHYSICAL,
                        "RECOVERY_ACTION_COMPLETED_SAVE_FAILED",
                        "The selected element action completed, but its locator was not saved.",
                        completed.runtimeVariableId(),
                        completed.runtimeValue());
            }
        }
        executionTrace.info("phase=V1_RECOVERY_COMPLETED runId={} instructionId={} saved={}",
                runId, instructionId, saved);
        return completed;
    }

    private static String normalizedRecoveryAction(String action) {
        String normalized = Objects.toString(action, "").trim().toUpperCase(Locale.ROOT);
        if (!Set.of("CLICK", "INPUT", "OUTPUT").contains(normalized)) {
            throw new IllegalArgumentException("Java V1 recovery action is invalid");
        }
        return normalized;
    }

    /** Retains database-backed rows and refreshes only current Page Scanner evidence. */
    public synchronized JsonObject replaceCandidates(
            String runId, int instructionId, JsonArray refreshedCandidates) {
        PendingRecovery pending = pendingByRun.get(runId);
        if (pending == null || pending.instructionId != instructionId) {
            throw new IllegalStateException("This Java V1 locator recovery is no longer pending.");
        }
        Map<String, RecoveryCandidate> current = candidatesFromJson(refreshedCandidates, "CURRENT");
        Map<String, RecoveryCandidate> indexed = new LinkedHashMap<>();
        pending.candidates.values().stream()
                .filter(candidate -> "PREVIOUS".equals(candidate.json.get("origin").getAsString()))
                .forEach(candidate -> indexed.put(candidate.id, candidate));
        int previousCount = indexed.size();
        current.values().forEach(candidate -> indexed.put(candidate.id, candidate));
        pendingByRun.put(runId, new PendingRecovery(
                pending.instructionId, pending.pageKey, pending.target,
                pending.preparation, pending.failedTarget,
                Collections.unmodifiableMap(new LinkedHashMap<>(indexed))));
        JsonObject recovery = new JsonObject();
        recovery.addProperty("state", "AWAITING_USER");
        recovery.add("failedTarget", pending.failedTarget.deepCopy());
        JsonArray rows = new JsonArray();
        indexed.values().forEach(candidate -> rows.add(candidate.json.deepCopy()));
        recovery.add("candidates", rows);
        executionTrace.info(
                "phase=V1_RECOVERY_CANDIDATES_REFRESHED runId={} instructionId={} previousCandidates={} currentCandidates={} totalCandidates={}",
                runId, instructionId, previousCount, current.size(), indexed.size());
        return recovery;
    }

    public synchronized JsonObject candidate(
            String runId, int instructionId, String candidateId) {
        PendingRecovery pending = pendingByRun.get(runId);
        RecoveryCandidate candidate = pending == null || pending.instructionId != instructionId
                ? null : pending.candidates.get(candidateId);
        if (candidate == null) {
            throw new IllegalStateException("The selected Java V1 locator candidate is stale.");
        }
        return candidate.json.deepCopy();
    }

    public synchronized void cancel(String runId, int instructionId, String reason) {
        PendingRecovery pending = pendingByRun.get(runId);
        if (pending != null && pending.instructionId == instructionId) {
            pendingByRun.remove(runId);
            executionTrace.info("phase=V1_RECOVERY_CLEARED runId={} instructionId={} reason={}",
                    runId, instructionId, safeReason(reason));
        }
    }

    public synchronized void clearRun(String runId, String reason) {
        if (pendingByRun.remove(runId) != null) {
            executionTrace.info("phase=V1_RECOVERY_RUN_CLEARED runId={} reason={}", runId, safeReason(reason));
        }
    }

    private static Outcome completedOutput(
            RecoveryTarget target, Result result, RunVariables variables) {
        if (target.outputVariableId == null) {
            return passed("The selected Java V1 locator completed the instruction.");
        }
        if (!result.found()) {
            return failed("GET_READ_FAILED", "GET could not read the selected Web Element.");
        }
        String value = result.value() == null ? "" : result.value();
        if (!variables.write(target.outputVariableId, value)) {
            return new Outcome(
                    StepStatus.FAILED,
                    StepDisposition.PHYSICAL,
                    "GET_RUNTIME_PERSISTENCE_FAILED",
                    "GET read the page, but its durable runtime value was not saved.",
                    target.outputVariableId,
                    RuntimeVariableValue.value(value));
        }
        return new Outcome(
                StepStatus.PASSED,
                StepDisposition.PHYSICAL,
                "GET_VALUE_WRITTEN",
                "GET updated the run-local variable through the selected locator.",
                target.outputVariableId,
                RuntimeVariableValue.value(value));
    }

    private static RecoveryTarget target(
            Plan plan,
            IntegrationDataset dataset,
            int instructionId,
            int rowIndex,
            RunVariables variables) {
        InstructionSnapshot instruction = plan.instruction(instructionId);
        if (instruction == null) return null;
        String command = CommandRegistry.canonicalize(instruction.action());
        if ("GET".equals(command) || "SET".equals(command)) {
            InstructionSnapshot parent = parent(plan, instruction);
            if (parent == null) return null;
            if ("GET".equals(command)) {
                Integer variableId = instruction.variableId("GET_WRITE");
                return variableId == null ? null : new RecoveryTarget(
                        instruction, parent, "OUTPUT", "", variableId);
            }
            Integer variableId = instruction.variableId("READ_SET");
            RuntimeVariableValue value = variables.read(variableId);
            return variableId == null || value == null || value.isVoid()
                    ? null
                    : new RecoveryTarget(instruction, parent, "INPUT", value.value(), null);
        }
        if (!CommandRegistry.isWebElementAction(command)) return null;
        if ("C".equals(command)) return new RecoveryTarget(instruction, instruction, "CLICK", "", null);
        if ("O".equals(command)) return new RecoveryTarget(instruction, instruction, "OUTPUT", "", null);
        if (!"I".equals(command)) return null;
        String value = inputValue(dataset.data(), instruction, rowIndex);
        return value == null ? null : new RecoveryTarget(instruction, instruction, "INPUT", value, null);
    }

    private static InstructionSnapshot parent(Plan plan, InstructionSnapshot command) {
        if (command.parentId() == null) return null;
        InstructionSnapshot parent = plan.instruction(command.parentId());
        return parent != null
                        && parent.block().id() == command.block().id()
                        && (command.parentBlockId() == null
                                || command.parentBlockId() == parent.block().id())
                        && CommandRegistry.isWebElementAction(parent.action())
                ? parent
                : null;
    }

    private static String inputValue(
            ExtractedData data, InstructionSnapshot instruction, int rowIndex) {
        if (rowIndex < 0 || rowIndex >= data.getNumberOfDataRows()) return null;
        String column = instruction.displayKey();
        if (!data.containsField(instruction.block().name(), column)) {
            column = instruction.name();
        }
        if (column == null || !data.containsField(instruction.block().name(), column)) return null;
        String value = data.getFieldValue(instruction.block().name(), column, rowIndex);
        if (value == null) return null;
        return instruction.codified() ? CryptationAlgorithm.decrypt(value) : value;
    }

    public static JsonObject unresolvedTarget(
            InstructionLoad target, String action, String pageKey, String diagnosticCode) {
        Objects.requireNonNull(target, "Unresolved recovery target is required");
        JsonObject value = new JsonObject();
        value.addProperty("origin", "BOT_JOB");
        value.addProperty("savedCanonicalName", Objects.toString(target.getName(), ""));
        value.addProperty("savedClientName", Objects.toString(target.getClientNamed(), ""));
        value.addProperty("ocrMappedName", "");
        value.addProperty("previousXPath", Objects.toString(target.getXpath(), ""));
        value.addProperty("previousCustomXPath", referenceValue(target, "custom-xpath"));
        value.addProperty("previousCss", Objects.toString(target.getCssSelector(), ""));
        value.add("previousStableAttributes", JSON.toJsonTree(stableAttributes(target)));
        value.addProperty("previousPageIdentity", pageKey);
        value.addProperty("currentPageIdentity", pageKey);
        value.addProperty("tag", Objects.toString(target.getTagName(), ""));
        value.addProperty("type", referenceValue(target, "type"));
        value.addProperty("role", firstNonBlank(
                referenceValue(target, "control.role"), referenceValue(target, "role")));
        value.addProperty("expectedAction", normalizedRecoveryAction(action));
        value.addProperty("diagnosticCode", Objects.toString(diagnosticCode, "TARGET_NOT_FOUND"));
        return value;
    }

    private static Map<String, String> stableAttributes(InstructionLoad target) {
        Map<String, String> raw = new LinkedHashMap<>();
        if (target.getReferenceLoadDTOList() == null) return raw;
        for (var reference : target.getReferenceLoadDTOList()) {
            if (reference == null || reference.getReferenceType() == null
                    || reference.getValue() == null) continue;
            String type = reference.getReferenceType().trim().toLowerCase(Locale.ROOT);
            String name = type.startsWith("attrdata:")
                    ? type.substring("attrdata:".length()) : type;
            if (TestIdLocatorContract.isSafeAttributeName(name)
                    && !reference.getValue().isBlank() && reference.getValue().length() <= MAX_TEXT) {
                raw.putIfAbsent(name, reference.getValue());
            }
        }
        Map<String, String> values = new LinkedHashMap<>();
        Set.of("id", "name", "role", "type", "title", "placeholder", "aria-label")
                .forEach(name -> {
                    if (raw.containsKey(name)) values.put(name, raw.get(name));
                });
        values.putAll(TestIdLocatorContract.testIdValues(raw));
        String configured = raw.get(TestIdLocatorContract.ATTRIBUTE_NAME_METADATA);
        if (TestIdLocatorContract.isSafeAttributeName(configured)) {
            values.put(TestIdLocatorContract.ATTRIBUTE_NAME_METADATA, configured);
        }
        return Map.copyOf(values);
    }

    private static String referenceValue(InstructionLoad target, String expected) {
        if (target.getReferenceLoadDTOList() == null) return "";
        String needle = expected.toLowerCase(Locale.ROOT);
        for (var reference : target.getReferenceLoadDTOList()) {
            if (reference == null || reference.getReferenceType() == null
                    || reference.getValue() == null) continue;
            String type = reference.getReferenceType().trim().toLowerCase(Locale.ROOT);
            if (type.equals(needle) || type.equals("attrdata:" + needle)
                    || ("custom-xpath".equals(needle) && type.contains("custom") && type.contains("xpath"))) {
                return text(reference.getValue(), 2_048);
            }
        }
        return "";
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) return value;
        }
        return "";
    }

    private static List<RegistryCandidate> registry(Preparation preparation) {
        Map<Long, RegistryCandidate> unique = new LinkedHashMap<>();
        for (List<RegistryCandidate> tier : List.of(
                preparation.locatorCandidates(),
                preparation.canonicalCandidates(),
                preparation.aliasCandidates(),
                preparation.reviewCandidates())) {
            tier.forEach(candidate -> unique.putIfAbsent(candidate.scannedElementId(), candidate));
        }
        return List.copyOf(unique.values());
    }

    private static List<RecoveryCandidate> candidates(
            ARPlaywrightDriver driver,
            RecoveryTarget target,
            String pageKey,
            List<RegistryCandidate> registry) {
        if (registry.isEmpty()) return List.of();
        Object raw = driver.evaluate(LIVE_INSPECTION, target.action);
        if (!(raw instanceof List<?> values)) return List.of();
        List<RecoveryCandidate> result = new ArrayList<>();
        for (Object value : values.stream().limit(MAX_LIVE_CANDIDATES).toList()) {
            LiveCandidate live = live(value);
            if (live == null) continue;
            for (RegistryCandidate saved : registry) {
                Score score = score(saved, live, target);
                if (score.confidence < 0.35d) continue;
                JsonObject json = candidateJson(saved, live, pageKey, target.action, score);
                result.add(new RecoveryCandidate(
                        json.get("recoveryCandidateId").getAsString(),
                        saved.scannedElementId(),
                        saved.pageKey().isBlank() ? pageKey : saved.pageKey(),
                        live.xpath,
                        live.css,
                        live.tag,
                        json));
            }
        }
        return result.stream()
                .sorted(Comparator.comparingDouble((RecoveryCandidate value) ->
                                value.json.get("confidence").getAsDouble())
                        .reversed()
                        .thenComparingLong(value -> value.registryCandidateId)
                        .thenComparing(value -> value.id))
                .limit(MAX_RECOVERY_CANDIDATES)
                .toList();
    }

    private static LiveCandidate live(Object raw) {
        if (!(raw instanceof Map<?, ?> values)) return null;
        String xpath = text(values.get("xpath"), 2_048);
        String css = text(values.get("css"), 2_048);
        if (xpath.isBlank() && css.isBlank()) return null;
        List<String> names = new ArrayList<>();
        if (values.get("names") instanceof List<?> rawNames) {
            rawNames.stream().limit(16).map(value -> text(value, MAX_TEXT))
                    .filter(value -> !value.isBlank()).forEach(names::add);
        }
        return new LiveCandidate(
                List.copyOf(names),
                text(values.get("tagName"), 32),
                text(values.get("type"), 80),
                text(values.get("role"), 80),
                xpath,
                css,
                stringMap(values.get("stableAttributes")));
    }

    private static Score score(
            RegistryCandidate saved, LiveCandidate live, RecoveryTarget target) {
        Set<String> expected = new LinkedHashSet<>();
        for (String value : new String[] {
                saved.clientName(),
                saved.canonicalName(),
                saved.ocrName(),
                target.target.clientNamed(),
                target.target.name() }) {
            String normalized = normalize(value);
            if (!normalized.isBlank()) expected.add(normalized);
        }
        Set<String> names = new LinkedHashSet<>();
        live.names.stream().map(SmokeTestIntegrationV1RecoveryCoordinator::normalize)
                .filter(value -> !value.isBlank()).forEach(names::add);
        boolean exact = expected.stream().anyMatch(names::contains);
        boolean partial = expected.stream().anyMatch(left -> names.stream().anyMatch(right ->
                left.length() >= 4 && right.length() >= 4
                        && (left.contains(right) || right.contains(left))));
        boolean tagMatch = saved.tagName().isBlank()
                || normalize(saved.tagName()).equals(normalize(live.tag));
        boolean typeMatch = saved.typeElement().isBlank()
                || normalize(saved.typeElement()).equals(normalize(live.type));
        String expectedRole = saved.attributes().getOrDefault("role", "");
        boolean roleMatch = expectedRole.isBlank()
                || normalize(expectedRole).equals(normalize(live.role));
        Boolean attributesMatch = attributeMatch(saved.attributes(), live.attributes);
        double confidence = 0d;
        List<String> reasons = new ArrayList<>();
        if (exact) { confidence += .55d; reasons.add("Exact saved/OCR name match"); }
        else if (partial) { confidence += .30d; reasons.add("Partial normalized name match"); }
        if (!saved.tagName().isBlank() && tagMatch) { confidence += .15d; reasons.add("Compatible tag"); }
        if (!saved.typeElement().isBlank() && typeMatch) { confidence += .10d; reasons.add("Compatible type"); }
        if (!expectedRole.isBlank() && roleMatch) { confidence += .10d; reasons.add("Compatible role"); }
        if (Boolean.TRUE.equals(attributesMatch)) { confidence += .10d; reasons.add("Stable attribute match"); }
        List<String> warnings = new ArrayList<>();
        if (!exact) warnings.add("Name is not an exact match");
        if (!tagMatch || !typeMatch || !roleMatch) warnings.add("Element semantics changed");
        return new Score(Math.min(1d, Math.round(confidence * 100d) / 100d),
                List.copyOf(reasons), List.copyOf(warnings), attributesMatch);
    }

    private static JsonObject candidateJson(
            RegistryCandidate saved,
            LiveCandidate live,
            String pageKey,
            String action,
            Score score) {
        JsonObject value = new JsonObject();
        String basis = "PREVIOUS\0" + saved.scannedElementId() + "\0" + live.xpath + "\0" + live.css
                + "\0" + JSON.toJson(live.attributes);
        value.addProperty("origin", "PREVIOUS");
        value.addProperty("recoveryCandidateId", sha256(basis));
        value.addProperty("registryCandidateId", saved.scannedElementId());
        value.addProperty("savedCanonicalName", saved.canonicalName());
        value.addProperty("savedClientName", saved.clientName());
        value.addProperty("ocrMappedName", saved.ocrName());
        value.addProperty("previousXPath", saved.xpath());
        value.addProperty("previousCustomXPath", saved.customXPath());
        value.addProperty("previousCss", saved.cssSelector());
        value.add("previousStableAttributes", JSON.toJsonTree(saved.attributes()));
        value.addProperty("newXPath", live.xpath);
        value.addProperty("newCss", live.css);
        value.add("newStableAttributes", JSON.toJsonTree(live.attributes));
        value.addProperty("previousPageIdentity", saved.pageKey().isBlank() ? pageKey : saved.pageKey());
        value.addProperty("currentPageIdentity", pageKey);
        value.addProperty("tag", live.tag);
        value.addProperty("type", live.type);
        value.addProperty("role", live.role);
        value.addProperty("expectedAction", action);
        value.addProperty("confidence", score.confidence);
        value.add("reasons", JSON.toJsonTree(score.reasons));
        value.add("ambiguityWarnings", JSON.toJsonTree(score.warnings));
        JsonObject matches = new JsonObject();
        nullableMatch(matches, "xpath", match(saved.xpath(), live.xpath));
        nullableMatch(matches, "customXPath", match(saved.customXPath(), live.xpath));
        nullableMatch(matches, "css", match(saved.cssSelector(), live.css));
        nullableMatch(matches, "stableAttributes", score.attributesMatch);
        nullableMatch(matches, "frame", saved.iframeXpath().isBlank() ? null : false);
        nullableMatch(matches, "shadow",
                saved.shadowHost().isBlank() && saved.shadowRoot().isBlank() ? null : false);
        value.add("matches", matches);
        return value;
    }

    private static InstructionLoad selectedTarget(
            InstructionSnapshot original, RecoveryCandidate candidate) {
        InstructionLoad selected = original.toInstructionLoad();
        selected.setXpath(candidate.newXPath);
        selected.setCssSelector(candidate.newCss);
        selected.setTagName(candidate.tag);
        selected.setCoordinates("");
        selected.setName("");
        selected.setClientNamed("");
        selected.setIFrameXPath("");
        selected.setShadowHost("");
        selected.setShadowRoot("");
        selected.setReferenceLoadDTOList(recoveryReferences(candidate.json));
        return selected;
    }

    private static List<ReferenceLoadDTO> recoveryReferences(JsonObject candidate) {
        if (candidate == null || !candidate.has("newStableAttributes")
                || !candidate.get("newStableAttributes").isJsonObject()) return List.of();
        List<ReferenceLoadDTO> references = new ArrayList<>();
        candidate.getAsJsonObject("newStableAttributes").entrySet().forEach(entry -> {
            if (!entry.getValue().isJsonPrimitive() || !entry.getValue().getAsJsonPrimitive().isString()) return;
            ReferenceLoadDTO reference = new ReferenceLoadDTO();
            reference.setReferenceType("AttrData:" + entry.getKey());
            reference.setValue(entry.getValue().getAsString());
            references.add(reference);
        });
        return List.copyOf(references);
    }

    private static boolean samePage(ARPlaywrightDriver driver, String expectedPageKey) {
        try {
            return expectedPageKey.equals(
                    ScannedPageIdentity.fromLiveUrl(driver.currentUrl()).pageKey());
        } catch (RuntimeException invalidPage) {
            return false;
        }
    }

    private static ARPlaywrightDriver activeDriver() {
        ARPlaywrightDriver driver = ARWebDriver.getInstance().currentPlaywrightDriver();
        return driver != null && driver.isOpen() ? driver : null;
    }

    private static Map<String, String> stringMap(Object raw) {
        if (!(raw instanceof Map<?, ?> values)) return Map.of();
        Map<String, String> result = new LinkedHashMap<>();
        values.forEach((key, value) -> {
            String name = text(key, 80);
            String content = text(value, MAX_TEXT);
            if (!name.isBlank() && !content.isBlank() && result.size() < 16) {
                result.putIfAbsent(name, content);
            }
        });
        return Map.copyOf(result);
    }

    private static Boolean attributeMatch(
            Map<String, String> previous, Map<String, String> current) {
        if (previous == null || previous.isEmpty()) return null;
        return previous.entrySet().stream().anyMatch(entry ->
                entry.getValue().equals(current.get(entry.getKey())));
    }

    private static Map<String, RecoveryCandidate> candidatesFromJson(JsonArray values, String expectedOrigin) {
        Map<String, RecoveryCandidate> result = new LinkedHashMap<>();
        if (values == null || values.size() > MAX_RECOVERY_CANDIDATES) return result;
        for (var value : values) {
            if (!value.isJsonObject()) continue;
            JsonObject json = value.getAsJsonObject().deepCopy();
            try {
                String id = json.get("recoveryCandidateId").getAsString();
                long registryId = json.get("registryCandidateId").getAsLong();
                String previousPage = json.get("previousPageIdentity").getAsString();
                String xpath = json.get("newXPath").getAsString();
                String css = json.get("newCss").getAsString();
                String tag = json.get("tag").getAsString();
                String origin = json.has("origin") ? json.get("origin").getAsString() : "";
                if (!id.matches("[0-9a-f]{64}") || registryId <= 0
                        || !expectedOrigin.equals(origin)) continue;
                result.putIfAbsent(id, new RecoveryCandidate(
                        id, registryId, previousPage, xpath, css, tag, json));
            } catch (RuntimeException invalid) {
                // A malformed refreshed row is never admitted into pending recovery authority.
            }
        }
        return result;
    }

    private static Boolean match(String previous, String current) {
        return previous == null || previous.isBlank() ? null : previous.trim().equals(current.trim());
    }

    private static void nullableMatch(JsonObject target, String name, Boolean value) {
        if (value == null) target.add(name, com.google.gson.JsonNull.INSTANCE);
        else target.addProperty(name, value);
    }

    private static String text(Object value, int maximum) {
        if (!(value instanceof String text)) return "";
        String trimmed = text.trim();
        return trimmed.length() <= maximum ? trimmed : trimmed.substring(0, maximum);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private static boolean hasScope(String value) {
        if (value == null || value.isBlank()) return false;
        return !Set.of("false", "null", "none", "0")
                .contains(value.trim().toLowerCase(Locale.ROOT));
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static String safeReason(String reason) {
        String value = reason == null ? "UNKNOWN" : reason.trim().toUpperCase(Locale.ROOT);
        return value.matches("[A-Z][A-Z0-9_-]{1,40}") ? value : "UNKNOWN";
    }

    private static Outcome passed(String message) {
        return new Outcome(
                StepStatus.PASSED, StepDisposition.PHYSICAL, "STEP_COMPLETED", message, null, null);
    }

    private static Outcome failed(String code, String message) {
        return new Outcome(
                StepStatus.FAILED, StepDisposition.PHYSICAL, code, message, null, null);
    }

    private record RecoveryTarget(
            InstructionSnapshot instruction,
            InstructionSnapshot target,
            String action,
            String input,
            Integer outputVariableId) {}

    private record PendingRecovery(
            int instructionId,
            String pageKey,
            RecoveryTarget target,
            Preparation preparation,
            JsonObject failedTarget,
            Map<String, RecoveryCandidate> candidates) {}

    private record RecoveryCandidate(
            String id,
            long registryCandidateId,
            String previousPageKey,
            String newXPath,
            String newCss,
            String tag,
            JsonObject json) {}

    private record LiveCandidate(
            List<String> names,
            String tag,
            String type,
            String role,
            String xpath,
            String css,
            Map<String, String> attributes) {}

    private record Score(
            double confidence,
            List<String> reasons,
            List<String> warnings,
            Boolean attributesMatch) {}
}
