package com.allinweb.ch.facade.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ExcelWriteFileWriteCoordinatorTest {

    @Test
    void serializesEveryWriterForTheSameNormalizedFile() throws Exception {
        int writers = 24;
        AtomicInteger active = new AtomicInteger();
        AtomicInteger maximum = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(8);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int index = 0; index < writers; index++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    ExcelWriteFileWriteCoordinator.run(Path.of("build", "excel-write.xlsx"), () -> {
                        int concurrent = active.incrementAndGet();
                        maximum.accumulateAndGet(concurrent, Math::max);
                        try {
                            Thread.yield();
                        } finally {
                            active.decrementAndGet();
                        }
                    });
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> future : futures) future.get();
        } finally {
            executor.shutdownNow();
        }
        assertEquals(1, maximum.get());
        assertEquals(0, active.get());
    }
}
