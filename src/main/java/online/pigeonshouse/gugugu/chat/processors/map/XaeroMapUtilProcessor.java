package online.pigeonshouse.gugugu.chat.processors.map;

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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class XaeroMapUtilProcessor implements MessageProcessor {
    private static final String COMMAND_PREFIX = "/xaeromap addwaypoint ";
    private static final int WAYPOINT_EXPIRATION_HOURS = 1;
    private static final String WAYPOINT_NAME_SUFFIX = " Waypoint";
    private static final Pattern COORD_PATTERN = Pattern.compile(
            "^\\[\\s*(-?\\d+)\\s*[ ,]?\\s*(-?\\d+)(?:\\s*[ ,]?\\s*(-?\\d+))?\\s*\\]$"
    );

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
        Matcher coordMatcher = COORD_PATTERN.matcher(message);
        if (coordMatcher.matches()) {
            int x = Integer.parseInt(coordMatcher.group(1));
            int y; int z;
            if (coordMatcher.group(3) != null) {
                y = Integer.parseInt(coordMatcher.group(2));
                z = Integer.parseInt(coordMatcher.group(3));
            } else {
                y = sender.getBlockY();
                z = Integer.parseInt(coordMatcher.group(2));
            }
            ServerLevel level = sender.serverLevel();
            ResourceLocation dim = level.dimension().location();

            LevelWaypoint wp = LevelWaypoint.builder()
                    .name(message + WAYPOINT_NAME_SUFFIX)
                    .abbreviation(message.substring(1, 2))
                    .x(x)
                    .y(y)
                    .z(z)
                    .state(false)
                    .namespace(dim.getNamespace())
                    .worldName(dim.getPath())
                    .build();

            addWaypointButton(context, wp);
            return;
        }

        try {
            LevelWaypoint jmWp = LevelWaypoint.journeyMapParse(message);
            addConvertButton(context, jmWp);
        } catch (IllegalArgumentException ignored) {
            MinecraftServer server = sender.getServer();
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
        // Coordinate test
        Matcher coordMatcher = COORD_PATTERN.matcher(message);
        if (coordMatcher.matches()) {
            int x = Integer.parseInt(coordMatcher.group(1));
            int y; int z;
            if (coordMatcher.group(3) != null) {
                y = Integer.parseInt(coordMatcher.group(2));
                z = Integer.parseInt(coordMatcher.group(3));
            } else {
                y = sender.getBlockY();
                z = Integer.parseInt(coordMatcher.group(2));
            }
            ServerLevel level = sender.serverLevel();
            ResourceLocation dim = level.dimension().location();

            LevelWaypoint wp = LevelWaypoint.builder()
                    .name(message + WAYPOINT_NAME_SUFFIX)
                    .abbreviation(message.substring(1, 2))
                    .x(x)
                    .y(y)
                    .z(z)
                    .state(false)
                    .namespace(dim.getNamespace())
                    .worldName(dim.getPath())
                    .build();

            addWaypointButton(context, wp);
            return;
        }

        // JourneyMap parse
        try {
            LevelWaypoint jmWp = LevelWaypoint.journeyMapParse(message);
            addConvertButton(context, jmWp);
        } catch (IllegalArgumentException ignored) {
            ServerPlayer target = sender.getServer().getPlayerList().getPlayerByName(message);
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
        addWaypointButton(context, wp);
    }

    private void addWaypointButton(MessageContext context, LevelWaypoint wp) {
        String uuid = UUID.randomUUID().toString();
        context.addElement(new TextElement(" "));
        context.addElement(createButton(
                "[点击添加Xaero地图标记]",
                wp.getName(),
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
                ChatFormatting.GREEN
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
