package com.allinweb.ch.facade;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * Minimal standalone RFC-6455 WebSocket server for testing the manual injection script:
 *   D:\Projects_DevOps\MultiPlugins\specifications\ORIGINALS_STUDY_1\ORIGINAL SCRIPTS\script-search-in-use-manual.js
 *
 * Usage:
 *   1. Run this class. It prints the port it's listening on.
 *   2. Open your web page, Allow pasting in the DevTools console.
 *   3. Paste the manual script wrapped in a call with the printed port, e.g.:
 *        (function(...script body...))(
 *          ["button","input","a","select"],   // searchTerms
 *          [],                                // hiddenFields
 *          9999,                              // socketPort (the one printed below)
 *          "test-session-1",                  // sessionId
 *          "scannerGrid",                     // destination
 *          "op-1", "hb-1", "job-1"            // operationId, homeBankingId, botJobId
 *        );
 *   4. This server logs every frame received (base64-decoded when possible).
 *   5. Ctrl+C to stop.
 *
 * No external dependencies — uses raw sockets. Handles: handshake, text frames up to
 * 64 KB payload, masked client-to-server frames, pong replies, and sends a simple
 * text ack back to the client.
 */
public class ManualScriptWebSocketTest {

    private static final int DEFAULT_PORT = 9999;
    private static final String WS_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";

    public static void main(String[] args) throws Exception {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : DEFAULT_PORT;

        try (ServerSocket server = new ServerSocket(port)) {
            System.out.println("=== Manual Script WebSocket Test Server ===");
            System.out.println("Listening on: ws://localhost:" + port + "/websocket?sessionId=<id>");
            System.out.println("Paste the manual script into the browser console with socketPort = " + port);
            System.out.println("Ctrl+C to stop.\n");

            while (true) {
                Socket client = server.accept();
                new Thread(() -> handleClient(client), "ws-client-" + client.getPort()).start();
            }
        }
    }

