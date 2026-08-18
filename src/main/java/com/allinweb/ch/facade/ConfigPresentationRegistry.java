package com.allinweb.ch.facade;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

public final class ConfigPresentationRegistry {
    private static final ConfigPresentationRegistry INSTANCE = new ConfigPresentationRegistry();

    private final AtomicReference<ConfigPresentation> presentation = new AtomicReference<>(new NoopConfigPresentation());

    private ConfigPresentationRegistry() {}

    public static ConfigPresentationRegistry getInstance() {
        return INSTANCE;
    }

    public void install(ConfigPresentation presentation) {
        this.presentation.set(Objects.requireNonNull(presentation, "presentation"));
    }

    public void reset() {
        presentation.set(new NoopConfigPresentation());
    }

    public ConfigPresentation current() {
        return presentation.get();
    }

    private static final class NoopConfigPresentation implements ConfigPresentation {
        @Override
        public String choosePath(String mode) {
            return null;
        }

        @Override
        public void openOrganizations() {}

        @Override
        public void closeModal() {}
    }
}
