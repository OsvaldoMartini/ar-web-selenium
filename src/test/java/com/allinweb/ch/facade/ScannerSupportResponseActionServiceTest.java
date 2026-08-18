package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ScannerSupportResponseActionServiceTest {

    private final ScannerSupportResponseActionService service = new ScannerSupportResponseActionService();

    @Test
    void mapsKnownActions() {
        assertEquals(ScannerSupportResponseActionService.Action.SEND, service.actionOf("send"));
        assertEquals(ScannerSupportResponseActionService.Action.SAVE, service.actionOf("save"));
        assertEquals(ScannerSupportResponseActionService.Action.CANCEL, service.actionOf("cancel"));
    }

    @Test
    void mapsUnknownActions() {
        assertEquals(ScannerSupportResponseActionService.Action.UNKNOWN, service.actionOf(null));
        assertEquals(ScannerSupportResponseActionService.Action.UNKNOWN, service.actionOf("other"));
    }

    @Test
    void detectsDomReviewCancellation() {
        assertTrue(service.isDomReviewCancelled(null, "send"));
        assertTrue(service.isDomReviewCancelled("<html></html>", "cancel"));
        assertFalse(service.isDomReviewCancelled("<html></html>", "save"));
    }

    @Test
    void detectsElementsReviewCancellation() {
        assertTrue(service.isElementsReviewCancelled("cancel", "message"));
        assertTrue(service.isElementsReviewCancelled("send", null));
        assertTrue(service.isElementsReviewCancelled("send", " "));
        assertFalse(service.isElementsReviewCancelled("send", "message"));
    }
}
