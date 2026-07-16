package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.allinweb.ch.model.SplitDTO;
import org.junit.jupiter.api.Test;

class ScannerInsertBlockSelectionServiceTest {

    @Test
    void insertsImmediatelyWhenRequestAlreadyHasBlockId() {
        ScannerInsertBlockSelectionService service = service(true, false);
        SplitDTO request = new SplitDTO();
        request.setBlockId(12);

        assertEquals(ScannerInsertBlockSelectionService.Decision.INSERT_NOW, service.decide(request));
    }

    @Test
    void promptsWhenBlocksExistAndNoRealBlockIsSelected() {
        ScannerInsertBlockSelectionService service = service(true, false);

        assertEquals(
                ScannerInsertBlockSelectionService.Decision.PROMPT_FOR_BLOCK,
                service.decide(new SplitDTO()));
    }

    @Test
    void insertsImmediatelyWhenSelectedBlockIsReal() {
        ScannerInsertBlockSelectionService service = service(true, true);

        assertEquals(ScannerInsertBlockSelectionService.Decision.INSERT_NOW, service.decide(new SplitDTO()));
    }

    @Test
    void insertsImmediatelyWhenNoBlocksExist() {
        ScannerInsertBlockSelectionService service = service(false, false);

        assertEquals(ScannerInsertBlockSelectionService.Decision.INSERT_NOW, service.decide(new SplitDTO()));
    }

    private static ScannerInsertBlockSelectionService service(boolean hasBlocks, boolean realBlockSelected) {
        return new ScannerInsertBlockSelectionService(() -> hasBlocks, () -> realBlockSelected);
    }
}
