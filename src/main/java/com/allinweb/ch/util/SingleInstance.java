package com.allinweb.ch.util;

import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.WRITE;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;

/**
 * Cross-process single-instance guard backed by exclusive OS file locks.
 *
 * <p>More than one independent lock can be held at the same time — for example a fixed,
 * config-independent <em>global</em> lock (so any second launch is blocked no matter which config
 * or log folder it uses) plus a per-{@code path_log} lock. Each {@link #acquire(String, String)}
 * keeps its own channel/lock, keyed by the resolved lock file; {@link #release()} frees them all.
 * The operating system also frees these locks automatically if the JVM dies, so a crashed instance
 * does not leave a stale lock that blocks the next start.
 */
@Slf4j
public final class SingleInstance {

    private static final class Holder {
        private final FileChannel channel;
        private final FileLock lock;
        private final Path lockFile;

        private Holder(FileChannel channel, FileLock lock, Path lockFile) {
            this.channel = channel;
            this.lock = lock;
            this.lockFile = lockFile;
        }
    }

    private static final Map<Path, Holder> HOLDERS = new ConcurrentHashMap<>();

    private SingleInstance() {}

    /** Acquire the lock in {@code user.dir}. */
    public static boolean acquire(String appId) {
        return acquire(appId, System.getProperty("user.dir"));
    }

    /**
     * Acquire the {@code .<appId>.lock} lock in {@code directory}. Returns {@code true} if this
     * instance holds it (including if it already did), {@code false} if another live instance does.
     */
    public static synchronized boolean acquire(String appId, String directory) {
        try {
            Path dir = Paths.get(directory);
            if (!Files.isDirectory(dir)) {
                Files.createDirectories(dir);
            }
            Path lockFile = dir.resolve("." + appId + ".lock").toAbsolutePath();
            if (HOLDERS.containsKey(lockFile)) {
                return true; // already held by this instance
            }
            FileChannel channel = FileChannel.open(lockFile, CREATE, WRITE);
            FileLock lock = channel.tryLock();
            if (lock != null) {
                HOLDERS.put(lockFile, new Holder(channel, lock, lockFile));
                log.info("Lock acquired: {}", lockFile);
                return true;
            }
            channel.close(); // another instance holds it; do not leak the channel
            return false;
        } catch (OverlappingFileLockException | IOException e) {
            return false;
        }
    }

    /** Path of one currently held lock file, or {@code null} if none is held. */
    public static Path getLockFilePath() {
        return HOLDERS.isEmpty() ? null : HOLDERS.values().iterator().next().lockFile;
    }

    /** Release and delete every lock held by this instance. Safe to call more than once. */
    public static synchronized void release() {
        for (Holder holder : HOLDERS.values()) {
            try {
                if (holder.lock != null) holder.lock.release();
            } catch (IOException ignored) {
            }
            try {
                if (holder.channel != null) holder.channel.close();
            } catch (IOException ignored) {
            }
            try {
                Files.deleteIfExists(holder.lockFile);
            } catch (IOException ignored) {
            }
        }
        HOLDERS.clear();
    }
}
