package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class UiThreadDispatcherTest {
    private final UiThreadDispatcher dispatcher = UiThreadDispatcher.getInstance();

    @AfterEach
    void resetDispatcher() {
        dispatcher.reset();
    }

    @Test
    void defaultsToDirectExecution() {
        List<String> calls = new ArrayList<>();

        dispatcher.execute(() -> calls.add("ran"));

        assertEquals(List.of("ran"), calls);
    }

    @Test
    void delegatesToInstalledDispatcher() {
        List<String> calls = new ArrayList<>();
        dispatcher.install(task -> {
            calls.add("dispatch");
            task.run();
        });

        dispatcher.execute(() -> calls.add("task"));

        assertEquals(List.of("dispatch", "task"), calls);
    }
}
