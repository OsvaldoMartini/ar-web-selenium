package com.allinweb.ch.socket;

import com.allinweb.ch.facade.BotJobDetailsWorkspaceRegistry;
import com.allinweb.ch.facade.BotJobGraphMutationService;
import com.allinweb.ch.facade.BotJobGraphMutationTransaction.CommitResult;
import com.allinweb.ch.facade.BotJobGraphMutationTransaction.GraphSnapshot;
import com.allinweb.ch.facade.BotJobGraphMutationTransaction.GraphVariableFact;
import com.allinweb.ch.facade.BotJobGraphMutationTransaction.MutationRefusedException;
import com.allinweb.ch.facade.ScannerBotJobTasksPublisher;
import com.allinweb.ch.facade.VariableRelationshipService;
import com.allinweb.ch.facade.VariablesCrossBlockInstructionMutationProfile;
import com.allinweb.ch.facade.VariablesInstructionCopyService;
import com.allinweb.ch.facade.VariablesInstructionCopyTransaction.CopyResult;
import com.allinweb.ch.facade.VariablesInstructionMutationProfile;
import com.allinweb.ch.facade.VariablesReactAuthoredMutationProfile;
import com.allinweb.ch.facade.VariablesVariableDeleteService;
import com.allinweb.ch.facade.VariablesVariableDeleteTransaction.DeleteResult;
import com.allinweb.ch.facade.PerformDataBase;
import com.allinweb.ch.facade.actions.RuntimeVariableMemoryRegistry;
import com.allinweb.ch.facade.actions.RuntimeVariableMemoryRegistry.BotJobKey;
import com.allinweb.ch.facade.actions.RuntimeVariableMemoryRegistry.Definition;
import com.allinweb.ch.facade.actions.RuntimeVariableMemoryRegistry.Snapshot;
import com.allinweb.ch.facade.actions.RuntimeVariableMemoryRegistry.ValueSource;
import com.allinweb.ch.facade.actions.RuntimeVariableValue.VoidReason;
import com.allinweb.ch.facade.variables.runtime.BotJobRuntimeVariableModels.DefinitionDraft;
import com.allinweb.ch.facade.variables.runtime.BotJobRuntimeVariableModels.MutationResult;
import com.allinweb.ch.facade.variables.runtime.BotJobRuntimeVariableModels.OwnerKey;
import com.allinweb.ch.facade.variables.runtime.BotJobRuntimeVariableModels.ValueState;
import com.allinweb.ch.facade.variables.runtime.BotJobRuntimeVariableService;
import com.allinweb.ch.model.DetachedWorkspaceSessions;
import com.allinweb.ch.model.InstructionGraphMutationV3;
import com.allinweb.ch.model.VariablesInstructionCopyV1;
import com.allinweb.ch.model.VariablesWorkspaceVariableDelete;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.websocket.Session;
import lombok.extern.slf4j.Slf4j;

/**
 * Owns the one detached Variables page and binds it to one exact active Bot Job workspace epoch.
 *
 * <p>The binding is backend-owned. Browser query parameters and request identity fields are never
 * accepted as authority. A failed refresh emits a structured preserve-snapshot error so the React
 * page can keep rendering its last known-good graph.
 */
@Slf4j
public final class VariablesWorkspaceService {
    public static final String WORKSPACE_SESSION_ID =
            DetachedWorkspaceSessions.VARIABLES_MANAGER;
    public static final String SNAPSHOT_OPERATION = "variablesWorkspace.snapshot";
    public static final String RUNTIME_MEMORY_SNAPSHOT_OPERATION =
            "variablesWorkspace.runtimeMemory.snapshot";
    private static final long RELOAD_GRACE_MILLIS = 2_500L;
    private static final long RUNTIME_MEMORY_PUBLICATION_DELAY_MILLIS = 25L;
    private static final int MAX_RUNTIME_VALUE_CHARACTERS = 1_000_000;
    private static final RuntimeVariableMemoryRegistry RUNTIME_MEMORY =
            RuntimeVariableMemoryRegistry.getInstance();

    private static final VariablesWorkspaceService INSTANCE =
            new VariablesWorkspaceService(
                     VariablesWorkspaceService::activeWorkspace,
                     VariableRelationshipService.getInstance()::load,
                     new DefaultWindowPort(),
                     new Gson(),
                     DefaultTaskPort.INSTANCE,
                     DefaultMutationPort.INSTANCE,
                     DefaultVariableDeletePort.INSTANCE,
                     DefaultInstructionCopyPort.INSTANCE,
                     DurableRuntimeMemoryPort.INSTANCE);

    static {
        RUNTIME_MEMORY.addChangeListener(
                INSTANCE::notifyRuntimeMemoryChanged);
    }

    private final WorkspacePort workspaces;
    private final GraphPort graphs;
    private final WindowPort windows;
    private final Gson gson;
    private final TaskPort tasks;
    private final MutationPort mutations;
    private final VariableDeletePort variableDeletes;
    private final InstructionCopyPort instructionCopies;
    private final RuntimeMemoryPort runtimeMemory;
    private final Object stateLock = new Object();
    private final Map<BotJobKey, Long> pendingRuntimeMemoryRevisions =
            new ConcurrentHashMap<>();
    private final Set<BotJobKey> scheduledRuntimeMemoryOwners =
            ConcurrentHashMap.newKeySet();
    private Binding binding;
    private Session managerTransport;
    private long disconnectGeneration;

    VariablesWorkspaceService(
             WorkspacePort workspaces, GraphPort graphs, WindowPort windows, Gson gson) {
        this(
                workspaces,
                graphs,
                windows,
                gson,
                DefaultTaskPort.INSTANCE,
                UnavailableMutationPort.INSTANCE,
                UnavailableVariableDeletePort.INSTANCE,
                UnavailableInstructionCopyPort.INSTANCE);
    }

    VariablesWorkspaceService(
            WorkspacePort workspaces,
            GraphPort graphs,
            WindowPort windows,
            Gson gson,
            TaskPort tasks) {
        this(
                workspaces,
                graphs,
                windows,
                gson,
                tasks,
                UnavailableMutationPort.INSTANCE,
                UnavailableVariableDeletePort.INSTANCE,
                UnavailableInstructionCopyPort.INSTANCE);
    }

    VariablesWorkspaceService(
            WorkspacePort workspaces,
            GraphPort graphs,
            WindowPort windows,
            Gson gson,
            TaskPort tasks,
            MutationPort mutations) {
        this(
                workspaces,
                graphs,
                windows,
                gson,
                tasks,
                mutations,
                UnavailableVariableDeletePort.INSTANCE,
                UnavailableInstructionCopyPort.INSTANCE);
    }

    VariablesWorkspaceService(
            WorkspacePort workspaces,
            GraphPort graphs,
            WindowPort windows,
            Gson gson,
            TaskPort tasks,
            MutationPort mutations,
            VariableDeletePort variableDeletes) {
        this(
                workspaces,
                graphs,
                windows,
                gson,
                tasks,
                mutations,
                variableDeletes,
                UnavailableInstructionCopyPort.INSTANCE);
    }

    VariablesWorkspaceService(
            WorkspacePort workspaces,
            GraphPort graphs,
            WindowPort windows,
            Gson gson,
            TaskPort tasks,
            MutationPort mutations,
            VariableDeletePort variableDeletes,
            InstructionCopyPort instructionCopies) {
        this(
                workspaces,
                graphs,
                windows,
                gson,
                tasks,
                mutations,
                variableDeletes,
                instructionCopies,
                LegacyRuntimeMemoryPort.INSTANCE);
    }

    VariablesWorkspaceService(
            WorkspacePort workspaces,
            GraphPort graphs,
            WindowPort windows,
            Gson gson,
            TaskPort tasks,
            MutationPort mutations,
            VariableDeletePort variableDeletes,
            InstructionCopyPort instructionCopies,
            RuntimeMemoryPort runtimeMemory) {
        this.workspaces = workspaces;
        this.graphs = graphs;
        this.windows = windows;
        this.gson = gson;
        this.tasks = tasks;
        this.mutations = mutations;
        this.variableDeletes = variableDeletes;
        this.instructionCopies = instructionCopies;
        this.runtimeMemory = runtimeMemory;
    }

    public static VariablesWorkspaceService getInstance() {
        return INSTANCE;
    }

    /** True only while the singleton Variables page is open for this exact Bot Job. */
    public boolean isOpenForBotJob(int botJobId) {
        Binding current = currentBinding();
        return botJobId > 0
                && current != null
                && current.botJobId() == botJobId
                && windows.isOpen(WORKSPACE_SESSION_ID);
    }

    /** Opens or retargets the single Variables page for an already-authorized Bot Job action. */
    public JsonObject openForBotJob(int botJobId) {
        JsonObject request = new JsonObject();
        request.addProperty("botJobId", botJobId);
        try {
            // Registry work is deliberately outside the Variables state monitor.
            WorkspaceContext workspace = workspaces.require(botJobId, 0L);
            Binding next = new Binding(
                    UUID.randomUUID().toString(),
                    workspace.workspaceEpoch(),
                    workspace.botJobId(),
                    workspace.homeBankingId(),
                    workspace.botJobName(),
                    workspace.organizationName(),
                    "");
            Binding previous;
            synchronized (stateLock) {
                previous = binding;
                binding = next;
                disconnectGeneration++;
            }

            boolean alreadyOpen = windows.isOpen(WORKSPACE_SESSION_ID);
            boolean opened;
            try {
                opened = windows.openOrFocus(
                        WORKSPACE_SESSION_ID,
                        workspace.botJobId(),
                        "Bot Job Details requested the Variables workspace.");
            } catch (RuntimeException error) {
                restoreIfCurrent(next, previous);
                throw error;
            }
            if (!opened) {
                restoreIfCurrent(next, previous);
                return failure(
                        request,
                        "The Variables workspace could not be opened.",
                        currentBinding());
            }

            Binding current = currentBindingFor(next.botJobId(), next.workspaceEpoch());
            if (current == null) {
                return failure(
                        request,
                        "The Variables workspace target changed before it could be opened.",
                        next);
            }
            JsonObject response = success(
                    request,
                    current,
                    alreadyOpen
                            ? "Variables workspace retargeted and brought to front."
                            : "Variables workspace opened.");
            response.addProperty("alreadyOpen", alreadyOpen);
            response.addProperty(
                    "retargeted",
                    alreadyOpen
                            && (previous == null
                                    || previous.botJobId() != current.botJobId()
                                    || previous.workspaceEpoch() != current.workspaceEpoch()));

            if (alreadyOpen) {
                JsonObject snapshot =
                        loadSnapshot(current, null, "Variables workspace synchronized.");
                if (isCurrent(current)) {
                    boolean delivered = publish(snapshot, current);
                    if (delivered && isSuccessful(snapshot)) {
                        advanceDeliveredRevision(
                                current, text(snapshot, "graphRevision"));
                    }
                }
            }
            return response;
        } catch (IllegalArgumentException | IllegalStateException error) {
            return failure(request, error.getMessage(), currentBinding());
        } catch (RuntimeException error) {
            log.error("Unable to open the Variables workspace", error);
            return failure(
                    request,
                    "The Variables workspace could not be opened.",
                    currentBinding());
        }
    }

