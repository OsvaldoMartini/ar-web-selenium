package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.allinweb.ch.model.BotJobLoadDTO;
import com.allinweb.ch.model.ElementDTO;
import com.allinweb.ch.model.InstructionLoad;
import com.allinweb.ch.model.ScannerWorkspaceSessions;
import com.allinweb.ch.model.SplitDTO;
import com.allinweb.ch.util.ErrorMessage;
import org.junit.jupiter.api.Test;

class ScannerElementTestLookupServiceTest {

    @Test
    void resolvesBotJobInstructionAndLoadsWhenMemoryIsEmpty() {
        Ports ports = new Ports(true, null);
        ScannerElementTestLookupService service = new ScannerElementTestLookupService(ports, ports);
        InstructionLoad instruction = new InstructionLoad();
        instruction.setId(77);
        ports.instruction = instruction;

        ScannerElementTestLookupService.Result result = service.resolve(request(null, 11, null, 77), botJob(22, 33));

        assertEquals(ScannerElementTestLookupService.BOT_JOB_INSTRUCTION_TABLE, result.tableName());
        assertEquals(11, result.whereId());
        assertSame(instruction, result.instruction());
        assertNull(result.loadError());
        assertEquals("load:11:instruction", ports.loadCall);
        assertEquals("find:instruction:11:77", ports.findCall);
    }

    @Test
    void resolvesComponentInstructionFromHomeBankingId() {
        Ports ports = new Ports(false, null);
        ScannerElementTestLookupService service = new ScannerElementTestLookupService(ports, ports);

        ScannerElementTestLookupService.Result result = service.resolve(
                request(ScannerWorkspaceSessions.COMPONENT_TASKS, null, 44, 88), botJob(22, 33));

        assertEquals(ScannerElementTestLookupService.COMPONENT_INSTRUCTION_TABLE, result.tableName());
        assertEquals(44, result.whereId());
        assertNull(result.loadError());
        assertNull(ports.loadCall);
        assertEquals("find:component_instruction:44:88", ports.findCall);
    }

    @Test
    void returnsLoadErrorAndSkipsLookupWhenReloadFails() {
        ErrorMessage error = new ErrorMessage("title", "header", "detail");
        Ports ports = new Ports(true, error);
        ScannerElementTestLookupService service = new ScannerElementTestLookupService(ports, ports);

        ScannerElementTestLookupService.Result result = service.resolve(request(null, 11, null, 77), botJob(22, 33));

        assertSame(error, result.loadError());
        assertNull(result.instruction());
        assertNull(ports.findCall);
    }

    @Test
    void skipsLookupForNonTestStepOperation() {
        Ports ports = new Ports(true, null);
        ScannerElementTestLookupService service = new ScannerElementTestLookupService(ports, ports);
        SplitDTO request = request(null, 11, null, 77);
        request.setOperationId("OTHER");

        ScannerElementTestLookupService.Result result = service.resolve(request, botJob(22, 33));

        assertEquals(ScannerElementTestLookupService.BOT_JOB_INSTRUCTION_TABLE, result.tableName());
        assertEquals(11, result.whereId());
        assertNull(result.instruction());
        assertNull(result.loadError());
        assertNull(ports.loadCall);
        assertNull(ports.findCall);
    }

    private static SplitDTO request(String sessionId, Integer botJobId, Integer homeBankingId, int elementId) {
        ElementDTO element = new ElementDTO();
        element.setId(elementId);
        SplitDTO split = new SplitDTO();
        split.setSessionId(sessionId);
        split.setBotJobId(botJobId);
        split.setHomeBankingId(homeBankingId);
        split.setOperationId(ScannerElementTestLookupService.TEST_STEP_OPERATION);
        split.setElementDetails(new ElementDTO[] {element});
        return split;
    }

    private static BotJobLoadDTO botJob(int botJobId, int homeBankingId) {
        BotJobLoadDTO botJob = new BotJobLoadDTO();
        botJob.setId(botJobId);
        botJob.setHomeBankingId(homeBankingId);
        return botJob;
    }

    private static final class Ports
            implements ScannerElementTestLookupService.ListsPort, ScannerElementTestLookupService.DataPort {
        private final boolean empty;
        private final ErrorMessage loadError;
        private InstructionLoad instruction;
        private String loadCall;
        private String findCall;

        private Ports(boolean empty, ErrorMessage loadError) {
            this.empty = empty;
            this.loadError = loadError;
        }

        @Override
        public boolean isInstructionListEmpty(String tableName) {
            return empty;
        }

        @Override
        public InstructionLoad getInstructionById(String tableName, int whereId, int instructionId) {
            findCall = "find:" + tableName + ":" + whereId + ":" + instructionId;
            return instruction;
        }

        @Override
        public ErrorMessage loadInstructions(int whereId, String tableName) {
            loadCall = "load:" + whereId + ":" + tableName;
            return loadError;
        }
    }
}
