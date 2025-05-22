package online.pigeonshouse.gugugu.utils.thread;

import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 默认加权分发策略，实现 DistributionStrategy 接口
 *
 * @param <E> 队列元素类型
 */
public class WeightedDistribution<E> implements DistributionStrategy<E> {
    @Override
    public Map<Integer, List<E>> distribute(
            Collection<E> tasks,
            List<BlockingQueue<E>> privateQs,
            BlockingQueue<E> publicQ
    ) {
        int totalPools = privateQs.size() + 1;
        int maxSize = privateQs.stream().mapToInt(BlockingQueue::size).max().orElse(0);
        maxSize = Math.max(maxSize, publicQ.size());

        int[] weights = new int[totalPools];
        int sum = 0;
        for (int i = 0; i < privateQs.size(); i++) {
            weights[i] = (maxSize - privateQs.get(i).size()) + 1;
            sum += weights[i];
        }
        weights[privateQs.size()] = (maxSize - publicQ.size()) + 1;
        sum += weights[privateQs.size()];

        Map<Integer, List<E>> map = new HashMap<>();
        for (int i = 0; i < totalPools; i++) {
            map.put(i, new ArrayList<>());
        }

        Random rnd = ThreadLocalRandom.current();
        for (E task : tasks) {
            int r = rnd.nextInt(sum);
            int cum = 0;
            for (int i = 0; i < totalPools; i++) {
                cum += weights[i];
                if (r < cum) {
                    map.get(i).add(task);
                    break;
                }
            }
        }
        return map;
    }
}