package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.allinweb.ch.model.PluginManifestDTO;
import com.allinweb.ch.model.ScannerWorkspaceSessions;
import com.allinweb.ch.socket.WebSocketSessionManager;
import com.google.gson.Gson;
import javax.websocket.RemoteEndpoint;
import javax.websocket.Session;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ScannerDialogPublisherTest {

    private Session session;

    @AfterEach
    void removeSession() {
        if (session != null) {
            WebSocketSessionManager.removeSession(ScannerWorkspaceSessions.SCANNER_ELEMENT_PANE, session);
        }
    }

    @Test
    void publishesAlertEventsToReactScannerSession() throws Exception {
        session = openSession();
        WebSocketSessionManager.addSession(ScannerWorkspaceSessions.SCANNER_ELEMENT_PANE, session);

        ScannerDialogPublisher publisher =
                new ScannerDialogPublisher(new WebSocketSessionManager(), new Gson());

        assertTrue(publisher.alert(
                ScannerDialogPublisher.Severity.ERROR,
                "Plugin Test",
                "Download failed",
                "Server returned 404"));

        verify(session.getBasicRemote()).sendText(contains("\"operationId\":\"scanner.dialog.alert\""));
        verify(session.getBasicRemote()).sendText(contains("\\\"kind\\\":\\\"alert\\\""));
        verify(session.getBasicRemote()).sendText(contains("\\\"severity\\\":\\\"error\\\""));
        verify(session.getBasicRemote()).sendText(contains("\\\"header\\\":\\\"Download failed\\\""));
    }

    @Test
    void publishesToastEventsToReactScannerSession() throws Exception {
        session = openSession();
        WebSocketSessionManager.addSession(ScannerWorkspaceSessions.SCANNER_ELEMENT_PANE, session);

        ScannerDialogPublisher publisher =
                new ScannerDialogPublisher(new WebSocketSessionManager(), new Gson());

        assertTrue(publisher.toast(ScannerDialogPublisher.Severity.WARNING, "Plugin cache refreshed", 4));

        verify(session.getBasicRemote()).sendText(contains("\"operationId\":\"scanner.dialog.toast\""));
        verify(session.getBasicRemote()).sendText(contains("\\\"kind\\\":\\\"toast\\\""));
        verify(session.getBasicRemote()).sendText(contains("\\\"severity\\\":\\\"warning\\\""));
        verify(session.getBasicRemote()).sendText(contains("\\\"header\\\":\\\"Plugin cache refreshed\\\""));
    }

    @Test
    void publishesProgressEventsToReactScannerSession() throws Exception {
        session = openSession();
        WebSocketSessionManager.addSession(ScannerWorkspaceSessions.SCANNER_ELEMENT_PANE, session);

        ScannerDialogPublisher publisher =
                new ScannerDialogPublisher(new WebSocketSessionManager(), new Gson());

        assertTrue(publisher.progress("plugin-download", "Downloading Plugin", "Downloading...", 0.5, 1, 2));

        verify(session.getBasicRemote()).sendText(contains("\"operationId\":\"scanner.dialog.progress\""));
        verify(session.getBasicRemote()).sendText(contains("\\\"kind\\\":\\\"progress\\\""));
        verify(session.getBasicRemote()).sendText(contains("\\\"id\\\":\\\"plugin-download\\\""));
        verify(session.getBasicRemote()).sendText(contains("\\\"progress\\\":0.5"));
        verify(session.getBasicRemote()).sendText(contains("\\\"current\\\":1"));
        verify(session.getBasicRemote()).sendText(contains("\\\"total\\\":2"));
    }

    @Test
    void publishesPluginUpdateDialogEventsToReactScannerSession() throws Exception {
        session = openSession();
        WebSocketSessionManager.addSession(ScannerWorkspaceSessions.SCANNER_ELEMENT_PANE, session);

        ScannerDialogPublisher publisher =
                new ScannerDialogPublisher(new WebSocketSessionManager(), new Gson());

        assertTrue(publisher.pluginUpdate(
                "plugins",
                true,
                java.util.Collections.singletonList(
                        new String[] {"pluginId", "Plugin Name", "1.0", "10 KB", "plugin.zip", "MISSING"})));

        verify(session.getBasicRemote()).sendText(contains("\"operationId\":\"scanner.dialog.pluginUpdate\""));
        verify(session.getBasicRemote()).sendText(contains("\\\"kind\\\":\\\"pluginUpdate\\\""));
        verify(session.getBasicRemote()).sendText(contains("\\\"pluginsDir\\\":\\\"plugins\\\""));
        verify(session.getBasicRemote()).sendText(contains("\\\"serverConfigured\\\":true"));
        verify(session.getBasicRemote()).sendText(contains("Plugin Name"));
    }

    @Test
    void publishesPluginListDialogEventsToReactScannerSession() throws Exception {
        session = openSession();
        WebSocketSessionManager.addSession(ScannerWorkspaceSessions.SCANNER_ELEMENT_PANE, session);

        ScannerDialogPublisher publisher =
                new ScannerDialogPublisher(new WebSocketSessionManager(), new Gson());
        PluginManifestDTO manifest = new PluginManifestDTO();
        manifest.setVersion("1.0");

        assertTrue(publisher.pluginList(manifest, "http://plugins", "plugins"));

        verify(session.getBasicRemote()).sendText(contains("\"operationId\":\"scanner.dialog.pluginList\""));
        verify(session.getBasicRemote()).sendText(contains("\\\"kind\\\":\\\"pluginList\\\""));
        verify(session.getBasicRemote()).sendText(contains("\\\"serverBase\\\":\\\"http://plugins\\\""));
        verify(session.getBasicRemote()).sendText(contains("\\\"pathPlugins\\\":\\\"plugins\\\""));
    }

    private Session openSession() {
        Session openSession = mock(Session.class);
        RemoteEndpoint.Basic remote = mock(RemoteEndpoint.Basic.class);
        when(openSession.isOpen()).thenReturn(true);
        when(openSession.getBasicRemote()).thenReturn(remote);
        return openSession;
    }
}
