package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

class ScannerSupportCaptureSendServiceTest {

    private final ScannerSupportCaptureSendService service = new ScannerSupportCaptureSendService();

    @Test
    void sendsDomCaptureThroughSupportCapture() {
        SupportCapture.CaptureResult result = service.sendDomCapture();

        assertFalse(result.isOk());
        assertEquals("Support upload disabled", result.error());
    }

    @Test
    void sendsElementsReviewThroughSupportCapture() {
        SupportCapture.CaptureResult result = service.sendElementsReview("[]", "Check it");

        assertFalse(result.isOk());
        assertEquals("Support upload disabled", result.error());
    }
}
