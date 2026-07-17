package com.allinweb.ch.facade.scanner.testrun;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.allinweb.ch.facade.scanner.prelaunch.ScannerPreLaunchPreparation;
import com.allinweb.ch.model.BlockLoadDTO;
import com.allinweb.ch.util.ErrorMessage;
import java.util.List;
import org.junit.jupiter.api.Test;

class ScannerTestRunDefinitionValidationTest {

    @Test
    void validateAcceptsLoadedDefinitionsWithBlocks() {
        ScannerTestRunDefinitionValidation validation = new ScannerTestRunDefinitionValidation();
        ScannerPreLaunchPreparation.Result definitions =
                new ScannerPreLaunchPreparation.Result(null, List.of(), List.of(new BlockLoadDTO()), false);

        ScannerTestRunDefinitionValidation.Result result = validation.validate(definitions);

        assertEquals(ScannerTestRunDefinitionValidation.Status.READY, result.status());
    }

    @Test
    void validateReportsLoadError() {
        ScannerTestRunDefinitionValidation validation = new ScannerTestRunDefinitionValidation();
        ErrorMessage error = new ErrorMessage("Load", "failed", "Cannot load");
        ScannerPreLaunchPreparation.Result definitions =
                new ScannerPreLaunchPreparation.Result(error, List.of(), List.of(), false);

        ScannerTestRunDefinitionValidation.Result result = validation.validate(definitions);

        assertEquals(ScannerTestRunDefinitionValidation.Status.LOAD_ERROR, result.status());
        assertSame(error, result.errorMessage());
    }

    @Test
    void validateReportsMissingBotJob() {
        ScannerTestRunDefinitionValidation validation = new ScannerTestRunDefinitionValidation();
        ScannerPreLaunchPreparation.Result definitions =
                new ScannerPreLaunchPreparation.Result(null, List.of(), List.of(), true);

        ScannerTestRunDefinitionValidation.Result result = validation.validate(definitions);

        assertEquals(ScannerTestRunDefinitionValidation.Status.MISSING_BOT_JOB, result.status());
    }

    @Test
    void validateReportsEmptyBlocks() {
        ScannerTestRunDefinitionValidation validation = new ScannerTestRunDefinitionValidation();
        ScannerPreLaunchPreparation.Result definitions =
                new ScannerPreLaunchPreparation.Result(null, List.of(), List.of(), false);

        ScannerTestRunDefinitionValidation.Result result = validation.validate(definitions);

        assertEquals(ScannerTestRunDefinitionValidation.Status.EMPTY_BLOCKS, result.status());
    }
}
