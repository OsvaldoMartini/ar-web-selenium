package com.allinweb.ch.facade;

import com.allinweb.ch.model.BlockMoveDTO;
import com.allinweb.ch.model.BotJobLoadDTO;
import com.allinweb.ch.model.ScannerWorkspaceOperations;
import com.allinweb.ch.model.ScannerWorkspaceSessions;

/** Resolves scanner block-update routing details outside the JavaFX scene. */
public final class ScannerBlockUpdateRouteService {

    public Result resolve(String incomingOperation, BlockMoveDTO move, BotJobLoadDTO currentBotJob, String previousBlock) {
        String nextPreviousBlock = transitionPreviousBlock(previousBlock, incomingOperation);
        String updateOperation = updateOperation(move);
        nextPreviousBlock = transitionPreviousBlock(nextPreviousBlock, updateOperation);
        return new Result(updateOperation, blockTable(move), whereId(move, currentBotJob), nextPreviousBlock);
    }

    private static String updateOperation(BlockMoveDTO move) {
        return isComponentSession(move)
                ? ScannerWorkspaceOperations.UPDATE_BLOCKS_COMP
                : ScannerWorkspaceOperations.UPDATE_BLOCKS;
    }

    private static String blockTable(BlockMoveDTO move) {
        return isComponentSession(move) ? "component_block" : "block";
    }

    private static int whereId(BlockMoveDTO move, BotJobLoadDTO currentBotJob) {
        Integer ownerId = isComponentSession(move)
                ? value(currentBotJob == null ? null : currentBotJob.getHomeBankingId(), move.getHomeBankingId())
                : value(currentBotJob == null ? null : currentBotJob.getId(), move.getBotJobId());
        return ownerId == null ? -1 : ownerId;
    }

    private static boolean isComponentSession(BlockMoveDTO move) {
        return ScannerWorkspaceSessions.COMPONENT_TASKS.equals(move.getSessionId());
    }

    private static Integer value(Integer preferred, Integer fallback) {
        return preferred != null ? preferred : fallback;
    }

    private static String transitionPreviousBlock(String previousBlock, String operation) {
        if (previousBlock != null && !previousBlock.equals(operation)) {
            return operation;
        }
        if (previousBlock == null) {
            return operation;
        }
        return previousBlock;
    }

    public record Result(String updateOperation, String blockTable, int whereId, String previousBlock) {}
}
