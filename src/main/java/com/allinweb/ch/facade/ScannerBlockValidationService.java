package com.allinweb.ch.facade;

/** Coordinates scanner block validation without depending on UI controls. */
public final class ScannerBlockValidationService {
    public Result validate(String blockTable, int ownerId, Operations operations) {
        int newBlockId = operations.createBlockIfNone(blockTable, ownerId);
        if (newBlockId > 0) {
            if (operations.loadBlocks(ownerId, blockTable)) {
                operations.refreshBlocks();
            }
            return new Result(newBlockId, 0, newBlockId, false);
        }

        SelectedBlock selectedBlock = operations.selectedBlock();
        if (selectedBlock == null || selectedBlock.blockId() < 0) {
            return new Result(-1, 0, newBlockId, true);
        }

        int executeSpecificBlock = selectedBlock.blockOrderNumber() < 0
                ? 0
                : selectedBlock.blockOrderNumber() - 1;
        return new Result(selectedBlock.blockId(), executeSpecificBlock, selectedBlock.blockId(), false);
    }

    public record SelectedBlock(int blockId, int blockOrderNumber) {}

    public record Result(
            int currentBlockId,
            int executeSpecificBlock,
            int returnBlockId,
            boolean showNoBlockSelected) {}

    public interface Operations {
        int createBlockIfNone(String blockTable, int ownerId);

        boolean loadBlocks(int ownerId, String blockTable);

        void refreshBlocks();

        SelectedBlock selectedBlock();
    }
}