    private static void handleClient(Socket socket) {
        String peer = socket.getRemoteSocketAddress().toString();
        System.out.println("[+] Connected: " + peer);
        try (Socket s = socket;
                InputStream in = s.getInputStream();
                OutputStream out = s.getOutputStream()) {

            // ── Handshake ───────────────────────────────────────────────────
            Map<String, String> headers = readHttpHeaders(in);
            String requestLine = headers.get("__request__");
            System.out.println("    " + requestLine);

            String key = headers.get("sec-websocket-key");
            if (key == null) {
                System.out.println("    not a WebSocket handshake, closing");
                return;
            }

            String accept = Base64.getEncoder()
                    .encodeToString(MessageDigest.getInstance("SHA-1")
                            .digest((key + WS_GUID).getBytes(StandardCharsets.UTF_8)));

            String response = "HTTP/1.1 101 Switching Protocols\r\n"
                    + "Upgrade: websocket\r\n"
                    + "Connection: Upgrade\r\n"
                    + "Sec-WebSocket-Accept: " + accept + "\r\n\r\n";
            out.write(response.getBytes(StandardCharsets.UTF_8));
            out.flush();
            System.out.println("    handshake OK (sessionId=" + extractSessionId(requestLine) + ")");

            // ── Frame loop ──────────────────────────────────────────────────
            while (true) {
                Frame f = readFrame(in);
                if (f == null) {
                    System.out.println("[-] Closed: " + peer);
                    return;
                }
                switch (f.opcode) {
                    case 0x1: // text
                        handleText(new String(f.payload, StandardCharsets.UTF_8), out);
                        break;
                    case 0x2: // binary
                        System.out.println("[BIN] " + f.payload.length + " bytes");
                        break;
                    case 0x8: // close
                        System.out.println("[-] Close frame from " + peer);
                        sendFrame(out, 0x8, new byte[0]);
                        return;
                    case 0x9: // ping
                        sendFrame(out, 0xA, f.payload);
                        break;
                    case 0xA: // pong
                        break;
                    default:
                        System.out.println("[?] opcode=" + f.opcode);
                }
            }
        } catch (IOException e) {
            System.out.println("[!] " + peer + " — " + e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void handleText(String payload, OutputStream out) throws IOException {
        System.out.println("[TXT] " + truncate(payload, 200));
        // Try to base64-decode (the manual script sends everything base64-wrapped)
        try {
            String decoded = new String(Base64.getDecoder().decode(payload), StandardCharsets.UTF_8);
            System.out.println("      decoded: " + truncate(decoded, 400));
        } catch (IllegalArgumentException ignored) {
            // not base64, that's fine
        }
        // Simple ack so the script can observe round-trip
        String ack = "{\"type\":\"ack\",\"body\":\"received " + payload.length() + " chars\"}";
        sendFrame(out, 0x1, ack.getBytes(StandardCharsets.UTF_8));
    }

    // ── HTTP header reader ──────────────────────────────────────────────────

    private static Map<String, String> readHttpHeaders(InputStream in) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        int prev = -1, curr;
        int matched = 0;
        while ((curr = in.read()) != -1) {
            buf.write(curr);
            if ((prev == '\r' && curr == '\n') || (prev == '\n' && curr == '\n')) {
                matched++;
                if (matched == 2) break;
            } else if (curr != '\r' && curr != '\n') {
                matched = 0;
            }
            prev = curr;
        }
        String raw = buf.toString(StandardCharsets.UTF_8);
        String[] lines = raw.split("\r?\n");
        Map<String, String> out = new HashMap<>();
        if (lines.length > 0) out.put("__request__", lines[0]);
        for (int i = 1; i < lines.length; i++) {
            int idx = lines[i].indexOf(':');
            if (idx > 0) {
                out.put(lines[i].substring(0, idx).trim().toLowerCase(),
                        lines[i].substring(idx + 1).trim());
            }
        }
        return out;
    }

    private static String extractSessionId(String requestLine) {
        int q = requestLine.indexOf("sessionId=");
        if (q < 0) return "(none)";
        int end = requestLine.indexOf(' ', q);
        String rest = end > q ? requestLine.substring(q + 10, end) : requestLine.substring(q + 10);
        int amp = rest.indexOf('&');
        return amp >= 0 ? rest.substring(0, amp) : rest;
    }

    // ── Frame read/write ────────────────────────────────────────────────────

    private static class Frame {
        int opcode;
        byte[] payload;
    }

    private static Frame readFrame(InputStream in) throws IOException {
        int b1 = in.read();
        if (b1 == -1) return null;
        int b2 = in.read();
        if (b2 == -1) return null;

        Frame f = new Frame();
        f.opcode = b1 & 0x0F;
        boolean masked = (b2 & 0x80) != 0;
        long len = b2 & 0x7F;

        if (len == 126) {
            len = ((in.read() & 0xFF) << 8) | (in.read() & 0xFF);
        } else if (len == 127) {
            len = 0;
            for (int i = 0; i < 8; i++) len = (len << 8) | (in.read() & 0xFF);
        }

        byte[] mask = new byte[4];
        if (masked) {
            for (int i = 0; i < 4; i++) mask[i] = (byte) in.read();
        }

        if (len > 16 * 1024 * 1024) throw new IOException("payload too large: " + len);
        byte[] data = new byte[(int) len];
        int read = 0;
        while (read < data.length) {
            int n = in.read(data, read, data.length - read);
            if (n == -1) throw new IOException("unexpected EOF in payload");
            read += n;
        }
        if (masked) {
            for (int i = 0; i < data.length; i++) data[i] ^= mask[i & 3];
        }
        f.payload = data;
        return f;
    }

    private static void sendFrame(OutputStream out, int opcode, byte[] payload) throws IOException {
        out.write(0x80 | (opcode & 0x0F));
        if (payload.length < 126) {
            out.write(payload.length);
        } else if (payload.length < 65536) {
            out.write(126);
            out.write((payload.length >> 8) & 0xFF);
            out.write(payload.length & 0xFF);
        } else {
            out.write(127);
            for (int i = 7; i >= 0; i--) out.write((int) ((((long) payload.length) >> (8 * i)) & 0xFF));
        }
        out.write(payload);
        out.flush();
    }

    private static String truncate(String s, int max) {
        if (s.length() <= max) return s;
        return s.substring(0, max) + "... (" + s.length() + " chars)";
    }
}
