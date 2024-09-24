package com.allinweb.ch.socket;

import java.util.HashMap;
import java.util.Map;

public class StompFrame {
    private String command;
    private Map<String, String> headers;
    private String body;

    public StompFrame(String command) {
        this.command = command;
        this.headers = new HashMap<>();
    }

    public String getCommand() {
        return command;
    }

    public void setHeader(String key, String value) {
        headers.put(key, value);
    }

    public String getHeader(String key) {
        return headers.get(key);
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public String getBody() {
        return body;
    }

    // Convert the STOMP frame back to its string format to send over WebSocket
    public String toString() {
        StringBuilder frame = new StringBuilder();
        frame.append(command).append("\n");
        for (Map.Entry<String, String> header : headers.entrySet()) {
            frame.append(header.getKey()).append(":").append(header.getValue()).append("\n");
        }
        frame.append("\n");
        if (body != null) {
            frame.append(body);
        }
        frame.append("\0"); // STOMP frames end with a null character
        return frame.toString();
    }
}