    /** Loads the page from the exact registered Variables transport. */
    public JsonObject bootstrap(
            JsonObject body, String requesterSessionId, Session requesterTransport) {
        return loadForManager(
                body,
                requesterSessionId,
                requesterTransport,
                "Variables workspace loaded.");
    }

    /** Explicitly refreshes the graph while preserving the prior UI snapshot on failure. */
    public JsonObject refresh(
            JsonObject body, String requesterSessionId, Session requesterTransport) {
        return loadForManager(
                body,
                requesterSessionId,
                requesterTransport,
                "Variable relationships refreshed.");
    }

    /**
     * Persists one exact runtime value from the authoritative Variables transport.
     *
     * <p>The database transaction is the authority. Empty text remains {@code VALUE("")}; only an
     * explicit CLEAR request produces VOID. Both the Bot Job-wide revision and the entry revision
     * are checked by the same transaction before the committed snapshot is returned.
     */
    public JsonObject updateRuntimeMemory(
            JsonObject body, String requesterSessionId, Session requesterTransport) {
        JsonObject request = body == null ? new JsonObject() : body;
        Binding current = null;
        try {
            current = authorizeRuntimeRequest(
                    request, requesterSessionId, requesterTransport);
            requireContractVersion(request);
            long variableId = positiveLong(request, "variableId");
            long baseRuntimeRevision = nonNegativeLong(
                    request, "baseRuntimeRevision", true);
            long expectedEntryRevision = nonNegativeLong(
                    request, "expectedEntryRevision", true);

            String operation = text(request, "operation")
                    .trim()
                    .toUpperCase(Locale.ROOT);
            if (operation.isBlank()) operation = "SET";
            String replacementValue = null;
            if ("SET".equals(operation)) {
                if (!request.has("value") || request.get("value").isJsonNull()) {
                    throw new IllegalArgumentException(
                            "SET requires an explicit runtime variable value.");
                }
                try {
                    replacementValue = request.get("value").getAsString();
                } catch (RuntimeException invalidValue) {
                    throw new IllegalArgumentException(
                            "The runtime variable value is invalid.");
                }
                if (replacementValue.length() > MAX_RUNTIME_VALUE_CHARACTERS) {
                    throw new IllegalArgumentException(
                            "The runtime variable value is too large.");
                }
            } else if (!"CLEAR".equals(operation)) {
                throw new IllegalArgumentException(
                        "Runtime memory supports only SET or CLEAR.");
            }

            final Binding authorized = current;
            final String exactReplacementValue = replacementValue;
            final String authorizedOperation = operation;
            RuntimeMemoryMutation committed = commitAuthorizedRuntimeMutation(
                    authorized,
                    requesterTransport,
                    () -> "SET".equals(authorizedOperation)
                            ? runtimeMemory.setValue(
                                    authorized,
                                    variableId,
                                    exactReplacementValue,
                                    baseRuntimeRevision,
                                    expectedEntryRevision)
                            : runtimeMemory.clearValue(
                                    authorized,
                                    variableId,
                                    baseRuntimeRevision,
                                    expectedEntryRevision));
            return runtimeMutationResponse(request, current, committed);
        } catch (IllegalArgumentException | IllegalStateException refused) {
            return failure(
                    request,
                    refused.getMessage(),
                    current == null ? currentBinding() : current);
        } catch (RuntimeException failure) {
            log.error("Unable to update Variables runtime memory", failure);
            return failure(
                    request,
                    "The runtime variable value could not be updated.",
                    current == null ? currentBinding() : current);
        }
    }

    /** Creates one independent Bot Job variable. It has no producer and defaults to VOID. */
    public JsonObject createVariable(
            JsonObject body, String requesterSessionId, Session requesterTransport) {
        JsonObject request = body == null ? new JsonObject() : body;
        Binding current = null;
        try {
            current = authorizeRuntimeRequest(
                    request, requesterSessionId, requesterTransport);
            requireContractVersion(request);
            String name = text(request, "name").trim();
            if (name.isBlank()) {
                throw new IllegalArgumentException("Variable name is required.");
            }
            String requestedState = text(request, "initialState").trim();
            if (!requestedState.isBlank()
                    && !ValueState.VOID.name().equalsIgnoreCase(requestedState)) {
                throw new IllegalArgumentException(
                        "New independent variables must begin as VOID.");
            }

            final Binding authorized = current;
            RuntimeMemoryMutation committed = commitAuthorizedRuntimeMutation(
                    authorized,
                    requesterTransport,
                    () -> runtimeMemory.create(
                            authorized, name, ValueState.VOID, null));
            JsonObject response = runtimeMutationResponse(
                    request, current, committed);
            if (committed.variableId() != null) {
                response.addProperty("variableId", committed.variableId());
                response.addProperty("createdVariableId", committed.variableId());
            }
            if (committed.applied()) {
                notifyMutation(current.botJobId());
            }
            return response;
        } catch (IllegalArgumentException | IllegalStateException refused) {
            return failure(
                    request,
                    refused.getMessage(),
                    current == null ? currentBinding() : current);
        } catch (RuntimeException failure) {
            log.error("Unable to create Variables definition", failure);
            return failure(
                    request,
                    "The variable definition could not be created.",
                    current == null ? currentBinding() : current);
        }
    }

    /** Atomically resets every current value to VOID without deleting definitions or links. */
    public JsonObject clearAllRuntimeMemory(
            JsonObject body, String requesterSessionId, Session requesterTransport) {
        JsonObject request = body == null ? new JsonObject() : body;
        Binding current = null;
        try {
            current = authorizeRuntimeRequest(
                    request, requesterSessionId, requesterTransport);
            requireContractVersion(request);
            long baseRuntimeRevision = nonNegativeLong(
                    request, "baseRuntimeRevision", true);
            final Binding authorized = current;
            RuntimeMemoryMutation committed = commitAuthorizedRuntimeMutation(
                    authorized,
                    requesterTransport,
                    () -> runtimeMemory.clearAll(
                            authorized, baseRuntimeRevision));
            return runtimeMutationResponse(request, current, committed);
        } catch (IllegalArgumentException | IllegalStateException refused) {
            return failure(
                    request,
                    refused.getMessage(),
                    current == null ? currentBinding() : current);
        } catch (RuntimeException failure) {
            log.error("Unable to clear Variables runtime memory", failure);
            return failure(
                    request,
                    "Runtime values could not be cleared.",
                    current == null ? currentBinding() : current);
        }
    }

    /**
     * Persists one complete React-planned version-3 graph mutation.
     *
     * <p>The detached binding supplies the authoritative owner. Java does not expand the dragged
     * row or choose a relationship target; it validates stale expected facts and commits exactly
     * the submitted complete layout and patches.
     */
    public JsonObject mutate(
            JsonObject body, String requesterSessionId, Session requesterTransport) {
        JsonObject request = body == null ? new JsonObject() : body;
        Binding current = null;
        try {
            requireManagerTransport(requesterSessionId, requesterTransport);
            current = currentBinding();
            if (current == null) {
                throw new IllegalArgumentException(
                        "No Bot Job is bound to the Variables workspace.");
            }
            String requestedBindingEpoch = text(request, "bindingEpoch");
            if (requestedBindingEpoch.isBlank()
                    || !requestedBindingEpoch.equals(current.bindingEpoch())) {
                throw new IllegalArgumentException(
                        "The Variables target changed. Reload the current Bot Job.");
            }

            WorkspaceContext workspace =
                    workspaces.require(current.botJobId(), current.workspaceEpoch());
            current = current.withWorkspace(workspace);
            InstructionGraphMutationV3.Request mutationRequest;
            try {
                mutationRequest = gson.fromJson(
                        request, InstructionGraphMutationV3.Request.class);
            } catch (RuntimeException malformed) {
                throw new IllegalArgumentException(
                        "The Variables graph mutation request is malformed.");
            }
            if (mutationRequest == null) {
                throw new IllegalArgumentException(
                        "A Variables graph mutation request is required.");
            }

            Binding authorized = current;
            String mutationProfile =
                    normalizeMutationProfile(text(request, "mutationProfile"));
            CommitResult committed =
                    BotJobDetailsWorkspaceRegistry.getInstance().commitWorkspaceMutation(
                            authorized.botJobId(),
                            authorized.workspaceEpoch(),
                            () -> persistMutation(
                                    authorized, mutationRequest, mutationProfile));
            JsonObject response = mutationSuccess(request, authorized, committed);
            if (!isCurrent(authorized) || !isManagerTransport(requesterTransport)) {
                response.addProperty("resyncRequired", true);
                response.addProperty(
                        "message",
                        "Instruction order saved, but the Variables target changed. "
                                + "Refreshing authoritative workspaces.");
            }
            return response;
        } catch (MutationPersistenceException persistenceFailure) {
            Throwable cause = persistenceFailure.getCause();
            if (cause instanceof MutationRefusedException refused) {
                return mutationFailure(
                        request, refused.code(), refused.getMessage(), current);
            }
            log.error("Unable to persist a Variables graph mutation", cause);
            return mutationFailure(
                    request,
                    "PERSISTENCE_FAILED",
                    "The Variables graph change was not saved.",
                    current);
        } catch (IllegalArgumentException | IllegalStateException invalidRequest) {
            return mutationFailure(
                    request,
                    "REQUEST_REFUSED",
                    invalidRequest.getMessage(),
                    current == null ? currentBinding() : current);
        } catch (RuntimeException failure) {
            log.error("Unable to process a Variables graph mutation", failure);
            return mutationFailure(
                    request,
                    "MUTATION_FAILED",
                    "The Variables graph change was not completed.",
                    current == null ? currentBinding() : current);
        }
    }

