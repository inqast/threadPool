package threadpool.pool;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class ThreadPoolExecutor implements CustomExecutor {

    private final int corePoolSize;
    private final int maxPoolSize;
    private final long keepAliveTime;
    private final TimeUnit timeUnit;
    private final int queueSize;
    private final int minSpareThreads;

    private final List<BlockingQueue<Runnable>> taskQueues;
    private final ThreadFactory threadFactory;
    private final List<Worker> workers;
    private final AtomicInteger activeWorkers;
    private final AtomicInteger nextQueueIndex;
    private final RejectedExecutionHandler rejectedExecutionHandler;

    private volatile boolean isActive;

    public ThreadPoolExecutor(
            int corePoolSize,
            int maxPoolSize,
            long keepAliveTime,
            TimeUnit timeUnit,
            int queueSize,
            int minSpareThreads,
            RejectedExecutionHandler rejectedExecutionHandler
    ) {
        validate(corePoolSize, maxPoolSize, keepAliveTime, queueSize, minSpareThreads);
        
        this.corePoolSize = corePoolSize;
        this.maxPoolSize = maxPoolSize;
        this.keepAliveTime = keepAliveTime;
        this.timeUnit = timeUnit;
        this.queueSize = queueSize;
        this.minSpareThreads = minSpareThreads;
        this.rejectedExecutionHandler = rejectedExecutionHandler;

        this.taskQueues = new ArrayList<>();
        this.threadFactory = new CustomThreadFactory();
        this.workers = new ArrayList<>();
        this.activeWorkers = new AtomicInteger(0);
        this.nextQueueIndex = new AtomicInteger(0);

        initializeWorkers();
    }

    private void validate(
        int corePoolSize,
            int maxPoolSize,
            long keepAliveTime,
            int queueSize,
            int minSpareThreads
    ) {
        if (corePoolSize < 0 ||
         maxPoolSize <= 0 || 
         maxPoolSize < corePoolSize || 
         keepAliveTime < 0 ||
          minSpareThreads < 0 || 
          queueSize < 0) {
            throw new IllegalArgumentException("Invalid thread pool configuration parameters.");
        }
    }

    private void initializeWorkers() {
        this.isActive = true;

        for (int i = 0; i < corePoolSize; i++) {
            BlockingQueue<Runnable> queue = new LinkedBlockingQueue<>(queueSize);
            taskQueues.add(queue);
            addWorker(queue);
        }
    }

    private void addWorker(BlockingQueue<Runnable> queue) {
        if (activeWorkers.get() >= maxPoolSize) {
            return;
        }

        Worker worker = new Worker(this, queue, keepAliveTime, timeUnit);
        workers.add(worker);

        Thread thread = threadFactory.newThread(worker);
        thread.start();

        activeWorkers.incrementAndGet();
    }

    @Override
    public void execute(Runnable task) {
        if (!isActive) {
            throw new RejectedExecutionException("[Rejected] ThreadPool is shutdown");
        }

        if (trySendToNewWorker(task)) {
                return;
        }

        if (!trySendToExistingWorker(task)) {
            rejectedExecutionHandler.rejectedExecution(task, this);
        }
    }

    private boolean trySendToNewWorker(Runnable task) {
        if (activeWorkers.get() >= maxPoolSize) {
            return false;
        }

        BlockingQueue<Runnable> newQueue = new LinkedBlockingQueue<>(queueSize);
        taskQueues.add(newQueue);

        addWorker(newQueue);

        boolean ok = newQueue.offer(task);
        if (ok) {
            System.out.println("[Pool] Task accepted into new queue #" + (taskQueues.size() - 1));
        }

        return ok;
    }

    private boolean trySendToExistingWorker(Runnable task) {
        BlockingQueue<Runnable> queue = getNextQueue();
        boolean ok = queue.offer(task);
        if (ok) {
            System.out.println("[Pool] Task accepted into queue #" + taskQueues.indexOf(queue));
        }

        return ok;
    }

    private BlockingQueue<Runnable> getNextQueue() {
        int index = nextQueueIndex.getAndIncrement() % taskQueues.size();
        return taskQueues.get(index);
    }

    @Override
    public <T> Future<T> submit(Callable<T> task) {
        FutureTask<T> futureTask = new FutureTask<>(task);
        execute(futureTask);
        return futureTask;
    }

    @Override
    public void shutdown() {
        isActive = false;

        for (Worker worker : workers) {
            worker.stop();
        }

        for (Worker worker : workers) {
            try {
                worker.awaitTermination();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @Override
    public void shutdownNow() {
        isActive = false;
    
        for (BlockingQueue<Runnable> queue : taskQueues) {
            queue.clear();
        }

        for (Worker worker : workers) {
            worker.stop();
        }
    }

    synchronized boolean canTerminateWorker() {
        return activeWorkers.get() > corePoolSize &&
                activeWorkers.get() > minSpareThreads;
    }

    synchronized void cleanUp(Worker worker) {
        workers.remove(worker);
        activeWorkers.decrementAndGet();
        taskQueues.remove(worker.getQueue());
    }

    private class Worker implements Runnable {

        private final BlockingQueue<Runnable> taskQueue;
        private final ThreadPoolExecutor pool;

        private final long keepAliveTime;
        private final TimeUnit timeUnit;
        private final Thread workerThread;

        private volatile boolean isActive = true;

        Worker(
            ThreadPoolExecutor pool,
            BlockingQueue<Runnable> taskQueue, 
            long keepAliveTime, 
            TimeUnit timeUnit
        ) {
            this.pool = pool;
            this.taskQueue = taskQueue;
            this.keepAliveTime = keepAliveTime;
            this.timeUnit = timeUnit;
            this.workerThread = Thread.currentThread();
        }

        void stop() {
            isActive = false;
            workerThread.interrupt();
        }

        void awaitTermination() throws InterruptedException {
            workerThread.join();
        }

        @Override
        public void run() {
            while (isActive) {
                try {
                    Runnable task = taskQueue.poll(keepAliveTime, timeUnit);

                    if (task == null && pool.canTerminateWorker()) {
                        System.out.println("[Worker] " + Thread.currentThread().getName() + " idle timeout, stopping");
                        break;
                    }

                    if (task != null) {
                        System.out.println("[Worker] " + Thread.currentThread().getName() + " executes task");
                        task.run();
                    }
                } catch (InterruptedException e) {
                    if (!isActive) {
                        break;
                    }
                }
            }
            
            pool.cleanUp(this);
            System.out.println("[Worker] " + Thread.currentThread().getName() + " terminated");
        }

        BlockingQueue<Runnable> getQueue() {
            return taskQueue;
        }
    } 
} 