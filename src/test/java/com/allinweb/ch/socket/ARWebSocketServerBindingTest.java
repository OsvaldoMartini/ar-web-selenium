package com.allinweb.ch.socket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetAddress;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.junit.jupiter.api.Test;

class ARWebSocketServerBindingTest {

    @Test
    void sharedConnectorFactoryBindsOnlyToIpv4Loopback() throws Exception {
        Server server = new Server();
        try {
            ServerConnector connector = ARWebSocketServer.createLoopbackConnector(server, 0);
            server.addConnector(connector);

            assertEquals("127.0.0.1", connector.getHost());
            assertEquals(0, connector.getPort());
            assertTrue(InetAddress.getByName(connector.getHost()).isLoopbackAddress());
        } finally {
            server.destroy();
        }
    }
}
