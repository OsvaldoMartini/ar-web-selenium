package com.allinweb.ch.facade;

import com.allinweb.ch.facade.BotJobDetailsWorkspaceRegistry.MetadataCommit;
import com.allinweb.ch.facade.BotJobDetailsWorkspaceRegistry.RevisionConflictException;
import com.allinweb.ch.facade.BotJobDetailsWorkspaceRegistry.Snapshot;
import com.allinweb.ch.model.BotJobDetailsPersistedState;
import com.allinweb.ch.model.BotJobDetailsRequest;
import com.allinweb.ch.model.BotJobDetailsResponse;
import com.allinweb.ch.model.BotJobDetailsState;
import com.allinweb.ch.model.BotJobToolbarContext;
import com.allinweb.ch.util.ErrorMessage;
import com.google.common.base.Strings;
import com.google.gson.JsonObject;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** UI-independent bootstrap and metadata service for the React Bot Job Details workspace. */
public final class BotJobDetailsService {

    private static final BotJobDetailsService INSTANCE = new BotJobDetailsService(
            BotJobDetailsWorkspaceRegistry.getInstance(), new DefaultBotJobDetailsDataPort());

    private final BotJobDetailsWorkspaceRegistry registry;
    private final BotJobDetailsDataPort data;

    BotJobDetailsService(BotJobDetailsWorkspaceRegistry registry, BotJobDetailsDataPort data) {
        this.registry = registry;
        this.data = data;
    }

    public static BotJobDetailsService getInstance() {
        return INSTANCE;
    }

    public BotJobDetailsResponse bootstrap(BotJobDetailsRequest request) {
        try {
            return BotJobDetailsResponse.success("Bot Job Details loaded", request, buildState(request.botJobId()));
        } catch (RuntimeException error) {
            return failure(request, "BOOTSTRAP_FAILED", error.getMessage(), Map.of());
        }
    }

    public synchronized BotJobDetailsResponse refreshEnvironments(BotJobDetailsRequest request) {
        try {
            registry.require(request.botJobId());
            data.load(request.botJobId());
            registry.environmentsChanged(request.botJobId());
            return BotJobDetailsResponse.success(
                    "Environments refreshed", request, buildState(request.botJobId()));
        } catch (Exception error) {
            return failure(request, "ENVIRONMENT_REFRESH_FAILED", error.getMessage(), Map.of());
        }
    }

    public synchronized BotJobDetailsResponse updateMetadata(BotJobDetailsRequest request) {
        JsonObject body = request.body();
        Snapshot current;
        try {
            current = registry.require(request.botJobId());
        } catch (RuntimeException error) {
            return failure(request, "WRONG_ACTIVE_JOB", error.getMessage(), Map.of());
        }

        long expectedMetadataRevision = longValue(
                body,
                "expectedMetadataRevision",
                longValue(body, "expectedRevision", -1));
        if (expectedMetadataRevision != current.metadataRevision()) {
            return failure(
                    request,
                    "REVISION_CONFLICT",
                    "Bot Job Details changed; review the latest values before saving",
                    Map.of());
        }

        String name = sanitizeName(stringValue(body, "name"));
        String description = stringValue(body, "description");
        int homeUrlId = intValue(body, "homeUrlId", -1);
        BotJobDetailsPersistedState persisted;
        try {
            persisted = data.load(request.botJobId());
        } catch (Exception error) {
            return failure(
                    request,
                    "VALIDATION_STATE_LOAD_FAILED",
                    "Unable to validate the current Bot Job details: " + safe(error.getMessage()),
                    Map.of());
        }
        Map<String, String> fieldErrors = validate(current, persisted, name, homeUrlId);
        if (!fieldErrors.isEmpty()) {
            return failure(request, "VALIDATION_FAILED", "Review the highlighted fields", fieldErrors);
        }

        MetadataCommit<ErrorMessage> commit;
        try {
            commit = registry.commitMetadata(
                    request.botJobId(),
                    expectedMetadataRevision,
                    name,
                    description,
                    homeUrlId,
                    () -> data.updateMetadata(request.botJobId(), homeUrlId, name, description));
        } catch (RevisionConflictException conflict) {
            return failure(request, "REVISION_CONFLICT", conflict.getMessage(), Map.of());
        } catch (RuntimeException error) {
            return failure(
                    request,
                    "UPDATE_FAILED",
                    Strings.isNullOrEmpty(error.getMessage())
                            ? "Bot Job details could not be saved"
                            : error.getMessage(),
                    Map.of());
        }

        ErrorMessage updateError = commit.persistenceError();
        if (updateError != null) {
            String message = errorText(updateError);
            if (message.toLowerCase().contains("unique constraint")) {
                fieldErrors.put("name", "A Bot Job with this name already exists");
            }
            return failure(request, "UPDATE_FAILED", message, fieldErrors);
        }

        BotJobDetailsPersistedState committedState = committedMetadataState(
                persisted, name, description, homeUrlId);
        return BotJobDetailsResponse.success(
                "Bot Job details saved", request, toState(commit.snapshot(), committedState));
    }

