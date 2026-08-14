package com.allinweb.ch.socket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.allinweb.ch.db.migrations.M20260807_PageScanSnapshot;
import com.allinweb.ch.facade.BotJobDetailsWorkspaceRegistry;
import com.allinweb.ch.facade.PageScanSnapshotTestState;
import com.allinweb.ch.model.BotJobLoadDTO;
import com.allinweb.ch.model.DetachedWorkspaceSessions;
import com.google.gson.JsonObject;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import javax.websocket.Session;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;

@Isolated("Mutates snapshot storage health and ARPropertyManager retention settings")
class PageMappingsWorkspaceRetentionTest {

    private static final int HOME_BANKING_ID = 7;
    private static final int BOT_JOB_ID = 42;
    @TempDir
    Path temporaryDirectory;

    private PageScanSnapshotTestState state;

    @BeforeEach
    void isolateSnapshotConfiguration() throws Exception {
        state = PageScanSnapshotTestState.isolate(temporaryDirectory);
    }

    @AfterEach
    void restoreSnapshotConfiguration() throws Exception {
        BotJobDetailsWorkspaceRegistry.getInstance().closeActive();
        state.close();
    }

    @Test
    void staleExpectedPolicyReturnsCurrentSystemPolicyAndRequiresReload() throws Exception {
        state.setPolicy(30, 2);
        Session transport = org.mockito.Mockito.mock(Session.class);
        PageMappingsWorkspaceService service = service(transport);
        JsonObject opened = service.openForBotJob(BOT_JOB_ID);
        JsonObject request = request(opened, "purge-stale-policy");
        request.addProperty("expectedRetentionDays", 29);
        request.addProperty("expectedMaxUnpinnedPerPage", 2);

        try (Connection connection = database()) {
            JsonObject response = service.purgeRetention(
                    request,
                    DetachedWorkspaceSessions.PAGE_MAPPINGS_MANAGER,
                    transport,
                    connection);

            assertFalse(response.get("ok").getAsBoolean());
            assertTrue(response.get("reloadRequired").getAsBoolean());
            assertEquals("purge-stale-policy", response.get("requestId").getAsString());
            assertEquals(opened.get("bindingEpoch").getAsString(),
                    response.get("bindingEpoch").getAsString());
            JsonObject retention = response.getAsJsonObject("retention");
            assertEquals(30, retention.get("retentionDays").getAsInt());
            assertEquals(2, retention.get("maxUnpinnedPerPage").getAsInt());
            assertEquals("SYSTEM", retention.get("policyScope").getAsString());
            assertEquals("BOT_JOB", retention.get("countScope").getAsString());
        }
    }

    @Test
    void missingExpectedPolicyFieldFailsClosedAndRequiresReload() throws Exception {
        state.setPolicy(0, 1);
        Session transport = org.mockito.Mockito.mock(Session.class);
        PageMappingsWorkspaceService service = service(transport);
        JsonObject opened = service.openForBotJob(BOT_JOB_ID);
        JsonObject request = request(opened, "purge-malformed-policy");
        request.addProperty("expectedRetentionDays", 0);

        try (Connection connection = database()) {
            JsonObject response = service.purgeRetention(
                    request,
                    DetachedWorkspaceSessions.PAGE_MAPPINGS_MANAGER,
                    transport,
                    connection);

            assertFalse(response.get("ok").getAsBoolean());
            assertTrue(response.get("reloadRequired").getAsBoolean());
            assertEquals("purge-malformed-policy", response.get("requestId").getAsString());
        }
    }

    @Test
    void stalePinSelectionReturnsCorrelatedReloadRequiredOutcome() throws Exception {
        Session transport = org.mockito.Mockito.mock(Session.class);
        PageMappingsWorkspaceService service = service(transport);
        JsonObject opened = service.openForBotJob(BOT_JOB_ID);
        JsonObject request = request(opened, "pin-stale-capture");
        request.addProperty("scanId", "missing-scan");
        request.addProperty("pinned", true);

        try (Connection connection = database()) {
            JsonObject response = service.pinSnapshot(
                    request,
                    DetachedWorkspaceSessions.PAGE_MAPPINGS_MANAGER,
                    transport,
                    connection);

            assertFalse(response.get("ok").getAsBoolean());
            assertTrue(response.get("reloadRequired").getAsBoolean());
            assertEquals("pin-stale-capture", response.get("requestId").getAsString());
            assertEquals(opened.get("bindingEpoch").getAsString(),
                    response.get("bindingEpoch").getAsString());
        }
    }

