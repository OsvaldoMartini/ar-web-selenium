package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.allinweb.ch.model.ErrorMessage;
import com.allinweb.ch.model.SplitDTO;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the DB-free concerns of the extracted ROW_MOVE pipeline.
 * The full pipeline (revision, graph validation, persist, refresh) is covered by
 * ComponentRowMoveRealDbTest against real data.
 */
class RowMoveServiceTest {

    private final RowMoveService service = RowMoveService.getInstance();

    @Test
    void refusesMissingRequestId() {
        SplitDTO split = new SplitDTO();
        split.setRequestId(null);
        ErrorMessage error = service.move(split, "instruction", "block", 1);
        assertNotNull(error, "A move without a request id must be refused");
        assertEquals("Move Instruction Refused", error.getErrorTitle());
        assertEquals("Request ID is required", error.getErrorHeader());
    }

    @Test
    void refusesBlankRequestId() {
        SplitDTO split = new SplitDTO();
        split.setRequestId("   ");
        ErrorMessage error = service.move(split, "component_instruction", "component_block", 1);
        assertNotNull(error, "A move with a blank request id must be refused");
        assertEquals("Move Instruction Refused", error.getErrorTitle());
    }
}
