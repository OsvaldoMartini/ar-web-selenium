package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class NewBotJobManagerLifecycleTest {
    private final NewBotJobManagerLifecycle lifecycle = NewBotJobManagerLifecycle.getInstance();

    @AfterEach
    void resetLifecycle() {
        lifecycle.reset();
    }

    @Test
    void delegatesOpenAndClose() {
        List<String> calls = new ArrayList<>();
        lifecycle.install(new NewBotJobManagerLifecycle.Handler() {
            @Override
            public void openNewBotJob(boolean enabledLicence) {
                calls.add("open:" + enabledLicence);
            }

            @Override
            public void closeModal() {
                calls.add("close");
            }
        });

        lifecycle.openNewBotJob(false);
        lifecycle.closeModal();

        assertEquals(List.of("open:false", "close"), calls);
    }

    @Test
    void noopWhenNoHandlerInstalled() {
        lifecycle.reset();

        lifecycle.openNewBotJob(true);
        lifecycle.closeModal();
    }
}
