package com.allinweb.ch.facade.execution;

import com.allinweb.ch.model.SmokeTestIntegrationContracts.RuntimeMode;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/** Exact run/owner authority for Page Scanner access while Locator Recovery is awaiting a user. */
public final class SmokeRecoveryScannerRegistry {
    private static final SmokeRecoveryScannerRegistry INSTANCE = new SmokeRecoveryScannerRegistry();

    private final Map<String, RecoveryAuthority> byRun = new ConcurrentHashMap<>();

    private SmokeRecoveryScannerRegistry() {}

    public static SmokeRecoveryScannerRegistry getInstance() {
        return INSTANCE;
    }

    public void register(
            String runId,
            RuntimeMode runtimeMode,
            int homeBankingId,
            int botJobId,
            long workspaceEpoch) {
        if (runId == null || runId.isBlank() || runtimeMode == null
                || homeBankingId <= 0 || botJobId <= 0 || workspaceEpoch <= 0) {
            throw new IllegalArgumentException("Locator Recovery scanner authority is invalid");
        }
        byRun.put(runId, new RecoveryAuthority(
                runId, runtimeMode, homeBankingId, botJobId, workspaceEpoch));
    }

    public void clear(String runId) {
        if (runId != null) byRun.remove(runId);
    }

    public boolean permits(
            RuntimeMode runtimeMode, int homeBankingId, int botJobId, long workspaceEpoch) {
        return authority(runtimeMode, homeBankingId, botJobId, workspaceEpoch) != null;
    }

    public String requireRunId(
            RuntimeMode runtimeMode, int homeBankingId, int botJobId, long workspaceEpoch) {
        RecoveryAuthority authority = authority(
                runtimeMode, homeBankingId, botJobId, workspaceEpoch);
        if (authority == null) {
            throw new IllegalStateException("Locator Recovery is not awaiting Page Scanner for this runtime");
        }
        return authority.runId();
    }

    private RecoveryAuthority authority(
            RuntimeMode runtimeMode, int homeBankingId, int botJobId, long workspaceEpoch) {
        RecoveryAuthority match = null;
        for (RecoveryAuthority candidate : byRun.values()) {
            if (candidate.runtimeMode() != runtimeMode
                    || candidate.homeBankingId() != homeBankingId
                    || candidate.botJobId() != botJobId
                    || candidate.workspaceEpoch() != workspaceEpoch) {
                continue;
            }
            if (match != null && !Objects.equals(match.runId(), candidate.runId())) {
                throw new IllegalStateException("Multiple Locator Recovery runs match this browser owner");
            }
            match = candidate;
        }
        return match;
    }

    private record RecoveryAuthority(
            String runId,
            RuntimeMode runtimeMode,
            int homeBankingId,
            int botJobId,
            long workspaceEpoch) {}
}