    /**
     * Copies exactly the React-submitted instruction IDs to one current Bot Job block.
     *
     * <p>The request cannot choose its owner. The active detached Variables binding supplies that
     * identity, and the database transaction creates fresh IDs without changing source rows.
     */
    public JsonObject copyInstructions(
            JsonObject body, String requesterSessionId, Session requesterTransport) {
        JsonObject request = body == null ? new JsonObject() : body;
        Binding current = null;
        try {
            requireManagerTransport(requesterSessionId, requesterTransport);
            current = currentBinding();
            if (current == null) {
                throw new IllegalArgumentException(
                        "No Bot Job is bound to the Variables workspace.");
            }
            String requestedBindingEpoch = text(request, "bindingEpoch");
            if (requestedBindingEpoch.isBlank()
                    || !requestedBindingEpoch.equals(current.bindingEpoch())) {
                throw new IllegalArgumentException(
                        "The Variables target changed. Reload the current Bot Job.");
            }

            WorkspaceContext workspace =
                    workspaces.require(current.botJobId(), current.workspaceEpoch());
            current = current.withWorkspace(workspace);
            VariablesInstructionCopyV1.Request copyRequest;
            try {
                copyRequest =
                        gson.fromJson(request, VariablesInstructionCopyV1.Request.class);
            } catch (RuntimeException malformed) {
                throw new IllegalArgumentException(
                        "The Variables instruction-copy request is malformed.");
            }
            if (copyRequest == null) {
                throw new IllegalArgumentException(
                        "A Variables instruction-copy request is required.");
            }

            Binding authorized = current;
            CopyResult committed =
                    BotJobDetailsWorkspaceRegistry.getInstance().commitWorkspaceMutation(
                            authorized.botJobId(),
                            authorized.workspaceEpoch(),
                            () -> persistInstructionCopy(authorized, copyRequest));
            JsonObject response =
                    instructionCopySuccess(request, authorized, committed);
            if (!isCurrent(authorized)
                    || !isManagerTransport(requesterTransport)) {
                response.addProperty("resyncRequired", true);
                response.addProperty(
                        "message",
                        "Instructions copied, but the workspace target changed. "
                                + "Refreshing authoritative workspaces.");
            }
            return response;
        } catch (InstructionCopyPersistenceException persistenceFailure) {
            Throwable cause = persistenceFailure.getCause();
            if (cause instanceof MutationRefusedException refused) {
                return instructionCopyFailure(
                        request, refused.code(), refused.getMessage(), current);
            }
            log.error("Unable to persist Variables instruction copy", cause);
            return instructionCopyFailure(
                    request,
                    "VARIABLE_COPY_PERSISTENCE_FAILED",
                    "The selected instructions were not copied.",
                    current);
        } catch (IllegalArgumentException | IllegalStateException refused) {
            return instructionCopyFailure(
                    request,
                    "VARIABLE_COPY_REQUEST_REFUSED",
                    refused.getMessage(),
                    current == null ? currentBinding() : current);
        } catch (RuntimeException failure) {
            log.error("Unable to process Variables instruction copy", failure);
            return instructionCopyFailure(
                    request,
                    "VARIABLE_COPY_FAILED",
                    "Instruction copy was not completed.",
                    current == null ? currentBinding() : current);
        }
    }

    /**
     * Deletes exactly the variable IDs selected by React from the authoritative Variables page.
     *
     * <p>The request cannot choose its Bot Job owner. The active backend binding and registered
     * singleton transport supply that identity, while the database transaction verifies graph
     * version/revision and clears only matching instruction variable bindings.
     */
    public JsonObject deleteVariables(
            JsonObject body, String requesterSessionId, Session requesterTransport) {
        JsonObject request = body == null ? new JsonObject() : body;
        Binding current = null;
        try {
            requireManagerTransport(requesterSessionId, requesterTransport);
            current = currentBinding();
            if (current == null) {
                throw new IllegalArgumentException(
                        "No Bot Job is bound to the Variables workspace.");
            }
            String requestedBindingEpoch = text(request, "bindingEpoch");
            if (requestedBindingEpoch.isBlank()
                    || !requestedBindingEpoch.equals(current.bindingEpoch())) {
                throw new IllegalArgumentException(
                        "The Variables target changed. Reload the current Bot Job.");
            }

            WorkspaceContext workspace =
                    workspaces.require(current.botJobId(), current.workspaceEpoch());
            current = current.withWorkspace(workspace);
            VariablesWorkspaceVariableDelete.Request deleteRequest;
            try {
                deleteRequest = gson.fromJson(
                        request, VariablesWorkspaceVariableDelete.Request.class);
            } catch (RuntimeException malformed) {
                throw new IllegalArgumentException(
                        "The Variables deletion request is malformed.");
            }
            if (deleteRequest == null) {
                throw new IllegalArgumentException(
                        "A Variables deletion request is required.");
            }

            Binding authorized = current;
            DeleteResult committed =
                    BotJobDetailsWorkspaceRegistry.getInstance().commitWorkspaceMutation(
                            authorized.botJobId(),
                            authorized.workspaceEpoch(),
                            () -> persistVariableDeletion(authorized, deleteRequest));
            JsonObject response =
                    variableDeleteSuccess(request, authorized, committed);
            if (!isCurrent(authorized)
                    || !isManagerTransport(requesterTransport)) {
                response.addProperty("resyncRequired", true);
                response.addProperty(
                        "message",
                        "Variables deleted, but the workspace target changed. "
                                + "Refreshing authoritative workspaces.");
            }
            return response;
        } catch (VariableDeletePersistenceException persistenceFailure) {
            Throwable cause = persistenceFailure.getCause();
            if (cause instanceof MutationRefusedException refused) {
                return variableDeleteFailure(
                        request, refused.code(), refused.getMessage(), current);
            }
            log.error("Unable to persist Variables deletion", cause);
            return variableDeleteFailure(
                    request,
                    "VARIABLE_DELETE_PERSISTENCE_FAILED",
                    "The selected variables were not deleted.",
                    current);
        } catch (IllegalArgumentException | IllegalStateException refused) {
            return variableDeleteFailure(
                    request,
                    "VARIABLE_DELETE_REQUEST_REFUSED",
                    refused.getMessage(),
                    current == null ? currentBinding() : current);
        } catch (RuntimeException failure) {
            log.error("Unable to process Variables deletion", failure);
            return variableDeleteFailure(
                    request,
                    "VARIABLE_DELETE_FAILED",
                    "Variables deletion was not completed.",
                    current == null ? currentBinding() : current);
        }
    }

    /**
     * Publishes authoritative views after the correlated acknowledgement attempt.
     *
     * <p>A closed requester cannot undo the commit or suppress synchronization to surviving
     * workspaces.
     */
    public void publishCommittedMutation(JsonObject response) {
        if (response == null
                || !response.has("ok")
                || !response.get("ok").getAsBoolean()
                || !response.has("committed")
                || !response.get("committed").getAsBoolean()
                || !response.has("botJobId")
                || !response.has("homeBankingId")) {
            return;
        }
        int botJobId = response.get("botJobId").getAsInt();
        int homeBankingId = response.get("homeBankingId").getAsInt();
        var gridError =
                ScannerBotJobTasksPublisher.getInstance()
                        .publishGridOnly(homeBankingId, botJobId);
        if (gridError != null) {
            log.warn(
                    "Variables mutation committed for Bot Job {}, but Bot Job Details refresh "
                    + "failed: {}",
                    botJobId,
                    gridError.getErrorMessage());
        }
        try {
            JsonObject graph = graphs.load(botJobId);
            if (isSuccessful(graph)) {
                reconcileRuntimeMemory(
                        graph, new BotJobKey(homeBankingId, botJobId));
            } else {
                log.warn(
                        "Variables mutation committed for Bot Job {}, but runtime variable "
                                + "memory could not be reconciled",
                        botJobId);
            }
        } catch (RuntimeException reconciliationFailure) {
            log.warn(
                    "Variables mutation committed for Bot Job {}, but runtime variable memory "
                            + "reconciliation failed: {}",
                    botJobId,
                    reconciliationFailure.getMessage());
        }
        notifyMutation(botJobId);
    }

    private CommitResult persistMutation(
            Binding authorized,
            InstructionGraphMutationV3.Request request,
            String mutationProfile) {
        try {
            return mutations.mutate(
                    authorized.homeBankingId(),
                    authorized.botJobId(),
                    authorized.workspaceEpoch(),
                    request,
                    mutationProfile);
        } catch (SQLException error) {
            throw new MutationPersistenceException(error);
        }
    }

    private DeleteResult persistVariableDeletion(
            Binding authorized,
            VariablesWorkspaceVariableDelete.Request request) {
        try {
            return variableDeletes.delete(
                    authorized.homeBankingId(),
                    authorized.botJobId(),
                    authorized.workspaceEpoch(),
                    request);
        } catch (SQLException error) {
            throw new VariableDeletePersistenceException(error);
        }
    }

    private CopyResult persistInstructionCopy(
            Binding authorized,
            VariablesInstructionCopyV1.Request request) {
        try {
            return instructionCopies.copy(
                    authorized.homeBankingId(),
                    authorized.botJobId(),
                    authorized.workspaceEpoch(),
                    request);
        } catch (SQLException error) {
            throw new InstructionCopyPersistenceException(error);
        }
    }

    static String normalizeMutationProfile(String requestedProfile) {
        String profile = requestedProfile == null ? "" : requestedProfile.trim();
        if (profile.isBlank()
                || VariablesInstructionMutationProfile.PROFILE_ID.equals(profile)) {
            return VariablesInstructionMutationProfile.PROFILE_ID;
        }
        if (VariablesCrossBlockInstructionMutationProfile.PROFILE_ID.equals(profile)) {
            return VariablesCrossBlockInstructionMutationProfile.PROFILE_ID;
        }
        if (VariablesReactAuthoredMutationProfile.PROFILE_ID.equals(profile)) {
            return VariablesReactAuthoredMutationProfile.PROFILE_ID;
        }
        throw new IllegalArgumentException(
                "The requested Variables mutation profile is not supported.");
    }

