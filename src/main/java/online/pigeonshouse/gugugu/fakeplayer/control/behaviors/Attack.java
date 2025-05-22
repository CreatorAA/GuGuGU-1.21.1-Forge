package online.pigeonshouse.gugugu.fakeplayer.control.behaviors;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import online.pigeonshouse.gugugu.fakeplayer.control.Behavior;
import online.pigeonshouse.gugugu.utils.MinecraftUtil;

import java.util.concurrent.atomic.AtomicInteger;

public class Attack extends Behavior {
    private final AtomicInteger count = new AtomicInteger(1);
    private final AtomicInteger interval = new AtomicInteger(1);
    BlockPos currentBlock;
    float curBlockDamageMP = 0;
    private int thisTick = 0;

    public Attack(ServerPlayer player) {
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
        return "attack";
    }

    @Override
    public void andThen(Behavior behavior) {
        if (this == behavior) return;

        if (behavior instanceof Attack) {
            count.set(((Attack) behavior).count.get());
            interval.set(((Attack) behavior).interval.get());
        } else {
            throw new IllegalArgumentException("can not andThen " + behavior.getClass().getName());
        }
    }

    @Override
    public int priority() {
        return 5;
    }

    @Override
    public void behavior() {
        if (thisTick == 0) {
            thisTick = 1;
            return;
        }

        if (thisTick++ % interval.get() == 0) {
            thisTick = 0;
            HitResult result = MinecraftUtil.rayTrace(player, 4, false);
            switch (result.getType()) {
                case BLOCK:
                    attackBlock((BlockHitResult) result);
                    break;
                case ENTITY:
                    attackEntity((EntityHitResult) result);
            }

            if (count.get() != -1) count.decrementAndGet();
        }
    }

    private void attackEntity(EntityHitResult result) {
        player.attack(result.getEntity());
        player.swing(InteractionHand.MAIN_HAND);
        player.resetAttackStrengthTicker();
        player.resetLastActionTime();
    }

    private void attackBlock(BlockHitResult result) {
        BlockPos pos = result.getBlockPos();
        Direction side = result.getDirection();
        if (player.blockActionRestricted(player.level(), pos, player.gameMode.getGameModeForPlayer()))
            return;

        BlockState state = player.level().getBlockState(pos);
        boolean notAir = !state.isAir();

        if (player.gameMode.getGameModeForPlayer().isCreative()) {
            player.gameMode.handleBlockBreakAction(pos, ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK,
                    side, player.level().getMaxBuildHeight(), -1);
            return;
        }

        if (currentBlock == null || !currentBlock.equals(pos)) {
            if (currentBlock != null) {
                player.gameMode.handleBlockBreakAction(currentBlock,
                        ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK, side,
                        player.level().getMaxBuildHeight(), -1);
            }
            player.gameMode.handleBlockBreakAction(pos,
                    ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, side,
                    player.level().getMaxBuildHeight(), -1);

            if (notAir && curBlockDamageMP == 0) {
                state.attack(player.level(), pos, player);
            }

            if (notAir && state.getDestroyProgress(player, player.level(), pos) >= 1) {
                currentBlock = null;
            } else {
                currentBlock = pos;
                curBlockDamageMP = 0;
            }
        } else {
            curBlockDamageMP += state.getDestroyProgress(player, player.level(), pos);

            if (curBlockDamageMP >= 1) {
                player.gameMode.handleBlockBreakAction(pos, ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK, side, player.level().getMaxBuildHeight(), -1);
                currentBlock = null;
            }
            player.level().destroyBlockProgress(-1, pos, (int) (curBlockDamageMP * 10));
        }

        player.resetLastActionTime();
        player.swing(InteractionHand.MAIN_HAND);
    }

    @Override
    public boolean isContinue() {
        return count.get() == -1 || count.get() > 0;
    }
}