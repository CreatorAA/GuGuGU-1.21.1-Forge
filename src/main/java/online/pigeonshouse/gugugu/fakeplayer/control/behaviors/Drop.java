package online.pigeonshouse.gugugu.fakeplayer.control.behaviors;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import online.pigeonshouse.gugugu.fakeplayer.control.Behavior;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class Drop extends Behavior {
    private final AtomicInteger count = new AtomicInteger(1);
    private final AtomicInteger interval = new AtomicInteger(1);
    private final AtomicBoolean dropGroup = new AtomicBoolean(false);
    private int thisTick = 0;

    public Drop(ServerPlayer player) {
        super(player);
    }

    public void setCount(int count) {
        this.count.set(count);
    }

    public void setInterval(int interval) {
        this.interval.set(interval);
    }

    public void setDropGroup(boolean dropGroup) {
        this.dropGroup.set(dropGroup);
    }

    @Override
    public String action() {
        return "drop";
    }

    @Override
    public void andThen(Behavior behavior) {
        if (this == behavior) return;

        if (behavior instanceof Drop) {
            count.set(((Drop) behavior).count.get());
            interval.set(((Drop) behavior).interval.get());
            dropGroup.set(((Drop) behavior).dropGroup.get());
        } else {
            throw new IllegalArgumentException("can not andThen " + behavior.getClass().getName());
        }
    }

    @Override
    public int priority() {
        return 1;
    }

    @Override
    public void behavior() {
        if (thisTick == 0) {
            thisTick = 1;
            return;
        }

        if (thisTick++ % interval.get() == 0) {
            thisTick = 0;

            Inventory inv = player.getInventory();
            if (!inv.getSelected().isEmpty()) {
                player.resetLastActionTime();

                player.drop(inv.removeFromSelected(dropGroup.get()),
                        false, true);
            }

            if (count.get() != -1) {
                count.decrementAndGet();
            }
        }
    }

    @Override
    public boolean isContinue() {
        return count.get() == -1 || count.get() > 0;
    }
}