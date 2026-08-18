package com.allinweb.ch.facade.scanner;

import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ScannerRuntimeProviderTest {
    private final ScannerRuntimeProvider provider = ScannerRuntimeProvider.getInstance();

    @AfterEach
    void cleanup() {
        provider.reset();
    }

    @Test
    void returnsInstalledRuntimeSupplierResult() {
        provider.installRuntimeSupplier(() -> null);

        assertNull(provider.currentRuntime());
    }
}
