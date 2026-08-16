package com.allinweb.ch.facade.execution.v2;

import com.allinweb.ch.facade.execution.v2.ExecutionV2Contracts.IssuedGrant;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Minimal loopback adapter for the isolated Node runtime.
 *
 * <p>The opaque run token never leaves this Java object. A later authorized Smoke adapter may
 * construct requests from frozen backend facts, but React must never receive or author this token.
 */
public final class ExecutionRuntimeHttpClient {
    private static final org.slf4j.Logger executionTrace =
            org.slf4j.LoggerFactory.getLogger("com.allinweb.smoke.execution");
    private static final String TOKEN_HEADER = "X-ARWeb-Run-Token";
    private static final int MAX_RESPONSE_BYTES = 16 * 1024 * 1024;
    private static final Pattern TOKEN = Pattern.compile("[A-Za-z0-9_-]{43}");

    private final ExecutionRuntimeClientConfiguration configuration;
    private final Transport transport;
    private final Gson gson = new Gson();

    public ExecutionRuntimeHttpClient(ExecutionRuntimeClientConfiguration configuration) {
        this(configuration, new JavaHttpTransport(configuration));
    }

    ExecutionRuntimeHttpClient(
            ExecutionRuntimeClientConfiguration configuration, Transport transport) {
        this.configuration = Objects.requireNonNull(configuration, "configuration");
        this.transport = Objects.requireNonNull(transport, "transport");
    }

    RuntimeRun reserve(IssuedGrant grant) {
        Objects.requireNonNull(grant, "Execution V2 grant is required");
        JsonObject data = exchange(
                "POST",
                "/v2/runs/reserve",
                grant.compactGrant(),
                null,
                null);
        String token = requiredString(data, "runAccessToken", false);
        JsonObject run = requiredObject(data, "run");
        String runId = requiredString(run, "runId", true);
        if (!grant.runId().equals(runId) || !isCanonicalToken(token)) {
            throw new ExecutionRuntimeClientException("RUNTIME_RESERVATION_INVALID");
        }
        return new RuntimeRun(runId, token);
    }

    JsonObject start(RuntimeRun run, StartFacts facts) {
        Objects.requireNonNull(facts, "Execution V2 start facts are required");
        JsonObject body = new JsonObject();
        body.addProperty("endpoint", facts.endpoint().toString());
        JsonObject browser = new JsonObject();
        browser.addProperty("headless", facts.headless());
        if (facts.channel() != null) browser.addProperty("channel", facts.channel());
        if (!facts.arguments().isEmpty()) {
            var arguments = new com.google.gson.JsonArray();
            facts.arguments().forEach(arguments::add);
            browser.add("args", arguments);
        }
        body.add("browser", browser);
        return tokenExchange("POST", run, "start", body);
    }

    JsonObject action(RuntimeRun run, JsonObject authoritativeAction) {
        Objects.requireNonNull(authoritativeAction, "Execution V2 action facts are required");
        return tokenExchange("POST", run, "actions", authoritativeAction.deepCopy());
    }

    String pageIdentity(RuntimeRun run) {
        JsonObject response = tokenExchange("GET", run, "page-identity", null);
        String pageKey = requiredString(response, "pageKey", false);
        if (!pageKey.matches("url-v1:[0-9a-f]{64}")) {
            throw new ExecutionRuntimeClientException("RUNTIME_RESPONSE_INVALID");
        }
        return pageKey;
    }

    JsonObject heartbeat(RuntimeRun run) {
        return tokenExchange("GET", run, "heartbeat", null);
    }

    JsonObject refresh(RuntimeRun run) {
        return tokenExchange("POST", run, "refresh", null);
    }

    JsonObject stop(RuntimeRun run) {
        return tokenExchange("POST", run, "stop", null);
    }

    JsonObject closeBrowser(RuntimeRun run) {
        return tokenExchange("POST", run, "close-browser", null);
    }

