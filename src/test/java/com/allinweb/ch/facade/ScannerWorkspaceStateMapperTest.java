package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.allinweb.ch.model.BotJobDetailsState;
import com.allinweb.ch.model.ScannerWorkspaceState;
import java.util.List;
import org.junit.jupiter.api.Test;

class ScannerWorkspaceStateMapperTest {

    @Test
    void mapsBotJobDetailsStateToScannerState() {
        ScannerWorkspaceState.Browser browser =
                new ScannerWorkspaceState.Browser("OPEN", "https://active.example", "Active", 2, true);

        ScannerWorkspaceState state = ScannerWorkspaceStateMapper.toScannerState(source(), browser);

        assertEquals(42, state.botJobId());
        assertEquals("Apre Acconto", state.botJobName());
        assertEquals(2, state.homeBankingId());
        assertEquals("https://bank.example", state.environmentUrl());
        assertEquals(browser, state.browser());
        assertEquals(1, state.blocks().size());
        assertEquals(100, state.blocks().get(0).id());
        assertEquals("Login", state.blocks().get(0).name());
        assertEquals("All - Interactive controls", state.focus().profile());
        assertTrue(state.focus().searchTerms().contains("input"));
        assertTrue(state.ocr().available());
        assertTrue(state.capabilities().canUsePageScanner());
        assertTrue(state.capabilities().canApplyElements());
        assertEquals("IDLE", state.executionState());
    }

    private BotJobDetailsState source() {
        return new BotJobDetailsState(
                9L,
                5L,
                42,
                "Apre Acconto",
                "desc",
                "Web",
                true,
                2,
                "Banca Stato",
                11,
                "Production",
                "https://bank.example",
                3,
                true,
                List.of(),
                List.of(new BotJobDetailsState.Block(100, 1, "Login", "", 1, true, 0)),
                new BotJobDetailsState.Capabilities(true, true, true, true, true, true, true, true),
                "IDLE",
                "scanner",
                false);
    }
}
