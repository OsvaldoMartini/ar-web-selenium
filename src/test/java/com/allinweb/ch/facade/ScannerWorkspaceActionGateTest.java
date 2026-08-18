package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.allinweb.ch.model.ScannerWorkspaceAction;
import com.allinweb.ch.model.ScannerWorkspaceState;
import java.util.List;
import org.junit.jupiter.api.Test;

class ScannerWorkspaceActionGateTest {

    private final ScannerWorkspaceActionGate gate = new ScannerWorkspaceActionGate();

    @Test
    void allowsRefreshStateAndClearGridWithoutBrowser() {
        ScannerWorkspaceState state = state(false, false, false);

        assertDoesNotThrow(() -> gate.validateAllowed(ScannerWorkspaceAction.REFRESH_STATE, state));
        assertDoesNotThrow(() -> gate.validateAllowed(ScannerWorkspaceAction.CLEAR_GRID, state));
        assertDoesNotThrow(() -> gate.validateAllowed(ScannerWorkspaceAction.STOP_PRE_LAUNCH, state));
    }

    @Test
    void rejectsPageScannerWhenCapabilityIsDisabled() {
        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> gate.validateAllowed(ScannerWorkspaceAction.PAGE_SCANNER, state(true, false, true)));

        assertEquals("Page Scanner is not available for this Bot Job", error.getMessage());
    }

    @Test
    void rejectsBrowserActionsWhenBrowserIsNotScannable() {
        IllegalStateException refresh = assertThrows(
                IllegalStateException.class,
                () -> gate.validateAllowed(ScannerWorkspaceAction.REFRESH_PAGE, state(false, true, true)));
        IllegalStateException tabs = assertThrows(
                IllegalStateException.class,
                () -> gate.validateAllowed(ScannerWorkspaceAction.NEXT_TAB, state(false, true, true)));

        assertEquals("Refresh Web Page requires an open scanner browser", refresh.getMessage());
        assertEquals("Browser tab navigation requires an open scanner browser", tabs.getMessage());
    }

    @Test
    void rejectsPreLaunchWhenExecutionIsDisabled() {
        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> gate.validateAllowed(ScannerWorkspaceAction.PRE_LAUNCH, state(true, true, false)));

        assertEquals("Scanner execution is not available for this Bot Job", error.getMessage());
    }

    @Test
    void allowsActionsWhenCapabilitiesMatch() {
        ScannerWorkspaceState state = state(true, true, true);

        assertDoesNotThrow(() -> gate.validateAllowed(ScannerWorkspaceAction.PAGE_SCANNER, state));
        assertDoesNotThrow(() -> gate.validateAllowed(ScannerWorkspaceAction.REFRESH_PAGE, state));
        assertDoesNotThrow(() -> gate.validateAllowed(ScannerWorkspaceAction.PREVIOUS_TAB, state));
        assertDoesNotThrow(() -> gate.validateAllowed(ScannerWorkspaceAction.PRE_LAUNCH, state));
    }

    private ScannerWorkspaceState state(boolean scannable, boolean canPageScanner, boolean canExecute) {
        return new ScannerWorkspaceState(
                1L,
                42,
                "Apre Acconto",
                2,
                "https://bank.example",
                List.of(),
                new ScannerWorkspaceState.Browser(scannable ? "OPEN" : "CLOSED", "", "", scannable ? 1 : 0, scannable),
                new ScannerWorkspaceState.Focus("default", List.of("input")),
                new ScannerWorkspaceState.Ocr(true, "IDLE"),
                new ScannerWorkspaceState.Capabilities(true, canPageScanner, true, canExecute, true),
                "IDLE");
    }
}
