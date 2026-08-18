package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.allinweb.ch.db.ScannedPageIdentity;
import com.allinweb.ch.facade.RuntimeElementHealingService.Preparation;
import com.allinweb.ch.facade.RuntimeElementHealingService.Status;
import com.allinweb.ch.model.InstructionLoad;
import com.microsoft.playwright.ElementHandle;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PlaywrightRuntimeHealingExecutorRenderWaitTest {
    private static final String URL = "https://example.test/login";

    @Test
    void retriesTheCompleteV1ResolutionUntilALateRenderedElementIsActionable() {
        Page page = mock(Page.class);
        Locator locator = mock(Locator.class);
        ElementHandle element = mock(ElementHandle.class);
        InstructionLoad instruction = instruction("//input[@id='iban']", "input");
        when(page.isClosed()).thenReturn(false);
        when(page.url()).thenReturn(URL);
        when(page.locator("xpath=//input[@id='iban']")).thenReturn(locator);
        when(locator.elementHandles()).thenReturn(List.of(), List.of(element));
        when(element.evaluate(anyString(), any())).thenReturn(validation(true, false, false));
        when(element.evaluate(anyString())).thenReturn("input");

        PlaywrightRuntimeHealingExecutor.Result result =
                new PlaywrightRuntimeHealingExecutor(250, 1)
                        .execute(page, instruction, null,
                                PlaywrightRuntimeHealingExecutor.Action.CLICK, preparation());

        assertTrue(result.succeeded());
        assertEquals("COMPLETED", result.diagnostic().code());
        verify(locator, times(2)).elementHandles();
    }

    @Test
    void reportsLocatedDisabledControlWithoutAttemptingOrOpeningLocatorRecovery() {
        Page page = mock(Page.class);
        Locator locator = mock(Locator.class);
        ElementHandle element = mock(ElementHandle.class);
        InstructionLoad instruction = instruction("//button[@id='avanti']", "button");
        when(page.isClosed()).thenReturn(false);
        when(page.url()).thenReturn(URL);
        when(page.locator("xpath=//button[@id='avanti']")).thenReturn(locator);
        when(locator.elementHandles()).thenReturn(List.of(element));
        when(element.evaluate(anyString(), any())).thenReturn(validation(false, true, false));

        PlaywrightRuntimeHealingExecutor.Result result =
                new PlaywrightRuntimeHealingExecutor(20, 1)
                        .execute(page, instruction, null,
                                PlaywrightRuntimeHealingExecutor.Action.CLICK, preparation());

        assertEquals("ELEMENT_DISABLED", result.diagnostic().code());
        assertEquals(0, result.diagnostic().physicalAttempts());
    }

    private static InstructionLoad instruction(String xpath, String tag) {
        InstructionLoad instruction = new InstructionLoad();
        instruction.setId(1779);
        instruction.setXpath(xpath);
        instruction.setTagName(tag);
        return instruction;
    }

    private static Preparation preparation() {
        return new Preparation(
                Status.READY,
                2,
                5,
                ScannedPageIdentity.fromLiveUrl(URL).pageKey(),
                List.of(),
                List.of(),
                List.of(),
                List.of());
    }

    private static Map<String, Object> validation(
            boolean actionOk, boolean disabled, boolean readonly) {
        return Map.of(
                "visible", true,
                "tagOk", true,
                "actionOk", actionOk,
                "frameOk", true,
                "shadowOk", true,
                "disabled", disabled,
                "readonly", readonly,
                "actionKindSupported", true);
    }
}
