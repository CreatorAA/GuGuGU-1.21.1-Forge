package online.pigeonshouse.gugugu.utils.thread;

/**
 * 工作线程创建工厂接口
 *
 * @param <T> 任务类型
 * @param <R> 返回类型
 */
public interface WorkerFactory<T, R> {
    /**
     * 创建一个新的工作处理器实例
     *
     * @return Worker 实例
     */
    Worker<T, R> createWorker();
}