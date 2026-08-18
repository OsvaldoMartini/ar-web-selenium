package com.allinweb.ch.facade;

import com.allinweb.ch.util.ErrorMessage;
import java.util.List;

public final class ScannerDefaultBlockService {
    private static final String DEFAULT_BLOCK = "Default Block";

    public int createIfNone(String blockTable, int ownerId, Operations operations) {
        ErrorMessage error = operations.loadBlocks(ownerId, blockTable);
        if (error != null || !operations.blocksEmpty()) {
            return -1;
        }

        error = operations.initiateBlock(blockTable, ownerId, DEFAULT_BLOCK, DEFAULT_BLOCK, 1, false);
        if (error != null) {
            operations.showOperationFailed(error);
            return -1;
        }

        List<Integer> ids = operations.createdBlockIds();
        if (!ids.isEmpty() && ids.get(0) != null && ids.get(0) > 0) {
            return ids.get(0);
        }
        return -1;
    }

    public interface Operations {
        ErrorMessage loadBlocks(int ownerId, String blockTable);

        boolean blocksEmpty();

        ErrorMessage initiateBlock(
                String blockTable,
                int ownerId,
                String blockName,
                String blockDescription,
                int blockOrder,
                boolean forceOrder);

        List<Integer> createdBlockIds();

        void showOperationFailed(ErrorMessage error);
    }
}
