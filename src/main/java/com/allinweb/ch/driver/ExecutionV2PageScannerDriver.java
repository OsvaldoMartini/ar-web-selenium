package com.allinweb.ch.driver;

import com.allinweb.ch.facade.PlaywrightElementScanner;
import com.allinweb.ch.facade.execution.v2.ExecutionRuntimeRunCoordinator.ScannerSession;
import com.allinweb.ch.model.FieldData;
import com.allinweb.ch.model.InstructionLoad;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/** Remote adapter over one owner-scoped browser parked by the isolated V2 runtime. */
public final class ExecutionV2PageScannerDriver extends ARPlaywrightDriver {
    private final ScannerSession session;
    private final PlaywrightElementScanner scanner = new PlaywrightElementScanner();
    private final Gson gson = new Gson();
    private final AtomicBoolean closed = new AtomicBoolean();

    public ExecutionV2PageScannerDriver(ScannerSession session) {
        this.session = Objects.requireNonNull(session, "Execution V2 scanner session is required");
    }

    @Override public boolean isOpen() { return !closed.get(); }
    @Override public void assertBrowserCompatible(String browserType) {}
    @Override public void openOrNavigate(String browserType, String url, String optionsConfig) {
        requireOpen();
    }
    @Override public void reload() { rpc("reload", null); }
    @Override public String currentUrl() { return stringValue(rpc("url", null)); }
    @Override public String title() { return stringValue(rpc("title", null)); }
    @Override public String content() { return stringValue(rpc("content", null)); }
    @Override public long waitForPageSettled(long maxWaitMs) {
        long started = System.nanoTime();
        rpc("wait-settled", null);
        return Math.max(0L, (System.nanoTime() - started) / 1_000_000L);
    }
    @Override public int[] viewportSize() {
        Object value = rpc("viewport", null);
        if (!(value instanceof List<?> list) || list.size() < 2
                || !(list.get(0) instanceof Number width) || !(list.get(1) instanceof Number height)) return null;
        return new int[] {width.intValue(), height.intValue()};
    }
    @Override public byte[] screenshot(boolean fullPage) {
        return Base64.getDecoder().decode(stringValue(rpc("screenshot", Map.of("fullPage", fullPage))));
    }
    @Override public Object evaluate(String script) { return evaluate(script, null); }
    @Override public Object evaluate(String script, Object argument) {
        JsonObject request = new JsonObject();
        request.addProperty("operation", "evaluate");
        request.addProperty("script", Objects.requireNonNull(script, "Evaluation script is required"));
        if (argument != null) request.add("argument", gson.toJsonTree(argument));
        return javaValue(session.exchange(request));
    }
    @Override public List<com.allinweb.ch.model.ElementDTO> scanElements(
            String[] searchTerms, boolean includeHidden) {
        return scanner.scan(this::evaluate, searchTerms, includeHidden);
    }
    @Override public boolean click(InstructionLoad instruction) { return clickOnce(instruction); }
    @Override public boolean clickOnce(InstructionLoad instruction) {
        return action(instruction, "CLICK", "");
    }
    @Override public boolean fill(InstructionLoad instruction, FieldData data) {
        return fillOnce(instruction, data);
    }
    @Override public boolean fillOnce(InstructionLoad instruction, FieldData data) {
        return action(instruction, "INPUT", data == null ? "" : Objects.toString(data.getValue(), ""));
    }
    @Override public void close() { shutdown(); }
    @Override public void shutdown() {
        if (!closed.compareAndSet(false, true)) return;
        try {
            session.close();
        } finally {
            // ARPlaywrightDriver owns a local serialization executor even though this adapter
            // overrides every browser operation. Retire that executor with the remote lease.
            super.shutdown();
        }
    }

    private boolean action(InstructionLoad instruction, String action, String value) {
        if (instruction == null) return false;
        Object result = rpc("test-element", Map.of(
                "action", action,
                "xpath", Objects.toString(instruction.getXpath(), ""),
                "css", Objects.toString(instruction.getCssSelector(), ""),
                "value", value));
        return Boolean.TRUE.equals(result);
    }

    private Object rpc(String operation, Map<String, ?> fields) {
        requireOpen();
        JsonObject request = new JsonObject();
        request.addProperty("operation", operation);
        if (fields != null) fields.forEach((key, value) -> request.add(key, gson.toJsonTree(value)));
        return javaValue(session.exchange(request));
    }

    private Object javaValue(JsonElement value) {
        return value == null || value.isJsonNull() ? null : gson.fromJson(value, Object.class);
    }

    private static String stringValue(Object value) {
        if (!(value instanceof String text)) throw new IllegalStateException("Execution V2 scanner response is invalid");
        return text;
    }

    private void requireOpen() {
        if (closed.get()) throw new IllegalStateException("Execution V2 scanner is closed");
    }
}
