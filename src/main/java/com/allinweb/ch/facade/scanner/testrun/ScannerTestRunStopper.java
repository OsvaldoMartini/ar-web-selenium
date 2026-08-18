package com.allinweb.ch.facade.scanner.testrun;

public final class ScannerTestRunStopper {
    private final Operations operations;

    public ScannerTestRunStopper(Operations operations) {
        this.operations = operations;
    }

    public boolean cancelStartup() {
        if (!operations.startupActive()) return false;
        long allocatedExecutionId = operations.activeExecutionId();
        if (allocatedExecutionId > 0L && !isExecutionComplete(allocatedExecutionId)) {
            operations.requestStop(allocatedExecutionId);
        }
        interruptExecution();
        return true;
    }

    public boolean stop(long expectedExecutionId) {
        if (expectedExecutionId <= 0
                || operations.activeExecutionId() != expectedExecutionId
                || isExecutionComplete(expectedExecutionId)) {
            operations.info("TEST RUN \u2014 ignored stale stop for execution {}", expectedExecutionId);
            return false;
        }
        operations.info("TEST RUN \u2014 stop requested");
        if (!operations.requestStop(expectedExecutionId)) {
            operations.info("TEST RUN ignored completed stop for execution {}", expectedExecutionId);
            return false;
        }
        interruptExecution();
        return true;
    }

    public long currentExecutionId() {
        long active = operations.activeExecutionId();
        return active > 0 ? active : operations.lastSubmittedExecutionId();
    }

    public boolean isExecutionComplete(long executionId) {
        return executionId <= 0 || operations.completedExecutionId() >= executionId;
    }

    public String terminalState(long executionId) {
        return operations.terminalOutcome(executionId);
    }

    private void interruptExecution() {
        operations.resetSingleBlock();
        operations.requestIntercept();
    }

    public interface Operations {
        boolean startupActive();

        long activeExecutionId();

        long lastSubmittedExecutionId();

        long completedExecutionId();

        boolean requestStop(long executionId);

        String terminalOutcome(long executionId);

        void resetSingleBlock();

        void requestIntercept();

        void info(String message, Object... args);
    }
}
