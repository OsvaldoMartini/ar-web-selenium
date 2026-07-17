package com.allinweb.ch.facade.scanner.browser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ScannerBrowserNotAttachedMessageServiceTest {

    @Test
    void messageIncludesBrowserClosedGuidanceAndWebDriverPath() {
        ScannerBrowserNotAttachedMessageService service = new ScannerBrowserNotAttachedMessageService();

        ScannerBrowserNotAttachedMessageService.Message message = service.message("C:\\drivers\\chromedriver.exe");

        assertEquals("The Browser attached with this Web Scanner is Not Active", message.title());
        assertTrue(message.header().contains("Session deleted"));
        assertTrue(message.detail().contains("C:\\drivers\\chromedriver.exe"));
        assertTrue(message.action().contains("Re-Open the Scanner Tool"));
        assertTrue(message.cause().contains("Web Browser was closed"));
        assertEquals(0, message.timeoutSeconds());
    }
}
