package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.allinweb.ch.model.BotJobLoadDTO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ScannerCurrentJobContextTest {
    private final ScannerCurrentJobContext context = ScannerCurrentJobContext.getInstance();

    @AfterEach
    void clearContext() {
        context.clear();
    }

    @Test
    void returnsNullWhenNoScannerJobIsActive() {
        context.clear();

        assertNull(context.currentHomeUrlId());
    }

    @Test
    void exposesCurrentScannerHomeUrlId() {
        BotJobLoadDTO job = new BotJobLoadDTO();
        job.setHomeUrlId(77);

        context.setCurrentBotJob(job);

        assertEquals(77, context.currentHomeUrlId());
    }

    @Test
    void clearsCurrentScannerJob() {
        BotJobLoadDTO job = new BotJobLoadDTO();
        job.setHomeUrlId(77);
        context.setCurrentBotJob(job);

        context.clear();

        assertNull(context.currentHomeUrlId());
    }
}
