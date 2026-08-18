package com.allinweb.ch.facade;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.Base64;
import java.util.concurrent.CompletionStage;

/** Smoke client: connect, send one base64-encoded message, wait briefly, close. */
public class _WsSmokeClient {
    public static void main(String[] args) throws Exception {
        int port = Integer.parseInt(args[0]);
        String url = "ws://localhost:" + port + "/websocket?sessionId=smoke-1";

        WebSocket ws = HttpClient.newHttpClient()
                .newWebSocketBuilder()
                .buildAsync(URI.create(url), new WebSocket.Listener() {
                    @Override
                    public CompletionStage<?> onText(WebSocket w, CharSequence data, boolean last) {
                        System.out.println("[client <-] " + data);
                        return WebSocket.Listener.super.onText(w, data, last);
                    }
                })
                .join();

        String msg = "{\"type\":\"echo\",\"sessionId\":\"smoke-1\",\"operationId\":\"t\",\"body\":\"subscribe\"}";
        String b64 = Base64.getEncoder().encodeToString(msg.getBytes("UTF-8"));
        ws.sendText(b64, true).join();
        Thread.sleep(500);
        ws.sendClose(WebSocket.NORMAL_CLOSURE, "bye").join();
        Thread.sleep(200);
    }
}