    JsonObject release(RuntimeRun run) {
        JsonObject response = tokenExchange("DELETE", run, "release", null);
        run.retire();
        return response;
    }

    ScannerRun openScanner(IssuedGrant grant) {
        Objects.requireNonNull(grant, "Execution V2 scanner grant is required");
        JsonObject response = exchange(
                "POST", "/v2/scanners/open", grant.compactGrant(), null, null);
        String token = requiredString(response, "scannerToken", false);
        if (!isCanonicalToken(token)) {
            throw new ExecutionRuntimeClientException("RUNTIME_RESPONSE_INVALID");
        }
        return new ScannerRun(requiredString(response, "scannerId", true), token);
    }

    JsonElement scanner(ScannerRun scanner, JsonObject request) {
        Objects.requireNonNull(scanner, "Execution V2 scanner is required");
        JsonObject response = exchange(
                "POST", "/v2/scanners/" + scanner.scannerId() + "/rpc",
                null, scanner.requireToken(), request);
        return response.has("value") ? response.get("value").deepCopy() : com.google.gson.JsonNull.INSTANCE;
    }

    void closeScanner(ScannerRun scanner) {
        if (scanner == null || scanner.closed()) return;
        exchange(
                "POST", "/v2/scanners/" + scanner.scannerId() + "/close",
                null, scanner.requireToken(), null);
        scanner.close();
    }

    private JsonObject tokenExchange(
            String method, RuntimeRun run, String operation, JsonObject body) {
        Objects.requireNonNull(run, "Execution V2 run is required");
        return exchange(
                method,
                "/v2/runs/" + run.runId() + "/" + operation,
                null,
                run.requireToken(),
                body);
    }

    private JsonObject exchange(
            String method, String path, String grant, String token, JsonObject body) {
        long started = System.nanoTime();
        String operation = operationName(path);
        String runId = runId(path);
        executionTrace.debug(
                "phase=V2_HTTP_REQUEST operation={} method={} runId={} bodyPresent={} timeoutSeconds={}",
                operation, method, runId, body != null, configuration.requestTimeout().toSeconds());
        Response response;
        try {
            response = transport.exchange(
                    method,
                    configuration.resolve(path),
                    grant,
                    token,
                    body == null ? null : gson.toJson(body),
                    configuration.requestTimeout());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            traceFailure(operation, method, runId, "RUNTIME_REQUEST_INTERRUPTED", started);
            throw new ExecutionRuntimeClientException("RUNTIME_REQUEST_INTERRUPTED", interrupted);
        } catch (IOException failure) {
            traceFailure(operation, method, runId, "NODE_RUNTIME_UNAVAILABLE", started);
            throw new ExecutionRuntimeClientException("RUNTIME_UNAVAILABLE", failure);
        }
        if (response.body().length > MAX_RESPONSE_BYTES) {
            traceFailure(operation, method, runId, "RUNTIME_RESPONSE_TOO_LARGE", started);
            throw new ExecutionRuntimeClientException("RUNTIME_RESPONSE_TOO_LARGE");
        }
        JsonObject envelope;
        try {
            JsonElement parsed = JsonParser.parseString(
                    new String(response.body(), StandardCharsets.UTF_8));
            envelope = parsed.getAsJsonObject();
        } catch (RuntimeException invalid) {
            traceFailure(operation, method, runId, "RUNTIME_RESPONSE_INVALID", started);
            throw new ExecutionRuntimeClientException("RUNTIME_RESPONSE_INVALID");
        }
        if (!envelope.has("ok")
                || !envelope.get("ok").isJsonPrimitive()
                || !envelope.getAsJsonPrimitive("ok").isBoolean()) {
            traceFailure(operation, method, runId, "RUNTIME_RESPONSE_INVALID", started);
            throw new ExecutionRuntimeClientException("RUNTIME_RESPONSE_INVALID");
        }
        if (response.status() < 200 || response.status() >= 300
                || !envelope.get("ok").getAsBoolean()) {
            String code = safeFailureCode(envelope);
            executionTrace.warn(
                    "phase=V2_HTTP_RESPONSE operation={} method={} runId={} status={} ok=false code={} durationMs={} responseBytes={}",
                    operation, method, runId, response.status(), code,
                    elapsedMillis(started), response.body().length);
            throw new ExecutionRuntimeClientException(code);
        }
        executionTrace.debug(
                "phase=V2_HTTP_RESPONSE operation={} method={} runId={} status={} ok=true durationMs={} responseBytes={}",
                operation, method, runId, response.status(), elapsedMillis(started),
                response.body().length);
        return requiredObject(envelope, "data").deepCopy();
    }