    /**
     * Pushes a changed graph to the detached page. Calling this for execution color/status events
     * is intentionally unsupported; callers invoke it only after persisted graph mutations.
     */
    public boolean publishIfOpen(int botJobId) {
        Binding current = currentBinding();
        if (current == null
                || current.botJobId() != botJobId
                || !windows.isOpen(WORKSPACE_SESSION_ID)) {
            return false;
        }
        try {
            WorkspaceContext workspace =
                    workspaces.require(current.botJobId(), current.workspaceEpoch());
            current = current.withWorkspace(workspace);
            JsonObject snapshot =
                    loadSnapshot(current, null, "Variable relationships updated.");
            if (!isSuccessful(snapshot)) {
                if (isCurrent(current)) publish(snapshot, current);
                return false;
            }
            String revision = text(snapshot, "graphRevision");
            Binding latest = currentBinding(current.bindingEpoch());
            if (latest == null) return false;
            if (!revision.isBlank() && revision.equals(latest.graphRevision())) {
                return false;
            }
            boolean delivered = publish(snapshot, latest);
            if (delivered) {
                advanceDeliveredRevision(latest, revision);
            }
            return delivered;
        } catch (IllegalArgumentException | IllegalStateException staleWorkspace) {
            JsonObject error = failure(null, staleWorkspace.getMessage(), current);
            if (isCurrent(current)) publish(error, current);
            return false;
        } catch (RuntimeException error) {
            log.warn(
                    "Unable to publish Variables snapshot for Bot Job {}: {}",
                    botJobId,
                    error.getMessage());
            JsonObject response = failure(
                    null,
                    "Variable relationships could not be refreshed.",
                    current);
            if (isCurrent(current)) publish(response, current);
            return false;
        }
    }

    /**
     * Queues relationship refresh after the caller's transaction/registry monitor can unwind.
     */
    public void notifyMutation(int botJobId) {
        if (botJobId <= 0) return;
        tasks.executeMutation(() -> publishIfOpen(botJobId));
    }

    void notifyRuntimeMemoryChanged(BotJobKey owner, long revision) {
        if (!isRuntimeMemoryPublishTarget(owner)) return;
        pendingRuntimeMemoryRevisions.merge(owner, revision, Math::max);
        if (!scheduledRuntimeMemoryOwners.add(owner)) return;
        tasks.scheduleRuntimePublication(
                () -> drainRuntimeMemoryPublication(owner),
                RUNTIME_MEMORY_PUBLICATION_DELAY_MILLIS);
    }

    private void drainRuntimeMemoryPublication(BotJobKey owner) {
        try {
            Long requestedRevision = pendingRuntimeMemoryRevisions.remove(owner);
            if (requestedRevision == null || !isRuntimeMemoryPublishTarget(owner)) return;
            publishRuntimeMemoryIfOpen(owner);
        } finally {
            scheduledRuntimeMemoryOwners.remove(owner);
            // Close the add/remove race: a writer may have merged a newer revision after the
            // first drain removed the prior value but before this owner left the scheduled set.
            Long pending = pendingRuntimeMemoryRevisions.get(owner);
            if (pending != null && isRuntimeMemoryPublishTarget(owner)) {
                notifyRuntimeMemoryChanged(owner, pending);
            } else if (!isRuntimeMemoryPublishTarget(owner)) {
                pendingRuntimeMemoryRevisions.remove(owner);
            }
        }
    }

    private boolean isRuntimeMemoryPublishTarget(BotJobKey owner) {
        synchronized (stateLock) {
            return owner != null
                    && binding != null
                    && binding.botJobId() == owner.botJobId()
                    && binding.homeBankingId() == owner.homeBankingId()
                    && managerTransport != null
                    && managerTransport.isOpen();
        }
    }

    /**
     * Publishes runtime memory without reloading or invalidating the persisted instruction graph.
     */
    boolean publishRuntimeMemoryIfOpen(BotJobKey owner) {
        Binding current = currentBinding();
        if (owner == null
                || current == null
                || current.botJobId() != owner.botJobId()
                || current.homeBankingId() != owner.homeBankingId()
                || !windows.isOpen(WORKSPACE_SESSION_ID)) {
            return false;
        }
        try {
            WorkspaceContext workspace =
                    workspaces.require(current.botJobId(), current.workspaceEpoch());
            current = current.withWorkspace(workspace);
            Binding latest = currentBinding(current.bindingEpoch());
            if (latest == null
                    || latest.botJobId() != owner.botJobId()
                    || latest.homeBankingId() != owner.homeBankingId()) {
                return false;
            }
            current = latest.withWorkspace(workspace);
            JsonObject payload = new JsonObject();
            payload.addProperty("ok", true);
            payload.addProperty("bindingEpoch", current.bindingEpoch());
            payload.addProperty("workspaceEpoch", current.workspaceEpoch());
            payload.addProperty("botJobId", current.botJobId());
            payload.addProperty("homeBankingId", current.homeBankingId());
            JsonObject snapshot = runtimeMemory.hydrate(current, null);
            payload.addProperty(
                    "memoryRevision",
                    nonNegativeLong(snapshot, "revision", true));
            payload.add("runtimeMemory", snapshot);
            return windows.send(
                    current.homeBankingId(),
                    WORKSPACE_SESSION_ID,
                    RUNTIME_MEMORY_SNAPSHOT_OPERATION,
                    payload);
        } catch (IllegalArgumentException | IllegalStateException staleWorkspace) {
            return false;
        } catch (RuntimeException failure) {
            log.warn(
                    "Unable to publish runtime memory for Bot Job {}: {}",
                    owner.botJobId(),
                    failure.getMessage());
            return false;
        }
    }

    /** Retires the binding and closes only this detached page. */
    public boolean retireForBotJob(int botJobId, String reason) {
        RetireResult result = retireForBotJobDetailed(botJobId, reason);
        return result.matched() && result.closed();
    }

    public RetireResult retireForBotJobDetailed(int botJobId, String reason) {
        Binding retired;
        synchronized (stateLock) {
            if (binding == null || binding.botJobId() != botJobId) {
                return new RetireResult(false, true, false, false);
            }
            retired = binding;
            binding = null;
            managerTransport = null;
            disconnectGeneration++;
        }
        BotJobKey retiredOwner = runtimeOwner(retired);
        pendingRuntimeMemoryRevisions.remove(retiredOwner);

        String closeReason = reason == null || reason.isBlank()
                ? "The Variables workspace was retired."
                : reason;
        boolean wasOpen = windows.isOpen(WORKSPACE_SESSION_ID);
        boolean tombstoneDelivered = !wasOpen || publishRetired(retired, closeReason);
        boolean closeRequested = !wasOpen || windows.close(WORKSPACE_SESSION_ID, closeReason);
        boolean forceClosed = false;
        if (wasOpen && !closeRequested) {
            forceClosed = windows.forceClose(WORKSPACE_SESSION_ID, closeReason);
        }
        boolean closed = !wasOpen || closeRequested || forceClosed;
        if (!closed || !tombstoneDelivered) {
            log.warn(
                    "Variables workspace retirement incomplete for Bot Job {}: tombstoneDelivered={} closeRequested={} forceClosed={}",
                    botJobId,
                    tombstoneDelivered,
                    closeRequested,
                    forceClosed);
        }
        return new RetireResult(true, closed, tombstoneDelivered, forceClosed);
    }

    /**
     * Rotates the binding epoch for a newly connected/reloaded singleton transport, or closes a
     * shell that connected after its Bot Job binding had already retired.
     */
    public void connected(String sessionId, Session transport) {
        if (!isWorkspaceSession(sessionId)
                || !windows.isRegistered(sessionId, transport)) {
            return;
        }
        Binding current = currentBinding();
        if (current == null) {
            sendRetiredClose();
            return;
        }
        try {
            workspaces.require(current.botJobId(), current.workspaceEpoch());
        } catch (IllegalArgumentException | IllegalStateException stale) {
            clearIfCurrent(current);
            sendRetiredClose();
            return;
        }
        synchronized (stateLock) {
            if (binding == null
                    || !binding.bindingEpoch().equals(current.bindingEpoch())) {
                return;
            }
            managerTransport = transport;
            binding = binding.withBindingEpoch(UUID.randomUUID().toString());
            disconnectGeneration++;
        }
    }

    /**
     * Preserves a short tombstone window for browser reloads whose old socket closes before the new
     * socket opens. If no replacement connects, an explicit page close retires after the grace.
     */
    public void disconnected(String sessionId, Session transport) {
        Binding disconnectedBinding;
        long generation;
        synchronized (stateLock) {
            if (!isWorkspaceSession(sessionId)
                    || transport == null
                    || managerTransport != transport) {
                return;
            }
            managerTransport = null;
            disconnectedBinding = binding;
            generation = ++disconnectGeneration;
        }
        if (disconnectedBinding == null) return;
        tasks.scheduleDisconnect(
                () -> expireDisconnectedBinding(disconnectedBinding, generation),
                RELOAD_GRACE_MILLIS);
    }

    private void expireDisconnectedBinding(Binding expected, long generation) {
        // Probe the owner outside stateLock. Both a stale owner and an explicit close retire after
        // the grace; an authoritative reconnect invalidates this generation.
        try {
            workspaces.require(expected.botJobId(), expected.workspaceEpoch());
        } catch (IllegalArgumentException | IllegalStateException ignored) {
            // The guarded state transition below is the same for either condition.
        }
        synchronized (stateLock) {
            if (disconnectGeneration != generation
                    || managerTransport != null
                    || binding == null
                    || !binding.bindingEpoch().equals(expected.bindingEpoch())) {
                return;
            }
            binding = null;
            disconnectGeneration++;
        }
    }

    public static boolean isWorkspaceSession(String sessionId) {
        return WORKSPACE_SESSION_ID.equals(sessionId);
    }

    private JsonObject loadForManager(
            JsonObject body,
            String requesterSessionId,
            Session requesterTransport,
            String message) {
        JsonObject request = body == null ? new JsonObject() : body;
        Binding current = null;
        try {
            requireManagerTransport(requesterSessionId, requesterTransport);
            current = currentBinding();
            if (current == null) {
                throw new IllegalArgumentException(
                        "No Bot Job is bound to the Variables workspace.");
            }
            String requestedEpoch = text(request, "bindingEpoch");
            if (!requestedEpoch.isBlank()
                    && !requestedEpoch.equals(current.bindingEpoch())) {
                throw new IllegalArgumentException(
                        "The Variables target changed. Reload the current Bot Job.");
            }

            WorkspaceContext workspace =
                    workspaces.require(current.botJobId(), current.workspaceEpoch());
            current = current.withWorkspace(workspace);
            JsonObject snapshot = loadSnapshot(current, request, message);
            if (!isCurrent(current)
                    || !isManagerTransport(requesterTransport)) {
                return failure(
                        request,
                        "The Variables target changed while it was loading.",
                        currentBinding());
            }
            return snapshot;
        } catch (IllegalArgumentException | IllegalStateException error) {
            return failure(
                    request,
                    error.getMessage(),
                    current == null ? currentBinding() : current);
        } catch (RuntimeException error) {
            log.error("Unable to load the Variables workspace", error);
            return failure(
                    request,
                    "Variable relationships could not be loaded.",
                    current == null ? currentBinding() : current);
        }
    }

