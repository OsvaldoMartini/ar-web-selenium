package com.allinweb.ch.facade;

import com.allinweb.ch.model.BlockLoadDTO;
import com.allinweb.ch.model.ScannerWorkspaceState;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ScannerWorkspaceBlockOptions {
    public static final String BLOCK_ID = "blockId";
    public static final String BLOCK_ORDER_NUMBER = "blockOrderNumber";
    public static final String BLOCK_NAME = "blockName";

    private ScannerWorkspaceBlockOptions() {}

    public static Map<String, Object> from(BlockLoadDTO block) {
        Map<String, Object> option = new LinkedHashMap<>();
        option.put(BLOCK_ID, block.getId());
        option.put(BLOCK_ORDER_NUMBER, block.getBlockOrderNumber());
        option.put(BLOCK_NAME, block.getName());
        return option;
    }

    public static Map<String, Object> from(ScannerWorkspaceState.Block block) {
        Map<String, Object> option = new LinkedHashMap<>();
        option.put(BLOCK_ID, block.id());
        option.put(BLOCK_ORDER_NUMBER, block.order());
        option.put(BLOCK_NAME, block.name());
        return option;
    }
}
