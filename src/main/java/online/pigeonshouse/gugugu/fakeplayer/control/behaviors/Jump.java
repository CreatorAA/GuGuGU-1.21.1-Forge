package online.pigeonshouse.gugugu.fakeplayer.control.behaviors;

import net.minecraft.server.level.ServerPlayer;
import online.pigeonshouse.gugugu.fakeplayer.control.Behavior;

import java.util.concurrent.atomic.AtomicInteger;

public class Jump extends Behavior {
    private final AtomicInteger count = new AtomicInteger(1);
    private final AtomicInteger interval = new AtomicInteger(1);
    private int thisTick = 0;

    public Jump(ServerPlayer player) {
        super(player);
    }

    public void setCount(int count) {
        this.count.set(count);
    }

    public void setInterval(int interval) {
        this.interval.set(interval);
    }

    @Override
    public String action() {
        return "jump";
    }

    @Override
    public void andThen(Behavior behavior) {
        if (this == behavior) return;

        if (behavior instanceof Jump) {
            count.set(((Jump) behavior).count.get());
            interval.set(((Jump) behavior).interval.get());
        } else {
            throw new IllegalArgumentException("can not andThen " + behavior.getClass().getName());
        }
    }

    @Override
    public int priority() {
        return 0;
    }

    @Override
    public void behavior() {
        if (thisTick == 0) {
            thisTick = 1;
            return;
        }

        if (thisTick++ % interval.get() == 0) {
            thisTick = 0;

            if (player.onGround()) {
                player.jumpFromGround();
                if (count.get() != -1)
                    count.decrementAndGet();
            }
        }
    }

    @Override
    public boolean isContinue() {
        return count.get() == -1 || count.get() > 0;
    }
}