package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.allinweb.ch.model.FieldData;
import com.allinweb.ch.model.InstructionLoad;
import com.microsoft.playwright.Keyboard;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import org.junit.jupiter.api.Test;

class PlaywrightActionExecutorSingleShotTest {

    @Test
    void clickOnceDoesNotRunSecondClickOrCoordinateFallbackAfterLocatorClickFailure() {
        Page page = mock(Page.class);
        Locator locator = mock(Locator.class);
        Locator target = mock(Locator.class);
        InstructionLoad instruction = new InstructionLoad();
        instruction.setCssSelector("#target");
        instruction.setCoordinates("10,20");

        when(page.locator("#target")).thenReturn(locator);
        when(locator.count()).thenReturn(1);
        when(locator.first()).thenReturn(target);
        org.mockito.Mockito.doThrow(new RuntimeException("blocked"))
                .when(target)
                .click(any(Locator.ClickOptions.class));

        assertFalse(new PlaywrightActionExecutor().clickOnce(page, instruction));

        verify(target, times(1)).click(any(Locator.ClickOptions.class));
        verify(target, never()).dispatchEvent("click");
        verify(page, never()).mouse();
    }

    @Test
    void fillOnceDoesNotClickCoordinatesAfterLocatorFillFailure() {
        Page page = mock(Page.class);
        Locator locator = mock(Locator.class);
        Locator target = mock(Locator.class);
        InstructionLoad instruction = new InstructionLoad();
        instruction.setCssSelector("#field");
        instruction.setCoordinates("10,20");

        when(page.locator("#field")).thenReturn(locator);
        when(locator.count()).thenReturn(1);
        when(locator.first()).thenReturn(target);
        when(target.evaluate(any(String.class))).thenReturn(Boolean.TRUE);
        org.mockito.Mockito.doThrow(new RuntimeException("readonly"))
                .when(target)
                .fill(any(String.class), any(Locator.FillOptions.class));

        assertFalse(new PlaywrightActionExecutor().fillOnce(page, instruction, new FieldData("field", "abc")));

        verify(target, times(1)).fill(any(String.class), any(Locator.FillOptions.class));
        verify(page, never()).mouse();
    }

    @Test
    void fillOncePressesEnterAndTabWhenForceCoordinatesRequestThem() {
        Page page = mock(Page.class);
        Keyboard keyboard = mock(Keyboard.class);
        Locator locator = mock(Locator.class);
        Locator target = mock(Locator.class);
        InstructionLoad instruction = new InstructionLoad();
        instruction.setCssSelector("#field");
        instruction.setForceCoordinates("ET");

        when(page.locator("#field")).thenReturn(locator);
        when(page.keyboard()).thenReturn(keyboard);
        when(locator.count()).thenReturn(1);
        when(locator.first()).thenReturn(target);
        when(target.evaluate(any(String.class))).thenReturn(Boolean.TRUE);

        org.junit.jupiter.api.Assertions.assertTrue(
                new PlaywrightActionExecutor().fillOnce(page, instruction, new FieldData("field", "abc")));

        verify(target, times(1)).fill(any(String.class), any(Locator.FillOptions.class));
        verify(keyboard, times(1)).press("Enter");
        verify(keyboard, times(1)).press("Tab");
    }
}
