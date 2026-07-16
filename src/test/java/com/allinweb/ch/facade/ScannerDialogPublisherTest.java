package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

    private Session openSession() {
        Session openSession = mock(Session.class);
        RemoteEndpoint.Basic remote = mock(RemoteEndpoint.Basic.class);
        when(openSession.isOpen()).thenReturn(true);
        when(openSession.getBasicRemote()).thenReturn(remote);
        return openSession;
    }
}
