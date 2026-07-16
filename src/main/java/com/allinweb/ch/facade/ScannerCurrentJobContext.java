package com.allinweb.ch.facade;

import com.allinweb.ch.model.BotJobLoadDTO;
import java.util.concurrent.atomic.AtomicReference;

/** Holds the active scanner job identity without requiring backend code to touch JavaFX scene classes. */
public final class ScannerCurrentJobContext {
    private static final ScannerCurrentJobContext INSTANCE = new ScannerCurrentJobContext();

    private final AtomicReference<BotJobLoadDTO> currentBotJob = new AtomicReference<>();

    private ScannerCurrentJobContext() {}

    public static ScannerCurrentJobContext getInstance() {
        return INSTANCE;
    }

    public void setCurrentBotJob(BotJobLoadDTO botJob) {
        currentBotJob.set(botJob);
    }

    public void clear() {
        currentBotJob.set(null);
    }

    public Integer currentHomeUrlId() {
        BotJobLoadDTO botJob = currentBotJob.get();
        return botJob == null ? null : botJob.getHomeUrlId();
    }
}
