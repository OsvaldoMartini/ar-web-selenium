package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class OrganizationManagerLifecycleTest {
    private final OrganizationManagerLifecycle lifecycle = OrganizationManagerLifecycle.getInstance();

    @AfterEach
    void resetLifecycle() {
        lifecycle.reset();
    }

    @Test
    void delegatesOpenAndClose() {
        List<String> calls = new ArrayList<>();
        lifecycle.install(new OrganizationManagerLifecycle.Handler() {
            @Override
            public void openOrganizations() {
                calls.add("open");
            }

            @Override
            public void closeModal() {
                calls.add("close");
            }
        });

        lifecycle.openOrganizations();
        lifecycle.closeModal();

        assertEquals(List.of("open", "close"), calls);
    }

    @Test
    void noopWhenNoHandlerInstalled() {
        lifecycle.reset();

        lifecycle.openOrganizations();
        lifecycle.closeModal();
    }
}
