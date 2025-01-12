package search;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import org.junit.Test;

public class Computable_Runnable_Tasks_Test {

    @Test
    public void should_wait_all_tasks_tests() {
        // Assuming you have a list of Runnable or Callable tasks
        List<NamedRunnable> standardTasks = new ArrayList<>();

        // Populate the list with your tasks
        // For demonstration, let's assume we have three tasks
        for (int i = 0; i < 3; i++) {
            NamedRunnable task = createStandardNamedTasks(i);
            standardTasks.add(task);
        }

        // Create a CompletableFuture for each task
        List<CompletableFuture<Void>> completableFutures = new ArrayList<>();
        for (NamedRunnable task : standardTasks) {
            CompletableFuture<Void> future = CompletableFuture.runAsync(task, Executors.newCachedThreadPool());
            completableFutures.add(future);
        }

        // Wait for all CompletableFuture to complete
        CompletableFuture<Void> allOf = CompletableFuture.allOf(completableFutures.toArray(new CompletableFuture[0]));

        // Join() waits for all CompletableFuture to complete
        try {
            allOf.join();
            System.out.println("All tasks have finished.");
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }

    // Sample method to create standard tasks (Runnable)
    private NamedRunnable createStandardNamedTasks(int taskId) {
        return new NamedRunnable("Task Martini " + taskId) {
            @Override
            public void run() {
                System.out.println("Task " + taskId + " started.");
                // Simulate some work
                try {
                    Thread.sleep(1000); // 1 second
                    System.out.println(this.getName());
                } catch (InterruptedException e) {
                    System.out.println(e.getMessage());
                }
                System.out.println("Task " + taskId + " completed.");
            }
        };
    }

    // Sample method to create standard tasks (Runnable)
    private Runnable createStandardTasks(int taskId) {
        return () -> {
            System.out.println("Task " + taskId + " started.");
            // Simulate some work
            try {

                Thread.sleep(1000); // 1 second
            } catch (InterruptedException e) {
                System.out.println(e.getMessage());
            }
            System.out.println("Task " + taskId + " completed.");
        };
    }

    public class NamedRunnable implements Runnable {

        private String name;

        public NamedRunnable(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        @Override
        public void run() {
            // Your runnable logic here
        }
    }
}
