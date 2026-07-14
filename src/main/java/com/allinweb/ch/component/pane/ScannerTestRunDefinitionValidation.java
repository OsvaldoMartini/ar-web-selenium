package com.allinweb.ch.component.pane;

import com.allinweb.ch.util.ErrorMessage;

final class ScannerTestRunDefinitionValidation {

    Result validate(ScannerPreLaunchPreparation.Result definitions) {
        if (definitions.errorMessage() != null) {
            return Result.loadError(definitions.errorMessage());
        }
        if (definitions.botJobMissing()) {
            return Result.missingBotJob();
        }
        if (definitions.blocksLoaded() == null || definitions.blocksLoaded().isEmpty()) {
            return Result.emptyBlocks();
        }
        return Result.ready();
    }

    record Result(Status status, ErrorMessage errorMessage) {
        private static Result ready() {
            return new Result(Status.READY, null);
        }

        private static Result loadError(ErrorMessage errorMessage) {
            return new Result(Status.LOAD_ERROR, errorMessage);
        }

        private static Result missingBotJob() {
            return new Result(Status.MISSING_BOT_JOB, null);
        }

        private static Result emptyBlocks() {
            return new Result(Status.EMPTY_BLOCKS, null);
        }
    }

    enum Status {
        READY,
        LOAD_ERROR,
        MISSING_BOT_JOB,
        EMPTY_BLOCKS
    }
}
