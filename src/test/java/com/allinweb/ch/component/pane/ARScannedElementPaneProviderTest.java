package com.allinweb.ch.component.pane;

import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ARScannedElementPaneProviderTest {
    private final ARScannedElementPaneProvider provider = ARScannedElementPaneProvider.getInstance();

    @AfterEach
    void cleanup() {
        provider.reset();
    }

    @Test
    void returnsInstalledPaneSupplierResult() {
        provider.installPaneSupplier(() -> null);

        assertNull(provider.currentPane());
    }
}
