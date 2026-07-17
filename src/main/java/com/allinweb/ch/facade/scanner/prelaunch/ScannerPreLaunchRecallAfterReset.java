package com.allinweb.ch.facade.scanner.prelaunch;

public final class ScannerPreLaunchRecallAfterReset {
    private final Operations operations;

    public ScannerPreLaunchRecallAfterReset(Operations operations) {
        this.operations = operations;
    }

    public void resetInstructionsAndRecall() {
        if (operations.resetInstructionExecutionFlags()) {
            operations.recallJob();
        }
    }

    public interface Operations {
        boolean resetInstructionExecutionFlags();

        boolean recallJob();
    }
}
