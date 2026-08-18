package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.allinweb.ch.model.ElementDTO;
import com.allinweb.ch.model.InstructionLoad;
import com.allinweb.ch.model.ScannerWorkspaceOperations;
import com.allinweb.ch.model.ScannerWorkspaceSessions;
import com.allinweb.ch.model.SplitDTO;
import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ScannerMobileTestForwarderTest {

    @Test
    void appliesMatchingInstructionAndForwardsTestPayload() {
        RecordingData data = new RecordingData();
        data.instructions.add(InstructionLoad.builder()
                .id(12)
                .homeBankingId(7)
                .botJobId(42)
                .blockId(5)
                .name("Login")
                .actions("click")
                .variableId(88)
                .build());
        RecordingSender sender = new RecordingSender();
        ScannerMobileTestForwarder forwarder = new ScannerMobileTestForwarder(
                ScannerMobileTestRoute.standard(), data, sender, new Gson());
        SplitDTO split = splitWithElement(12);

        forwarder.forward(split, ScannerWorkspaceOperations.TEST_CLICK_DTO);

        assertEquals(ScannerWorkspaceOperations.TEST_CLICK_DTO, split.getOperationId());
        assertEquals(12, split.getInstructionId());
        assertEquals("Login", split.getInstructionName());
        assertEquals(88, split.getElementDetails()[0].getId());
        assertEquals("send:7:" + ScannerWorkspaceSessions.MOBILE_RETURN_SERVER + ":"
                + ScannerWorkspaceOperations.TEST_CLICK_DTO, sender.sendCall);
        assertEquals(ScannerWorkspaceOperations.TEST_CLICK_DTO, sender.sentPayload.getOperationId());
    }

    @Test
    void forwardsWithoutInstructionMutationWhenThereIsNoMatch() {
        RecordingData data = new RecordingData();
        data.instructions.add(InstructionLoad.builder().id(99).name("Other").build());
        RecordingSender sender = new RecordingSender();
        ScannerMobileTestForwarder forwarder = new ScannerMobileTestForwarder(
                ScannerMobileTestRoute.standard(), data, sender, new Gson());
        SplitDTO split = splitWithElement(12);
        split.setHomeBankingId(7);

        forwarder.forward(split, ScannerWorkspaceOperations.TEST_INPUT_DTO);

        assertEquals(ScannerWorkspaceOperations.TEST_INPUT_DTO, split.getOperationId());
        assertNull(split.getInstructionId());
        assertEquals(12, split.getElementDetails()[0].getId());
        assertEquals("send:7:" + ScannerWorkspaceSessions.MOBILE_RETURN_SERVER + ":"
                + ScannerWorkspaceOperations.TEST_INPUT_DTO, sender.sendCall);
    }

    @Test
    void doesNotForwardInsertOperations() {
        RecordingData data = new RecordingData();
        RecordingSender sender = new RecordingSender();
        ScannerMobileTestForwarder forwarder = new ScannerMobileTestForwarder(
                ScannerMobileTestRoute.standard(), data, sender, new Gson());

        forwarder.forward(splitWithElement(12), ScannerWorkspaceOperations.NEW_ELEMENT_DTO);

        assertNull(sender.sendCall);
    }

    private static SplitDTO splitWithElement(int elementId) {
        SplitDTO split = new SplitDTO();
        split.setHomeBankingId(7);
        ElementDTO element = new ElementDTO();
        element.setId(elementId);
        split.setElementDetails(new ElementDTO[] {element});
        return split;
    }

    private static final class RecordingData implements ScannerMobileTestForwarder.DataPort {
        private final List<InstructionLoad> instructions = new ArrayList<>();

        @Override
        public List<InstructionLoad> instructions() {
            return instructions;
        }
    }

    private static final class RecordingSender implements ScannerMobileTestForwarder.SenderPort {
        private final Gson gson = new Gson();
        private String sendCall;
        private SplitDTO sentPayload;

        @Override
        public void sendMessageJson(int homeBankingId, String sessionId, String json, String operationId) {
            sendCall = "send:" + homeBankingId + ":" + sessionId + ":" + operationId;
            sentPayload = gson.fromJson(json, SplitDTO.class);
        }
    }
}
