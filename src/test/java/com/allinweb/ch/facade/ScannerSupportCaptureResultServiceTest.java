package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ScannerSupportCaptureResultServiceTest {

    private final ScannerSupportCaptureResultService service = new ScannerSupportCaptureResultService();

    @Test
    void mapsSuccessfulDomCapture() {
        ScannerSupportCaptureResultService.AlertMessage message =
                service.domCapture(SupportCapture.CaptureResult.ok("T-1"));

        assertTrue(message.ok());
        assertEquals("DOM capture sent", message.header());
        assertEquals("Ticket: T-1", message.content());
    }

    @Test
    void mapsFailedDomCapture() {
        ScannerSupportCaptureResultService.AlertMessage message =
                service.domCapture(SupportCapture.CaptureResult.error("disabled"));

        assertFalse(message.ok());
        assertEquals("Could not send DOM capture", message.header());
        assertEquals("disabled", message.content());
    }

    @Test
    void mapsSuccessfulElementsReview() {
        ScannerSupportCaptureResultService.AlertMessage message =
                service.elementsReview(SupportCapture.CaptureResult.ok("T-2"));

        assertTrue(message.ok());
        assertEquals("Elements review sent", message.header());
        assertEquals("Ticket: T-2", message.content());
    }

    @Test
    void mapsFailedElementsReview() {
        ScannerSupportCaptureResultService.AlertMessage message =
                service.elementsReview(SupportCapture.CaptureResult.error("offline"));

        assertFalse(message.ok());
        assertEquals("Could not send elements review", message.header());
        assertEquals("offline", message.content());
    }
}
