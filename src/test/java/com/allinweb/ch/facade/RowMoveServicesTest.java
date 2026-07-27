package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.allinweb.ch.model.SplitDTO;
import com.allinweb.ch.util.ErrorMessage;
import org.junit.jupiter.api.Test;

/**
 * DB-free unit tests for the separated row-move pipelines (Bot Job vs Components).
 * The full pipelines (revision, graph validation, persist, refresh) are covered by
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
}
