package com.allinweb.ch.facade.execution.v2;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/** Owns the optional local Node V2 sidecar without exposing its grant secret to React. */
public final class ExecutionV2RuntimeSupervisor {
    public static final String HOME_ENV = "ARWEB_EXECUTION_V2_HOME";
    public static final String NODE_ENV = "ARWEB_EXECUTION_V2_NODE_BINARY";
    private static final Duration HEALTH_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration START_TIMEOUT = Duration.ofSeconds(20);
    private static final org.slf4j.Logger trace =
            org.slf4j.LoggerFactory.getLogger("com.allinweb.smoke.execution");
    private static final ExecutionV2RuntimeSupervisor INSTANCE = new ExecutionV2RuntimeSupervisor();

    private final ExecutionRuntimeClientConfiguration clientConfiguration;
    private final ExecutionRuntimeRunCoordinator coordinator;
    private final String encodedSecret;
    private final String keyId;
    private final boolean externallyConfiguredSecret;
    private Process process;
    private String lastCode = "RUNTIME_STOPPED";

    private ExecutionV2RuntimeSupervisor() {
        clientConfiguration = ExecutionRuntimeClientConfiguration.fromEnvironment();
        Optional<ExecutionRuntimeGrantConfiguration> configured =
                ExecutionRuntimeGrantConfiguration.fromEnvironment();
        ExecutionRuntimeGrantConfiguration grants;
        if (configured.isPresent()) {
            grants = configured.get();
            encodedSecret = System.getenv(ExecutionRuntimeGrantConfiguration.SECRET_ENV).trim();
            keyId = grants.keyId();
            externallyConfiguredSecret = true;
        } else {
            byte[] secret = new byte[32];
            new SecureRandom().nextBytes(secret);
            encodedSecret = Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
            keyId = "managed-v1";
            grants = new ExecutionRuntimeGrantConfiguration(secret, keyId, Duration.ofSeconds(90));
            externallyConfiguredSecret = false;
        }
        coordinator = ExecutionRuntimeRunCoordinator.create(grants, clientConfiguration);
        Runtime.getRuntime().addShutdownHook(new Thread(this::stopOwnedProcess, "execution-v2-shutdown"));
        trace.info(
                "phase=V2_SUPERVISOR_CONFIGURED port={} credentialSource={}",
                clientConfiguration.baseUri().getPort(),
                externallyConfiguredSecret ? "ENVIRONMENT" : "PROCESS_EPHEMERAL");
    }

    public static ExecutionV2RuntimeSupervisor getInstance() {
        return INSTANCE;
    }

    public ExecutionRuntimeRunCoordinator coordinator() {
        return coordinator;
    }

    public synchronized Status status() {
        reapExitedProcess();
        boolean ready = ready();
        if (ready) {
            return new Status(process != null ? "READY" : "READY_EXTERNAL",
                    process != null ? "RUNTIME_READY" : "RUNTIME_EXTERNAL_READY",
                    clientConfiguration.baseUri().getPort(), process != null);
        }
        return new Status(process != null ? "STARTING" : "STOPPED", lastCode,
                clientConfiguration.baseUri().getPort(), process != null);
    }

