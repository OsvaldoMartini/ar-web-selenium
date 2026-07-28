package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.allinweb.ch.model.SplitDTO;
import com.allinweb.ch.util.ErrorMessage;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * DB-free unit tests for the separated row-move pipelines (Bot Job vs Components).
 * The full pipelines (revision, persistence, refresh) are covered by
 * ComponentRowMoveRealDbTest against real data.
 */
class RowMoveServicesTest {

    @Test
    void botJobPipelineRefusesMissingRequestId() {
        SplitDTO split = new SplitDTO();
        split.setRequestId(null);
        ErrorMessage error = BotJobRowMoveService.getInstance().move(split, 1);
        assertNotNull(error, "A move without a request id must be refused");
        assertEquals("Move Instruction Refused", error.getErrorTitle());
        assertEquals("Request ID is required", error.getErrorHeader());
    }

    @Test
    void componentPipelineRefusesMissingRequestId() {
        SplitDTO split = new SplitDTO();
        split.setRequestId("   ");
        ErrorMessage error = ComponentRowMoveService.getInstance().move(split, 1);
        assertNotNull(error, "A move with a blank request id must be refused");
        assertEquals("Move Instruction Refused", error.getErrorTitle());
        assertEquals("Request ID is required", error.getErrorHeader());
    }

    @Test
    void botJobPipelineRefusesMissingCompleteLayoutBeforeRevisionValidation() {
        SplitDTO split = new SplitDTO();
        split.setRequestId("bot-empty-layout");
        split.setRowMoveLayoutVersion(2);
        split.setUpdatedRows(null);

        ErrorMessage error = BotJobRowMoveService.getInstance().move(split, 1);

        assertNotNull(error);
        assertEquals("Move Instruction Refused", error.getErrorTitle());
        assertEquals("Complete row layout is required", error.getErrorHeader());
    }

    @Test
    void componentPipelineRefusesEmptyCompleteLayoutBeforeRevisionValidation() {
        SplitDTO split = new SplitDTO();
        split.setRequestId("component-empty-layout");
        split.setRowMoveLayoutVersion(2);
        split.setUpdatedRows(List.of());

        ErrorMessage error = ComponentRowMoveService.getInstance().move(split, 1);

        assertNotNull(error);
        assertEquals("Move Instruction Refused", error.getErrorTitle());
        assertEquals("Complete row layout is required", error.getErrorHeader());
    }

    @Test
    void botJobPipelineDoesNotReportCommittedMoveAsFailedWhenRefreshFails() {
        ErrorMessage refreshError = new ErrorMessage(
                "Refresh Error", "Instructions could not be reloaded", "database temporarily busy");

        assertNull(BotJobRowMoveService.finishCommittedMove(refreshError, 5));
    }

    @Test
    void componentPipelineDoesNotReportCommittedMoveAsFailedWhenRefreshFails() {
        ErrorMessage refreshError = new ErrorMessage(
                "Refresh Error", "Components could not be reloaded", "database temporarily busy");

        assertNull(ComponentRowMoveService.finishCommittedMove(refreshError, 2));
    }
}
