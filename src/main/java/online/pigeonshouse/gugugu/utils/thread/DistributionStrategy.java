package online.pigeonshouse.gugugu.utils.thread;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;

/**
 * 任务分发策略接口，泛型为队列元素类型
 *
 * @param <E> 队列元素类型
 */
public interface DistributionStrategy<E> {
    /**
     * 将任务集合分配到各私有队列与公共队列
     *
     * @param tasks     待分发任务集合
     * @param privateQs 各私有队列列表
     * @param publicQ   公共队列
     * @return key 为队列索引（0~threadCount-1 表示私有队列，threadCount 表示公共队列），
     * value 为该队列对应的任务列表
     */
    Map<Integer, List<E>> distribute(Collection<E> tasks,
                                     List<BlockingQueue<E>> privateQs,
                                     BlockingQueue<E> publicQ);
}