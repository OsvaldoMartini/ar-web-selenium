package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ScannerJavaVersionServiceTest {

    private final ScannerJavaVersionService service = new ScannerJavaVersionService();

    @Test
    void readsLegacyJavaMajorVersion() {
        assertEquals(8, service.majorVersion("1.8.0_311"));
    }

    @Test
    void readsModernJavaMajorVersion() {
        assertEquals(17, service.majorVersion("17.0.1"));
    }

    @Test
    void readsSingleSegmentJavaMajorVersion() {
        assertEquals(21, service.majorVersion("21"));
    }
}
