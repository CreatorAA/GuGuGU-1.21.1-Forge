package online.pigeonshouse.gugugu.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Monster;
import online.pigeonshouse.gugugu.utils.MinecraftUtil;

import java.lang.management.ManagementFactory;
import java.text.DecimalFormat;
import java.util.List;

public class StatusMessageCommand {
    private static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("#.##");

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                LiteralArgumentBuilder.<CommandSourceStack>literal("showstats")
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            sendStats(player, player.getServer());
                            return 1;
                        })
        );
    }

    private static void sendStats(ServerPlayer player, MinecraftServer server) {
        double mspt = server.getAverageTickTimeNanos() / 1_000_000.0;
        double tps = Math.min(1000.0 / mspt, 20.0);
        long usedMem = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / (1024 * 1024);
        long maxMem = Runtime.getRuntime().maxMemory() / (1024 * 1024);

        long uptimeMs = ManagementFactory.getRuntimeMXBean().getUptime();
        long upH = uptimeMs / 3_600_000;
        long upM = (uptimeMs / 60_000) % 60;

        int playerCount = server.getPlayerCount();
        int maxPlayers = server.getMaxPlayers();
        String version = server.getServerVersion();
        long seed = server.overworld().getSeed();

        ServerLevel world = player.serverLevel();
        long ticks = world.getDayTime();
        long day = ticks / 24000 + 1;
        long tod = ticks % 24000;
        int h = (int) ((tod / 1000 + 6) % 24);
        int m = (int) ((tod % 1000) * 60 / 1000);
        String timeStr = String.format("Day %d | %02d:%02d", day, h, m);

        player.sendSystemMessage(Component.literal("=== Server Status ===").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));

        MutableComponent base = Component.literal("")
                .append(interactiveLabel("Version", version,
                        null,
                        null, ChatFormatting.AQUA))
                .append(interactiveLabel("Players", playerCount + "/" + maxPlayers,
                        null, null,
                        playerCount >= maxPlayers * 0.9 ? ChatFormatting.RED : playerCount >= maxPlayers * 0.7 ? ChatFormatting.YELLOW : ChatFormatting.GREEN))
                .append(interactiveLabel("Time", timeStr,
                        null, null, ChatFormatting.YELLOW))
                .append(interactiveLabel("Uptime", upH + "h " + upM + "m",
                        null, null, ChatFormatting.GRAY))
                .append(interactiveLabel("Seed", String.valueOf(seed),
                        Component.literal("世界种子"),
                        new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, String.valueOf(seed)), ChatFormatting.DARK_GREEN));
        player.sendSystemMessage(base);

        MutableComponent perf = Component.literal("")
                .append(interactiveLabel("TPS", DECIMAL_FORMAT.format(tps) + " (" + DECIMAL_FORMAT.format(tps / 20 * 100) + "%)",
                        null, null,
                        tps >= 19.5 ? ChatFormatting.GREEN : tps >= 18 ? ChatFormatting.YELLOW : ChatFormatting.RED))
                .append(interactiveLabel("MSPT", DECIMAL_FORMAT.format(mspt) + "ms",
                        null, null,
                        mspt <= 50 ? ChatFormatting.GREEN : mspt <= 100 ? ChatFormatting.YELLOW : ChatFormatting.RED))
                .append(interactiveLabel("Memory", usedMem + "MB/" + maxMem + "MB",
                        null, null,
                        (double) usedMem / maxMem > 0.9 ? ChatFormatting.RED : (double) usedMem / maxMem > 0.7 ? ChatFormatting.YELLOW : ChatFormatting.GREEN));
        player.sendSystemMessage(perf);

        player.sendSystemMessage(Component.literal("--- World Details ---").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        List<ServerLevel> levels = MinecraftUtil.iterableToStream(server.getAllLevels()).toList();
        levels.forEach(lvl -> sendWorldInfo(player, lvl));

        player.sendSystemMessage(Component.literal("====================").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
    }

    private static void sendWorldInfo(ServerPlayer player, ServerLevel lvl) {
        String nm = lvl.dimension().location().getPath();
        int p = lvl.players().size();
        int chunks = lvl.getChunkSource().getLoadedChunksCount();
        List<Entity> ents = MinecraftUtil.iterableToList(lvl.getEntities().getAll());
        long mons = ents.stream().filter(e -> e instanceof Monster).count();
        int tot = ents.size();
        int cap = p * 70;
        double perc = (double) mons / cap;
        ChatFormatting mobColor = perc < 0.5 ? ChatFormatting.GREEN : perc < 0.8 ? ChatFormatting.YELLOW : perc < 1 ? ChatFormatting.GOLD : ChatFormatting.RED;

        MutableComponent info = Component.literal("")
                .append(interactiveLabel(nm, p + " players",
                        null, null, ChatFormatting.AQUA))
                .append(Component.literal(" | ").withStyle(ChatFormatting.GRAY))
                .append(interactiveLabel("Chunks", String.valueOf(chunks),
                        null, null, ChatFormatting.LIGHT_PURPLE))
                .append(Component.literal(" | ").withStyle(ChatFormatting.GRAY))
                .append(interactiveLabel("Entities", String.valueOf(tot),
                        null, null,
                        tot > 1000 ? ChatFormatting.RED : tot > 500 ? ChatFormatting.YELLOW : ChatFormatting.GREEN))
                .append(Component.literal(" | ").withStyle(ChatFormatting.GRAY))
                .append(interactiveLabel("Mobs", mons + "/" + cap + " (" + DECIMAL_FORMAT.format(perc * 100) + "%)",
                        null, null, mobColor));
        player.sendSystemMessage(info);
    }

    private static MutableComponent interactiveLabel(String label, String value, Component hover, ClickEvent click, ChatFormatting color) {
        MutableComponent comp = Component.literal(label + ": ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(value).withStyle(color));
        if (hover != null) {
            comp.withStyle(style -> style.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                    Component.literal("点击复制：").withStyle(ChatFormatting.GOLD).append(hover))));
        }
        if (click != null) comp.withStyle(style -> style.withClickEvent(click));
        return comp.append("  ");
    }
}