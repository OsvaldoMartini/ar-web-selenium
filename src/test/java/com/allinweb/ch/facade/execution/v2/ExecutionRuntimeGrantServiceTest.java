package com.allinweb.ch.facade.execution.v2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.allinweb.ch.facade.execution.v2.ExecutionV2Contracts.AuthorizedGrantFacts;
import com.allinweb.ch.facade.execution.v2.ExecutionV2Contracts.DataMode;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ExecutionRuntimeGrantServiceTest {
    private static final Path FIXTURE = Path.of(
            "playwright-runtime-ts", "fixtures", "java-hs256-grant-v1.json");

    @Test
    void emitsTheExactGrantAcceptedByTheTypeScriptRuntime() throws IOException {
        JsonObject fixture = JsonParser.parseReader(Files.newBufferedReader(FIXTURE)).getAsJsonObject();
        JsonObject claims = fixture.getAsJsonObject("claims");
        byte[] secret = Base64.getUrlDecoder().decode(
                fixture.get("secretBase64Url").getAsString());
        ExecutionRuntimeGrantConfiguration configuration =
                new ExecutionRuntimeGrantConfiguration(
                        secret,
                        fixture.get("keyId").getAsString(),
                        Duration.ofSeconds(
                                claims.get("exp").getAsLong() - claims.get("iat").getAsLong()));
        ExecutionRuntimeGrantService service = new ExecutionRuntimeGrantService(
                configuration,
                Clock.fixed(
                        Instant.ofEpochSecond(claims.get("iat").getAsLong()), ZoneOffset.UTC),
                () -> UUID.fromString(claims.get("jti").getAsString()),
                () -> UUID.fromString(claims.get("runId").getAsString()));

        var issued = service.issue(new AuthorizedGrantFacts(
                claims.get("organizationId").getAsInt(),
                claims.get("homeBankingId").getAsInt(),
                claims.get("botJobId").getAsInt(),
                claims.get("workspaceEpoch").getAsLong(),
                claims.get("graphRevision").getAsString(),
                claims.get("planRevision").getAsString(),
                DataMode.parse(claims.get("dataMode").getAsString())));

        assertEquals(fixture.get("compactGrant").getAsString(), issued.compactGrant());
        assertEquals(claims.get("jti").getAsString(), issued.grantId());
        assertEquals(claims.get("runId").getAsString(), issued.runId());
        assertEquals(Instant.ofEpochSecond(claims.get("iat").getAsLong()), issued.issuedAt());
        assertEquals(Instant.ofEpochSecond(claims.get("exp").getAsLong()), issued.expiresAt());
    }

    @Test
    void missingSecretDisablesIssuanceAndMalformedConfigurationFailsClosed() {
        assertTrue(ExecutionRuntimeGrantConfiguration.fromEnvironment(Map.of()).isEmpty());
        assertThrows(
                IllegalArgumentException.class,
                () -> ExecutionRuntimeGrantConfiguration.fromEnvironment(Map.of(
                        ExecutionRuntimeGrantConfiguration.SECRET_ENV, "not+base64url")));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ExecutionRuntimeGrantConfiguration(
                        new byte[31], "v1", Duration.ofSeconds(90)));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ExecutionRuntimeGrantConfiguration(
                        new byte[32], "v1", Duration.ofSeconds(121)));
    }

    @Test
    void authorizedFactsRejectUnsafeOwnersEpochsAndRevisions() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new AuthorizedGrantFacts(
                        13, 13, 29, 0, "a".repeat(64), "b".repeat(64), DataMode.REAL));
        assertThrows(
                IllegalArgumentException.class,
                () -> new AuthorizedGrantFacts(
                        13,
                        13,
                        29,
                        ExecutionV2Contracts.MAX_JAVASCRIPT_SAFE_INTEGER + 1,
                        "a".repeat(64),
                        "b".repeat(64),
                        DataMode.REAL));
        assertThrows(
                IllegalArgumentException.class,
                () -> new AuthorizedGrantFacts(
                        13, 13, 29, 7, "A".repeat(64), "b".repeat(64), DataMode.REAL));
    }
}
