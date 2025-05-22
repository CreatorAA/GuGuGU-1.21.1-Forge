package online.pigeonshouse.gugugu.utils.thread;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 通用线程池，支持私有队列与公共队列、任务分发策略、生命周期控制及运行指标统计，
 * 并且支持提交带返回值的任务（通过 Future 获取结果）。
 *
 * @param <T> 任务类型
 * @param <R> 返回类型
 */
public class GenericThreadPool<T, R> implements AutoCloseable {
    private final int threadCount;                                           // 线程数
    private final WorkerFactory<T, R> factory;                               // 工作线程工厂
    private final DistributionStrategy<TaskWrapper<T, R>> distributor;       // 分发策略
    private final List<BlockingQueue<TaskWrapper<T, R>>> privateQs;          // 私有队列列表
    private final BlockingQueue<TaskWrapper<T, R>> publicQ;                  // 公共队列
    private final List<Thread> threads;                                      // 工作线程列表

    private final AtomicBoolean shutdown = new AtomicBoolean(false);         // 是否已关闭标志
    private final AtomicInteger activeTasks = new AtomicInteger(0);          // 当前活跃任务数
    private final LongAdder submittedCount = new LongAdder();                // 提交任务总数
    private final LongAdder completedCount = new LongAdder();                // 完成任务总数
    private final LongAdder errorCount = new LongAdder();                    // 错误任务总数

    private final ReentrantLock completionLock = new ReentrantLock();        // 完成等待锁
    private final Condition completionCond = completionLock.newCondition();  // 完成等待条件

    /**
     * Builder，用于设置可选参数并构建线程池
     *
     * @param <T> 任务类型
     * @param <R> 返回类型
     */
    public static class Builder<T, R> {
        private final WorkerFactory<T, R> factory;
        private int threadCount = Runtime.getRuntime().availableProcessors();
        private BlockingQueue<TaskWrapper<T, R>> publicQ = new LinkedBlockingQueue<>();
        private DistributionStrategy<TaskWrapper<T, R>> distributor = new WeightedDistribution<>();
        private ThreadFactory threadFactory = Executors.defaultThreadFactory();

        /**
         * 构造 Builder
         *
         * @param factory 工作线程工厂
         */
        public Builder(WorkerFactory<T, R> factory) {
            this.factory = factory;
        }

        /**
         * 设置线程数量
         *
         * @param c 线程数
         * @return Builder 自身
         */
        public Builder<T, R> threadCount(int c) {
            if (c > 0) threadCount = c;
            return this;
        }

        /**
         * 设置公共队列
         *
         * @param q 公共队列实例
         * @return Builder 自身
         */
        public Builder<T, R> publicQueue(BlockingQueue<TaskWrapper<T, R>> q) {
            if (q != null) publicQ = q;
            return this;
        }

        /**
         * 设置任务分发策略
         *
         * @param d 分发策略实例
         * @return Builder 自身
         */
        public Builder<T, R> distributor(DistributionStrategy<TaskWrapper<T, R>> d) {
            if (d != null) distributor = d;
            return this;
        }

        /**
         * 设置线程工厂
         *
         * @param tf 线程工厂实例
         * @return Builder 自身
         */
        public Builder<T, R> threadFactory(ThreadFactory tf) {
            if (tf != null) threadFactory = tf;
            return this;
        }

        /**
         * 构建 GenericThreadPool 实例
         *
         * @return GenericThreadPool 实例
         */
        public GenericThreadPool<T, R> build() {
            return new GenericThreadPool<>(factory, threadCount, publicQ, distributor, threadFactory);
        }
    }

    /**
     * 私有构造方法，由 Builder 调用
     */
    private GenericThreadPool(
            WorkerFactory<T, R> factory,
            int threadCount,
            BlockingQueue<TaskWrapper<T, R>> publicQ,
            DistributionStrategy<TaskWrapper<T, R>> distributor,
            ThreadFactory threadFactory
    ) {
        this.factory = factory;
        this.threadCount = threadCount;
        this.publicQ = publicQ;
        this.distributor = distributor;
        this.privateQs = new ArrayList<>(threadCount);
        this.threads = new ArrayList<>(threadCount);

        for (int i = 0; i < threadCount; i++) {
            privateQs.add(new LinkedBlockingQueue<>());
        }

        for (int i = 0; i < threadCount; i++) {
            int finalI = i;
            Thread t = threadFactory.newThread(() -> runWorker(privateQs.get(finalI)));
            t.setName("GenericPool-Worker-" + i);
            threads.add(t);
            t.start();
        }
    }