    public BotJobDetailsState currentState(int botJobId) {
        return buildState(botJobId);
    }

    public int activeHomeBankingId(int botJobId) {
        return registry.require(botJobId).homeBankingId();
    }

    public BotJobToolbarContext captureToolbarContext(int botJobId) {
        for (int attempt = 0; attempt < 2; attempt++) {
            Snapshot before = registry.require(botJobId);
            BotJobDetailsPersistedState persisted;
            try {
                persisted = data.load(botJobId);
            } catch (Exception error) {
                throw new IllegalStateException("Unable to load Bot Job toolbar context: " + error.getMessage(), error);
            }
            Snapshot after = registry.require(botJobId);
            if (before.revision() == after.revision() && before.workspaceEpoch() == after.workspaceEpoch()) {
                if (persisted.botJobId() != botJobId) {
                    throw new IllegalStateException("Loaded Bot Job toolbar context does not match the active Bot Job");
                }
                return new BotJobToolbarContext(
                        after.workspaceEpoch(),
                        persisted.botJobId(),
                        persisted.homeBankingId(),
                        persisted.homeUrlId(),
                        persisted.name(),
                        persisted.projectType(),
                        persisted.organizationName(),
                        persisted.environmentUrl(),
                        persisted.active());
            }
        }
        throw new IllegalStateException("Bot Job Details changed while toolbar context was loading");
    }

    private BotJobDetailsState buildState(int botJobId) {
        for (int attempt = 0; attempt < 2; attempt++) {
            Snapshot before = registry.require(botJobId);
            BotJobDetailsPersistedState persisted;
            try {
                persisted = data.load(botJobId);
            } catch (Exception error) {
                throw new IllegalStateException("Unable to load Bot Job Details: " + error.getMessage(), error);
            }
            Snapshot after = registry.require(botJobId);
            if (before.revision() == after.revision()) {
                return toState(after, persisted);
            }
        }
        throw new IllegalStateException("Bot Job Details changed while state was loading");
    }

    private BotJobDetailsState toState(Snapshot snapshot, BotJobDetailsPersistedState persisted) {
        List<BotJobDetailsState.Environment> environments = persisted.environments().stream()
                .map(environment -> new BotJobDetailsState.Environment(
                        environment.id(),
                        environment.name(),
                        environment.url(),
                        environment.homeBankingId(),
                        persisted.organizationName()))
                .toList();
        List<BotJobDetailsState.Block> blocks = persisted.blocks().stream()
                .map(block -> new BotJobDetailsState.Block(
                        block.id(),
                        block.order(),
                        block.name(),
                        block.description(),
                        block.typeId(),
                        block.active(),
                        block.waitSeconds()))
                .toList();

        boolean desktopBrowserTools = supportsDesktopBrowserTools(persisted.projectType());
        boolean licensePermits = data.licenseActive();
        BotJobDetailsState.Capabilities capabilities = new BotJobDetailsState.Capabilities(
                licensePermits,
                licensePermits,
                desktopBrowserTools && licensePermits,
                licensePermits,
                desktopBrowserTools && licensePermits,
                desktopBrowserTools && licensePermits,
                licensePermits,
                licensePermits);

        return new BotJobDetailsState(
                snapshot.revision(),
                snapshot.metadataRevision(),
                snapshot.botJobId(),
                persisted.name(),
                persisted.description(),
                persisted.projectType(),
                persisted.active(),
                persisted.homeBankingId(),
                persisted.organizationName(),
                persisted.homeUrlId(),
                persisted.environmentName(),
                persisted.environmentUrl(),
                data.navigationTimeSeconds(),
                data.transferPathConfigured(),
                environments,
                blocks,
                capabilities,
                snapshot.executionState(),
                snapshot.activeSurface(),
                snapshot.componentsVisible());
    }

