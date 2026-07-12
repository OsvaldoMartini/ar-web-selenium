package com.allinweb.ch.facade;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Session-bound allowlist for local folders selected through the native directory chooser. */
public final class BotJobTransferPathRegistry {

    private static final BotJobTransferPathRegistry INSTANCE = new BotJobTransferPathRegistry();

    private final Map<String, String> selectedPaths = new ConcurrentHashMap<>();

    private BotJobTransferPathRegistry() {}

    public static BotJobTransferPathRegistry getInstance() {
        return INSTANCE;
    }

    public String select(String sessionId, int botJobId, File directory) {
        if (sessionId == null || sessionId.isBlank()) throw new IllegalArgumentException("Session is required");
        if (botJobId <= 0) throw new IllegalArgumentException("Bot Job is required");
        if (directory == null || !directory.isDirectory()) {
            throw new IllegalArgumentException("Select an existing transfer folder");
        }
        String canonical = canonical(directory);
        selectedPaths.put(key(sessionId, botJobId), canonical);
        return canonical;
    }

    public String require(String sessionId, int botJobId, String claimedPath) {
        String selected = selectedPaths.get(key(sessionId, botJobId));
        if (selected == null) throw new IllegalStateException("Choose a transfer folder first");
        if (claimedPath == null || !selected.equalsIgnoreCase(canonical(new File(claimedPath)))) {
            throw new IllegalArgumentException("Transfer folder does not match the folder selected for this session");
        }
        return selected;
    }

    public void clear(String sessionId, int botJobId) {
        selectedPaths.remove(key(sessionId, botJobId));
    }

    public void clearSession(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) return;
        String prefix = sessionId + ':';
        selectedPaths.keySet().removeIf(key -> key.startsWith(prefix));
    }

    private static String key(String sessionId, int botJobId) {
        return sessionId + ':' + botJobId;
    }

    private static String canonical(File file) {
        try {
            return file.getCanonicalPath();
        } catch (IOException error) {
            throw new IllegalArgumentException("Transfer folder cannot be resolved", error);
        }
    }
}
