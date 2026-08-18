package com.allinweb.ch.facade;

import com.allinweb.ch.model.BotJobLoadDTO;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

public final class NewBotJobPresentationRegistry {
    private static final NewBotJobPresentationRegistry INSTANCE = new NewBotJobPresentationRegistry();

    private final AtomicReference<NewBotJobPresentation> presentation =
            new AtomicReference<>(new NoopNewBotJobPresentation());

    private NewBotJobPresentationRegistry() {}

    public static NewBotJobPresentationRegistry getInstance() {
        return INSTANCE;
    }

    public void install(NewBotJobPresentation presentation) {
        this.presentation.set(Objects.requireNonNull(presentation, "presentation"));
    }

    public void reset() {
        presentation.set(new NoopNewBotJobPresentation());
    }

    public NewBotJobPresentation current() {
        return presentation.get();
    }

    private static final class NoopNewBotJobPresentation implements NewBotJobPresentation {
        @Override
        public void openOrganizations() {}

        @Override
        public void openBotJobAndClose(BotJobLoadDTO botJob) {}

        @Override
        public void closeModal() {}
    }
}
