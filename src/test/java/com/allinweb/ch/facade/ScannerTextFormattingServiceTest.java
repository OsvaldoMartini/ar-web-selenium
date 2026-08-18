package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class ScannerTextFormattingServiceTest {

    private final ScannerTextFormattingService service = new ScannerTextFormattingService();

    @Test
    void keepsNullAndEmptyText() {
        assertNull(service.truncate(null, 5));
        assertEquals("", service.truncate("", 5));
    }

    @Test
    void keepsShortText() {
        assertEquals("short", service.truncate("short", 5));
    }

    @Test
    void truncatesLongTextWithEllipsis() {
        assertEquals("abc...", service.truncate("abcdef", 3));
    }
}
