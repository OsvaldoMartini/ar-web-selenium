package com.allinweb.ch.facade.actions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.allinweb.ch.db.ScannedPageIdentity;
import com.allinweb.ch.driver.ARPlaywrightDriver;
import com.allinweb.ch.driver.ARWebDriver;
import com.allinweb.ch.facade.PlaywrightRuntimeHealingExecutor.Diagnostic;
import com.allinweb.ch.facade.PlaywrightRuntimeHealingExecutor.Result;
import com.allinweb.ch.facade.RuntimeElementHealingService;
import com.allinweb.ch.facade.RuntimeElementHealingService.Preparation;
import com.allinweb.ch.facade.RuntimeElementHealingService.Status;
import com.allinweb.ch.model.FieldData;
import com.allinweb.ch.model.InstructionLoad;
import com.allinweb.ch.util.ARConstantsEngine;
import com.allinweb.ch.util.ARPriorities;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class PlaywrightBridgeTest {

    private static final int HOME_BANKING_ID = 2;
    private static final int BOT_JOB_ID = 32;
    private static final String PAGE_URL = "https://bank.example.test/accounts";

    @Test
    void testClickUsesTheCapturedActivePageWithoutStartupMutation() {
        Fixture fixture = fixture("Submit");
        when(fixture.activePage().runtimeClick(fixture.instruction(), fixture.preparation()))
                .thenReturn(result(true, true, null, "CLICK"));

        assertTrue(withHealing(fixture, bridge -> bridge.tryPlaywrightWebAction(
                fixture.instruction(),
                new FieldData("Test", ""),
                ARConstantsEngine.CLICK,
                new HashMap<>())));

        verify(fixture.activePage()).runtimeClick(fixture.instruction(), fixture.preparation());
        verifyNoStartupPageMutation(fixture.runtime(), fixture.activePage());
    }

    @Test
    void testInputUsesTheCapturedActivePageWithoutStartupMutation() {
        Fixture fixture = fixture("Customer");
        FieldData input = new FieldData("Test", "Banca Stato");
        when(fixture.activePage().runtimeInput(
                        fixture.instruction(), input, fixture.preparation()))
                .thenReturn(result(true, true, null, "INPUT"));

        assertTrue(withHealing(fixture, bridge -> bridge.tryPlaywrightWebAction(
                fixture.instruction(), input, ARConstantsEngine.INSERT, new HashMap<>())));

        verify(fixture.activePage()).runtimeInput(
                fixture.instruction(), input, fixture.preparation());
        verifyNoStartupPageMutation(fixture.runtime(), fixture.activePage());
    }

    @Test
    void emptyOutputIsSuccessfulAndStoredAsLegitimateWebData() {
        Fixture fixture = fixture("Amount");
        Map<String, String> outputs = new HashMap<>();
        when(fixture.activePage().runtimeOutput(
                        fixture.instruction(), fixture.preparation()))
                .thenReturn(result(true, true, "", "OUTPUT"));

        assertTrue(withHealing(fixture, bridge -> bridge.tryPlaywrightWebAction(
                fixture.instruction(),
                new FieldData("Amount", ""),
                ARConstantsEngine.OUTPUT,
                outputs)));

        assertEquals("", outputs.get("189-Amount"));
        verify(fixture.activePage()).runtimeOutput(
                fixture.instruction(), fixture.preparation());
        verifyNoStartupPageMutation(fixture.runtime(), fixture.activePage());
    }

    @Test
    void missingOutputIsNotCollapsedIntoAnEmptyValue() {
        Fixture fixture = fixture("Amount");
        Map<String, String> outputs = new HashMap<>();
        when(fixture.activePage().runtimeOutput(
                        fixture.instruction(), fixture.preparation()))
                .thenReturn(result(false, false, null, "OUTPUT"));

        assertFalse(withHealing(fixture, bridge -> bridge.tryPlaywrightWebAction(
                fixture.instruction(),
                new FieldData("Amount", ""),
                ARConstantsEngine.OUTPUT,
                outputs)));

        assertFalse(outputs.containsKey("189-Amount"));
        verify(fixture.activePage()).runtimeOutput(
                fixture.instruction(), fixture.preparation());
        verifyNoStartupPageMutation(fixture.runtime(), fixture.activePage());
    }

    @Test
    void passesTheExactOwnerAndPageContextIntoRuntimeHealing() {
        Fixture fixture = fixture("Amount");
        Map<String, String> outputs = new HashMap<>();
        when(fixture.activePage().runtimeOutput(
                        fixture.instruction(), fixture.preparation()))
                .thenReturn(result(true, true, "CHF 12", "OUTPUT"));

        assertTrue(withHealing(fixture, bridge -> bridge.tryPlaywrightWebAction(
                fixture.instruction(),
                new FieldData("Amount", ""),
                ARConstantsEngine.OUTPUT,
                outputs)));

        assertEquals("CHF 12", outputs.get("189-Amount"));
        verify(fixture.healing()).prepare(
                HOME_BANKING_ID, BOT_JOB_ID, PAGE_URL, fixture.instruction());
        verify(fixture.activePage()).runtimeOutput(
                fixture.instruction(), fixture.preparation());
        verifyNoStartupPageMutation(fixture.runtime(), fixture.activePage());
    }

    private static Fixture fixture(String name) {
        ActionContext context = mock(ActionContext.class);
        ARWebDriver runtime = mock(ARWebDriver.class);
        ARPlaywrightDriver activePage = mock(ARPlaywrightDriver.class);
        ARPriorities priorities = mock(ARPriorities.class);
        RuntimeElementHealingService healing = mock(RuntimeElementHealingService.class);
        InstructionLoad instruction = new InstructionLoad();
        instruction.setId(189);
        instruction.setName(name);
        instruction.setHomeBankingId(HOME_BANKING_ID);
        instruction.setBotJobId(BOT_JOB_ID);
        Preparation preparation = new Preparation(
                Status.READY,
                HOME_BANKING_ID,
                BOT_JOB_ID,
                ScannedPageIdentity.fromLiveUrl(PAGE_URL).pageKey(),
                List.of(),
                List.of(),
                List.of(),
                List.of());

        when(context.arWebDriver()).thenReturn(runtime);
        when(context.priorities()).thenReturn(priorities);
        when(priorities.getJobId()).thenReturn(BOT_JOB_ID);
        when(runtime.isPlaywrightEnabled()).thenReturn(true);
        when(runtime.currentPlaywrightDriver()).thenReturn(activePage);
        when(activePage.isOpen()).thenReturn(true);
        when(activePage.currentUrl()).thenReturn(PAGE_URL);
        when(healing.prepare(
                        eq(HOME_BANKING_ID),
                        eq(BOT_JOB_ID),
                        eq(PAGE_URL),
                        eq(instruction)))
                .thenReturn(preparation);
        return new Fixture(context, runtime, activePage, healing, instruction, preparation);
    }

    private static boolean withHealing(Fixture fixture, BridgeCall call) {
        try (MockedStatic<RuntimeElementHealingService> singleton =
                mockStatic(RuntimeElementHealingService.class)) {
            singleton.when(RuntimeElementHealingService::getInstance)
                    .thenReturn(fixture.healing());
            return call.run(new PlaywrightBridge(fixture.context()));
        }
    }

    private static Result result(boolean succeeded, boolean found, String value, String action) {
        return new Result(
                succeeded,
                found,
                value,
                new Diagnostic(
                        succeeded ? "COMPLETED" : "TARGET_NOT_FOUND",
                        "AUTHORED",
                        action,
                        189,
                        0,
                        found ? 1 : 0,
                        succeeded,
                        succeeded,
                        succeeded,
                        succeeded,
                        succeeded ? 1 : 0));
    }

    private static void verifyNoStartupPageMutation(
            ARWebDriver runtime, ARPlaywrightDriver activePage) {
        verify(runtime, never()).getPlaywrightDriver();
        verify(activePage, never()).openOrNavigate(anyString(), anyString(), anyString());
        verify(activePage, never()).navigate(anyString());
        verify(activePage, never()).reload();
    }

    @FunctionalInterface
    private interface BridgeCall {
        boolean run(PlaywrightBridge bridge);
    }

    private record Fixture(
            ActionContext context,
            ARWebDriver runtime,
            ARPlaywrightDriver activePage,
            RuntimeElementHealingService healing,
            InstructionLoad instruction,
            Preparation preparation) {}
}