    private static void traceFailure(
            String operation, String method, String runId, String code, long started) {
        executionTrace.warn(
                "phase=V2_HTTP_FAILED operation={} method={} runId={} code={} durationMs={}",
                operation, method, runId, code, elapsedMillis(started));
    }

    private static long elapsedMillis(long started) {
        return Math.max(0L, (System.nanoTime() - started) / 1_000_000L);
    }

    private static String operationName(String path) {
        if ("/v2/runs/reserve".equals(path)) return "reserve";
        int separator = path == null ? -1 : path.lastIndexOf('/');
        String operation = separator < 0 ? "unknown" : path.substring(separator + 1);
        if (operation.matches("[0-9a-fA-F-]{36}")) return "release";
        return operation.matches("[a-z-]{2,40}") ? operation : "unknown";
    }

    private static String runId(String path) {
        if (path == null) return "-";
        var matcher = Pattern.compile("^/v2/runs/([0-9a-f-]{36})(?:/|$)", Pattern.CASE_INSENSITIVE)
                .matcher(path);
        return matcher.find() ? matcher.group(1) : "-";
    }

    private static String safeFailureCode(JsonObject envelope) {
        if (envelope != null && envelope.has("code") && envelope.get("code").isJsonPrimitive()) {
            String code = envelope.get("code").getAsString();
            if (code.matches("[A-Z][A-Z0-9_]{2,80}")) return code;
        }
        return "RUNTIME_REQUEST_REFUSED";
    }

