package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class PageScannerTaskGateTest {

    @Test
    void serializesOperationsOnOneWorker() throws Exception {
        PageScannerTaskGate gate = gate(4);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch completed = new CountDownLatch(2);
        AtomicInteger active = new AtomicInteger();
        AtomicInteger maximumActive = new AtomicInteger();

        try {
            assertTrue(gate.submit(() -> runTracked(active, maximumActive, releaseFirst, completed)));
            assertTrue(gate.submit(() -> runTracked(active, maximumActive, null, completed)));

            assertTrue(waitUntil(() -> gate.queuedTaskCount() == 1));
            assertEquals(1, active.get());
            releaseFirst.countDown();

            assertTrue(completed.await(2, TimeUnit.SECONDS));
            assertEquals(1, maximumActive.get());
            assertTrue(waitUntil(() -> !gate.isBusy()));
        } finally {
            gate.shutdownNow();
        }
    }

    @Test
    void rejectsWorkBeyondTheBoundedQueue() throws Exception {
        PageScannerTaskGate gate = gate(1);
        CountDownLatch running = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicBoolean queuedRan = new AtomicBoolean();

        try {
            assertTrue(gate.submit(() -> {
                running.countDown();
                await(release);
            }));
            assertTrue(running.await(1, TimeUnit.SECONDS));
            assertTrue(gate.submit(() -> queuedRan.set(true)));
            assertFalse(gate.submit(() -> {}));
            assertEquals(1, gate.queuedTaskCount());

            release.countDown();
            assertTrue(waitUntil(queuedRan::get));
        } finally {
            release.countDown();
            gate.shutdownNow();
        }
    }

    @Test
    void clearDropsQueuedWorkAndGateCanBeReused() throws Exception {
        PageScannerTaskGate gate = gate(2);
        CountDownLatch running = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch reused = new CountDownLatch(1);
        AtomicBoolean discardedRan = new AtomicBoolean();

        try {
            assertTrue(gate.submit(() -> {
                running.countDown();
                await(release);
            }));
            assertTrue(running.await(1, TimeUnit.SECONDS));
            assertTrue(gate.submit(() -> discardedRan.set(true)));
            assertEquals(1, gate.queuedTaskCount());

            gate.clearQueued();
            assertEquals(0, gate.queuedTaskCount());
            assertTrue(gate.submit(reused::countDown));

            release.countDown();
            assertTrue(reused.await(2, TimeUnit.SECONDS));
            assertFalse(discardedRan.get());
            assertTrue(waitUntil(() -> !gate.isBusy()));
        } finally {
            release.countDown();
            gate.shutdownNow();
        }
    }

    private static PageScannerTaskGate gate(int capacity) {
        AtomicInteger ids = new AtomicInteger();
        return new PageScannerTaskGate(capacity, operation -> {
            Thread thread = new Thread(operation, "page-scanner-gate-test-" + ids.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        });
    }

    private static void runTracked(
            AtomicInteger active,
            AtomicInteger maximumActive,
            CountDownLatch release,
            CountDownLatch completed) {
        int current = active.incrementAndGet();
        maximumActive.accumulateAndGet(current, Math::max);
        try {
            if (release != null) await(release);
        } finally {
            active.decrementAndGet();
            completed.countDown();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private static boolean waitUntil(java.util.function.BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(2).toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) return true;
            Thread.sleep(5L);
        }
        return condition.getAsBoolean();
    }
}
