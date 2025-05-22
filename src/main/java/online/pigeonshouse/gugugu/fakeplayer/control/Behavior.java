package online.pigeonshouse.gugugu.fakeplayer.control;

import net.minecraft.server.level.ServerPlayer;

public abstract class Behavior {
    protected final ServerPlayer player;

    public Behavior(ServerPlayer player) {
        this.player = player;
    }

    public abstract String action();

    /**
     * 传递一个Behavior，并对自己合成
     */
    public abstract void andThen(Behavior behavior);

    /**
     * 行为优先级
     */
    public abstract int priority();

    /**
     * 行为执行
     */
    public abstract void behavior();

    /**
     * 是否继续执行
     */
    public boolean isContinue() {
        return false;
    }
}