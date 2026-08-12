package com.allinweb.ch.socket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ExcelDataWorkspaceSelectionTest {
    @Test
    void defaultsAndClampsSelectionAgainstTheCurrentModeRows() {
        assertNull(ExcelDataWorkspaceService.clampSelectedRow(null, 0));
        assertEquals(0, ExcelDataWorkspaceService.clampSelectedRow(null, 3));
        assertEquals(0, ExcelDataWorkspaceService.clampSelectedRow(-4, 3));
        assertEquals(1, ExcelDataWorkspaceService.clampSelectedRow(1, 3));
        assertEquals(2, ExcelDataWorkspaceService.clampSelectedRow(8, 3));
    }

    @Test
    void deletionKeepsTheSameLogicalSelectionWhenAnotherRowIsRemoved() {
        assertEquals(1, ExcelDataWorkspaceService.selectedRowAfterDelete(2, 0, 3));
        assertEquals(1, ExcelDataWorkspaceService.selectedRowAfterDelete(1, 2, 3));
        assertEquals(1, ExcelDataWorkspaceService.selectedRowAfterDelete(1, 1, 3));
        assertEquals(2, ExcelDataWorkspaceService.selectedRowAfterDelete(3, 3, 3));
        assertNull(ExcelDataWorkspaceService.selectedRowAfterDelete(0, 0, 0));
    }

    @Test
    void selectionFollowsItsLogicalRowAcrossMovesAndCrossings() {
        assertEquals(3, ExcelDataWorkspaceService.selectedRowAfterMove(1, 1, 3, 4));
        assertEquals(1, ExcelDataWorkspaceService.selectedRowAfterMove(3, 3, 1, 4));
        assertEquals(1, ExcelDataWorkspaceService.selectedRowAfterMove(2, 0, 3, 4));
        assertEquals(2, ExcelDataWorkspaceService.selectedRowAfterMove(1, 3, 0, 4));
        assertEquals(0, ExcelDataWorkspaceService.selectedRowAfterMove(0, 2, 3, 4));
    }

    @Test
    void onlyUnsavedRealDataBlocksBatchLaunch() {
        assertTrue(ExcelDataWorkspaceService.blocksLaunchForUnsavedRealData("REAL", true));
        assertFalse(ExcelDataWorkspaceService.blocksLaunchForUnsavedRealData("REAL", false));
        assertFalse(ExcelDataWorkspaceService.blocksLaunchForUnsavedRealData("SYNTHETIC", true));
        assertFalse(ExcelDataWorkspaceService.blocksLaunchForUnsavedRealData(null, true));
    }
}
