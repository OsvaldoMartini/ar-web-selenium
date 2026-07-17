package com.allinweb.ch.facade;

import com.allinweb.ch.model.BlockDetailsDTO;
import com.allinweb.ch.model.BlockLoadDTO;
import com.allinweb.ch.model.BlockMoveDTO;
import com.allinweb.ch.util.ErrorMessage;
import java.util.List;

/** Persists a block created from the scanner modal without depending on UI controls. */
public final class ScannerModalBlockCreationService {
    public ErrorMessage create(String name, int targetOrder, Context context, Operations operations) {
        List<BlockLoadDTO> toRenumber =
                context.planner().buildRenumberPlan(context.botJobId(), targetOrder, operations.blocks());
        if (!toRenumber.isEmpty()) {
            ErrorMessage error = operations.updateBlockOrder(context.botJobId(), toRenumber);
            if (error != null) {
                return error;
            }
            operations.updateMemoryBlockOrder(context.botJobId(), toRenumber);
        }

        BlockDetailsDTO newBlock = new BlockDetailsDTO();
        newBlock.setBlockName(name);
        newBlock.setBlockOrderNumber(targetOrder);
        newBlock.setBotJobId(context.botJobId());
        newBlock.setActive(true);
        newBlock.setForceOrder(true);
        ErrorMessage error = operations.insertBlock(context.botJobId(), newBlock);
        if (error != null) {
            return error;
        }

        operations.reloadBlocks(context.botJobId());
        operations.reloadCompleteJobs(context.botJobId());
        try {
            operations.publishUpdateBlocks(context.homeBankingId(), new BlockMoveDTO());
        } catch (Exception broadcastError) {
            operations.publishUpdateBlocksFailed(broadcastError);
        }
        return null;
    }

    public record Context(int botJobId, int homeBankingId, ScannerCreateBlockPlanner planner) {}

    public interface Operations {
        List<BlockLoadDTO> blocks();

        ErrorMessage updateBlockOrder(int botJobId, List<BlockLoadDTO> toRenumber);

        void updateMemoryBlockOrder(int botJobId, List<BlockLoadDTO> toRenumber);

        ErrorMessage insertBlock(int botJobId, BlockDetailsDTO block);

        void reloadBlocks(int botJobId);

        void reloadCompleteJobs(int botJobId);

        void publishUpdateBlocks(int homeBankingId, BlockMoveDTO signal);

        void publishUpdateBlocksFailed(Exception error);
    }
}
