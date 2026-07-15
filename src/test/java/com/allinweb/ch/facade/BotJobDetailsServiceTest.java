package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.allinweb.ch.model.BotJobDetailsPersistedState;
import com.allinweb.ch.model.BotJobDetailsRequest;
import com.allinweb.ch.model.BotJobDetailsResponse;
import com.allinweb.ch.model.BotJobLoadDTO;
import com.allinweb.ch.model.BotJobToolbarContext;
import com.allinweb.ch.model.HomeBankingLoadDTO;
import com.allinweb.ch.model.ScannerWorkspaceSessions;
import com.allinweb.ch.util.ErrorMessage;
import com.google.gson.JsonObject;
import java.sql.SQLException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BotJobDetailsServiceTest {

    private BotJobDetailsWorkspaceRegistry registry;
    private FakeDataPort data;
    private BotJobDetailsService service;

    @BeforeEach
    void setUp() {
        registry = new BotJobDetailsWorkspaceRegistry();
        data = new FakeDataPort();
        service = new BotJobDetailsService(registry, data);

        HomeBankingLoadDTO organization = new HomeBankingLoadDTO();
        organization.setName("Bank");
        BotJobLoadDTO job = new BotJobLoadDTO();
        job.setId(42);
        job.setName("Payments");
        job.setDescription("Payment flow");
        job.setPriority("Web App");
        job.setHomeBankingId(7);
        job.setHomeUrlId(8);
        job.setHomeBankingLoadDTO(organization);
        registry.activate(job, false);
    }

    @Test
    void bootstrapMapsAllowlistedStateAndCorrelationFields() {
        BotJobDetailsResponse response = service.bootstrap(request("bootstrap-1", new JsonObject()));

        assertTrue(response.ok());
        assertEquals("bootstrap-1", response.requestId());
        assertEquals(42, response.botJobId());
        assertEquals("Payments", response.state().name());
        assertEquals("Bank", response.state().organizationName());
        assertEquals("TEST", response.state().environmentName());
        assertEquals(1, response.state().blocks().size());
        assertEquals(91, response.state().blocks().get(0).id());
        assertTrue(response.state().transferPathConfigured());
        assertEquals("IDLE", response.state().executionState());
    }

    @Test
    void rejectsWrongActiveJobBeforeReadingDatabase() {
        JsonObject body = new JsonObject();
        BotJobDetailsResponse response = service.bootstrap(
                new BotJobDetailsRequest(ScannerWorkspaceSessions.BOT_JOB_TASKS, "b-2", 99, body));

        assertFalse(response.ok());
        assertEquals("BOOTSTRAP_FAILED", response.errorCode());
        assertEquals(0, data.loadCount);
    }

    @Test
    void updateRequiresCurrentRevisionAndStableEnvironmentId() {
        long revision = service.currentState(42).metadataRevision();
        JsonObject staleBody = metadataBody(revision - 1, "Payments QA", 9);
        BotJobDetailsResponse stale = service.updateMetadata(request("save-stale", staleBody));
        assertFalse(stale.ok());
        assertEquals("REVISION_CONFLICT", stale.errorCode());
        assertEquals(0, data.updateCount);

        JsonObject validBody = metadataBody(revision, "Payments QA", 9);
        BotJobDetailsResponse saved = service.updateMetadata(request("save-1", validBody));
        assertTrue(saved.ok());
        assertEquals(1, data.updateCount);
        assertEquals("Payments QA", saved.state().name());
        assertEquals(9, saved.state().homeUrlId());
        assertTrue(saved.state().revision() > revision);
    }

    @Test
    void committedSaveBuildsItsResponseWithoutASecondDatabaseRead() {
        long revision = service.currentState(42).metadataRevision();
        data.failLoadsAfterUpdate = true;

        BotJobDetailsResponse saved =
                service.updateMetadata(request("save-no-reload", metadataBody(revision, "Payments QA", 9)));

        assertTrue(saved.ok());
        assertEquals("Payments QA", saved.state().name());
        assertEquals("QA", saved.state().environmentName());
    }

    @Test
    void disablesEveryProtectedCapabilityWhenTheLicenseIsInactive() {
        data.licenseActive = false;

        BotJobDetailsResponse response = service.bootstrap(request("bootstrap-unlicensed", new JsonObject()));

        assertTrue(response.ok());
        assertFalse(response.state().capabilities().canUseWorkspaceActions());
        assertFalse(response.state().capabilities().canEditMetadata());
        assertFalse(response.state().capabilities().canUsePreScan());
        assertFalse(response.state().capabilities().canShowComponents());
        assertFalse(response.state().capabilities().canExecute());
        assertFalse(response.state().capabilities().canLaunch());
        assertFalse(response.state().capabilities().canUseFileActions());
        assertFalse(response.state().capabilities().canOpenOrganizations());
    }

    @Test
    void capturesAnImmutablePersistedToolbarContextForTheCurrentWorkspaceEpoch() {
        BotJobToolbarContext context = service.captureToolbarContext(42);
        data.state = FakeDataPort.persisted("Changed later", 9, "QA", "https://qa.example");

        assertEquals(registry.require(42).workspaceEpoch(), context.workspaceEpoch());
        assertEquals(42, context.botJobId());
        assertEquals(7, context.homeBankingId());
        assertEquals(8, context.homeUrlId());
        assertEquals("Payments", context.name());
        assertEquals("Web App", context.projectType());
        assertEquals("Bank", context.organizationName());
        assertEquals("https://test.example", context.endpointUrl());
        assertEquals("Payments", context.executionBotJob().getName());
        assertNotSame(context.executionBotJob(), context.executionBotJob());
    }

    private BotJobDetailsRequest request(String requestId, JsonObject body) {
        return new BotJobDetailsRequest(ScannerWorkspaceSessions.BOT_JOB_TASKS, requestId, 42, body);
    }

    private JsonObject metadataBody(long revision, String name, int homeUrlId) {
        JsonObject body = new JsonObject();
        body.addProperty("expectedMetadataRevision", revision);
        body.addProperty("name", name);
        body.addProperty("description", "Updated");
        body.addProperty("homeUrlId", homeUrlId);
        return body;
    }

    private static final class FakeDataPort implements BotJobDetailsDataPort {
        private int loadCount;
        private int updateCount;
        private boolean failLoadsAfterUpdate;
        private boolean licenseActive = true;
        private BotJobDetailsPersistedState state = persisted("Payments", 8, "TEST", "https://test.example");

        @Override
        public BotJobDetailsPersistedState load(int botJobId) throws SQLException {
            loadCount++;
            if (botJobId != 42) throw new SQLException("wrong job");
            if (failLoadsAfterUpdate && updateCount > 0) throw new SQLException("reload unavailable");
            return state;
        }

        @Override
        public ErrorMessage updateMetadata(int botJobId, int homeUrlId, String name, String description) {
            updateCount++;
            String environmentName = homeUrlId == 9 ? "QA" : "TEST";
            String environmentUrl = homeUrlId == 9 ? "https://qa.example" : "https://test.example";
            state = persisted(name, homeUrlId, environmentName, environmentUrl);
            return null;
        }

        @Override
        public int navigationTimeSeconds() {
            return 2;
        }

        @Override
        public boolean transferPathConfigured() {
            return true;
        }

        @Override
        public boolean licenseActive() {
            return licenseActive;
        }

        private static BotJobDetailsPersistedState persisted(
                String name, int homeUrlId, String environmentName, String environmentUrl) {
            return new BotJobDetailsPersistedState(
                    42,
                    name,
                    "Payment flow",
                    "Web App",
                    true,
                    7,
                    "Bank",
                    homeUrlId,
                    environmentName,
                    environmentUrl,
                    List.of(
                            new BotJobDetailsPersistedState.Environment(8, "TEST", "https://test.example", 7),
                            new BotJobDetailsPersistedState.Environment(9, "QA", "https://qa.example", 7)),
                    List.of(new BotJobDetailsPersistedState.Block(
                            91, 1, "Login", "", 1, true, 0)));
        }
    }
}
