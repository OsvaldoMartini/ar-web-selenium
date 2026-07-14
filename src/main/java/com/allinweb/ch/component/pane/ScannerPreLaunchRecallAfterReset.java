package com.allinweb.ch.component.pane;

final class ScannerPreLaunchRecallAfterReset {
    private final Operations operations;

    ScannerPreLaunchRecallAfterReset(Operations operations) {
        this.operations = operations;
    }

    void resetInstructionsAndRecall() {
        if (operations.resetInstructionExecutionFlags()) {
            operations.recallJob();
        }
    }

    interface Operations {
        boolean resetInstructionExecutionFlags();

        boolean recallJob();
    }
}
