package com.allinweb.ch.facade.actions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.allinweb.ch.driver.ARPlaywrightDriver;
import com.allinweb.ch.driver.ARWebDriver;
import com.allinweb.ch.facade.PerformDataBase;
import com.allinweb.ch.facade.PlaywrightActionExecutor.TextResult;
import com.allinweb.ch.facade.ScannedElementResolver;
import com.allinweb.ch.model.FieldData;
import com.allinweb.ch.model.InstructionLoad;
import com.allinweb.ch.model.ScannedElement;
import com.allinweb.ch.util.ARConstantsEngine;
import com.allinweb.ch.util.ARPriorities;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

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
                .tryPlaywrightWebAction(
                        instruction,
                        new FieldData("Test", ""),
                        ARConstantsEngine.CLICK,
                        new HashMap<>()));

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
                .tryPlaywrightWebAction(
                        instruction,
                        input,
                        ARConstantsEngine.INSERT,
                        new HashMap<>()));

        verify(activePage).fill(instruction, input);
        verifyNoStartupPageMutation(runtime, activePage);
    }

    @Test
    void emptyOutputIsSuccessfulAndStoredAsLegitimateWebData() {
        ActionContext context = mock(ActionContext.class);
        ARWebDriver runtime = mock(ARWebDriver.class);
        ARPlaywrightDriver activePage = mock(ARPlaywrightDriver.class);
        InstructionLoad instruction = new InstructionLoad();
        instruction.setId(189);
        instruction.setName("Amount");
        Map<String, String> outputs = new HashMap<>();
        when(context.arWebDriver()).thenReturn(runtime);
        when(runtime.isPlaywrightEnabled()).thenReturn(true);
        when(runtime.currentPlaywrightDriver()).thenReturn(activePage);
        when(activePage.isOpen()).thenReturn(true);
        when(activePage.textResult(instruction)).thenReturn(TextResult.found(""));

        assertTrue(new PlaywrightBridge(context)
                .tryPlaywrightWebAction(
                        instruction,
                        new FieldData("Amount", ""),
                        ARConstantsEngine.OUTPUT,
                        outputs));

        assertEquals("", outputs.get("189-Amount"));
        verify(activePage).textResult(instruction);
        verify(activePage, never()).text(instruction);
        verifyNoStartupPageMutation(runtime, activePage);
    }

    @Test
    void missingOutputIsNotCollapsedIntoAnEmptyValue() {
        ActionContext context = mock(ActionContext.class);
        ARWebDriver runtime = mock(ARWebDriver.class);
        ARPlaywrightDriver activePage = mock(ARPlaywrightDriver.class);
        InstructionLoad instruction = new InstructionLoad();
        instruction.setId(189);
        instruction.setName("Amount");
        Map<String, String> outputs = new HashMap<>();
        when(context.arWebDriver()).thenReturn(runtime);
        when(runtime.isPlaywrightEnabled()).thenReturn(true);
        when(runtime.currentPlaywrightDriver()).thenReturn(activePage);
        when(activePage.isOpen()).thenReturn(true);
        when(activePage.textResult(instruction)).thenReturn(TextResult.missing());

        assertFalse(new PlaywrightBridge(context)
                .tryPlaywrightWebAction(
                        instruction,
                        new FieldData("Amount", ""),
                        ARConstantsEngine.OUTPUT,
                        outputs));

        assertFalse(outputs.containsKey("189-Amount"));
        verify(activePage).textResult(instruction);
        verify(activePage, never()).text(instruction);
        verifyNoStartupPageMutation(runtime, activePage);
    }

    @Test
    void missingOutputUsesThePageScopedRegistryAndPreservesAHealedEmptyValue() {
        ActionContext context = mock(ActionContext.class);
        ARWebDriver runtime = mock(ARWebDriver.class);
        ARPlaywrightDriver activePage = mock(ARPlaywrightDriver.class);
        ARPriorities priorities = mock(ARPriorities.class);
        PerformDataBase database = mock(PerformDataBase.class);
        InstructionLoad instruction = new InstructionLoad();
        instruction.setId(189);
        instruction.setName("Amount");
        instruction.setXpath("//*[@id='stale-amount']");
        ScannedElement registryElement = new ScannedElement();
        registryElement.setXPath("//*[@id='current-amount']");
        registryElement.setCssSelector("#current-amount");
        ScannedElementResolver.Result registryResult = new ScannedElementResolver.Result(
                registryElement,
                ScannedElementResolver.Strategy.NAME_UNIQUE,
                0.85);
        Map<String, String> outputs = new HashMap<>();

        when(context.arWebDriver()).thenReturn(runtime);
        when(context.priorities()).thenReturn(priorities);
        when(priorities.getJobId()).thenReturn(32);
        when(runtime.isPlaywrightEnabled()).thenReturn(true);
        when(runtime.currentPlaywrightDriver()).thenReturn(activePage);
        when(activePage.isOpen()).thenReturn(true);
        when(activePage.currentUrl()).thenReturn("https://bank.example.test/accounts");
        when(activePage.textResult(instruction)).thenReturn(TextResult.missing());
        when(activePage.textResult(argThat(candidate ->
                        candidate != instruction
                                && "//*[@id='current-amount']".equals(candidate.getXpath())
                                && "#current-amount".equals(candidate.getCssSelector()))))
                .thenReturn(TextResult.found(""));
        when(database.resolveScannedElementByBotJobAndPage(
                        32,
                        "https://bank.example.test/accounts",
                        instruction))
                .thenReturn(registryResult);

        try (MockedStatic<PerformDataBase> databaseSingleton = mockStatic(PerformDataBase.class)) {
            databaseSingleton.when(PerformDataBase::getInstance).thenReturn(database);

            assertTrue(new PlaywrightBridge(context)
                    .tryPlaywrightWebAction(
                            instruction,
                            new FieldData("Amount", ""),
                            ARConstantsEngine.OUTPUT,
                            outputs));
        }

        assertEquals("", outputs.get("189-Amount"));
        verify(database).resolveScannedElementByBotJobAndPage(
                32,
                "https://bank.example.test/accounts",
                instruction);
        verify(activePage).textResult(instruction);
        verify(activePage).textResult(argThat(candidate ->
                candidate != instruction
                        && "//*[@id='current-amount']".equals(candidate.getXpath())
                        && "#current-amount".equals(candidate.getCssSelector())));
        verify(activePage, never()).text(instruction);
        verifyNoStartupPageMutation(runtime, activePage);
    }

    private static void verifyNoStartupPageMutation(ARWebDriver runtime, ARPlaywrightDriver activePage) {
        verify(runtime, never()).getPlaywrightDriver();
        verify(activePage, never()).openOrNavigate(anyString(), anyString(), anyString());
        verify(activePage, never()).navigate(anyString());
        verify(activePage, never()).reload();
    }
}
