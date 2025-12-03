package com.allinweb.ch.util;

import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.WRITE;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class SingleInstance {
    private static FileChannel channel;
    private static FileLock lock;

    public static boolean acquire(String appId) {
        try {
            Path lockFile = Paths.get(System.getProperty("user.dir"), "." + appId + ".lock");
            // CREATE file if missing, keep channel open for the whole JVM lifetime
            channel = FileChannel.open(lockFile, CREATE, WRITE);
            // exclusive, non-blocking
            lock = channel.tryLock();
            return lock != null;
        } catch (OverlappingFileLockException | IOException e) {
            return false; // someone (this or another JVM) already holds it
        }
    }

    public static void release() {
        try {
            if (lock != null) lock.release();
        } catch (IOException ignored) {
        }
        try {
            if (channel != null) channel.close();
        } catch (IOException ignored) {
        }
    }
}
