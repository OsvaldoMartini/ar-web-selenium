package com.allinweb.ch.socket;

import java.util.List;
import javax.websocket.server.ServerEndpointConfig;

public class StompConfigurator extends ServerEndpointConfig.Configurator {

    @Override
    public String getNegotiatedSubprotocol(List<String> supported, List<String> requested) {
        // The client is requesting STOMP protocols ("v12.stomp", "v11.stomp", "v10.stomp")
        // Here we can accept one of those protocols explicitly
        for (String request : requested) {
            if (supported.contains(request)) {
                return request; // Return the requested subprotocol if supported
            }
        }
        return ""; // No subprotocol supported if none match
    }
}
