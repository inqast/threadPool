package threadpool.pool;

import java.util.concurrent.RejectedExecutionException;

public interface RejectedExecutionHandler {
    void rejectedExecution(Runnable r, ThreadPoolExecutor executor) throws RejectedExecutionException;
} 