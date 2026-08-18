package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ConfigPresentationRegistryTest {
    private final ConfigPresentationRegistry registry = ConfigPresentationRegistry.getInstance();

    @AfterEach
    void resetRegistry() {
        registry.reset();
    }

    @Test
    void installsCurrentPresentation() {
        ConfigPresentation presentation = new RecordingPresentation();

        registry.install(presentation);

        assertSame(presentation, registry.current());
    }

    @Test
    void defaultPresentationIsNoop() {
        registry.reset();

        assertNull(registry.current().choosePath("directory"));
        registry.current().openOrganizations();
        registry.current().closeModal();
    }

    private static final class RecordingPresentation implements ConfigPresentation {
        @Override
        public String choosePath(String mode) {
            return mode;
        }

        @Override
        public void openOrganizations() {}

        @Override
        public void closeModal() {}
    }
}