    private JsonObject loadSnapshot(
            Binding current, JsonObject request, String message) {
        JsonObject graph = graphs.load(current.botJobId());
        if (!isSuccessful(graph)) {
            String error = text(graph, "error");
            return failure(
                    request,
                    error.isBlank()
                            ? "Variable relationships could not be loaded."
                            : error,
                    current);
        }

        // Reject a graph that crossed a Bot Job activation boundary while its SQL was running.
        WorkspaceContext authoritative =
                workspaces.require(current.botJobId(), current.workspaceEpoch());
        JsonObject response = graph.deepCopy();
        String contentRevision = text(graph, "graphRevision");
        if (!contentRevision.isBlank()) {
            response.addProperty("contentRevision", contentRevision);
        }
        JsonObject durableRuntimeMemory = runtimeMemory.hydrate(current, graph);
        response.add("runtimeMemory", durableRuntimeMemory);
        addMutationCapability(response, current);
        response.addProperty("message", message);
        response.addProperty("bindingEpoch", current.bindingEpoch());
        response.addProperty("workspaceEpoch", current.workspaceEpoch());
        JsonObject botJob = new JsonObject();
        botJob.addProperty("id", authoritative.botJobId());
        botJob.addProperty("name", authoritative.botJobName());
        botJob.addProperty("homeBankingId", authoritative.homeBankingId());
        botJob.addProperty("organizationName", authoritative.organizationName());
        response.add("botJob", botJob);
        correlate(request, response);
        return response;
    }

    private JsonObject reconcileRuntimeMemory(JsonObject graph, Binding current) {
        return runtimeMemory.hydrate(current, graph);
    }

    private JsonObject reconcileRuntimeMemory(JsonObject graph, BotJobKey owner) {
        Binding current = currentBinding();
        if (current == null
                || current.homeBankingId() != owner.homeBankingId()
                || current.botJobId() != owner.botJobId()) {
            return new JsonObject();
        }
        return runtimeMemory.hydrate(current, graph);
    }

    private static BotJobKey runtimeOwner(Binding current) {
        return new BotJobKey(current.homeBankingId(), current.botJobId());
    }

    private Binding authorizeRuntimeRequest(
            JsonObject request,
            String requesterSessionId,
            Session requesterTransport) {
        requireManagerTransport(requesterSessionId, requesterTransport);
        Binding current = currentBinding();
        if (current == null) {
            throw new IllegalArgumentException(
                    "No Bot Job is bound to the Variables workspace.");
        }
        String requestedBindingEpoch = text(request, "bindingEpoch");
        if (requestedBindingEpoch.isBlank()
                || !requestedBindingEpoch.equals(current.bindingEpoch())) {
            throw new IllegalArgumentException(
                    "The Variables target changed. Reload the current Bot Job.");
        }
        long requestedWorkspaceEpoch = nonNegativeLong(
                request, "workspaceEpoch", true);
        if (requestedWorkspaceEpoch != current.workspaceEpoch()) {
            throw new IllegalArgumentException(
                    "The Variables workspace changed. Reload the current Bot Job.");
        }
        WorkspaceContext workspace =
                workspaces.require(current.botJobId(), current.workspaceEpoch());
        current = current.withWorkspace(workspace);
        if (!isCurrent(current) || !isManagerTransport(requesterTransport)) {
            throw new IllegalArgumentException(
                    "The Variables target changed before the request was saved.");
        }
        return current;
    }

    private RuntimeMemoryMutation commitAuthorizedRuntimeMutation(
            Binding authorized,
            Session requesterTransport,
            RuntimeMutationSupplier mutation) {
        synchronized (stateLock) {
            if (binding == null
                    || !binding.bindingEpoch().equals(authorized.bindingEpoch())
                    || binding.workspaceEpoch() != authorized.workspaceEpoch()
                    || managerTransport != requesterTransport) {
                throw new IllegalArgumentException(
                        "The Variables target changed before the request was saved.");
            }
            return mutation.get();
        }
    }

    private JsonObject runtimeMutationResponse(
            JsonObject request,
            Binding current,
            RuntimeMemoryMutation committed) {
        if (!committed.applied()) {
            JsonObject refused = failure(request, committed.message(), current);
            refused.addProperty("errorCode", committed.status());
            if (committed.runtimeMemory() != null) {
                refused.add("runtimeMemory", committed.runtimeMemory());
            }
            return refused;
        }
        JsonObject response = success(request, current, committed.message());
        response.add("runtimeMemory", committed.runtimeMemory());
        response.addProperty(
                "memoryRevision",
                nonNegativeLong(committed.runtimeMemory(), "revision", true));
        return response;
    }

    private static void requireContractVersion(JsonObject request) {
        if (positiveInteger(request, "contractVersion") != 1) {
            throw new IllegalArgumentException(
                    "Variables runtime-memory contract version 1 is required.");
        }
    }

    private static String exactText(JsonObject source, String field) {
        if (source == null || !source.has(field) || source.get(field).isJsonNull()) {
            throw new IllegalArgumentException(field + " is required.");
        }
        try {
            return source.get(field).getAsString();
        } catch (RuntimeException invalid) {
            throw new IllegalArgumentException(field + " must be text.");
        }
    }

    private static long positiveLong(JsonObject source, String field) {
        long value = nonNegativeLong(source, field, true);
        if (value <= 0L) {
            throw new IllegalArgumentException(field + " must be positive.");
        }
        return value;
    }

    private static long nonNegativeLong(
            JsonObject source, String field, boolean required) {
        try {
            if (source == null || !source.has(field) || source.get(field).isJsonNull()) {
                if (required) {
                    throw new IllegalArgumentException(field + " is required.");
                }
                return 0L;
            }
            long value = source.get(field).getAsLong();
            if (value < 0L) {
                throw new IllegalArgumentException(field + " cannot be negative.");
            }
            return value;
        } catch (IllegalArgumentException invalid) {
            throw invalid;
        } catch (RuntimeException invalid) {
            throw new IllegalArgumentException(field + " is invalid.");
        }
    }

    private void addMutationCapability(JsonObject response, Binding current) {
        JsonObject capability = new JsonObject();
        try {
            GraphSnapshot graph = mutations.inspect(
                    current.homeBankingId(),
                    current.botJobId(),
                    current.workspaceEpoch());
            capability.addProperty("enabled", true);
            capability.addProperty(
                    "contractVersion", InstructionGraphMutationV3.CONTRACT_VERSION);
            capability.addProperty(
                    "profile", VariablesInstructionMutationProfile.PROFILE_ID);
            capability.addProperty(
                    "crossBlockProfile",
                    VariablesCrossBlockInstructionMutationProfile.PROFILE_ID);
            capability.addProperty(
                    "reactAuthoredProfile",
                    VariablesReactAuthoredMutationProfile.PROFILE_ID);
            capability.addProperty("graphVersion", graph.graphVersion());
            capability.addProperty("graphRevision", graph.graphRevision());
            JsonObject owner = new JsonObject();
            owner.addProperty(
                    "workspaceKind",
                    InstructionGraphMutationV3.WorkspaceKind.BOT_JOB.name());
            owner.addProperty("homeBankingId", current.homeBankingId());
            owner.addProperty("botJobId", current.botJobId());
            capability.add("ownerAssertion", owner);
            capability.add("layoutRows", gson.toJsonTree(graph.layoutRows()));
            capability.add("instructionFacts", gson.toJsonTree(graph.instructionFacts()));
            capability.add("variableFacts", variableFactsJson(graph.variableFacts()));
            response.addProperty("graphVersion", graph.graphVersion());
        } catch (SQLException | RuntimeException unavailable) {
            capability.addProperty("enabled", false);
            capability.addProperty(
                    "message",
                    "Variables drag and drop is temporarily unavailable.");
            log.warn(
                    "Unable to load Variables mutation capability for Bot Job {}: {}",
                    current.botJobId(),
                    unavailable.getMessage());
        }
        response.add("mutationCapability", capability);
    }

    private JsonArray variableFactsJson(List<GraphVariableFact> variableFacts) {
        JsonArray result = new JsonArray();
        variableFacts.forEach((fact) -> {
            JsonObject item = new JsonObject();
            item.addProperty("variableId", fact.variableId());
            if (fact.ownerInstructionId() == null) {
                item.add("ownerInstructionId", JsonNull.INSTANCE);
            } else {
                item.addProperty("ownerInstructionId", fact.ownerInstructionId());
            }
            result.add(item);
        });
        return result;
    }

    private boolean publish(JsonObject response, Binding current) {
        if (response == null
                || current == null
                || !windows.isOpen(WORKSPACE_SESSION_ID)) {
            return false;
        }
        JsonObject payload = response.deepCopy();
        if (!payload.has("bindingEpoch")) {
            payload.addProperty("bindingEpoch", current.bindingEpoch());
        }
        if (!payload.has("workspaceEpoch")) {
            payload.addProperty("workspaceEpoch", current.workspaceEpoch());
        }
        return windows.send(
                current.homeBankingId(),
                WORKSPACE_SESSION_ID,
                SNAPSHOT_OPERATION,
                payload);
    }

    private void requireManagerTransport(String sessionId, Session transport) {
        if (!isWorkspaceSession(sessionId)
                || !windows.isRegistered(sessionId, transport)
                || !isManagerTransport(transport)) {
            throw new IllegalArgumentException(
                    "The Variables workspace requester is not authoritative.");
        }
    }

    private JsonObject success(JsonObject request, Binding current, String message) {
        JsonObject response = new JsonObject();
        response.addProperty("ok", true);
        response.addProperty("message", message);
        response.addProperty("bindingEpoch", current.bindingEpoch());
        response.addProperty("workspaceEpoch", current.workspaceEpoch());
        response.addProperty("botJobId", current.botJobId());
        response.addProperty("homeBankingId", current.homeBankingId());
        correlate(request, response);
        return response;
    }

    private JsonObject failure(JsonObject request, String message, Binding current) {
        JsonObject response = new JsonObject();
        response.addProperty("ok", false);
        response.addProperty("preserveSnapshot", true);
        response.addProperty(
                "error",
                message == null || message.isBlank()
                        ? "The Variables workspace request was refused."
                        : message);
        if (current != null) {
            response.addProperty("bindingEpoch", current.bindingEpoch());
            response.addProperty("workspaceEpoch", current.workspaceEpoch());
            response.addProperty("botJobId", current.botJobId());
            response.addProperty("homeBankingId", current.homeBankingId());
        }
        correlate(request, response);
        return response;
    }

