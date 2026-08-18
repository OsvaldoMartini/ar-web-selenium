package com.allinweb.ch.facade.execution.v2;

import com.allinweb.ch.facade.execution.v2.ExecutionV2Contracts.AuthorizedGrantFacts;
import com.allinweb.ch.facade.execution.v2.ExecutionV2Contracts.IssuedGrant;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Issues runtime grants only from an immutable, already-authorized facts object. This class is not
 * connected to a WebSocket route until a later adapter can resolve a distinct organization ID and
 * revalidate user, license, workspace, graph, plan, and dataset authority atomically.
 */
public final class ExecutionRuntimeGrantService {
    private final ExecutionRuntimeGrantConfiguration configuration;
    private final ExecutionRuntimeGrantSigner signer;
    private final Clock clock;
    private final Supplier<UUID> grantIdSupplier;
    private final Supplier<UUID> runIdSupplier;

    public ExecutionRuntimeGrantService(ExecutionRuntimeGrantConfiguration configuration) {
        this(configuration, Clock.systemUTC(), UUID::randomUUID, UUID::randomUUID);
    }

    ExecutionRuntimeGrantService(
            ExecutionRuntimeGrantConfiguration configuration,
            Clock clock,
            Supplier<UUID> grantIdSupplier,
            Supplier<UUID> runIdSupplier) {
        this.configuration = Objects.requireNonNull(configuration, "Execution V2 configuration is required");
        this.signer = new ExecutionRuntimeGrantSigner(configuration);
        this.clock = Objects.requireNonNull(clock, "Execution V2 clock is required");
        this.grantIdSupplier = Objects.requireNonNull(
                grantIdSupplier, "Execution V2 grant ID supplier is required");
        this.runIdSupplier = Objects.requireNonNull(
                runIdSupplier, "Execution V2 run ID supplier is required");
    }

    public IssuedGrant issue(AuthorizedGrantFacts facts) {
        Objects.requireNonNull(facts, "Execution V2 authorized facts are required");
        Instant issuedAt = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        Instant expiresAt = issuedAt.plus(configuration.lifetime());
        UUID grantIdValue = Objects.requireNonNull(
                grantIdSupplier.get(), "Execution V2 grant ID cannot be null");
        UUID runIdValue = Objects.requireNonNull(
                runIdSupplier.get(), "Execution V2 run ID cannot be null");
        if (grantIdValue.equals(runIdValue)) {
            throw new IllegalStateException("Execution V2 grant ID and run ID must be distinct");
        }
        String grantId = grantIdValue.toString();
        String runId = runIdValue.toString();

        JsonObject claims = new JsonObject();
        claims.addProperty("v", ExecutionV2Contracts.CONTRACT_VERSION);
        claims.addProperty("iss", ExecutionV2Contracts.GRANT_ISSUER);
        claims.addProperty("aud", ExecutionV2Contracts.GRANT_AUDIENCE);
        claims.addProperty("jti", grantId);
        claims.addProperty("runId", runId);
        claims.addProperty("organizationId", facts.organizationId());
        claims.addProperty("homeBankingId", facts.homeBankingId());
        claims.addProperty("botJobId", facts.botJobId());
        claims.addProperty("workspaceEpoch", facts.workspaceEpoch());
        claims.addProperty("graphRevision", facts.graphRevision());
        claims.addProperty("planRevision", facts.planRevision());
        claims.addProperty("dataMode", facts.dataMode().name());
        claims.addProperty("runtime", ExecutionV2Contracts.RUNTIME);
        JsonArray capabilities = new JsonArray();
        capabilities.add(ExecutionV2Contracts.CAPABILITY_RESERVE);
        capabilities.add(ExecutionV2Contracts.CAPABILITY_BOOTSTRAP);
        capabilities.add(ExecutionV2Contracts.CAPABILITY_RELEASE);
        capabilities.add(ExecutionV2Contracts.CAPABILITY_START);
        capabilities.add(ExecutionV2Contracts.CAPABILITY_ACTION);
        capabilities.add(ExecutionV2Contracts.CAPABILITY_REFRESH);
        capabilities.add(ExecutionV2Contracts.CAPABILITY_STOP);
        capabilities.add(ExecutionV2Contracts.CAPABILITY_HEARTBEAT);
        claims.add("capabilities", capabilities);
        claims.addProperty("iat", issuedAt.getEpochSecond());
        claims.addProperty("nbf", issuedAt.getEpochSecond());
        claims.addProperty("exp", expiresAt.getEpochSecond());

        return new IssuedGrant(
                ExecutionV2Contracts.CONTRACT_VERSION,
                configuration.keyId(),
                grantId,
                runId,
                issuedAt,
                expiresAt,
                signer.sign(claims));
    }
}