    private Map<String, String> validate(
            Snapshot snapshot,
            BotJobDetailsPersistedState persisted,
            String name,
            int homeUrlId) {
        Map<String, String> errors = new LinkedHashMap<>();
        if (Strings.isNullOrEmpty(name)) {
            errors.put("name", "Bot Job name cannot be empty");
        }
        boolean validEnvironment = persisted.environments().stream()
                .anyMatch(row -> row.id() == homeUrlId && row.homeBankingId() == snapshot.homeBankingId());
        if (!validEnvironment) {
            errors.put("homeUrlId", "Select an environment from the active organization");
        }
        return errors;
    }

    private BotJobDetailsPersistedState committedMetadataState(
            BotJobDetailsPersistedState before,
            String name,
            String description,
            int homeUrlId) {
        BotJobDetailsPersistedState.Environment selected = before.environments().stream()
                .filter(environment -> environment.id() == homeUrlId)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Committed environment is unavailable"));
        return new BotJobDetailsPersistedState(
                before.botJobId(),
                name,
                description,
                before.projectType(),
                before.active(),
                before.homeBankingId(),
                before.organizationName(),
                homeUrlId,
                selected.name(),
                selected.url(),
                before.environments(),
                before.blocks());
    }

    private BotJobDetailsResponse failure(
            BotJobDetailsRequest request,
            String code,
            String message,
            Map<String, String> fieldErrors) {
        BotJobDetailsState state = null;
        try {
            state = buildState(request.botJobId());
        } catch (RuntimeException ignored) {
            // A wrong/closed workspace has no authoritative state to return.
        }
        return BotJobDetailsResponse.failure(
                Strings.isNullOrEmpty(message) ? "Bot Job Details operation failed" : message,
                code,
                request,
                state,
                fieldErrors);
    }

    private boolean supportsDesktopBrowserTools(String projectType) {
        return "Web App".equalsIgnoreCase(projectType) || "Rest Api".equalsIgnoreCase(projectType);
    }

    private String sanitizeName(String rawName) {
        String safeName = safe(rawName).replaceAll("[\\\\/:*?\"<>|]", "");
        safeName = safeName.replaceAll("[\\p{Cntrl}]", "").trim();
        if (safeName.length() > 100) {
            safeName = safeName.substring(0, 100);
        }
        return safeName;
    }

    private String errorText(ErrorMessage error) {
        if (error == null) return "";
        if (!Strings.isNullOrEmpty(error.getErrorMessage())) return error.getErrorMessage();
        if (!Strings.isNullOrEmpty(error.getErrorHeader())) return error.getErrorHeader();
        return safe(error.getErrorTitle());
    }

    private String stringValue(JsonObject body, String field) {
        if (body == null || !body.has(field) || body.get(field).isJsonNull()) return "";
        try {
            return body.get(field).getAsString().trim();
        } catch (RuntimeException ignored) {
            return "";
        }
    }

    private int intValue(JsonObject body, String field, int fallback) {
        try {
            return body == null || !body.has(field) ? fallback : body.get(field).getAsInt();
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private long longValue(JsonObject body, String field, long fallback) {
        try {
            return body == null || !body.has(field) ? fallback : body.get(field).getAsLong();
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