    @Test
    void lostPinCommitAcknowledgementRequiresReloadEvenWhenTheMutationCommitted()
            throws Exception {
        Session transport = org.mockito.Mockito.mock(Session.class);
        PageMappingsWorkspaceService service = service(transport);
        JsonObject opened = service.openForBotJob(BOT_JOB_ID);
        JsonObject request = request(opened, "pin-unknown-outcome");
        request.addProperty("scanId", "owned-scan");
        request.addProperty("pinned", true);

        try (Connection database = database()) {
            insertReadySnapshot(database, "owned-scan");
            Connection lostAcknowledgement = commitAcknowledgementLost(database);

            JsonObject response = service.pinSnapshot(
                    request,
                    DetachedWorkspaceSessions.PAGE_MAPPINGS_MANAGER,
                    transport,
                    lostAcknowledgement);

            assertFalse(response.get("ok").getAsBoolean());
            assertTrue(response.get("reloadRequired").getAsBoolean());
            assertEquals("pin-unknown-outcome", response.get("requestId").getAsString());
            assertTrue(response.get("message").getAsString().contains("outcome is unknown"));
            assertTrue(pinned(database, "owned-scan"));
        }
    }

    @Test
    void unconfirmedPolicyPersistenceRequiresReloadWithoutReportingSuccess() throws Exception {
        state.setPolicy(0, 0);
        state.clearConfigurationFile();
        Session transport = org.mockito.Mockito.mock(Session.class);
        PageMappingsWorkspaceService service = service(transport);
        JsonObject opened = service.openForBotJob(BOT_JOB_ID);
        JsonObject request = request(opened, "save-policy-unconfirmed");
        request.addProperty("retentionDays", 14);
        request.addProperty("maxUnpinnedPerPage", 3);

        try (Connection connection = database()) {
            JsonObject response = service.updateRetention(
                    request,
                    DetachedWorkspaceSessions.PAGE_MAPPINGS_MANAGER,
                    transport,
                    connection);

            assertFalse(response.get("ok").getAsBoolean());
            assertTrue(response.get("reloadRequired").getAsBoolean());
            assertEquals("save-policy-unconfirmed", response.get("requestId").getAsString());
            assertTrue(response.get("message").getAsString().contains("could not be confirmed"));
        }
    }

    private static PageMappingsWorkspaceService service(Session exactTransport) {
        BotJobLoadDTO botJob = new BotJobLoadDTO();
        botJob.setId(BOT_JOB_ID);
        botJob.setHomeBankingId(HOME_BANKING_ID);
        botJob.setName("Payments");
        BotJobDetailsWorkspaceRegistry.Snapshot active =
                BotJobDetailsWorkspaceRegistry.getInstance().activate(botJob, false);
        return new PageMappingsWorkspaceService(
                id -> new PageMappingsWorkspaceService.OwnerTarget(
                        HOME_BANKING_ID, id, active.workspaceEpoch(), "Payments"),
                sessionId -> new PageMappingsWorkspaceService.OwnerTarget(
                        HOME_BANKING_ID, BOT_JOB_ID, active.workspaceEpoch(), "Payments"),
                new PageMappingsWorkspaceService.WindowAccess() {
                    @Override
                    public boolean isOpen() {
                        return false;
                    }

                    @Override
                    public boolean openOrFocus(int botJobId) {
                        return true;
                    }
                },
                binding -> true,
                (previous, current) -> {},
                (sessionId, transport) -> transport == exactTransport);
    }

    private static JsonObject request(JsonObject opened, String requestId) {
        JsonObject request = new JsonObject();
        request.addProperty("requestId", requestId);
        request.addProperty("homeBankingId", opened.get("homeBankingId").getAsInt());
        request.addProperty("botJobId", opened.get("botJobId").getAsInt());
        request.addProperty("workspaceEpoch", opened.get("workspaceEpoch").getAsLong());
        request.addProperty("bindingEpoch", opened.get("bindingEpoch").getAsString());
        return request;
    }

    private static Connection database() throws Exception {
        Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:");
        new M20260807_PageScanSnapshot().apply(connection, "TEXT");
        return connection;
    }

    private static void insertReadySnapshot(Connection connection, String scanId) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO page_scan_snapshot "
                        + "(scan_id,home_banking_id,bot_job_id,home_url_id,page_key,page_url,"
                        + "captured_at,element_count,artifact_path,manifest_sha256,status,pinned) "
                        + "VALUES (?,?,?,NULL,'page','https://example.test',"
                        + "'2026-08-09T00:00:00Z',0,'artifact','sha','READY',0)")) {
            statement.setString(1, scanId);
            statement.setInt(2, HOME_BANKING_ID);
            statement.setInt(3, BOT_JOB_ID);
            assertEquals(1, statement.executeUpdate());
        }
    }

    private static boolean pinned(Connection connection, String scanId) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT pinned FROM page_scan_snapshot WHERE scan_id=?")) {
            statement.setString(1, scanId);
            try (ResultSet rows = statement.executeQuery()) {
                assertTrue(rows.next());
                return rows.getInt(1) != 0;
            }
        }
    }

    private static Connection commitAcknowledgementLost(Connection delegate) {
        return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[] {Connection.class},
                (proxy, method, arguments) -> {
                    if ("commit".equals(method.getName())) {
                        delegate.commit();
                        throw new SQLException("commit acknowledgement lost for test");
                    }
                    try {
                        return method.invoke(delegate, arguments);
                    } catch (InvocationTargetException delegatedFailure) {
                        throw delegatedFailure.getCause();
                    }
                });
    }
}
