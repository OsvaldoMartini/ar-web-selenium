package com.allinweb.ch.facade;

import com.allinweb.ch.model.SplitDTO;

/** Decides whether scanner insert requests can run immediately or need a block-selection prompt. */
public final class ScannerInsertBlockSelectionService {
    private final ListsPort lists;
    private final PanePort pane;

    public ScannerInsertBlockSelectionService(ListsPort lists, PanePort pane) {
        this.lists = lists;
        this.pane = pane;
    }

    public Decision decide(SplitDTO request) {
        if (request != null && request.getBlockId() != null && request.getBlockId() > 0) {
            return Decision.INSERT_NOW;
        }
        if (lists.hasBlocks() && !pane.isRealBlockSelectedForInsert()) {
            return Decision.PROMPT_FOR_BLOCK;
        }
        return Decision.INSERT_NOW;
    }

    public enum Decision {
        INSERT_NOW,
        PROMPT_FOR_BLOCK
    }

    public interface ListsPort {
        boolean hasBlocks();
    }

    public interface PanePort {
        boolean isRealBlockSelectedForInsert();
    }
}
