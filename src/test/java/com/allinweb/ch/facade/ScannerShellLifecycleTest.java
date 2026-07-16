package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ScannerShellLifecycleTest {
    private final ScannerShellLifecycle lifecycle = ScannerShellLifecycle.getInstance();

    @AfterEach
    void resetLifecycle() {
        lifecycle.reset();
    }

    @Test
    void closeShellClosesDriversBeforeModal() {
        List<String> calls = new ArrayList<>();
        lifecycle.install(new ScannerShellLifecycle.Handler() {
            @Override
            public void closeWebDrivers() {
                calls.add("drivers");
            }

            @Override
            public void closeModal() {
                calls.add("modal");
            }
        });

        lifecycle.closeShell();

        assertEquals(List.of("drivers", "modal"), calls);
    }

    @Test
    void closeShellIsNoopWhenNoHandlerInstalled() {
        lifecycle.reset();

        lifecycle.closeShell();
    }
}
