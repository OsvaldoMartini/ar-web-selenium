package com.allinweb.ch.util;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * In-memory bounded ring buffer of log lines. Used by SupportCapture to attach
 * the last N log events to a DOM capture for diagnostic context.
 *
 * Populated by {@link ConsoleRingBufferAppender} (see logback.xml).
 */
public final class ConsoleRingBuffer {

    private static final int CAPACITY = 500;
    private static final Deque<String> BUFFER = new ArrayDeque<>(CAPACITY);

    private ConsoleRingBuffer() {}

    public static synchronized void append(String line) {
        if (line == null) return;
        if (BUFFER.size() >= CAPACITY) BUFFER.pollFirst();
        BUFFER.addLast(line);
    }

    public static synchronized List<String> snapshot(int n) {
        int count = Math.min(n, BUFFER.size());
        List<String> out = new ArrayList<>(count);
        int skip = BUFFER.size() - count;
        int i = 0;
        for (String s : BUFFER) {
            if (i++ < skip) continue;
            out.add(s);
        }
        return out;
    }
}
