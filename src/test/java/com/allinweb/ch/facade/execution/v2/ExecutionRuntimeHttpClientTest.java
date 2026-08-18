package com.allinweb.ch.facade.execution.v2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.allinweb.ch.facade.execution.v2.ExecutionRuntimeHttpClient.ExecutionRuntimeClientException;
import com.allinweb.ch.facade.execution.v2.ExecutionRuntimeHttpClient.Response;
import com.allinweb.ch.facade.execution.v2.ExecutionRuntimeHttpClient.RuntimeRun;
import com.allinweb.ch.facade.execution.v2.ExecutionRuntimeHttpClient.ScannerRun;
import com.allinweb.ch.facade.execution.v2.ExecutionRuntimeHttpClient.StartFacts;
import com.allinweb.ch.facade.execution.v2.ExecutionV2Contracts.IssuedGrant;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

class ExecutionRuntimeHttpClientTest {
    private static final String RUN_ID = "22222222-2222-4222-8222-222222222222";
    private static final String TOKEN = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";

    @Test
    void retainsTheOpaqueTokenInJavaAndUsesItForTheExactRunLifecycle() {
        FakeTransport transport = new FakeTransport();
        transport.add(201, "{\"ok\":true,\"data\":{\"created\":true,\"run\":{\"runId\":\""
                + RUN_ID + "\"},\"runAccessToken\":\"" + TOKEN + "\"}}");
        transport.add(202, successData("{\"state\":\"QUEUED\"}"));
        transport.add(200, successData("{\"state\":\"READY\"}"));
        transport.add(200, successData("{\"pageKey\":\"url-v1:"
                + "c".repeat(64) + "\"}"));
        transport.add(200, successData("{\"state\":\"STOPPED\"}"));
        transport.add(200, successData("{\"runId\":\"" + RUN_ID + "\"}"));
        ExecutionRuntimeHttpClient client = client(transport);

        RuntimeRun run = client.reserve(grant());
        assertEquals(RUN_ID, run.runId());
        assertFalse(run.toString().contains(TOKEN));
        client.start(run, new StartFacts(
                URI.create("https://example.test/"), true, "chromium",
                List.of("--disable-popup-blocking")));
        client.heartbeat(run);
        assertEquals("url-v1:" + "c".repeat(64), client.pageIdentity(run));
        client.stop(run);
        client.release(run);

        assertEquals("compact-grant", transport.calls.get(0).grant());
        assertEquals(TOKEN, transport.calls.get(1).token());
        assertTrue(transport.calls.get(1).body().contains("https://example.test/"));
        assertTrue(transport.calls.get(1).body().contains("--disable-popup-blocking"));
        assertTrue(transport.calls.get(3).uri().getPath().endsWith("/page-identity"));
        assertTrue(transport.calls.stream().skip(1).allMatch(call -> TOKEN.equals(call.token())));
        ExecutionRuntimeClientException retired = assertThrows(
                ExecutionRuntimeClientException.class, () -> client.heartbeat(run));
        assertEquals("RUNTIME_RUN_RETIRED", retired.code());
    }

    @Test
    void refusesMismatchedReservationsAndPreservesSafeRuntimeFailureCodes() {
        FakeTransport mismatch = new FakeTransport();
        mismatch.add(201, "{\"ok\":true,\"data\":{\"run\":{\"runId\":"
                + "\"33333333-3333-4333-8333-333333333333\"},"
                + "\"runAccessToken\":\"" + TOKEN + "\"}}");
        assertEquals(
                "RUNTIME_RESERVATION_INVALID",
                assertThrows(ExecutionRuntimeClientException.class,
                        () -> client(mismatch).reserve(grant())).code());

        FakeTransport refused = new FakeTransport();
        refused.add(409, "{\"ok\":false,\"code\":\"RUN_START_CONFLICT\","
                + "\"message\":\"hidden\"}");
        RuntimeRun run = runtimeRun();
        assertEquals(
                "RUN_START_CONFLICT",
                assertThrows(ExecutionRuntimeClientException.class,
                        () -> client(refused).start(
                                run,
                                new StartFacts(
                                        URI.create("https://example.test/"), true, null))).code());
    }

    @Test
    void explicitCloseBrowserUsesItsDedicatedTokenAuthorizedRoute() {
        FakeTransport transport = new FakeTransport();
        transport.add(201, "{\"ok\":true,\"data\":{\"run\":{\"runId\":\""
                + RUN_ID + "\"},\"runAccessToken\":\"" + TOKEN + "\"}}");
        transport.add(200, successData("{\"state\":\"STOPPED\"}"));
        ExecutionRuntimeHttpClient client = client(transport);
        RuntimeRun run = client.reserve(grant());

        client.closeBrowser(run);

        Call close = transport.calls.get(1);
        assertEquals("POST", close.method());
        assertTrue(close.uri().getPath().endsWith("/close-browser"));
        assertEquals(TOKEN, close.token());
    }

