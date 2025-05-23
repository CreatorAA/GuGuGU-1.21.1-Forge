package online.pigeonshouse.gugugu.backup;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import online.pigeonshouse.gugugu.GuGuGu;
import online.pigeonshouse.gugugu.event.MinecraftServerEvents;

public class BackupCommands {
    private static final String ROOT = "gbackup";
    private static final int PERMISSION_LEVEL = 4;


    public static void register(MinecraftServerEvents.CommandRegisterEvent evt) {
        CommandDispatcher<CommandSourceStack> dispatcher = evt.getDispatcher();

        dispatcher.register(
                Commands.literal(ROOT)
                        .requires(BackupCommands::canUse)
                        /* ---------------- inc --------------- */
                        .then(Commands.literal("inc")
                                .executes(ctx -> inc(ctx.getSource())))
                        /* --------------- full --------------- */
                        .then(Commands.literal("full")
                                .executes(ctx -> full(ctx.getSource())))
                        /* ------------- rollback ------------- */
                        .then(Commands.literal("rollback")
                                /*----------------- 单区块回档 ----------------*/
                                .executes(ctx -> rollback(ctx.getSource(),
                                        ctx.getSource().getPlayerOrException().getOnPos(),
                                        true,
                                        GuGuGu.INSTANCE.getBackupManager().getConfig().getHotRollbackSource()))
                                .then(Commands.literal("hot")
                                        .then(Commands.literal(BackupManager.INCREMENTAL)
                                                .executes(ctx -> rollback(ctx.getSource(),
                                                        ctx.getSource().getPlayerOrException().getOnPos(),
                                                        true,
                                                        BackupManager.INCREMENTAL)))
                                        .then(Commands.literal(BackupManager.FULL)
                                                .executes(ctx -> rollback(ctx.getSource(),
                                                        ctx.getSource().getPlayerOrException().getOnPos(),
                                                        true,
                                                        BackupManager.FULL))))
                                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                        .executes(ctx -> rollback(ctx.getSource(),
                                                BlockPosArgument.getBlockPos(ctx, "pos"),
                                                false,
                                                GuGuGu.INSTANCE.getBackupManager().getConfig().getHotRollbackSource()))
                                        .then(Commands.literal("hot")
                                                .then(Commands.literal(BackupManager.INCREMENTAL)
                                                        .executes(ctx -> rollback(ctx.getSource(),
                                                                BlockPosArgument.getBlockPos(ctx, "pos"),
                                                                false,
                                                                BackupManager.INCREMENTAL)))
                                                .then(Commands.literal(BackupManager.FULL)
                                                        .executes(ctx -> rollback(ctx.getSource(),
                                                                BlockPosArgument.getBlockPos(ctx, "pos"),
                                                                false,
                                                                BackupManager.FULL)))))
                                /*----------------- 区域回档 ----------------*/
                                .then(Commands.literal("area")
                                        .then(Commands.argument("pos1", BlockPosArgument.blockPos())
                                                .then(Commands.argument("pos2", BlockPosArgument.blockPos())
                                                        /* 默认数据源 */
                                                        .executes(ctx -> rollbackArea(ctx.getSource(),
                                                                BlockPosArgument.getBlockPos(ctx, "pos1"),
                                                                BlockPosArgument.getBlockPos(ctx, "pos2"),
                                                                GuGuGu.INSTANCE.getBackupManager().getConfig().getHotRollbackSource()))
                                                        /* area hot ... */
                                                        .then(Commands.literal("hot")
                                                                .then(Commands.literal(BackupManager.INCREMENTAL)
                                                                        .executes(ctx -> rollbackArea(ctx.getSource(),
                                                                                BlockPosArgument.getBlockPos(ctx, "pos1"),
                                                                                BlockPosArgument.getBlockPos(ctx, "pos2"),
                                                                                BackupManager.INCREMENTAL)))
                                                                .then(Commands.literal(BackupManager.FULL)
                                                                        .executes(ctx -> rollbackArea(ctx.getSource(),
                                                                                BlockPosArgument.getBlockPos(ctx, "pos1"),
                                                                                BlockPosArgument.getBlockPos(ctx, "pos2"),
                                                                                BackupManager.FULL))))))))
        );
    }

    private static boolean canUse(CommandSourceStack src) {
        if (src.hasPermission(PERMISSION_LEVEL)) return true;

        if (src.getEntity() == null) return false;

        String name = src.getEntity().getName().getString();
        return GuGuGu.getINSTANCE()
                .getBackupManager()
                .getConfig()
                .getCommandWhitelist()
                .contains(name);
    }

    private static int inc(CommandSourceStack src) {
        BackupManager mgr = GuGuGu.getINSTANCE().getBackupManager();
        try {
            mgr.backupIncremental("Manual incremental backup by " + src.getTextName());
            src.sendSuccess(() -> Component.literal("§a[GuGuGu] 增量备份任务已提交。"), true);
        } catch (Exception e) {
            src.sendFailure(Component.literal("§c[GuGuGu] 增量备份失败: " + e.getMessage()));
        }
        return 1;
    }

    private static int full(CommandSourceStack src) {
        BackupManager mgr = GuGuGu.getINSTANCE().getBackupManager();
        mgr.backupFull("Manual full backup by " + src.getTextName())
                .thenAccept(p -> src.sendSuccess(() -> Component.literal("§a[GuGuGu] 全量备份完成: " + p), false))
                .exceptionally(t -> {
                    src.sendFailure(Component.literal("§c[GuGuGu] 全量备份失败: " + t.getMessage()));
                    return null;
                });
        src.sendSuccess(() -> Component.literal("§e[GuGuGu] 全量备份已开始，完成后会通知。"), true);
        return 1;
    }

    private static int rollback(CommandSourceStack src,
                                BlockPos blockPos,
                                boolean formSource,
                                String hotType) throws CommandSyntaxException {

        BackupManager mgr = GuGuGu.getINSTANCE().getBackupManager();
        ServerLevel level = src.getLevel();
        ChunkPos pos = formSource ?
                new ChunkPos(src.getPlayerOrException().getOnPos()) :
                new ChunkPos(blockPos);

        try {
            boolean ok = mgr.rollbackChunkHot(level, pos, hotType);
            if (ok) {
                src.sendSuccess(() -> Component.literal("§a[GuGuGu] 区块 [" + pos.x + ", " + pos.z + "] 回档完成。"), false);
            } else {
                src.sendFailure(Component.literal("§e[GuGuGu] 未找到备份或区块无变动。"));
            }
        } catch (Exception e) {
            src.sendFailure(Component.literal("§c[GuGuGu] 回档失败: " + e.getMessage()));
        }
        return 1;
    }

    private static int rollbackArea(CommandSourceStack src,
                                    BlockPos pos1,
                                    BlockPos pos2,
                                    String hotType) {

        BackupManager mgr = GuGuGu.getINSTANCE().getBackupManager();
        ServerLevel level = src.getLevel();
        ChunkPos first = new ChunkPos(pos1);
        ChunkPos second = new ChunkPos(pos2);

        try {
            boolean ok = mgr.rollbackChunkHot(level, first, second, hotType);
            if (ok) {
                src.sendSuccess(() -> Component.literal(
                        "§a[GuGuGu] 区块范围 [" +
                                first.x + ", " + first.z + "] ~ [" +
                                second.x + ", " + second.z + "] 回档完成。"), false);
            } else {
                src.sendFailure(Component.literal("§e[GuGuGu] 部分 Region 文件缺失，回档终止。"));
            }
        } catch (Exception e) {
            src.sendFailure(Component.literal("§c[GuGuGu] 范围回档失败: " + e.getMessage()));
        }
        return 1;
    }
}
