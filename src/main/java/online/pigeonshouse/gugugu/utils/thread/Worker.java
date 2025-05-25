package online.pigeonshouse.gugugu.utils.thread;

/**
 * 工作线程处理器接口，支持返回值
 *
 * @param <T> 任务类型
 * @param <R> 返回类型
 */
public interface Worker<T, R> {
    /**
     * 处理任务的核心方法
     *
     * @param task 要处理的任务
     * @return 处理结果
     * @throws Exception 处理过程中可能抛出的异常
     */
    R process(T task) throws Exception;

    /**
     * 任务完成后的回调方法（可选）
     *
     * @param task   已完成的任务
     * @param result 处理结果
     */
    default void onComplete(T task, R result) {
    }

    /**
     * 任务处理出错时的回调方法（可选）
     *
     * @param task 出错的任务
     * @param e    异常信息
     */
    default void onError(T task, Exception e) {
    }
}