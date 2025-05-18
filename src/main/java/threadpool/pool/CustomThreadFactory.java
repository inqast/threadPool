package threadpool.pool;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public class CustomThreadFactory implements ThreadFactory {
    private final AtomicInteger threadNumber = new AtomicInteger(1);

    public CustomThreadFactory() {
    }

    @Override
    public Thread newThread(Runnable r) {
        String threadName = "Worker " + threadNumber.getAndIncrement();
        System.out.println("[ThreadFactory] Creating new thread: " + threadName);

        Thread thread = new Thread(r, threadName);
        thread.setDaemon(false);

        return thread;
    }
} 