package online.pigeonshouse.gugugu.chat.processors;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import lombok.Getter;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import online.pigeonshouse.gugugu.chat.MessageContext;
import online.pigeonshouse.gugugu.chat.MessageProcessor;
import online.pigeonshouse.gugugu.chat.elements.StyledTextElement;
import online.pigeonshouse.gugugu.chat.elements.TextElement;
import online.pigeonshouse.gugugu.event.MinecraftServerEvents;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class JourneyMapUtilProcessor implements MessageProcessor {
    private static final String COMMAND_PREFIX = "/journeymap addwaypoint ";
    private static final int WAYPOINT_EXPIRATION_HOURS = 1;
    private static final String WAYPOINT_NAME_SUFFIX = " Waypoint";

    private final Cache<String, String> journeyWaypoints = CacheBuilder.newBuilder()
            .expireAfterWrite(WAYPOINT_EXPIRATION_HOURS, TimeUnit.HOURS)
            .build();

    @Getter
    private final JourneyMapWaypointCommand command = new JourneyMapWaypointCommand();

    public JourneyMapUtilProcessor() {
        MinecraftServerEvents.COMMAND_REGISTER.addCallback(event ->
                command.register(event.getDispatcher()));
    }

    @Override
    public int getPriority() {
        return LOW - 20;
    }

    @Override
    public void process(MessageContext context) {
        String message = getProcessedMessage(context);
        if (message == null) return;

        try {
            LevelWaypoint xaeroWp = LevelWaypoint.xaeroParse(message);
            addConvertButton(context, xaeroWp);
        } catch (IllegalArgumentException e) {
            ServerPlayer player = findPlayerByName(message, context.getSender().server);
            if (player == null) return;

            LevelWaypoint newWp = buildLevelWaypoint(player.getName().getString(), player);
            addAddButton(context, newWp);
        }
    }

    @Override
    public void test(MessageContext context) {
        String message = getProcessedMessage(context);
        if (message == null) return;

        try {
            LevelWaypoint xaeroWp = LevelWaypoint.xaeroParse(message);
            addConvertButton(context, xaeroWp);
        } catch (IllegalArgumentException e) {
            ServerPlayer player = findPlayerByName(message, context.getSender().server);
            if (player == null) {
                player = context.getSender();
            }

            LevelWaypoint newWp = buildLevelWaypoint(player.getName().getString(), player);
            addAddButton(context, newWp);
        }
    }

    private ServerPlayer findPlayerByName(String name, MinecraftServer server) {
        return server.getPlayerList().getPlayerByName(name);
    }

    private String getProcessedMessage(MessageContext context) {
        String msg = context.getOriginalMessage();
        if (msg == null) return null;
        msg = msg.trim();
        return msg.isEmpty() ? null : msg;
    }

    private LevelWaypoint buildLevelWaypoint(String name, ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        ResourceLocation dim = level.dimension().location();

        return LevelWaypoint.builder()
                .name(name + WAYPOINT_NAME_SUFFIX)
                .abbreviation(name.substring(0, 1))
                .x(player.getBlockX())
                .y(player.getBlockY())
                .z(player.getBlockZ())
                .state(true)
                .namespace(dim.getNamespace())
                .worldName(dim.getPath())
                .build();
    }

    private void addAddButton(MessageContext context, LevelWaypoint wp) {
        String uuid = UUID.randomUUID().toString();
        context.addElement(new TextElement(" "));
        context.addElement(createButton("[点击添加Journey路径点]", wp.getName(), uuid));
        journeyWaypoints.put(uuid, wp.getJourneyMapWaypointString());
    }

    private void addConvertButton(MessageContext context, LevelWaypoint wp) {
        String uuid = UUID.randomUUID().toString();
        context.addElement(new TextElement(" "));
        context.addElement(createButton("[点击转换为Journey路径点]", wp.getName(), uuid));
        journeyWaypoints.put(uuid, wp.getJourneyMapWaypointString());
    }

    private StyledTextElement createButton(String label, String hoverText, String uuid) {
        return StyledTextElement.builder(label)
                .color(ChatFormatting.AQUA)
                .hoverText(hoverText)
                .clickRunCommand(COMMAND_PREFIX + uuid)
                .build();
    }

    public class JourneyMapWaypointCommand {
        public void register(CommandDispatcher<CommandSourceStack> dispatcher) {
            dispatcher.register(Commands.literal("journeymap")
                    .then(Commands.literal("addwaypoint")
                            .then(Commands.argument("uuid", StringArgumentType.string())
                                    .executes(ctx ->
                                            handleWaypointCommand(
                                                    ctx,
                                                    StringArgumentType.getString(ctx, "uuid")
                                            ))
                            )
                    )
            );
        }

        private int handleWaypointCommand(CommandContext<CommandSourceStack> ctx, String uuid) throws CommandSyntaxException {
            String waypoint = journeyWaypoints.getIfPresent(uuid);
            CommandSourceStack src = ctx.getSource();
            if (waypoint != null) {
                ServerPlayer player = src.getPlayerOrException();
                CommandSourceStack stack = player.createCommandSourceStack();
                MinecraftServer server = player.getServer();
                Commands commands = server.getCommands();
                commands.performPrefixedCommand(stack, waypoint);
                stack.sendSystemMessage(Component.literal("（由于技术原因，此消息依托命令报错执行，如果无法通过点击取得路径点，表示您缺少Journey模组）")
                        .withStyle(ChatFormatting.YELLOW));
                return 1;
            } else {
                src.sendFailure(Component.literal("未找到对应的 JourneyMap 标记或标记已过期"));
                return 0;
            }
        }
    }
}
