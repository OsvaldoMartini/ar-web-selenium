package com.allinweb.ch.component.pane;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TestRunExecutionSelectionTest {

    @Test
    void executeAllMapsToFirstBlockAndAllMode() {
        TestRunExecutionSelection selection = TestRunExecutionSelection.resolve(null, true, false);

        assertEquals(-1, selection.blockOrderNumber());
        assertFalse(selection.runSingleBlock());
    }

    @Test
    void numberedBlockInAllModeStartsThereAndContinues() {
        TestRunExecutionSelection selection = TestRunExecutionSelection.resolve(3, false, false);

        assertEquals(3, selection.blockOrderNumber());
        assertFalse(selection.runSingleBlock());
    }

    @Test
    void numberedBlockInOneModeStopsAfterSelectedScope() {
        TestRunExecutionSelection selection = TestRunExecutionSelection.resolve(3, false, true);

        assertEquals(3, selection.blockOrderNumber());
        assertTrue(selection.runSingleBlock());
    }

    @Test
    void executeAllAndOneIsRejectedDefensively() {
        assertThrows(IllegalArgumentException.class, () -> TestRunExecutionSelection.resolve(null, true, true));
    }

    @Test
    void missingOrNonPositiveNumberedBlockIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> TestRunExecutionSelection.resolve(null, false, false));
        assertThrows(IllegalArgumentException.class, () -> TestRunExecutionSelection.resolve(0, false, false));
        assertThrows(IllegalArgumentException.class, () -> TestRunExecutionSelection.resolve(-1, false, true));
    }
}
