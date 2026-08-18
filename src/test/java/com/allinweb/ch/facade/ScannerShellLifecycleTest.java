package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.allinweb.ch.model.BlockLoadDTO;
import com.allinweb.ch.model.BotJobLoadDTO;
import com.allinweb.ch.model.HomeBankingLoadDTO;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ScannerShellLifecycleTest {
    private final ScannerShellLifecycle lifecycle = ScannerShellLifecycle.getInstance();

    @AfterEach
    void resetLifecycle() {
        lifecycle.reset();
    }

    @Test
    void closeShellClosesDriversBeforeModal() {
        List<String> calls = new ArrayList<>();
        lifecycle.install(new ScannerShellLifecycle.Handler() {
            @Override
            public void openShell(HomeBankingLoadDTO homeBanking, BotJobLoadDTO botJob, BlockLoadDTO block) {}

            @Override
            public void closeWebDrivers() {
                calls.add("drivers");
            }

            @Override
            public void closeModal() {
                calls.add("modal");
            }

            @Override
            public Integer currentBotJobId() {
                return null;
            }
        });

        lifecycle.closeShell();

        assertEquals(List.of("drivers", "modal"), calls);
    }

    @Test
    void closeShellIsNoopWhenNoHandlerInstalled() {
        lifecycle.reset();

        lifecycle.closeShell();
    }

    @Test
    void delegatesOpenAndCurrentBotJob() {
        List<String> calls = new ArrayList<>();
        BotJobLoadDTO botJob = new BotJobLoadDTO();
        botJob.setId(42);
        lifecycle.install(new ScannerShellLifecycle.Handler() {
            @Override
            public void openShell(HomeBankingLoadDTO homeBanking, BotJobLoadDTO botJob, BlockLoadDTO block) {
                calls.add("open:" + botJob.getId());
            }

            @Override
            public void closeWebDrivers() {}

            @Override
            public void closeModal() {}

            @Override
            public Integer currentBotJobId() {
                return botJob.getId();
            }
        });

        lifecycle.openShell(new HomeBankingLoadDTO(), botJob, new BlockLoadDTO());

        assertEquals(List.of("open:42"), calls);
        assertEquals(42, lifecycle.currentBotJobId());
    }

    @Test
    void currentBotJobIsEmptyWhenNoHandlerInstalled() {
        lifecycle.reset();

        assertNull(lifecycle.currentBotJobId());
    }
}
