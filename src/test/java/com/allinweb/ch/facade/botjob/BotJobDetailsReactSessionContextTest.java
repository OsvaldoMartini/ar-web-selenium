package com.allinweb.ch.facade.botjob;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.allinweb.ch.model.BotJobLoadDTO;
import com.allinweb.ch.model.HomeBankingLoadDTO;
import com.allinweb.ch.model.ScannerWorkspaceSessions;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class BotJobDetailsReactSessionContextTest {

    @Test
    void reusedCallbackResolvesTheNewActiveBotJob() {
        BotJobDetailsReactSessionContext context = new BotJobDetailsReactSessionContext();
        context.activate(job(42, 7, "Organization A", "Job A"));
        context.updatePayload(42, ScannerWorkspaceSessions.BOT_JOB_TASKS, "[{\"job\":\"A\"}]");

        Supplier<BotJobDetailsReactSessionContext.Context> reusedCallback =
                () -> context.resolve(ScannerWorkspaceSessions.BOT_JOB_TASKS);
        assertEquals(42, reusedCallback.get().botJobId());

        context.activate(job(84, 9, "Organization B", "Job B"));
        assertFalse(context.updatePayload(42, ScannerWorkspaceSessions.BOT_JOB_TASKS, "[{\"job\":\"STALE_A\"}]"));
        assertTrue(context.updatePayload(84, ScannerWorkspaceSessions.BOT_JOB_TASKS, "[{\"job\":\"B\"}]"));

        BotJobDetailsReactSessionContext.Context switched = reusedCallback.get();
        assertEquals(84, switched.botJobId());
        assertEquals(9, switched.homeBankingId());
        assertEquals("Organization B", switched.organizationName());
        assertEquals("Job B", switched.botJobName());
        assertEquals("[{\"job\":\"B\"}]", switched.jsonData());
    }

    @Test
    void closingWorkspaceDeactivatesTheOldContext() {
        BotJobDetailsReactSessionContext context = new BotJobDetailsReactSessionContext();
        context.activate(job(42, 7, "Organization A", "Job A"));

        assertTrue(context.deactivate(42));
        assertFalse(context.deactivate(42));
        assertThrows(IllegalStateException.class, () -> context.resolve(ScannerWorkspaceSessions.BOT_JOB_TASKS));
    }

    @Test
    void reopeningTheSameJobCannotReusePayloadFromTheClosedGeneration() {
        BotJobDetailsReactSessionContext context = new BotJobDetailsReactSessionContext();
        BotJobLoadDTO job = job(42, 7, "Organization A", "Job A");
        context.activate(job);
        assertTrue(context.updatePayload(42, ScannerWorkspaceSessions.BOT_JOB_TASKS, "[{\"generation\":1}]"));
        assertTrue(context.deactivate(42));

        context.activate(job);

        assertEquals("[]", context.resolve(ScannerWorkspaceSessions.BOT_JOB_TASKS).jsonData());
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
