package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ScannerSupportSavedFileMessageServiceTest {

    private final ScannerSupportSavedFileMessageService service = new ScannerSupportSavedFileMessageService();

    @Test
    void buildsPageReviewSavedMessage() {
        ScannerSupportSavedFileMessageService.Message message =
                service.pageReview(new ScannerSupportFileSaveService.SavedSupportFile("C:\\temp\\page.support"));

        assertEquals("Support file saved", message.header());
        assertEquals(
                "File: C:\\temp\\page.support\n\n"
                        + "Drag & drop this file on the Support Portal to create a ticket.",
                message.content());
    }

    @Test
    void buildsElementsReviewSavedMessage() {
        ScannerSupportSavedFileMessageService.Message message =
                service.elementsReview(new ScannerSupportFileSaveService.SavedSupportFile("C:\\temp\\elements.support"));

        assertEquals("Elements review saved", message.header());
        assertEquals(
                "File: C:\\temp\\elements.support\n\n"
                        + "Drag & drop this file on the Support Portal to submit.",
                message.content());
    }
}
