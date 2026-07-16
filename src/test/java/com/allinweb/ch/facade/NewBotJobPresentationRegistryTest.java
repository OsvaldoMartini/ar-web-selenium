package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertSame;

import com.allinweb.ch.model.BotJobLoadDTO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class NewBotJobPresentationRegistryTest {
    private final NewBotJobPresentationRegistry registry = NewBotJobPresentationRegistry.getInstance();

    @AfterEach
    void resetRegistry() {
        registry.reset();
    }

    @Test
    void installsCurrentPresentation() {
        NewBotJobPresentation presentation = new RecordingPresentation();

        registry.install(presentation);

        assertSame(presentation, registry.current());
    }

    @Test
    void defaultPresentationIsNoop() {
        registry.reset();

        registry.current().closeModal();
    }

    private static final class RecordingPresentation implements NewBotJobPresentation {
        @Override
        public void openOrganizations() {}

        @Override
        public void openBotJobAndClose(BotJobLoadDTO botJob) {}

        @Override
        public void closeModal() {}
    }
}