    private JsonObject mutationSuccess(
            JsonObject request,
            Binding current,
            CommitResult committed) {
        JsonObject response = mutationResponseBase(request, current);
        response.addProperty("ok", true);
        response.addProperty("committed", true);
        response.addProperty("resyncRequired", false);
        response.addProperty("committedGraphVersion", committed.committedGraphVersion());
        response.addProperty("graphRevision", committed.graphRevision());
        response.addProperty(
                "message",
                "Instruction order saved. Refreshing Variables and Bot Job Details.");
        return response;
    }

    private JsonObject mutationFailure(
            JsonObject request,
            String errorCode,
            String message,
            Binding current) {
        JsonObject response = mutationResponseBase(request, current);
        response.addProperty("ok", false);
        response.addProperty("preserveSnapshot", true);
        response.addProperty(
                "errorCode",
                errorCode == null || errorCode.isBlank()
                        ? "MUTATION_REFUSED"
                        : errorCode);
        response.addProperty(
                "message",
                message == null || message.isBlank()
                        ? "The Variables graph change was refused."
                        : message);
        return response;
    }

    private JsonObject instructionCopySuccess(
            JsonObject request,
            Binding current,
            CopyResult committed) {
        JsonObject response = mutationResponseBase(request, current);
        response.addProperty(
                "contractVersion", VariablesInstructionCopyV1.CONTRACT_VERSION);
        response.addProperty("ok", true);
        response.addProperty("committed", true);
        response.addProperty("duplicate", committed.duplicate());
        response.addProperty("resyncRequired", false);
        response.addProperty("scope", committed.scope().name());
        response.addProperty(
                "selectedInstructionId", committed.selectedInstructionId());
        response.addProperty("targetBlockId", committed.targetBlockId());
        response.add(
                "sourceInstructionIds",
                gson.toJsonTree(committed.sourceInstructionIds()));
        response.add(
                "generatedInstructionIds",
                gson.toJsonTree(committed.generatedInstructionIds()));
        response.add(
                "createdInstructionIds",
                gson.toJsonTree(
                        committed.sourceInstructionIds().stream()
                                .map(committed.generatedInstructionIds()::get)
                                .toList()));
        response.add(
                "generatedVariableIds",
                gson.toJsonTree(committed.generatedVariableIds()));
        response.addProperty(
                "appliedCount", committed.generatedInstructionIds().size());
        response.addProperty(
                "copiedReferenceCount", committed.copiedReferenceCount());
        response.addProperty(
                "previousGraphVersion", committed.previousGraphVersion());
        response.addProperty(
                "committedGraphVersion", committed.committedGraphVersion());
        response.addProperty(
                "graphVersion", committed.committedGraphVersion());
        response.addProperty("graphRevision", committed.graphRevision());
        response.addProperty(
                "message",
                committed.generatedInstructionIds().size() == 1
                        ? "Instruction copied. Refreshing Variables and Bot Job Details."
                        : committed.generatedInstructionIds().size()
                                + " instructions copied. Refreshing Variables and Bot Job Details.");
        return response;
    }

    private JsonObject instructionCopyFailure(
            JsonObject request,
            String errorCode,
            String message,
            Binding current) {
        JsonObject response = mutationResponseBase(request, current);
        response.addProperty(
                "contractVersion", VariablesInstructionCopyV1.CONTRACT_VERSION);
        response.addProperty("ok", false);
        response.addProperty("committed", false);
        response.addProperty("preserveSnapshot", true);
        response.addProperty(
                "errorCode",
                errorCode == null || errorCode.isBlank()
                        ? "VARIABLE_COPY_REFUSED"
                        : errorCode);
        response.addProperty(
                "message",
                message == null || message.isBlank()
                        ? "Instruction copy was refused."
                        : message);
        return response;
    }

    private JsonObject variableDeleteSuccess(
            JsonObject request,
            Binding current,
            DeleteResult committed) {
        JsonObject response = mutationResponseBase(request, current);
        response.addProperty(
                "contractVersion",
                VariablesWorkspaceVariableDelete.CONTRACT_VERSION);
        response.addProperty("ok", true);
        response.addProperty("committed", true);
        response.addProperty("resyncRequired", false);
        response.addProperty("mode", committed.mode().name());
        response.add("variableIds", gson.toJsonTree(committed.variableIds()));
        response.addProperty("deletedCount", committed.deletedCount());
        response.addProperty(
                "clearedInstructionCount",
                committed.clearedInstructionCount());
        response.addProperty(
                "previousGraphVersion",
                committed.previousGraphVersion());
        response.addProperty(
                "committedGraphVersion",
                committed.committedGraphVersion());
        response.addProperty("graphRevision", committed.graphRevision());
        response.addProperty(
                "message",
                committed.deletedCount() == 1
                        ? "Variable deleted. Refreshing Variables and Bot Job Details."
                        : committed.deletedCount()
                                + " variables deleted. Refreshing Variables and Bot Job Details.");
        return response;
    }

    private JsonObject variableDeleteFailure(
            JsonObject request,
            String errorCode,
            String message,
            Binding current) {
        JsonObject response = mutationResponseBase(request, current);
        response.addProperty(
                "contractVersion",
                VariablesWorkspaceVariableDelete.CONTRACT_VERSION);
        response.addProperty("ok", false);
        response.addProperty("committed", false);
        response.addProperty("preserveSnapshot", true);
        response.addProperty(
                "errorCode",
                errorCode == null || errorCode.isBlank()
                        ? "VARIABLE_DELETE_REFUSED"
                        : errorCode);
        response.addProperty(
                "message",
                message == null || message.isBlank()
                        ? "Variables deletion was refused."
                        : message);
        return response;
    }

    private JsonObject mutationResponseBase(JsonObject request, Binding current) {
        JsonObject response = new JsonObject();
        response.addProperty(
                "contractVersion", InstructionGraphMutationV3.CONTRACT_VERSION);
        correlate(request, response);
        if (current != null) {
            response.addProperty("bindingEpoch", current.bindingEpoch());
            response.addProperty("workspaceEpoch", current.workspaceEpoch());
            response.addProperty("botJobId", current.botJobId());
            response.addProperty("homeBankingId", current.homeBankingId());
            JsonObject owner = new JsonObject();
            owner.addProperty(
                    "workspaceKind",
                    InstructionGraphMutationV3.WorkspaceKind.BOT_JOB.name());
            owner.addProperty("homeBankingId", current.homeBankingId());
            owner.addProperty("botJobId", current.botJobId());
            response.add("ownerAssertion", owner);
        }
        return response;
    }

    private Binding currentBinding() {
        synchronized (stateLock) {
            return binding;
        }
    }

    private Binding currentBinding(String bindingEpoch) {
        synchronized (stateLock) {
            return binding != null
                            && binding.bindingEpoch().equals(bindingEpoch)
                    ? binding
                    : null;
        }
    }

    private Binding currentBindingFor(int botJobId, long workspaceEpoch) {
        synchronized (stateLock) {
            return binding != null
                            && binding.botJobId() == botJobId
                            && binding.workspaceEpoch() == workspaceEpoch
                    ? binding
                    : null;
        }
    }

    private boolean isCurrent(Binding expected) {
        return expected != null && currentBinding(expected.bindingEpoch()) != null;
    }

    private boolean isManagerTransport(Session transport) {
        synchronized (stateLock) {
            return transport != null && managerTransport == transport;
        }
    }

    private void restoreIfCurrent(Binding expected, Binding replacement) {
        synchronized (stateLock) {
            if (binding != null
                    && binding.bindingEpoch().equals(expected.bindingEpoch())) {
                binding = replacement;
                disconnectGeneration++;
            }
        }
    }

    private void clearIfCurrent(Binding expected) {
        synchronized (stateLock) {
            if (binding != null
                    && binding.bindingEpoch().equals(expected.bindingEpoch())) {
                binding = null;
                managerTransport = null;
                disconnectGeneration++;
            }
        }
    }

    private void advanceDeliveredRevision(Binding expected, String revision) {
        synchronized (stateLock) {
            if (binding != null
                    && binding.bindingEpoch().equals(expected.bindingEpoch())) {
                binding = binding.withGraphRevision(revision);
            }
        }
    }

    private boolean publishRetired(Binding retired, String reason) {
        JsonObject payload = new JsonObject();
        payload.addProperty("ok", false);
        payload.addProperty("retired", true);
        payload.addProperty("preserveSnapshot", false);
        payload.addProperty("error", reason);
        payload.addProperty("bindingEpoch", retired.bindingEpoch());
        payload.addProperty("workspaceEpoch", retired.workspaceEpoch());
        payload.addProperty("botJobId", retired.botJobId());
        return windows.send(
                retired.homeBankingId(),
                WORKSPACE_SESSION_ID,
                SNAPSHOT_OPERATION,
                payload);
    }

    private void sendRetiredClose() {
        JsonObject payload = new JsonObject();
        payload.addProperty(
                "reason", "The Variables workspace no longer has an active Bot Job.");
        windows.send(
                -1,
                WORKSPACE_SESSION_ID,
                PagesOpenWorkspaceService.WORKSPACE_CLOSE_OPERATION,
                payload);
    }

    private static boolean isSuccessful(JsonObject response) {
        return response != null
                && response.has("ok")
                && !response.get("ok").isJsonNull()
                && response.get("ok").getAsBoolean();
    }

    private static void correlate(JsonObject request, JsonObject response) {
        if (request == null) return;
        for (String field : new String[] {"requestId"}) {
            if (request.has(field)
                    && !request.get(field).isJsonNull()
                    && !response.has(field)) {
                response.add(field, request.get(field).deepCopy());
            }
        }
    }

    private static String text(JsonObject source, String field) {
        try {
            return source != null
                            && source.has(field)
                            && !source.get(field).isJsonNull()
                    ? source.get(field).getAsString()
                    : "";
        } catch (RuntimeException ignored) {
            return "";
        }
    }

    private static int positiveInteger(JsonObject source, String field) {
        try {
            int value = source != null
                            && source.has(field)
                            && !source.get(field).isJsonNull()
                    ? source.get(field).getAsInt()
                    : -1;
            return value > 0 ? value : -1;
        } catch (RuntimeException ignored) {
            return -1;
        }
    }

