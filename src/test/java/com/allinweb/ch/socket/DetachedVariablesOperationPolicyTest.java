package com.allinweb.ch.socket;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.allinweb.ch.model.DetachedWorkspaceSessions;
import org.junit.jupiter.api.Test;

class DetachedVariablesOperationPolicyTest {

    @Test
    void excelWriterUsesItsDedicatedIngressPolicy() {
        assertTrue(SimpleWebSocketServer.isDetachedVariablesTransport(
                DetachedWorkspaceSessions.VARIABLES_MANAGER));
        assertTrue(SimpleWebSocketServer.isDetachedVariablesTransport(
                DetachedWorkspaceSessions.RUNTIME_VARIABLES_MANAGER));
        assertFalse(SimpleWebSocketServer.isDetachedVariablesTransport(
                DetachedWorkspaceSessions.EXCEL_WRITER_MANAGER));
    }
}
