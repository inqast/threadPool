package threadpool;

import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import threadpool.pool.RejectedExecutionPolicies;
import threadpool.pool.ThreadPoolExecutor;

public class Main {
    public static void main(String[] args) {
        ThreadPoolExecutor pool = new ThreadPoolExecutor(2, 3, 1, TimeUnit.SECONDS, 2, 1, RejectedExecutionPolicies.ABORT);

        for (int i = 0; i < 20; i++) {
            final int taskId = i;
            try {
                pool.execute(() -> {
                    System.out.println("Task " + taskId + " started");
                    try {
                        Thread.sleep(2000); // Увеличиваем время выполнения задачи.
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    System.out.println("Task " + taskId + " completed");
                });
                System.out.println("Task " + taskId + " submitted");
            } catch (RejectedExecutionException e) {
                System.out.println("Task " + taskId + " was rejected: " + e.getMessage());
            }
        }

        try {
            Thread.sleep(30000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        System.out.println("Initiating shutdown...");
        pool.shutdown();
        System.out.println("Shutdown completed");
    }
}