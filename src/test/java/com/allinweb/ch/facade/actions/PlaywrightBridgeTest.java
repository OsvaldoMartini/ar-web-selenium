package com.allinweb.ch.facade.actions;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.allinweb.ch.driver.ARPlaywrightDriver;
import com.allinweb.ch.driver.ARWebDriver;
import com.allinweb.ch.model.FieldData;
import com.allinweb.ch.model.InstructionLoad;
import com.allinweb.ch.util.ARConstantsEngine;
import org.junit.jupiter.api.Test;

class PlaywrightBridgeTest {

    @Test
    void testClickUsesTheCapturedActivePageWithoutStartupMutation() {
        ActionContext context = mock(ActionContext.class);
        ARWebDriver runtime = mock(ARWebDriver.class);
        ARPlaywrightDriver activePage = mock(ARPlaywrightDriver.class);
        InstructionLoad instruction = new InstructionLoad();
        when(context.arWebDriver()).thenReturn(runtime);
        when(runtime.isPlaywrightEnabled()).thenReturn(true);
        when(runtime.currentPlaywrightDriver()).thenReturn(activePage);
        when(activePage.isOpen()).thenReturn(true);
        when(activePage.click(instruction)).thenReturn(true);

        assertTrue(new PlaywrightBridge(context)
                .tryPlaywrightWebAction(instruction, new FieldData("Test", ""), ARConstantsEngine.CLICK));

        verify(activePage).click(instruction);
        verifyNoStartupPageMutation(runtime, activePage);
    }

    @Test
    void testInputUsesTheCapturedActivePageWithoutStartupMutation() {
        ActionContext context = mock(ActionContext.class);
        ARWebDriver runtime = mock(ARWebDriver.class);
        ARPlaywrightDriver activePage = mock(ARPlaywrightDriver.class);
        InstructionLoad instruction = new InstructionLoad();
        FieldData input = new FieldData("Test", "Banca Stato");
        when(context.arWebDriver()).thenReturn(runtime);
        when(runtime.isPlaywrightEnabled()).thenReturn(true);
        when(runtime.currentPlaywrightDriver()).thenReturn(activePage);
        when(activePage.isOpen()).thenReturn(true);
        when(activePage.fill(instruction, input)).thenReturn(true);

        assertTrue(new PlaywrightBridge(context)
                .tryPlaywrightWebAction(instruction, input, ARConstantsEngine.INSERT));

        verify(activePage).fill(instruction, input);
        verifyNoStartupPageMutation(runtime, activePage);
    }

    private static void verifyNoStartupPageMutation(ARWebDriver runtime, ARPlaywrightDriver activePage) {
        verify(runtime, never()).getPlaywrightDriver();
        verify(activePage, never()).openOrNavigate(anyString(), anyString(), anyString());
        verify(activePage, never()).navigate(anyString());
        verify(activePage, never()).reload();
    }
}
