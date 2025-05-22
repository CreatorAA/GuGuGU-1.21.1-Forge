package online.pigeonshouse.gugugu.fakeplayer.control.behaviors;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import online.pigeonshouse.gugugu.fakeplayer.control.Behavior;
import online.pigeonshouse.gugugu.utils.MinecraftUtil;

import java.util.concurrent.atomic.AtomicInteger;

public class Use extends Behavior {
    private final AtomicInteger count = new AtomicInteger(1);
    private final AtomicInteger interval = new AtomicInteger(3);
    int waitTick = 0;
    private int thisTick = 0;


    public Use(ServerPlayer player) {
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
        return "use";
    }

    @Override
    public void andThen(Behavior behavior) {
        if (this == behavior) return;

        if (behavior instanceof Use) {
            count.set(((Use) behavior).count.get());
            interval.set(((Use) behavior).interval.get());
        } else {
            throw new IllegalArgumentException("can not andThen " + behavior.getClass().getName());
        }
    }

    @Override
    public int priority() {
        return 4;
    }

    @Override
    public void behavior() {
        if (thisTick == 0) {
            thisTick = 1;
            return;
        }

        if (waitTick > 0) {
            waitTick--;
            return;
        }

        if (player.isUsingItem()) {
            return;
        }

        if (thisTick++ % interval.get() == 0) {
            thisTick = 0;

            HitResult result = MinecraftUtil.rayTrace(player, 4, false);
            for (InteractionHand hand : InteractionHand.values()) {
                switch (result.getType()) {
                    case BLOCK:
                        useBlock(player, (BlockHitResult) result, hand);
                        break;
                    case ENTITY:
                        useEntity(player, (EntityHitResult) result, hand);
                        break;
                }

                ItemStack handItem = player.getItemInHand(hand);

                if (player.gameMode.useItem(player, player.level(), handItem, hand).consumesAction()) {
                    waitTick = 3;
                }
            }

            if (count.get() != -1)
                count.decrementAndGet();
        }
    }

    private void useBlock(ServerPlayer player, BlockHitResult result, InteractionHand hand) {
        if (player.hasContainerOpen()) return;
        player.resetLastActionTime();
        ServerLevel world = player.serverLevel();
        BlockPos pos = result.getBlockPos();
        Direction side = result.getDirection();

        if (pos.getY() < player.level().getMaxBuildHeight() - (side == Direction.UP ? 1 : 0)
                && world.mayInteract(player, pos)) {
            InteractionResult result1 = player.gameMode.useItemOn(player, world,
                    player.getItemInHand(hand), hand, result);

            if (result1.consumesAction() && result1.shouldSwing())
                player.swing(hand);
        }
    }

    private void useEntity(ServerPlayer player, EntityHitResult result, InteractionHand hand) {
        if (player.hasContainerOpen()) return;

        player.resetLastActionTime();
        Entity entity = result.getEntity();
        Vec3 relativeHitPos = result.getLocation().subtract(entity.getX(), entity.getY(), entity.getZ());

        MinecraftUtil.getServer().execute(() -> {
            if (entity.interactAt(player, relativeHitPos, hand).consumesAction()) {
                return;
            }
            player.interactOn(entity, hand);
        });
    }

    @Override
    public boolean isContinue() {
        return count.get() == -1 || count.get() > 0;
    }
}