    private static boolean isCanonicalToken(String value) {
        if (!TOKEN.matcher(value).matches()) return false;
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(value);
            return decoded.length == 32
                    && Base64.getUrlEncoder().withoutPadding().encodeToString(decoded).equals(value);
        } catch (IllegalArgumentException invalid) {
            return false;
        }
    }

    private static JsonObject requiredObject(JsonObject source, String name) {
        if (source == null || !source.has(name) || !source.get(name).isJsonObject()) {
            throw new ExecutionRuntimeClientException("RUNTIME_RESPONSE_INVALID");
        }
        return source.getAsJsonObject(name);
    }

    private static String requiredString(JsonObject source, String name, boolean uuid) {
        if (source == null
                || !source.has(name)
                || !source.get(name).isJsonPrimitive()
                || !source.getAsJsonPrimitive(name).isString()) {
            throw new ExecutionRuntimeClientException("RUNTIME_RESPONSE_INVALID");
        }
        String value = source.get(name).getAsString();
        if (value.isBlank()) throw new ExecutionRuntimeClientException("RUNTIME_RESPONSE_INVALID");
        if (uuid) {
            try {
                if (!UUID.fromString(value).toString().equals(value)) throw new IllegalArgumentException();
            } catch (IllegalArgumentException invalid) {
                throw new ExecutionRuntimeClientException("RUNTIME_RESPONSE_INVALID");
            }
        }
        return value;
    }

    public record StartFacts(URI endpoint, boolean headless, String channel, List<String> arguments) {
        public StartFacts {
            if (endpoint == null
                    || !("http".equalsIgnoreCase(endpoint.getScheme())
                    || "https".equalsIgnoreCase(endpoint.getScheme()))
                    || endpoint.getHost() == null
                    || endpoint.getHost().isBlank()
                    || endpoint.getUserInfo() != null
                    || endpoint.toString().length() > 2_048) {
                throw new IllegalArgumentException("Execution V2 endpoint is invalid");
            }
            if (channel != null
                    && !channel.equals("chrome")
                    && !channel.equals("msedge")
                    && !channel.equals("chromium")) {
                throw new IllegalArgumentException("Execution V2 browser channel is invalid");
            }
            arguments = List.copyOf(arguments == null ? List.of() : arguments);
            if (arguments.size() > 32) {
                throw new IllegalArgumentException("Execution V2 browser argument limit exceeded");
            }
            for (String argument : arguments) {
                if (argument == null
                        || argument.length() < 3
                        || argument.length() > 512
                        || !argument.startsWith("--")
                        || argument.chars().anyMatch(Character::isISOControl)) {
                    throw new IllegalArgumentException("Execution V2 browser argument is invalid");
                }
            }
        }

        public StartFacts(URI endpoint, boolean headless, String channel) {
            this(endpoint, headless, channel, List.of());
        }
    }

    static final class ScannerRun {
        private final String scannerId;
        private String token;

        ScannerRun(String scannerId, String token) {
            this.scannerId = scannerId;
            this.token = token;
        }

        String scannerId() { return scannerId; }
        boolean closed() { return token == null; }
        String requireToken() {
            if (token == null) throw new ExecutionRuntimeClientException("SCANNER_SESSION_CLOSED");
            return token;
        }
        void close() { token = null; }
    }

    public static final class RuntimeRun {
        private final String runId;
        private String token;

        private RuntimeRun(String runId, String token) {
            this.runId = runId;
            this.token = token;
        }

        public String runId() {
            return runId;
        }

        private synchronized String requireToken() {
            if (token == null) throw new ExecutionRuntimeClientException("RUNTIME_RUN_RETIRED");
            return token;
        }

        private synchronized void retire() {
            token = null;
        }

        @Override
        public String toString() {
            return "RuntimeRun[runId=" + runId + "]";
        }
    }

    static final class ExecutionRuntimeClientException extends RuntimeException {
        private final String code;

        ExecutionRuntimeClientException(String code) {
            super(code);
            this.code = code;
        }

        ExecutionRuntimeClientException(String code, Throwable cause) {
            super(code, cause);
            this.code = code;
        }

        String code() {
            return code;
        }
    }

    interface Transport {
        Response exchange(
                String method,
                URI uri,
                String grant,
                String token,
                String body,
                Duration timeout) throws IOException, InterruptedException;
    }

    record Response(int status, byte[] body) {
        Response {
            body = body == null ? new byte[0] : body.clone();
        }

        @Override
        public byte[] body() {
            return body.clone();
        }
    }

    private static final class JavaHttpTransport implements Transport {
        private final HttpClient client;

        private JavaHttpTransport(ExecutionRuntimeClientConfiguration configuration) {
            client = HttpClient.newBuilder()
                    .connectTimeout(configuration.requestTimeout())
                    .followRedirects(HttpClient.Redirect.NEVER)
                    .build();
        }

        @Override
        public Response exchange(
                String method,
                URI uri,
                String grant,
                String token,
                String body,
                Duration timeout) throws IOException, InterruptedException {
            HttpRequest.BodyPublisher publisher = body == null
                    ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8);
            HttpRequest.Builder request = HttpRequest.newBuilder(uri)
                    .timeout(timeout)
                    .method(method, publisher)
                    .header("Accept", "application/json");
            if (body != null) request.header("Content-Type", "application/json");
            if (grant != null) request.header("Authorization", "Bearer " + grant);
            if (token != null) request.header(TOKEN_HEADER, token);
            HttpResponse<byte[]> response = client.send(
                    request.build(), HttpResponse.BodyHandlers.ofByteArray());
            return new Response(response.statusCode(), response.body());
        }
    }
}
