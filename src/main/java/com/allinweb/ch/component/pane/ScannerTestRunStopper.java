package com.allinweb.ch.component.pane;

final class ScannerTestRunStopper {
    private final Operations operations;

    ScannerTestRunStopper(Operations operations) {
        this.operations = operations;
    }

    boolean cancelStartup() {
        if (!operations.startupActive()) return false;
        interruptBrowser("TEST RUN \u2014 error closing browser during startup cancellation: {}");
        return true;
    }

    boolean stop(long expectedExecutionId) {
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
        interruptBrowser("TEST RUN \u2014 error closing browser on stop: {}");
        return true;
    }

    long currentExecutionId() {
        long active = operations.activeExecutionId();
        return active > 0 ? active : operations.lastSubmittedExecutionId();
    }

    boolean isExecutionComplete(long executionId) {
        return executionId <= 0 || operations.completedExecutionId() >= executionId;
    }

    String terminalState(long executionId) {
        return operations.terminalOutcome(executionId);
    }

    private void interruptBrowser(String closeWarningMessage) {
        operations.resetSingleBlock();
        operations.requestIntercept();
        try {
            operations.closeCurrentDriver();
        } catch (Exception error) {
            operations.warn(closeWarningMessage, error.getMessage());
        }
        operations.clearCurrentDriver();
    }

    interface Operations {
        boolean startupActive();

        long activeExecutionId();

        long lastSubmittedExecutionId();

        long completedExecutionId();

        boolean requestStop(long executionId);

        String terminalOutcome(long executionId);

        void resetSingleBlock();

        void requestIntercept();

        void closeCurrentDriver();

        void clearCurrentDriver();

        void info(String message, Object... args);

        void warn(String message, Object... args);
    }
}