    /**
     * 工作线程主循环，从私有队列优先取任务，取不到再从公共队列获取
     *
     * @param privateQ 私有队列
     */
    private void runWorker(BlockingQueue<TaskWrapper<T, R>> privateQ) {
        Worker<T, R> worker = factory.createWorker();
        while (!shutdown.get() || !privateQ.isEmpty() || !publicQ.isEmpty()) {
            TaskWrapper<T, R> w = null;
            try {
                w = privateQ.poll(200, TimeUnit.MILLISECONDS);
                if (w == null) {
                    w = publicQ.poll(200, TimeUnit.MILLISECONDS);
                }
                if (w != null) {
                    try {
                        R result = worker.process(w.task);
                        completedCount.increment();
                        worker.onComplete(w.task, result);
                        w.future.complete(result);
                    } catch (Exception e) {
                        errorCount.increment();
                        worker.onError(w.task, e);
                        w.future.completeExceptionally(e);
                    } finally {
                        if (activeTasks.decrementAndGet() == 0) {
                            signalCompletion();
                        }
                    }
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    /**
     * 触发所有等待线程唤醒
     */
    private void signalCompletion() {
        completionLock.lock();
        try {
            completionCond.signalAll();
        } finally {
            completionLock.unlock();
        }
    }

    /**
     * 确保线程池未关闭，否则抛出异常
     */
    private void ensureRunning() {
        if (shutdown.get()) {
            throw new RejectedExecutionException("线程池已关闭，无法接收新任务");
        }
    }

    /**
     * 提交单个任务至公共队列，返回 Future<R> 以获取结果
     *
     * @param task 要提交的任务
     * @return 对应的 Future<R>
     */
    public Future<R> submit(T task) {
        ensureRunning();
        Objects.requireNonNull(task);
        submittedCount.increment();
        activeTasks.incrementAndGet();
        CompletableFuture<R> future = new CompletableFuture<>();
        TaskWrapper<T, R> wrapper = new TaskWrapper<>(task, future);
        publicQ.offer(wrapper);
        return future;
    }

    /**
     * 批量提交任务，使用分发策略分配至各队列，返回对应的 Future<R> 列表
     *
     * @param tasks 任务集合
     * @return Future<R> 列表，顺序对应于输入集合
     */
    public List<Future<R>> submitBatch(Collection<T> tasks) {
        ensureRunning();
        if (tasks == null || tasks.isEmpty()) {
            return Collections.emptyList();
        }
        List<TaskWrapper<T, R>> wrappers = new ArrayList<>(tasks.size());
        List<Future<R>> futures = new ArrayList<>(tasks.size());
        for (T t : tasks) {
            CompletableFuture<R> f = new CompletableFuture<>();
            wrappers.add(new TaskWrapper<>(t, f));
            futures.add(f);
        }
        submittedCount.add(tasks.size());
        activeTasks.addAndGet(tasks.size());
        Map<Integer, List<TaskWrapper<T, R>>> dist = distributor.distribute(wrappers, privateQs, publicQ);
        dist.forEach((idx, list) -> {
            if (idx < threadCount) {
                privateQs.get(idx).addAll(list);
            } else {
                publicQ.addAll(list);
            }
        });
        return futures;
    }

    /**
     * 根据队列索引取消未执行的任务
     *
     * @param poolIndex 队列索引（0~threadCount-1: 私有队列，threadCount: 公共队列）
     * @param task      要取消的任务
     * @return 是否成功取消
     */
    public boolean cancel(int poolIndex, T task) {
        BlockingQueue<TaskWrapper<T, R>> q = poolIndex < threadCount
                ? privateQs.get(poolIndex)
                : publicQ;
        for (TaskWrapper<T, R> w : q) {
            if (w.task.equals(task)) {
                boolean removed = q.remove(w);
                if (removed) {
                    activeTasks.decrementAndGet();
                    w.future.cancel(false);
                }
                return removed;
            }
        }
        return false;
    }

    /**
     * 阻塞等待所有任务执行完成
     *
     * @throws InterruptedException 中断异常
     */
    public void awaitCompletion() throws InterruptedException {
        completionLock.lock();
        try {
            while (activeTasks.get() > 0
                    || !publicQ.isEmpty()
                    || privateQs.stream().anyMatch(q -> !q.isEmpty())) {
                completionCond.await();
            }
        } finally {
            completionLock.unlock();
        }
    }

    /**
     * 发起有序关闭，不再接受新任务
     */
    public void shutdown() {
        shutdown.set(true);
    }

    /**
     * 立即关闭，尝试中断线程并返回未执行任务列表
     *
     * @return 未执行的任务列表
     */
    public List<T> shutdownNow() {
        shutdown.set(true);
        threads.forEach(Thread::interrupt);
        List<T> pending = new ArrayList<>();
        privateQs.forEach(q -> q.forEach(w -> pending.add(w.task)));
        publicQ.forEach(w -> pending.add(w.task));
        privateQs.forEach(BlockingQueue::clear);
        publicQ.clear();
        return pending;
    }

    /**
     * 判断是否已关闭
     *
     * @return true 如果已调用 shutdown 或 shutdownNow
     */
    public boolean isShutdown() {
        return shutdown.get();
    }

    /**
     * 判断所有线程是否已终止
     *
     * @return true 如果所有工作线程均已结束
     */
    public boolean isTerminated() {
        return threads.stream().allMatch(t -> !t.isAlive());
    }

    /**
     * 获取当前活跃任务数
     *
     * @return 活跃任务数
     */
    public int getActiveTaskCount() {
        return activeTasks.get();
    }

    /**
     * 获取提交任务总数
     *
     * @return 提交任务总数
     */
    public long getSubmittedCount() {
        return submittedCount.sum();
    }

    /**
     * 获取完成任务总数
     *
     * @return 完成任务总数
     */
    public long getCompletedCount() {
        return completedCount.sum();
    }

    /**
     * 获取错误任务总数
     *
     * @return 错误任务总数
     */
    public long getErrorCount() {
        return errorCount.sum();
    }

    /**
     * 获取各队列当前积压任务数量
     *
     * @return 列表，前 threadCount 项为私有队列大小，最后一项为公共队列大小
     */
    public List<Integer> getQueueSizes() {
        List<Integer> sizes = new ArrayList<>(threadCount + 1);
        privateQs.forEach(q -> sizes.add(q.size()));
        sizes.add(publicQ.size());
        return sizes;
    }

    /**
     * 关闭线程池并等待线程终止（实现 AutoCloseable）
     *
     * @throws InterruptedException 中断异常
     */
    @Override
    public void close() throws InterruptedException {
        shutdown();
        for (Thread t : threads) {
            t.join();
        }
    }

    private static class TaskWrapper<T, R> {
        final T task;
        final CompletableFuture<R> future;

        TaskWrapper(T task, CompletableFuture<R> future) {
            this.task = task;
            this.future = future;
        }
    }
}