package threadpool.pool;

import java.util.concurrent.RejectedExecutionException;

public class RejectedExecutionPolicies {
    public static final RejectedExecutionHandler CALLER_RUNS = (r, executor) -> {
        System.out.println("[Rejected] Task will be executed in caller thread");
        r.run();
    };
    
    public static final RejectedExecutionHandler ABORT = (r, executor) -> {
        throw new RejectedExecutionException("[Rejected] Task was rejected due to overload");
    };
} 