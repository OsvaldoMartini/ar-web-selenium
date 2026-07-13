package com.allinweb.ch.component.pane;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.allinweb.ch.model.BotJobLoadDTO;
import com.allinweb.ch.model.HomeBankingLoadDTO;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class BotJobDetailsWebViewBootstrapTest {

    @Test
    void reusedLoadCallbackResolvesTheNewActiveBotJob() {
        BotJobDetailsWebViewBootstrap bootstrap = new BotJobDetailsWebViewBootstrap();
        bootstrap.activate(job(42, 7, "Organization A", "Job A"));
        bootstrap.updatePayload(42, "botJobTasks", "[{\"job\":\"A\"}]");

        Supplier<BotJobDetailsWebViewBootstrap.Context> reusedLoadCallback =
                () -> bootstrap.resolve("botJobTasks");
        assertEquals(42, reusedLoadCallback.get().botJobId());

        bootstrap.activate(job(84, 9, "Organization B", "Job B"));
        assertFalse(bootstrap.updatePayload(42, "botJobTasks", "[{\"job\":\"STALE_A\"}]"));
        assertTrue(bootstrap.updatePayload(84, "botJobTasks", "[{\"job\":\"B\"}]"));

        BotJobDetailsWebViewBootstrap.Context switched = reusedLoadCallback.get();
        assertEquals(84, switched.botJobId());
        assertEquals(9, switched.homeBankingId());
        assertEquals("Organization B", switched.organizationName());
        assertEquals("Job B", switched.botJobName());
        assertEquals("[{\"job\":\"B\"}]", switched.jsonData());
    }

    private static BotJobLoadDTO job(int id, int homeBankingId, String organization, String name) {
        HomeBankingLoadDTO homeBanking = new HomeBankingLoadDTO();
        homeBanking.setName(organization);

        BotJobLoadDTO job = new BotJobLoadDTO();
        job.setId(id);
        job.setHomeBankingId(homeBankingId);
        job.setHomeBankingLoadDTO(homeBanking);
        job.setName(name);
        return job;
    }
}
