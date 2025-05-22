package online.pigeonshouse.gugugu.chat.processors;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
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

public class XaeroMapUtilProcessor implements MessageProcessor {
    private static final String COMMAND_PREFIX = "/xaeromap addwaypoint ";
    private static final int WAYPOINT_EXPIRATION_HOURS = 1;
    private static final String WAYPOINT_NAME_SUFFIX = " Waypoint";

    private final Cache<String, String> xaeroWaypoints = CacheBuilder.newBuilder()
            .expireAfterWrite(WAYPOINT_EXPIRATION_HOURS, TimeUnit.HOURS)
            .build();

    @Getter
    private final XaeroWaypointCommand command = new XaeroWaypointCommand();

    public XaeroMapUtilProcessor() {
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

        ServerPlayer sender = context.getSender();
        MinecraftServer server = sender.getServer();

        try {
            LevelWaypoint jmWp = LevelWaypoint.journeyMapParse(message);
            addConvertButton(context, jmWp);
        } catch (IllegalArgumentException ignored) {
            ServerPlayer target = server.getPlayerList().getPlayerByName(message);
            if (target != null) {
                addAddButton(context, target);
            }
        }
    }

    @Override
    public void test(MessageContext context) {
        String message = getProcessedMessage(context);
        if (message == null) return;

        ServerPlayer sender = context.getSender();
        MinecraftServer server = sender.getServer();

        try {
            LevelWaypoint jmWp = LevelWaypoint.journeyMapParse(message);
            addConvertButton(context, jmWp);
        } catch (IllegalArgumentException ignored) {
            ServerPlayer target = server.getPlayerList().getPlayerByName(message);
            if (target == null) {
                target = sender;
            }

            addAddButton(context, target);
        }
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
                .state(false)
                .namespace(dim.getNamespace())
                .worldName(dim.getPath())
                .build();
    }

    private void addAddButton(MessageContext context, ServerPlayer target) {
        String name = target.getName().getString();
        LevelWaypoint wp = buildLevelWaypoint(name, target);
        String uuid = UUID.randomUUID().toString();

        context.addElement(new TextElement(" "));
        context.addElement(createButton(
                "[点击添加Xaero地图标记]",
                name,
                uuid,
                ChatFormatting.AQUA
        ));
        xaeroWaypoints.put(uuid, wp.getXaeroWaypointString());
    }

    private void addConvertButton(MessageContext context, LevelWaypoint jmWp) {
        String xaeroString = jmWp.getXaeroWaypointString();
        String uuid = UUID.randomUUID().toString();
        xaeroWaypoints.put(uuid, xaeroString);

        context.addElement(new TextElement(" "));
        context.addElement(createButton(
                "[点击转换为XaeroMap路径点]",
                jmWp.getName(),
                uuid,
                ChatFormatting.AQUA
        ));
    }

    private StyledTextElement createButton(String label, String hoverText, String uuid, ChatFormatting color) {
        return StyledTextElement.builder(label)
                .color(color)
                .hoverText(hoverText)
                .clickRunCommand(COMMAND_PREFIX + uuid)
                .build();
    }

    public class XaeroWaypointCommand {
        public void register(CommandDispatcher<CommandSourceStack> dispatcher) {
            dispatcher.register(Commands.literal("xaeromap")
                    .then(Commands.literal("addwaypoint")
                            .then(Commands.argument("uuid", StringArgumentType.string())
                                    .executes(ctx -> handleWaypointCommand(
                                            ctx.getSource(),
                                            StringArgumentType.getString(ctx, "uuid")
                                    )))));
        }

        private int handleWaypointCommand(CommandSourceStack src, String uuid) {
            String waypoint = xaeroWaypoints.getIfPresent(uuid);
            if (waypoint != null) {
                Component msg = Component.literal(waypoint)
                        .append(Component.literal("（如果您看到这条消息，表示您并没有安装Xaero地图）")
                                .withStyle(ChatFormatting.YELLOW));
                src.sendSystemMessage(msg);
                return 1;
            }
            src.sendFailure(Component.literal("未找到对应的Xaero标记或标记已过期"));
            return 0;
        }
    }
}
