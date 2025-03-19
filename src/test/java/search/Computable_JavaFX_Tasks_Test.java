package search;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import javafx.concurrent.Task;
import org.junit.jupiter.api.Test;

public class Computable_JavaFX_Tasks_Test {

    @Test
    public void should_wait_all_tasks_tests() {
        // Assuming you have a list of JavaFX Tasks
        List<NamedTask<Void>> javafxTasks = new ArrayList<>();

        // Populate the list with your JavaFX Tasks
        // For demonstration, let's assume we have three tasks
        for (int i = 0; i < 3; i++) {
            NamedTask<Void> task = createJavaFXNamedTask(i);
            javafxTasks.add(task);
        }

        // Create a CompletableFuture for each JavaFX Task
        List<CompletableFuture<Void>> completableFutures = new ArrayList<>();
        for (NamedTask<Void> task : javafxTasks) {
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                // Start the JavaFX Task
                task.run();
                // Wait for the JavaFX Task to complete
                try {
                    task.get();
                    System.out.println("Task Name: " + task.getName());
                } catch (InterruptedException | ExecutionException e) {
                    System.out.println(e.getMessage());
                }
            });
            completableFutures.add(future);
        }

        // Wait for all CompletableFuture to complete
        CompletableFuture<Void> allOf = CompletableFuture.allOf(completableFutures.toArray(new CompletableFuture[0]));

        // Join() waits for all CompletableFuture to complete
        try {
            allOf.join();
            System.out.println("All JavaFX tasks have finished.");
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }

    // Sample method to create JavaFX Tasks
    private NamedTask<Void> createJavaFXNamedTask(int taskId) {
        return new NamedTask<>("Task " + taskId) {
            @Override
            protected Void call() throws Exception {
                System.out.println("JavaFX Task " + taskId + " started.");
                // Simulate some work
                Thread.sleep(1000); // 1 second
                System.out.println("JavaFX Task " + taskId + " completed.");
                return null;
            }
        };
    }

    // Sample method to create JavaFX Tasks
    private static Task<Void> createJavaFXTasks(int taskId) {
        return new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                System.out.println("JavaFX Task " + taskId + " started.");
                // Simulate some work
                Thread.sleep(1000); // 1 second
                System.out.println("JavaFX Task " + taskId + " completed.");
                return null;
            }
        };
    }

    public class NamedTask<T> extends Task<T> {

        private String name;

        public NamedTask(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        @Override
        protected T call() throws Exception {
            // Your task logic here
            return null;
        }
    }
}
