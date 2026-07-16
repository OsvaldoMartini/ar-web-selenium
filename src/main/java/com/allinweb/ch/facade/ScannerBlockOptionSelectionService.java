package com.allinweb.ch.facade;

import com.allinweb.ch.model.BlockOptions;

public final class ScannerBlockOptionSelectionService {
    public static final int CREATE_BLOCK_SENTINEL_ID = -999;
    public static final String CREATE_BLOCK_SENTINEL_TEXT = "+ Create new block\u2026";

    public BlockOptions createBlockSentinel() {
        return new BlockOptions(
                CREATE_BLOCK_SENTINEL_TEXT,
                CREATE_BLOCK_SENTINEL_TEXT,
                null,
                CREATE_BLOCK_SENTINEL_ID,
                null);
    }

    public boolean isCreateBlockSentinel(BlockOptions option) {
        return option != null
                && option.getBlockId() != null
                && option.getBlockId() == CREATE_BLOCK_SENTINEL_ID;
    }

    public boolean isRealBlock(BlockOptions option) {
        return option != null
                && option.getBlockId() != null
                && option.getBlockId() > 0
                && !isCreateBlockSentinel(option);
    }
}
