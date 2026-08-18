package com.allinweb.ch.facade;

import com.allinweb.ch.model.BotJobLoadDTO;
import com.allinweb.ch.model.InstructionLoad;
import com.allinweb.ch.model.ScannerWorkspaceSessions;
import com.allinweb.ch.model.SplitDTO;
import com.allinweb.ch.util.ErrorMessage;

/** Resolves the stored instruction backing a scanner TEST_STEP request. */
public final class ScannerElementTestLookupService {
    public static final String BOT_JOB_INSTRUCTION_TABLE = "instruction";
    public static final String COMPONENT_INSTRUCTION_TABLE = "component_instruction";
    public static final String TEST_STEP_OPERATION = "TEST_STEP";

    private final ListsPort lists;
    private final DataPort data;

    public ScannerElementTestLookupService(ListsPort lists, DataPort data) {
        this.lists = lists;
        this.data = data;
    }

    public Result resolve(SplitDTO splitDTO, BotJobLoadDTO currentBotJob) {
        Context context = context(splitDTO, currentBotJob);
        if (!isTestStep(splitDTO)) {
            return new Result(context.tableName(), context.whereId(), null, null);
        }

        // A TEST click is user-driven and infrequent. Reload every time so a non-empty cache
        // belonging to another Bot Job/organization can never resolve the same numeric ID.
        ErrorMessage loadError = data.loadInstructions(context.whereId(), context.tableName());

        InstructionLoad instruction = null;
        if (loadError == null && splitDTO.getElementDetails() != null && splitDTO.getElementDetails().length > 0) {
            Integer instructionId = splitDTO.getElementDetails()[0].getId();
            if (instructionId != null) {
                instruction = lists.getInstructionById(context.tableName(), context.whereId(), instructionId);
            }
        }

        return new Result(context.tableName(), context.whereId(), instruction, loadError);
    }

    private Context context(SplitDTO splitDTO, BotJobLoadDTO currentBotJob) {
        if (ScannerWorkspaceSessions.COMPONENT_TASKS.equals(splitDTO.getSourceSessionId())
                || ScannerWorkspaceSessions.COMPONENT_TASKS.equals(splitDTO.getSessionId())) {
            int whereId = value(splitDTO.getHomeBankingId(), value(currentBotJob == null ? null : currentBotJob.getHomeBankingId(), -1));
            return new Context(COMPONENT_INSTRUCTION_TABLE, whereId);
        }
        int whereId = value(splitDTO.getBotJobId(), value(currentBotJob == null ? null : currentBotJob.getId(), -1));
        return new Context(BOT_JOB_INSTRUCTION_TABLE, whereId);
    }

    private static boolean isTestStep(SplitDTO splitDTO) {
        return splitDTO != null
                && splitDTO.getOperationId() != null
                && TEST_STEP_OPERATION.equalsIgnoreCase(splitDTO.getOperationId());
    }

    private static int value(Integer preferred, int fallback) {
        return preferred == null ? fallback : preferred;
    }

    private record Context(String tableName, int whereId) {}

    public record Result(String tableName, int whereId, InstructionLoad instruction, ErrorMessage loadError) {}

    public interface ListsPort {
        boolean isInstructionListEmpty(String tableName);

        InstructionLoad getInstructionById(String tableName, int whereId, int instructionId);
    }

    public interface DataPort {
        ErrorMessage loadInstructions(int whereId, String tableName);
    }
}
