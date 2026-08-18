package com.allinweb.ch.facade.execution.v2;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/** Cross-language constants and immutable authority facts for Execution V2 grants. */
public final class ExecutionV2Contracts {
    public static final int CONTRACT_VERSION = 1;
    public static final String GRANT_ALGORITHM = "HS256";
    public static final String GRANT_TYPE = "ARWEB-EXECUTION-GRANT";
    public static final String GRANT_ISSUER = "arweb-java-gateway";
    public static final String GRANT_AUDIENCE = "arweb-playwright-runtime";
    public static final String RUNTIME = "TYPESCRIPT_PLAYWRIGHT_V2";
    public static final String CAPABILITY_RESERVE = "runtime.reserve";
    public static final String CAPABILITY_BOOTSTRAP = "runtime.bootstrap";
    public static final String CAPABILITY_RELEASE = "runtime.release";
    public static final String CAPABILITY_START = "runtime.start";
    public static final String CAPABILITY_ACTION = "runtime.action";
    public static final String CAPABILITY_REFRESH = "runtime.refresh";
    public static final String CAPABILITY_STOP = "runtime.stop";
    public static final String CAPABILITY_HEARTBEAT = "runtime.heartbeat";
    public static final long MAX_JAVASCRIPT_SAFE_INTEGER = 9_007_199_254_740_991L;

    private static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");

    private ExecutionV2Contracts() {}

    public enum DataMode {
        REAL,
        SYNTHETIC;

        public static DataMode parse(String value) {
            if (value == null) throw new IllegalArgumentException("Execution data mode is required");
            try {
                return valueOf(value.trim().toUpperCase(Locale.ROOT));
            } catch (RuntimeException invalid) {
                throw new IllegalArgumentException("Execution data mode must be REAL or SYNTHETIC");
            }
        }
    }

    /**
     * Facts that a future Java adapter must derive after exact user/license/workspace/plan
     * authorization. None of these values may come from an unverified React assertion.
     */
    public record AuthorizedGrantFacts(
            int organizationId,
            int homeBankingId,
            int botJobId,
            long workspaceEpoch,
            String graphRevision,
            String planRevision,
            DataMode dataMode) {
        public AuthorizedGrantFacts {
            if (organizationId <= 0 || homeBankingId <= 0 || botJobId <= 0) {
                throw new IllegalArgumentException("Execution V2 owner IDs must be positive");
            }
            if (workspaceEpoch <= 0 || workspaceEpoch > MAX_JAVASCRIPT_SAFE_INTEGER) {
                throw new IllegalArgumentException(
                        "Execution V2 workspaceEpoch must be a positive JavaScript-safe integer");
            }
            graphRevision = revision(graphRevision, "graphRevision");
            planRevision = revision(planRevision, "planRevision");
            dataMode = Objects.requireNonNull(dataMode, "Execution V2 dataMode is required");
        }
    }

    public record IssuedGrant(
            int contractVersion,
            String keyId,
            String grantId,
            String runId,
            Instant issuedAt,
            Instant expiresAt,
            String compactGrant) {
        public IssuedGrant {
            if (contractVersion != CONTRACT_VERSION) {
                throw new IllegalArgumentException("Execution V2 contract version is invalid");
            }
            keyId = required(keyId, "keyId");
            grantId = required(grantId, "grantId");
            runId = required(runId, "runId");
            issuedAt = Objects.requireNonNull(issuedAt, "Execution V2 issuedAt is required");
            expiresAt = Objects.requireNonNull(expiresAt, "Execution V2 expiresAt is required");
            if (!expiresAt.isAfter(issuedAt)) {
                throw new IllegalArgumentException("Execution V2 grant expiry must follow issuance");
            }
            compactGrant = required(compactGrant, "compactGrant");
        }
    }

    static String revision(String value, String name) {
        String normalized = required(value, name);
        if (!SHA_256.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Execution V2 " + name + " must be lowercase SHA-256");
        }
        return normalized;
    }

    static String required(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Execution V2 " + name + " is required");
        }
        return value;
    }
}