    public synchronized Status start() {
        reapExitedProcess();
        if (ready()) {
            lastCode = process == null ? "RUNTIME_EXTERNAL_READY" : "RUNTIME_READY";
            return status();
        }
        if (process != null) throw new IllegalStateException("Execution V2 runtime is still starting.");
        if (!externallyConfiguredSecret && portResponds()) {
            lastCode = "RUNTIME_PORT_IN_USE";
            throw new IllegalStateException("Execution V2 port is already used by another process.");
        }
        Path home = runtimeHome();
        Path script = home.resolve("dist").resolve("src").resolve("server.js");
        if (!Files.isRegularFile(script)) {
            lastCode = "RUNTIME_BUILD_MISSING";
            throw new IllegalStateException("Build the TypeScript Playwright runtime before starting it.");
        }
        ProcessBuilder builder = new ProcessBuilder(nodeBinary(), script.toString());
        builder.directory(home.toFile());
        builder.redirectErrorStream(true);
        String logDirectory = System.getProperty("LOG_PATH", "").trim();
        if (!logDirectory.isBlank()) {
            Path consoleLog = Path.of(logDirectory).resolve("ar_web_execution_v2_console.log");
            builder.redirectOutput(ProcessBuilder.Redirect.appendTo(consoleLog.toFile()));
            builder.environment().put("ARWEB_EXECUTION_V2_LOG_DIRECTORY", logDirectory);
        } else {
            builder.redirectOutput(ProcessBuilder.Redirect.DISCARD);
        }
        builder.environment().put(ExecutionRuntimeGrantConfiguration.SECRET_ENV, encodedSecret);
        builder.environment().put(ExecutionRuntimeGrantConfiguration.KEY_ID_ENV, keyId);
        builder.environment().put(ExecutionRuntimeClientConfiguration.PORT_ENV,
                Integer.toString(clientConfiguration.baseUri().getPort()));
        builder.environment().putIfAbsent("ARWEB_EXECUTION_V2_MAX_ACTIVE_RUNS", "5");
        builder.environment().putIfAbsent("ARWEB_EXECUTION_V2_MAX_ACTIVE_PER_ORGANIZATION", "5");
        try {
            process = builder.start();
        } catch (Exception failure) {
            process = null;
            lastCode = "RUNTIME_PROCESS_START_FAILED";
            trace.error("phase=V2_RUNTIME_PROCESS_START status=FAILED failureType={}",
                    failure.getClass().getSimpleName());
            throw new IllegalStateException("The TypeScript Playwright runtime could not be started.");
        }
        lastCode = "RUNTIME_STARTING";
        trace.info("phase=V2_RUNTIME_PROCESS_START status=STARTING port={} pid={}",
                clientConfiguration.baseUri().getPort(), process.pid());
        long deadline = System.nanoTime() + START_TIMEOUT.toNanos();
        while (System.nanoTime() < deadline && process.isAlive()) {
            if (ready()) {
                lastCode = "RUNTIME_READY";
                trace.info("phase=V2_RUNTIME_PROCESS_START status=READY port={} pid={}",
                        clientConfiguration.baseUri().getPort(), process.pid());
                return status();
            }
            try {
                Thread.sleep(150L);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                stopOwnedProcess();
                throw new IllegalStateException("Execution V2 runtime startup was interrupted.");
            }
        }
        stopOwnedProcess();
        lastCode = "RUNTIME_READINESS_TIMEOUT";
        throw new IllegalStateException("The TypeScript Playwright runtime did not become ready.");
    }

    public synchronized Status stop() {
        reapExitedProcess();
        if (process == null) {
            if (ready()) {
                throw new IllegalStateException("The running V2 runtime is externally managed.");
            }
            lastCode = "RUNTIME_STOPPED";
            return status();
        }
        long pid = process.pid();
        process.destroy();
        try {
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                process.waitFor(5, TimeUnit.SECONDS);
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Execution V2 runtime stop was interrupted.");
        } finally {
            process = null;
        }
        lastCode = "RUNTIME_STOPPED";
        trace.info("phase=V2_RUNTIME_PROCESS_STOP status=STOPPED pid={}", pid);
        return status();
    }

    private void stopOwnedProcess() {
        synchronized (this) {
            if (process == null) return;
            process.destroy();
            try {
                if (!process.waitFor(2, TimeUnit.SECONDS)) process.destroyForcibly();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                process.destroyForcibly();
            } finally {
                process = null;
            }
        }
    }

    private void reapExitedProcess() {
        if (process != null && !process.isAlive()) {
            lastCode = "RUNTIME_PROCESS_EXITED";
            process = null;
        }
    }

    private boolean ready() {
        try {
            HttpRequest request = HttpRequest.newBuilder(
                            clientConfiguration.baseUri().resolve("/health/ready"))
                    .timeout(HEALTH_TIMEOUT).GET().build();
            HttpResponse<Void> response = HttpClient.newBuilder()
                    .connectTimeout(HEALTH_TIMEOUT).build()
                    .send(request, HttpResponse.BodyHandlers.discarding());
            return response.statusCode() == 200;
        } catch (Exception unavailable) {
            return false;
        }
    }

    private boolean portResponds() {
        try {
            HttpRequest request = HttpRequest.newBuilder(clientConfiguration.baseUri())
                    .timeout(HEALTH_TIMEOUT).GET().build();
            HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.discarding());
            return true;
        } catch (Exception unavailable) {
            return false;
        }
    }

    private static Path runtimeHome() {
        String configured = System.getenv(HOME_ENV);
        Path home = configured == null || configured.isBlank()
                ? Path.of(System.getProperty("user.dir"), "playwright-runtime-ts")
                : Path.of(configured.trim());
        return home.toAbsolutePath().normalize();
    }

    private static String nodeBinary() {
        String configured = System.getenv(NODE_ENV);
        return configured == null || configured.isBlank() ? "node" : configured.trim();
    }

    public record Status(String state, String code, int port, boolean managed) {}
}
