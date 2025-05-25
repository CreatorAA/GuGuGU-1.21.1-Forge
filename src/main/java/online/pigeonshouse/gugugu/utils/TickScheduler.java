package online.pigeonshouse.gugugu.utils;

import online.pigeonshouse.gugugu.event.MinecraftServerEvents;

import java.util.Iterator;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class TickScheduler {
    private static final Queue<DelayedTask> TASKS = new ConcurrentLinkedQueue<>();

    public static void onServerTick(MinecraftServerEvents.ServerTickEvent event) {
        Iterator<DelayedTask> iterator = TASKS.iterator();
        while (iterator.hasNext()) {
            DelayedTask task = iterator.next();
            if (--task.remainingTicks <= 0) {
                try {
                    task.runnable.run();
                } finally {
                    task.latch.countDown();
                }
                iterator.remove();
            }
        }
    }

    public static DelayedTask schedule(int delayTicks, Runnable runnable) {
        DelayedTask task = new DelayedTask(delayTicks, runnable);
        TASKS.add(task);
        return task;
    }

    public static class DelayedTask {
        private final Runnable runnable;
        private final CountDownLatch latch = new CountDownLatch(1);
        private int remainingTicks;

        DelayedTask(int delay, Runnable runnable) {
            this.remainingTicks = delay;
            this.runnable = runnable;
        }

        public void join() throws InterruptedException {
            latch.await();
        }


        public boolean join(long timeoutTicks) throws InterruptedException {
            long timeoutMs = timeoutTicks * 50;
            return latch.await(timeoutMs, TimeUnit.MILLISECONDS);
        }

        public boolean isDone() {
            return latch.getCount() == 0;
        }
    }
}
