package com.allinweb.ch.facade;

import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Bounded, reusable serial execution gate for one detached Page Scanner browser page.
 *
 * <p>Closing a detached workspace invalidates and removes work that has not started, while the
 * executor remains available to a subsequently opened workspace. A task that is already running is
 * allowed to leave through the normal browser-shutdown path; newly submitted work remains behind it,
 * so two operations never manipulate the Playwright page concurrently.
 */
public final class PageScannerTaskGate {

    static final int DEFAULT_QUEUE_CAPACITY = 32;
    private static final AtomicInteger THREAD_IDS = new AtomicInteger();

    private final AtomicLong generation = new AtomicLong();
    private final ThreadPoolExecutor executor;

    public PageScannerTaskGate() {
        this(DEFAULT_QUEUE_CAPACITY, PageScannerTaskGate::daemonThread);
    }

    PageScannerTaskGate(int queueCapacity, ThreadFactory threadFactory) {
        if (queueCapacity <= 0) {
            throw new IllegalArgumentException("Page Scanner queue capacity must be positive");
        }
        Objects.requireNonNull(threadFactory, "Page Scanner thread factory is required");
        executor = new ThreadPoolExecutor(
                1,
                1,
                30L,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(queueCapacity),
                threadFactory,
                new ThreadPoolExecutor.AbortPolicy());
        executor.allowCoreThreadTimeOut(true);
    }

    /**
     * Schedules one operation when queue capacity is available.
     *
     * @return {@code false} when the bounded queue is already full
     */
    public synchronized boolean submit(Runnable operation) {
        Objects.requireNonNull(operation, "Page Scanner operation is required");
        long acceptedGeneration = generation.get();
        try {
            executor.execute(() -> {
                if (generation.get() == acceptedGeneration) {
                    operation.run();
                }
            });
            return true;
        } catch (RejectedExecutionException full) {
            return false;
        }
    }

    /** Invalidates and removes operations that have not started without shutting down the gate. */
    public synchronized void clearQueued() {
        generation.incrementAndGet();
        executor.getQueue().clear();
        executor.purge();
    }

    public boolean isBusy() {
        return executor.getActiveCount() > 0 || !executor.getQueue().isEmpty();
    }

    int queuedTaskCount() {
        return executor.getQueue().size();
    }

    /** Permanently stops this gate during terminal application shutdown. */
    public void shutdownNow() {
        generation.incrementAndGet();
        executor.shutdownNow();
    }

    private static Thread daemonThread(Runnable operation) {
        Thread thread = new Thread(
                operation,
                "detached-page-scanner-" + THREAD_IDS.incrementAndGet());
        thread.setDaemon(true);
        return thread;
    }
}
