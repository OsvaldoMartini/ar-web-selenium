package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ConfigManagerLifecycleTest {
    private final ConfigManagerLifecycle lifecycle = ConfigManagerLifecycle.getInstance();

    @AfterEach
    void resetLifecycle() {
        lifecycle.reset();
    }

    @Test
    void delegatesOpenAndClose() {
        List<String> calls = new ArrayList<>();
        lifecycle.install(new ConfigManagerLifecycle.Handler() {
            @Override
            public void openConfig(boolean enabledLicence) {
                calls.add("open:" + enabledLicence);
            }

            @Override
            public void closeModal() {
                calls.add("close");
            }
        });

        lifecycle.openConfig(true);
        lifecycle.closeModal();

        assertEquals(List.of("open:true", "close"), calls);
    }

    @Test
    void noopWhenNoHandlerInstalled() {
        lifecycle.reset();

        lifecycle.openConfig(false);
        lifecycle.closeModal();
    }
}