    @Test
    void scannerUsesTheGrantOnlyToOpenThenUsesItsOpaqueCapabilityToken() {
        FakeTransport transport = new FakeTransport();
        transport.add(201, successData("{\"scannerId\":\"33333333-3333-4333-8333-333333333333\","
                + "\"scannerToken\":\"" + TOKEN + "\"}"));
        transport.add(200, successData("{\"value\":\"https://example.test/current\"}"));
        transport.add(200, successData("{\"scannerId\":\"33333333-3333-4333-8333-333333333333\"}"));
        ExecutionRuntimeHttpClient client = client(transport);

        ScannerRun scanner = client.openScanner(grant());
        JsonObject request = new JsonObject();
        request.addProperty("operation", "url");
        assertEquals("https://example.test/current", client.scanner(scanner, request).getAsString());
        client.closeScanner(scanner);
        client.closeScanner(scanner);

        assertEquals("compact-grant", transport.calls.get(0).grant());
        assertEquals(null, transport.calls.get(0).token());
        assertEquals(TOKEN, transport.calls.get(1).token());
        assertEquals(TOKEN, transport.calls.get(2).token());
        assertEquals(3, transport.calls.size());
    }

    @Test
    void recoveryScannerUsesTheExactActiveRunTokenAndDedicatedRoute() {
        FakeTransport transport = new FakeTransport();
        transport.add(201, "{\"ok\":true,\"data\":{\"run\":{\"runId\":\""
                + RUN_ID + "\"},\"runAccessToken\":\"" + TOKEN + "\"}}");
        transport.add(200, successData("{\"value\":\"https://example.test/recovery\"}"));
        ExecutionRuntimeHttpClient client = client(transport);
        RuntimeRun run = client.reserve(grant());
        JsonObject request = new JsonObject();
        request.addProperty("operation", "url");

        assertEquals(
                "https://example.test/recovery",
                client.recoveryScanner(run, request).getAsString());

        Call scanner = transport.calls.get(1);
        assertEquals("POST", scanner.method());
        assertTrue(scanner.uri().getPath().endsWith("/scanner"));
        assertEquals(TOKEN, scanner.token());
        assertTrue(scanner.body().contains("\"operation\":\"url\""));
        assertFalse(scanner.body().contains(TOKEN));
    }

    @Test
    void runtimeConfigurationIsLoopbackOnlyAndStrictlyBounded() {
        assertEquals(
                60_110,
                ExecutionRuntimeClientConfiguration.fromEnvironment(java.util.Map.of())
                        .baseUri().getPort());
        assertThrows(
                IllegalArgumentException.class,
                () -> new ExecutionRuntimeClientConfiguration(
                        URI.create("http://example.test:60110"), Duration.ofSeconds(60)));
        assertThrows(
                IllegalArgumentException.class,
                () -> ExecutionRuntimeClientConfiguration.fromEnvironment(java.util.Map.of(
                        ExecutionRuntimeClientConfiguration.PORT_ENV, "0")));
        assertThrows(
                IllegalArgumentException.class,
                () -> new StartFacts(
                        URI.create("https://example.test/"), false, "chrome",
                        List.of("user-data-dir=x")));
    }

    private static ExecutionRuntimeHttpClient client(FakeTransport transport) {
        return new ExecutionRuntimeHttpClient(
                new ExecutionRuntimeClientConfiguration(
                        URI.create("http://127.0.0.1:60110"), Duration.ofSeconds(60)),
                transport);
    }

    private static IssuedGrant grant() {
        return new IssuedGrant(
                1,
                "v1",
                "11111111-1111-4111-8111-111111111111",
                RUN_ID,
                Instant.parse("2026-08-11T12:00:00Z"),
                Instant.parse("2026-08-11T12:01:30Z"),
                "compact-grant");
    }

    private static RuntimeRun runtimeRun() {
        FakeTransport transport = new FakeTransport();
        transport.add(201, "{\"ok\":true,\"data\":{\"run\":{\"runId\":\""
                + RUN_ID + "\"},\"runAccessToken\":\"" + TOKEN + "\"}}");
        return client(transport).reserve(grant());
    }

    private static String successData(String data) {
        return "{\"ok\":true,\"data\":" + data + "}";
    }

    private static final class FakeTransport implements ExecutionRuntimeHttpClient.Transport {
        private final Queue<Response> responses = new ArrayDeque<>();
        private final List<Call> calls = new ArrayList<>();

        private void add(int status, String body) {
            responses.add(new Response(status, body.getBytes(StandardCharsets.UTF_8)));
        }

        @Override
        public Response exchange(
                String method,
                URI uri,
                String grant,
                String token,
                String body,
                Duration timeout) {
            calls.add(new Call(method, uri, grant, token, body == null ? "" : body));
            return responses.remove();
        }
    }

    private record Call(String method, URI uri, String grant, String token, String body) {}
}