    private static WorkspaceContext activeWorkspace(int botJobId, long workspaceEpoch) {
        BotJobDetailsWorkspaceRegistry.Snapshot snapshot = workspaceEpoch > 0
                ? BotJobDetailsWorkspaceRegistry.getInstance()
                        .require(botJobId, workspaceEpoch)
                : BotJobDetailsWorkspaceRegistry.getInstance().require(botJobId);
        return new WorkspaceContext(
                snapshot.workspaceEpoch(),
                snapshot.botJobId(),
                snapshot.homeBankingId(),
                snapshot.name(),
                snapshot.organizationName());
    }

    interface WorkspacePort {
        WorkspaceContext require(int botJobId, long workspaceEpoch);
    }

    interface GraphPort {
        JsonObject load(int botJobId);
    }

    interface WindowPort {
        boolean isOpen(String sessionId);

        boolean isRegistered(String sessionId, Session transport);

        boolean openOrFocus(String sessionId, int botJobId, String reason);

        boolean close(String sessionId, String reason);

        boolean forceClose(String sessionId, String reason);

        boolean send(int homeBankingId, String sessionId, String operationId, JsonObject body);
    }

    interface TaskPort {
        void executeMutation(Runnable task);

        void scheduleDisconnect(Runnable task, long delayMillis);

        default void scheduleRuntimePublication(Runnable task, long delayMillis) {
            executeMutation(task);
        }
    }

    interface MutationPort {
        GraphSnapshot inspect(
                int homeBankingId,
                int botJobId,
                long workspaceEpoch)
                throws SQLException;

        CommitResult mutate(
                int homeBankingId,
                int botJobId,
                long workspaceEpoch,
                InstructionGraphMutationV3.Request request,
                String mutationProfile)
                throws SQLException;
    }

    interface VariableDeletePort {
        DeleteResult delete(
                int homeBankingId,
                int botJobId,
                long workspaceEpoch,
                VariablesWorkspaceVariableDelete.Request request)
                throws SQLException;
    }

    interface InstructionCopyPort {
        CopyResult copy(
                int homeBankingId,
                int botJobId,
                long workspaceEpoch,
                VariablesInstructionCopyV1.Request request)
                throws SQLException;
    }

    interface RuntimeMemoryPort {
        JsonObject hydrate(Binding owner, JsonObject graph);

        RuntimeMemoryMutation setValue(
                Binding owner,
                long variableId,
                String rawValue,
                long baseRuntimeRevision,
                long expectedEntryRevision);

        RuntimeMemoryMutation clearValue(
                Binding owner,
                long variableId,
                long baseRuntimeRevision,
                long expectedEntryRevision);

        RuntimeMemoryMutation create(
                Binding owner,
                String name,
                ValueState initialState,
                String rawValue);

        RuntimeMemoryMutation clearAll(
                Binding owner,
                long baseRuntimeRevision);
    }

    @FunctionalInterface
    interface RuntimeMutationSupplier {
        RuntimeMemoryMutation get();
    }

    record RuntimeMemoryMutation(
            boolean applied,
            String status,
            String message,
            JsonObject runtimeMemory,
            Long variableId) {}

    record WorkspaceContext(
            long workspaceEpoch,
            int botJobId,
            int homeBankingId,
            String botJobName,
            String organizationName) {}

    public record RetireResult(
            boolean matched, boolean closed, boolean tombstoneDelivered, boolean forceClosed) {}

    private record Binding(
            String bindingEpoch,
            long workspaceEpoch,
            int botJobId,
            int homeBankingId,
            String botJobName,
            String organizationName,
            String graphRevision) {
        private Binding withBindingEpoch(String value) {
            return new Binding(
                    value,
                    workspaceEpoch,
                    botJobId,
                    homeBankingId,
                    botJobName,
                    organizationName,
                    graphRevision);
        }

        private Binding withGraphRevision(String value) {
            return new Binding(
                    bindingEpoch,
                    workspaceEpoch,
                    botJobId,
                    homeBankingId,
                    botJobName,
                    organizationName,
                    value == null ? "" : value);
        }

        private Binding withWorkspace(WorkspaceContext workspace) {
            return new Binding(
                    bindingEpoch,
                    workspace.workspaceEpoch(),
                    workspace.botJobId(),
                    workspace.homeBankingId(),
                    workspace.botJobName(),
                    workspace.organizationName(),
                    graphRevision);
        }
    }

    private static final class MutationPersistenceException extends RuntimeException {
        private MutationPersistenceException(SQLException cause) {
            super(cause);
        }
    }

    private static final class VariableDeletePersistenceException extends RuntimeException {
        private VariableDeletePersistenceException(SQLException cause) {
            super(cause);
        }
    }

    private static final class InstructionCopyPersistenceException extends RuntimeException {
        private InstructionCopyPersistenceException(SQLException cause) {
            super(cause);
        }
    }

    private enum DurableRuntimeMemoryPort implements RuntimeMemoryPort {
        INSTANCE;

        private final BotJobRuntimeVariableService service =
                new BotJobRuntimeVariableService();

        @Override
        public JsonObject hydrate(Binding owner, JsonObject graph) {
            try (Connection connection =
                    PerformDataBase.getInstance().getConnection()) {
                var snapshot = service.hydrate(connection, durableOwner(owner));
                RUNTIME_MEMORY.hydrateDurableSnapshot(snapshot);
                return runtimeMemoryJson(snapshot);
            } catch (SQLException failure) {
                throw new IllegalStateException(
                        "Durable runtime memory could not be loaded.", failure);
            }
        }

        @Override
        public RuntimeMemoryMutation setValue(
                Binding owner,
                long variableId,
                String rawValue,
                long baseRuntimeRevision,
                long expectedEntryRevision) {
            try (Connection connection =
                    PerformDataBase.getInstance().getConnection()) {
                return mutation(service.setValue(
                        connection,
                        durableOwner(owner),
                        variableId,
                        rawValue,
                        com.allinweb.ch.facade.variables.runtime
                                .BotJobRuntimeVariableModels.ValueSource.MANUAL,
                        null,
                        baseRuntimeRevision,
                        expectedEntryRevision));
            } catch (SQLException failure) {
                throw new IllegalStateException(
                        "Durable runtime value could not be saved.", failure);
            }
        }

        @Override
        public RuntimeMemoryMutation clearValue(
                Binding owner,
                long variableId,
                long baseRuntimeRevision,
                long expectedEntryRevision) {
            try (Connection connection =
                    PerformDataBase.getInstance().getConnection()) {
                return mutation(service.clearValue(
                        connection,
                        durableOwner(owner),
                        variableId,
                        com.allinweb.ch.facade.variables.runtime
                                .BotJobRuntimeVariableModels.VoidReason.NO_PRODUCER_YET,
                        com.allinweb.ch.facade.variables.runtime
                                .BotJobRuntimeVariableModels.ValueSource.MANUAL,
                        null,
                        baseRuntimeRevision,
                        expectedEntryRevision));
            } catch (SQLException failure) {
                throw new IllegalStateException(
                        "Durable runtime value could not be cleared.", failure);
            }
        }

        @Override
        public RuntimeMemoryMutation create(
                Binding owner,
                String name,
                ValueState initialState,
                String rawValue) {
            DefinitionDraft draft = new DefinitionDraft(
                    "$String",
                    name,
                    null,
                    null,
                    null,
                    null,
                    initialState,
                    initialState == ValueState.VALUE ? rawValue : null);
            try (Connection connection =
                    PerformDataBase.getInstance().getConnection()) {
                return mutation(service.createDefinition(
                        connection, durableOwner(owner), draft, null));
            } catch (SQLException failure) {
                throw new IllegalStateException(
                        "Durable variable definition could not be created.", failure);
            }
        }

        @Override
        public RuntimeMemoryMutation clearAll(
                Binding owner,
                long baseRuntimeRevision) {
            try (Connection connection =
                    PerformDataBase.getInstance().getConnection()) {
                return mutation(service.clearAll(
                        connection,
                        durableOwner(owner),
                        baseRuntimeRevision,
                        com.allinweb.ch.facade.variables.runtime
                                .BotJobRuntimeVariableModels.VoidReason.CLIENT_RESET,
                        com.allinweb.ch.facade.variables.runtime
                                .BotJobRuntimeVariableModels.ValueSource.RESET,
                        null));
            } catch (SQLException failure) {
                throw new IllegalStateException(
                        "Durable runtime values could not be cleared.", failure);
            }
        }

        private static RuntimeMemoryMutation mutation(MutationResult result) {
            if (result.snapshot() != null) {
                // The database committed first. Only then is the execution cache replaced and its
                // listener allowed to publish the committed revision.
                RUNTIME_MEMORY.hydrateDurableSnapshot(result.snapshot());
            }
            JsonObject snapshot = result.snapshot() == null
                    ? null
                    : runtimeMemoryJson(result.snapshot());
            return new RuntimeMemoryMutation(
                    result.applied(),
                    result.status().name(),
                    result.message() == null ? "" : result.message(),
                    snapshot,
                    result.definition() == null ? null : result.definition().id());
        }

        private static OwnerKey durableOwner(Binding owner) {
            return new OwnerKey(owner.homeBankingId(), owner.botJobId());
        }

        private static JsonObject runtimeMemoryJson(
                com.allinweb.ch.facade.variables.runtime
                        .BotJobRuntimeVariableModels.Snapshot snapshot) {
            JsonObject json = new JsonObject();
            json.addProperty("revision", snapshot.memory().runtimeRevision());
            json.addProperty("resetGeneration", snapshot.memory().resetGeneration());
            Map<Long, com.allinweb.ch.facade.variables.runtime
                            .BotJobRuntimeVariableModels.RuntimeValue>
                    values = new HashMap<>();
            snapshot.values().forEach(value -> values.put(value.variableId(), value));
            JsonArray rows = new JsonArray();
            for (com.allinweb.ch.facade.variables.runtime
                    .BotJobRuntimeVariableModels.Definition definition
                    : snapshot.definitions()) {
                var value = values.get(definition.id());
                JsonObject row = new JsonObject();
                row.addProperty("variableId", definition.id());
                row.addProperty("name", definition.name());
                row.addProperty(
                        "type",
                        definition.type() == null ? "" : definition.type());
                if (value == null) {
                    row.addProperty("state", ValueState.VOID.name());
                    row.addProperty("value", "");
                    row.addProperty("voidReason", "NO_PRODUCER_YET");
                    row.addProperty("entryRevision", 0L);
                    row.addProperty("source", "SYSTEM");
                } else {
                    row.addProperty("state", value.state().name());
                    row.addProperty(
                            "value",
                            value.state() == ValueState.VALUE
                                    ? value.rawValue()
                                    : "");
                    if (value.voidReason() == null) {
                        row.add("voidReason", com.google.gson.JsonNull.INSTANCE);
                    } else {
                        row.addProperty("voidReason", value.voidReason().name());
                    }
                    row.addProperty("entryRevision", value.entryRevision());
                    row.addProperty("source", value.source().name());
                }
                rows.add(row);
            }
            json.add("variables", rows);
            return json;
        }
    }

