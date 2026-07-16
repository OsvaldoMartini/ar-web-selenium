package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.allinweb.ch.model.FieldData;
import org.junit.jupiter.api.Test;

class ScannerInstructionMessageServiceTest {
    private final ScannerInstructionMessageService service = new ScannerInstructionMessageService();

    @Test
    void prependsFailureAndKeepsValue() {
        FieldData result = service.prependFailure(new FieldData("Click login", "1"), "Not found");

        assertEquals("Not found - Click login", result.getKey());
        assertEquals("1", result.getValue());
    }
}
