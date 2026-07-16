package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class MainShellLifecycleTest {
    private final MainShellLifecycle lifecycle = MainShellLifecycle.getInstance();

    @AfterEach
    void resetLifecycle() {
        lifecycle.reset();
    }

    @Test
    void delegatesDefaultAndSessionOpen() {
        List<String> calls = new ArrayList<>();
        lifecycle.install(new MainShellLifecycle.Handler() {
            @Override
            public void openMain(boolean enabledLicence) {
                calls.add("main:" + enabledLicence);
            }

            @Override
            public void openMain(boolean enabledLicence, String initialSessionId) {
                calls.add("main:" + enabledLicence + ":" + initialSessionId);
            }
        });

        lifecycle.openMain(true);
        lifecycle.openMain(false, "activationRequired");

        assertEquals(List.of("main:true", "main:false:activationRequired"), calls);
    }

    @Test
    void noopWhenNoHandlerInstalled() {
        lifecycle.reset();

        lifecycle.openMain(true);
        lifecycle.openMain(false, "activationRequired");
    }
}
