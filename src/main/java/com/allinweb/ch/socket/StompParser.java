package com.allinweb.ch.socket;

public class StompParser {

    // Parse a raw STOMP frame into a StompFrame object
    public static StompFrame parse(String message) {
        String[] lines = message.split("\n");

        // The first line is the command
        String command = lines[0].trim();
        StompFrame stompFrame = new StompFrame(command);

        int i = 1;
        // Parse headers
        while (!lines[i].trim().isEmpty()) {
            String[] header = lines[i].split(":");
            stompFrame.setHeader(header[0].trim(), header[1].trim());
            i++;
        }

        // The body starts after an empty line
        StringBuilder body = new StringBuilder();
        for (i = i + 1; i < lines.length; i++) {
            body.append(lines[i]);
        }

        if (body.length() > 0) {
            stompFrame.setBody(body.toString().replace("\0", ""));
        }

        return stompFrame;
    }
}
