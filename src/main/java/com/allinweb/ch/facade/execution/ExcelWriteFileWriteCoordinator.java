package com.allinweb.ch.facade.execution;

import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/** Serializes writers targeting the same canonical Excel/CSV file inside this process. */
public final class ExcelWriteFileWriteCoordinator {
    private static final ConcurrentHashMap<String, LockEntry> FILE_LOCKS =
            new ConcurrentHashMap<>();

    private ExcelWriteFileWriteCoordinator() {}

    public static void run(Path path, Runnable writer) {
        if (path == null) throw new IllegalArgumentException("ExcelWrite path is required.");
        if (writer == null) throw new IllegalArgumentException("ExcelWrite writer is required.");
        String normalized = path.toAbsolutePath().normalize().toString();
        String key = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")
                ? normalized.toLowerCase(Locale.ROOT)
                : normalized;
        LockEntry entry = FILE_LOCKS.compute(key, (ignored, current) -> {
            LockEntry selected = current == null ? new LockEntry() : current;
            selected.references.incrementAndGet();
            return selected;
        });
        entry.lock.lock();
        try {
            writer.run();
        } finally {
            entry.lock.unlock();
            FILE_LOCKS.computeIfPresent(key, (ignored, current) -> {
                if (current != entry) return current;
                return current.references.decrementAndGet() == 0 ? null : current;
            });
        }
    }

    private static final class LockEntry {
        private final ReentrantLock lock = new ReentrantLock(true);
        private final AtomicInteger references = new AtomicInteger();
    }
}
