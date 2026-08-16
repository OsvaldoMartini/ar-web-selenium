package com.allinweb.ch.facade;

import com.allinweb.ch.driver.ExecutionV2PageScannerDriver;
import com.allinweb.ch.facade.execution.v2.ExecutionRuntimeRunCoordinator;
import com.allinweb.ch.facade.execution.v2.ExecutionV2RuntimeSupervisor;
import com.allinweb.ch.facade.execution.v2.ExecutionV2Contracts.AuthorizedGrantFacts;
import com.allinweb.ch.facade.execution.v2.ExecutionV2Contracts.DataMode;
import java.util.Locale;

/** Resolves the explicitly selected Page Scanner browser runtime after workspace authorization. */
public final class PageScannerRuntimeSelector {
    private static final String EMPTY_REVISION = "0".repeat(64);

    private PageScannerRuntimeSelector() {}

    public enum RuntimeMode {
        JAVA_V1,
        TYPESCRIPT_PLAYWRIGHT_V2;

        public static RuntimeMode parse(String value) {
            if (value == null || value.isBlank()) return JAVA_V1;
            try {
                return valueOf(value.trim().toUpperCase(Locale.ROOT));
            } catch (RuntimeException invalid) {
                throw new IllegalArgumentException("Page Scanner runtime must be JAVA_V1 or TYPESCRIPT_PLAYWRIGHT_V2");
            }
        }
    }

    public static PreScanWorkflowService openV2(
            PreScanWorkflowService.Context context, long workspaceEpoch) {
        if (context == null || context.homeBankingId() <= 0 || context.botJobId() <= 0
                || workspaceEpoch <= 0) {
            throw new IllegalArgumentException("Page Scanner V2 owner authority is invalid");
        }
        ExecutionV2RuntimeSupervisor supervisor = ExecutionV2RuntimeSupervisor.getInstance();
        ExecutionV2RuntimeSupervisor.Status status = supervisor.status();
        if (!"READY".equals(status.state()) && !"READY_EXTERNAL".equals(status.state())) {
            throw new IllegalStateException(
                    "Execution V2 runtime is not ready. Start SERVER before scanning V2.");
        }
        ExecutionRuntimeRunCoordinator coordinator = supervisor.coordinator();
        var scanner = coordinator.openScanner(new AuthorizedGrantFacts(
                context.homeBankingId(),
                context.homeBankingId(),
                context.botJobId(),
                workspaceEpoch,
                EMPTY_REVISION,
                EMPTY_REVISION,
                DataMode.REAL));
        return PreScanWorkflowService.forDriver(new ExecutionV2PageScannerDriver(scanner));
    }
}