    /**
     * Test-only compatibility adapter for package-private constructors. Production always uses the
     * durable port above.
     */
    private enum LegacyRuntimeMemoryPort implements RuntimeMemoryPort {
        INSTANCE;

        @Override
        public JsonObject hydrate(Binding owner, JsonObject graph) {
            if (graph == null) {
                return new Gson()
                        .toJsonTree(RUNTIME_MEMORY.snapshot(runtimeOwner(owner)))
                        .getAsJsonObject();
            }
            List<Definition> definitions = new ArrayList<>();
            JsonArray rows = graph != null
                            && graph.has("rawVariables")
                            && graph.get("rawVariables").isJsonArray()
                    ? graph.getAsJsonArray("rawVariables")
                    : new JsonArray();
            for (JsonElement item : rows) {
                if (item == null || !item.isJsonObject()) continue;
                JsonObject row = item.getAsJsonObject();
                int variableId = positiveInteger(row, "id");
                if (variableId <= 0) continue;
                definitions.add(new Definition(
                        variableId,
                        text(row, "name"),
                        text(row, "type")));
            }
            Snapshot snapshot = RUNTIME_MEMORY.reconcileDefinitions(
                    runtimeOwner(owner), definitions, true);
            return new Gson().toJsonTree(snapshot).getAsJsonObject();
        }

        @Override
        public RuntimeMemoryMutation setValue(
                Binding owner,
                long variableId,
                String rawValue,
                long baseRuntimeRevision,
                long expectedEntryRevision) {
            BotJobKey key = runtimeOwner(owner);
            Snapshot before = RUNTIME_MEMORY.snapshot(key);
            if (before.revision() != baseRuntimeRevision
                    || before.variables().stream()
                            .filter(value -> value.variableId() == variableId)
                            .noneMatch(value ->
                                    value.entryRevision() == expectedEntryRevision)) {
                return refused(before, "STALE_RUNTIME_REVISION");
            }
            boolean applied = RUNTIME_MEMORY.write(
                    key, Math.toIntExact(variableId), rawValue, ValueSource.MANUAL);
            return result(key, applied, "Runtime variable updated.");
        }

        @Override
        public RuntimeMemoryMutation clearValue(
                Binding owner,
                long variableId,
                long baseRuntimeRevision,
                long expectedEntryRevision) {
            BotJobKey key = runtimeOwner(owner);
            Snapshot before = RUNTIME_MEMORY.snapshot(key);
            if (before.revision() != baseRuntimeRevision
                    || before.variables().stream()
                            .filter(value -> value.variableId() == variableId)
                            .noneMatch(value ->
                                    value.entryRevision() == expectedEntryRevision)) {
                return refused(before, "STALE_RUNTIME_REVISION");
            }
            boolean applied = RUNTIME_MEMORY.markVoid(
                    key,
                    Math.toIntExact(variableId),
                    VoidReason.NO_PRODUCER_YET,
                    ValueSource.MANUAL);
            return result(key, applied, "Runtime variable cleared.");
        }

        @Override
        public RuntimeMemoryMutation create(
                Binding owner,
                String name,
                ValueState initialState,
                String rawValue) {
            throw new IllegalStateException(
                    "Variable creation is unavailable in this test service.");
        }

        @Override
        public RuntimeMemoryMutation clearAll(
                Binding owner,
                long baseRuntimeRevision) {
            throw new IllegalStateException(
                    "Clear All Values is unavailable in this test service.");
        }

        private static RuntimeMemoryMutation result(
                BotJobKey owner, boolean applied, String message) {
            Snapshot snapshot = RUNTIME_MEMORY.snapshot(owner);
            return new RuntimeMemoryMutation(
                    applied,
                    applied ? "APPLIED" : "VARIABLE_NOT_FOUND",
                    message,
                    new Gson().toJsonTree(snapshot).getAsJsonObject(),
                    null);
        }

        private static RuntimeMemoryMutation refused(
                Snapshot snapshot, String status) {
            return new RuntimeMemoryMutation(
                    false,
                    status,
                    "Runtime memory revision changed.",
                    new Gson().toJsonTree(snapshot).getAsJsonObject(),
                    null);
        }
    }

    private enum DefaultMutationPort implements MutationPort {
        INSTANCE;

        private final BotJobGraphMutationService service =
                BotJobGraphMutationService.getInstance();

        @Override
        public GraphSnapshot inspect(
                int homeBankingId,
                int botJobId,
                long workspaceEpoch)
                throws SQLException {
            return service.inspect(homeBankingId, botJobId, workspaceEpoch);
        }

        @Override
        public CommitResult mutate(
                int homeBankingId,
                int botJobId,
                long workspaceEpoch,
                InstructionGraphMutationV3.Request request,
                String mutationProfile)
                throws SQLException {
            if (VariablesReactAuthoredMutationProfile.PROFILE_ID.equals(
                    mutationProfile)) {
                return service.mutate(
                        homeBankingId, botJobId, workspaceEpoch, request);
            }
            if (VariablesCrossBlockInstructionMutationProfile.PROFILE_ID.equals(
                    mutationProfile)) {
                return service.mutateVariablesInstructionCrossBlockMove(
                        homeBankingId, botJobId, workspaceEpoch, request);
            }
            return service.mutateVariablesInstructionMove(
                    homeBankingId, botJobId, workspaceEpoch, request);
        }
    }

    private enum UnavailableMutationPort implements MutationPort {
        INSTANCE;

        @Override
        public GraphSnapshot inspect(
                int homeBankingId,
                int botJobId,
                long workspaceEpoch)
                throws SQLException {
            throw new SQLException("Variables graph mutation capability is unavailable.");
        }

        @Override
        public CommitResult mutate(
                int homeBankingId,
                int botJobId,
                long workspaceEpoch,
                InstructionGraphMutationV3.Request request,
                String mutationProfile)
                throws SQLException {
            throw new SQLException("Variables graph mutation capability is unavailable.");
        }
    }

    private enum DefaultVariableDeletePort implements VariableDeletePort {
        INSTANCE;

        private final VariablesVariableDeleteService service =
                VariablesVariableDeleteService.getInstance();

        @Override
        public DeleteResult delete(
                int homeBankingId,
                int botJobId,
                long workspaceEpoch,
                VariablesWorkspaceVariableDelete.Request request)
                throws SQLException {
            return service.delete(
                    homeBankingId, botJobId, workspaceEpoch, request);
        }
    }

    private enum UnavailableVariableDeletePort implements VariableDeletePort {
        INSTANCE;

        @Override
        public DeleteResult delete(
                int homeBankingId,
                int botJobId,
                long workspaceEpoch,
                VariablesWorkspaceVariableDelete.Request request)
                throws SQLException {
            throw new SQLException(
                    "Variables deletion capability is unavailable.");
        }
    }

    private enum DefaultInstructionCopyPort implements InstructionCopyPort {
        INSTANCE;

        private final VariablesInstructionCopyService service =
                VariablesInstructionCopyService.getInstance();

        @Override
        public CopyResult copy(
                int homeBankingId,
                int botJobId,
                long workspaceEpoch,
                VariablesInstructionCopyV1.Request request)
                throws SQLException {
            return service.copy(
                    homeBankingId, botJobId, workspaceEpoch, request);
        }
    }

    private enum UnavailableInstructionCopyPort implements InstructionCopyPort {
        INSTANCE;

        @Override
        public CopyResult copy(
                int homeBankingId,
                int botJobId,
                long workspaceEpoch,
                VariablesInstructionCopyV1.Request request)
                throws SQLException {
            throw new SQLException(
                    "Variables instruction-copy capability is unavailable.");
        }
    }

    private static final class DefaultWindowPort implements WindowPort {
        private final PagesOpenWorkspaceService pages =
                PagesOpenWorkspaceService.getInstance();
        private final WebSocketSessionManager sessions =
                WebSocketSessionManager.getInstance();
        private final Gson gson = new Gson();

        @Override
        public boolean isOpen(String sessionId) {
            return WebSocketSessionManager.isSessionOpen(sessionId);
        }

        @Override
        public boolean isRegistered(String sessionId, Session transport) {
            return sessionId != null
                    && transport != null
                    && transport.isOpen()
                    && WebSocketSessionManager.getSession(sessionId) == transport;
        }

        @Override
        public boolean openOrFocus(String sessionId, int botJobId, String reason) {
            return pages.openOrFocusDetachedWorkspace(sessionId, botJobId, reason);
        }

        @Override
        public boolean close(String sessionId, String reason) {
            return pages.closeDetachedWorkspaceSession(sessionId, reason);
        }

        @Override
        public boolean forceClose(String sessionId, String reason) {
            return !WebSocketSessionManager.isSessionOpen(sessionId)
                    || WebSocketSessionManager.closeSession(sessionId);
        }

        @Override
        public boolean send(
                int homeBankingId, String sessionId, String operationId, JsonObject body) {
            return sessions.sendMessageJson(
                            homeBankingId,
                            sessionId,
                            gson.toJson(body),
                            operationId)
                    != null;
        }
    }

    private static final class DefaultTaskPort implements TaskPort {
        private static final DefaultTaskPort INSTANCE = new DefaultTaskPort();
        private final ExecutorService mutationExecutor =
                Executors.newSingleThreadExecutor(runnable -> daemon(runnable, "variables-mutations"));
        private final ScheduledExecutorService disconnectExecutor =
                Executors.newSingleThreadScheduledExecutor(
                        runnable -> daemon(runnable, "variables-disconnect"));

        @Override
        public void executeMutation(Runnable task) {
            if (task != null) mutationExecutor.execute(task);
        }

        @Override
        public void scheduleDisconnect(Runnable task, long delayMillis) {
            if (task != null) {
                disconnectExecutor.schedule(
                        task, Math.max(0L, delayMillis), TimeUnit.MILLISECONDS);
            }
        }

        @Override
        public void scheduleRuntimePublication(Runnable task, long delayMillis) {
            if (task != null) {
                disconnectExecutor.schedule(
                        () -> mutationExecutor.execute(task),
                        Math.max(0L, delayMillis),
                        TimeUnit.MILLISECONDS);
            }
        }

        private static Thread daemon(Runnable runnable, String name) {
            Thread thread = new Thread(runnable, name);
            thread.setDaemon(true);
            return thread;
        }
    }
}
