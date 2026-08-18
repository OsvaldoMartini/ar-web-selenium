package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.microsoft.playwright.ElementHandle;
import com.microsoft.playwright.Locator;
import java.util.List;
import java.util.concurrent.CancellationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class PlaywrightRuntimeHealingExecutorCancellationTest {

    @AfterEach
    void clearInterruptStatus() {
        Thread.interrupted();
    }

    @Test
    void snapshotsCurrentMatchesWithoutStartingAnImplicitElementWait() {
        Locator locator = mock(Locator.class);
        ElementHandle element = mock(ElementHandle.class);
        when(locator.elementHandles()).thenReturn(List.of(element));

        List<ElementHandle> snapshot =
                PlaywrightRuntimeHealingExecutor.snapshotElements(locator);

        assertEquals(List.of(element), snapshot);
        verify(locator).elementHandles();
        verify(locator, never()).count();
        verify(locator, never()).nth(0);
    }

    @Test
    void refusesBeforeContactingPlaywrightWhenStopAlreadyInterruptedTheWorker() {
        Locator locator = mock(Locator.class);
        Thread.currentThread().interrupt();

        assertThrows(
                CancellationException.class,
                () -> PlaywrightRuntimeHealingExecutor.snapshotElements(locator));

        verifyNoInteractions(locator);
    }

    @Test
    void propagatesStopThatArrivesDuringTheDomSnapshot() {
        Locator locator = mock(Locator.class);
        when(locator.elementHandles()).thenAnswer(invocation -> {
            Thread.currentThread().interrupt();
            return List.of();
        });

        assertThrows(
                CancellationException.class,
                () -> PlaywrightRuntimeHealingExecutor.snapshotElements(locator));
    }
}
