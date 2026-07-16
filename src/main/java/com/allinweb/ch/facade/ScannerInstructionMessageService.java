package com.allinweb.ch.facade;

import com.allinweb.ch.model.FieldData;

public final class ScannerInstructionMessageService {
    public FieldData prependFailure(FieldData instructionMessage, String failedMessage) {
        return new FieldData(failedMessage + " - " + instructionMessage.getKey(), instructionMessage.getValue());
    }
}
