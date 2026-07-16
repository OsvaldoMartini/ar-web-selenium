package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ScannerSupportFileChooserServiceTest {

    private final ScannerSupportFileChooserService service = new ScannerSupportFileChooserService();

    @Test
    void buildsPageReviewChooserRequest() {
        ScannerSupportFileChooserService.Request request =
                service.pageReview(new ScannerSupportFileService.SupportFile("page.support", "{}"));

        assertEquals("Save Support File", request.title());
        assertEquals("page.support", request.initialFileName());
    }

    @Test
    void buildsElementsReviewChooserRequest() {
        ScannerSupportFileChooserService.Request request =
                service.elementsReview(new ScannerSupportFileService.SupportFile("elements.support", "{}"));

        assertEquals("Save Elements Review", request.title());
        assertEquals("elements.support", request.initialFileName());
    }

    @Test
    void exposesSupportExtensionFilter() {
        assertEquals("Support Files (*.support)", service.extensionDescription());
        assertEquals("*.support", service.extensionPattern());
    }
}
