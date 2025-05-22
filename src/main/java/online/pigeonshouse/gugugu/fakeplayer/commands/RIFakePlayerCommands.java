package online.pigeonshouse.gugugu.fakeplayer.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import lombok.extern.slf4j.Slf4j;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.DimensionArgument;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import online.pigeonshouse.gugugu.GuGuGu;
import online.pigeonshouse.gugugu.event.MinecraftServerEvents;
import online.pigeonshouse.gugugu.fakeplayer.PlayerInventoryViewer;
import online.pigeonshouse.gugugu.fakeplayer.RIFakeServerPlayer;
import online.pigeonshouse.gugugu.fakeplayer.RIFakeServerPlayerFactory;
import online.pigeonshouse.gugugu.fakeplayer.config.FakePlayerConfig;
import online.pigeonshouse.gugugu.fakeplayer.control.Behavior;
import online.pigeonshouse.gugugu.fakeplayer.control.PlayerControl;
import online.pigeonshouse.gugugu.fakeplayer.control.behaviors.Attack;
import online.pigeonshouse.gugugu.fakeplayer.control.behaviors.Drop;
import online.pigeonshouse.gugugu.fakeplayer.control.behaviors.Jump;
import online.pigeonshouse.gugugu.fakeplayer.control.behaviors.Use;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class RIFakePlayerCommands {
    public static final SimpleCommandExceptionType PLAYER_EXIST = new SimpleCommandExceptionType(Component.literal("The player already exists"));
    public static final SimpleCommandExceptionType PLAYER_IS_REAL = new SimpleCommandExceptionType(Component.literal("The player is a real player"));
    /// ///////////  行为控制  /////////////////////

    public static final Map<UUID, PlayerControl> controlTickTask = new ConcurrentHashMap<>();
    private static final String COMMAND_BASE = "rifakeplayer";
    private static final String COMMAND_ALIAS = "fp";

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, FakePlayerConfig config) {
        LiteralArgumentBuilder<CommandSourceStack> baseCommand = Commands.literal(COMMAND_BASE)
                .requires(source -> source.hasPermission(config.getCommandLevel()));

        baseCommand.then(Commands.literal("create")
                .then(Commands.argument("player", StringArgumentType.string())
                        .executes(ctx -> createFakePlayer(ctx.getSource(), StringArgumentType.getString(ctx, "player")))
                        .then(Commands.argument("pos", Vec3Argument.vec3())
                                .executes(ctx -> createFakePlayer(ctx.getSource(), StringArgumentType.getString(ctx, "player"), Vec3Argument.getVec3(ctx, "pos")))
                                .then(Commands.literal("in")
                                        .then(Commands.argument("in", DimensionArgument.dimension())
                                                .executes(ctx -> createFakePlayer(ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "player"),
                                                        Vec3Argument.getVec3(ctx, "pos"),
                                                        DimensionArgument.getDimension(ctx, "in")))))
                                .then(Commands.argument("yaw", FloatArgumentType.floatArg())
                                        .then(Commands.argument("pitch", FloatArgumentType.floatArg())
                                                .executes(ctx -> createFakePlayer(ctx.getSource(), StringArgumentType.getString(ctx, "player"), Vec3Argument.getVec3(ctx, "pos"), FloatArgumentType.getFloat(ctx, "yaw"), FloatArgumentType.getFloat(ctx, "pitch")))
                                                .then(Commands.literal("in")
                                                        .then(Commands.argument("in", DimensionArgument.dimension())
                                                                .executes(ctx -> createFakePlayer(ctx.getSource(),
                                                                        StringArgumentType.getString(ctx, "player"),
                                                                        Vec3Argument.getVec3(ctx, "pos"),
                                                                        FloatArgumentType.getFloat(ctx, "yaw"),
                                                                        FloatArgumentType.getFloat(ctx, "pitch"),
                                                                        DimensionArgument.getDimension(ctx, "in"))))))
                                )
                        )
                )
        );

        baseCommand.then(Commands.literal("kill")
                .then(Commands.argument("player", EntityArgument.player())
                        .executes(ctx -> killFakePlayer(EntityArgument.getEntity(ctx, "player")))
                )
        );

        baseCommand.then(Commands.literal("open")
                .requires(source -> source.hasPermission(4))
                .then(Commands.argument("target", EntityArgument.player())
                        .executes(ctx -> openFakePlayerInventory(ctx.getSource().getPlayerOrException(), EntityArgument.getPlayer(ctx, "target"), true))
                        .then(Commands.argument("allowModify", BoolArgumentType.bool())
                                .executes(ctx -> openFakePlayerInventory(ctx.getSource().getPlayerOrException(), EntityArgument.getPlayer(ctx, "target"), BoolArgumentType.getBool(ctx, "allowModify")))
                        )
                        .then(Commands.literal("viewer")
                                .then(Commands.argument("viewer", EntityArgument.player())
                                        .executes(ctx -> openFakePlayerInventory(EntityArgument.getPlayer(ctx, "viewer"), EntityArgument.getPlayer(ctx, "target"), true))
                                        .then(Commands.argument("allowModify", BoolArgumentType.bool())
                                                .executes(ctx -> openFakePlayerInventory(ctx.getSource().getPlayerOrException(), EntityArgument.getPlayer(ctx, "target"), BoolArgumentType.getBool(ctx, "allowModify")))
                                        )
                                )
                        )
                )
        );

        baseCommand.then(Commands.literal("setCreateName")
                .then(Commands.literal("prefix")
                        .executes(ctx -> setCreateNamePrefix(""))
                        .then(Commands.argument("prefix", StringArgumentType.string())
                                .executes(ctx -> setCreateNamePrefix(StringArgumentType.getString(ctx, "prefix")))
                        )
                )
                .then(Commands.literal("suffix")
                        .executes(ctx -> setCreateNameSuffix(""))
                        .then(Commands.argument("suffix", StringArgumentType.string())
                                .executes(ctx -> setCreateNameSuffix(StringArgumentType.getString(ctx, "suffix")))
                        )
                )
        );

        baseCommand.then(Commands.literal("autoLogin")
                        .then(Commands.literal("enable")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ctx -> {
                                            ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
                                            FakePlayerConfig fakePlayerConfig = GuGuGu.INSTANCE.getFakePlayerConfig();
                                            String playerName = player.getName().getString();
                                            fakePlayerConfig.getAutoLoginNames().add(playerName);
                                            fakePlayerConfig.save();
                                            ctx.getSource().sendSystemMessage(Component.literal("自动登录设置成功：" + playerName)
                                                    .withStyle(ChatFormatting.YELLOW));
                                            return 1;
                                        })
                                )
                        )
                        .then(Commands.literal("disable")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ctx -> {
                                            ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
                                            FakePlayerConfig fakePlayerConfig = GuGuGu.INSTANCE.getFakePlayerConfig();
                                            String playerName = player.getName().getString();
                                            fakePlayerConfig.getAutoLoginNames().remove(playerName);
                                            fakePlayerConfig.save();
                                            ctx.getSource().sendSystemMessage(Component.literal("自动登录已移除：" + playerName)
                                                    .withStyle(ChatFormatting.YELLOW));
                                            return 1;
                                        })
                                )
                        )
                        .then(Commands.literal("list")
                                .executes(ctx -> {
                                    FakePlayerConfig fakePlayerConfig = GuGuGu.INSTANCE.getFakePlayerConfig();
                                    Set<String> autoLoginNames = fakePlayerConfig.getAutoLoginNames();
                                    ctx.getSource().sendSystemMessage(Component.literal("自动登录假人列表：" + String.join(", ", autoLoginNames))
                                            .withStyle(ChatFormatting.YELLOW));
                                    return 1;
                                })
                        )
        );

        baseCommand.then(Commands.literal("test")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                        .executes(ctx -> {
                                            ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
                                            if (player instanceof RIFakeServerPlayer fakeServerPlayer) {

                                                return 1;
                                            }

                                            return 1;
                                        })
                                )
                        )
        );

        LiteralArgumentBuilder<CommandSourceStack> controlCommand = Commands.literal("control");

        controlCommand.then(Commands.argument("player", EntityArgument.player())
                .then(Commands.literal("kill")
                        .executes(source -> executeDisconnect(source.getSource(), EntityArgument.getPlayer(source, "player"))))
                .then(intervalAndContinue("use"))
                .then(intervalAndContinue("attack"))
                .then(intervalAndContinue("jump"))
                .then(drop())
                .then(Commands.literal("stopAll")
                        .executes(ctx -> stopAllBehavior(EntityArgument.getPlayer(ctx, "player")))
                )
        );

        baseCommand.then(controlCommand);
        dispatcher.register(baseCommand);
        dispatcher.register(Commands.literal(COMMAND_ALIAS).redirect(dispatcher.getRoot().getChild(COMMAND_BASE)));
    }

    private static int executeDisconnect(CommandSourceStack source, ServerPlayer player) {
        if (player instanceof RIFakeServerPlayer fakeServerPlayer) {
            fakeServerPlayer.disconnect(Component.empty());
            return 0;
        }

        source.sendFailure(Component.literal("[GuGuGu] 无法移除非本mod召唤出的假人的玩家！"));
        return 0;
    }

    private static int setCreateNamePrefix(String prefix) {
        FakePlayerConfig config = GuGuGu.INSTANCE.getFakePlayerConfig();
        config.setFakePlayerNamePrefix(prefix);
        config.save();
        return 0;
    }

    private static int setCreateNameSuffix(String suffix) {
        FakePlayerConfig config = GuGuGu.INSTANCE.getFakePlayerConfig();
        config.setFakePlayerNameSuffix(suffix);
        config.save();
        return 0;
    }

    private static int openFakePlayerInventory(ServerPlayer viewer, ServerPlayer target, boolean allowModify) throws CommandSyntaxException {
        if (target instanceof RIFakeServerPlayer fakeServerPlayer) {
            PlayerInventoryViewer.openFor(viewer, fakeServerPlayer, allowModify);
            return 0;
        }

        throw PLAYER_IS_REAL.create();
    }

    private static int killFakePlayer(Entity player) throws CommandSyntaxException {
        if (player instanceof RIFakeServerPlayer fakeServerPlayer) {
            fakeServerPlayer.disconnect(Component.empty());
            return 0;
        }

        throw PLAYER_IS_REAL.create();
    }

    private static int createFakePlayer(CommandSourceStack source, String player, Vec3 pos, ServerLevel in) throws CommandSyntaxException {
        return createFakePlayer(source, player, pos, source.getPlayerOrException().getYRot(), source.getPlayerOrException().getXRot(), in);
    }

    private static int createFakePlayer(CommandSourceStack source, String playerName, Vec3 pos, float yaw, float pitch) throws CommandSyntaxException {
        return createFakePlayer(source, playerName, pos, yaw, pitch, source.getLevel());
    }

    private static int createFakePlayer(CommandSourceStack source, String playerName, Vec3 pos) throws CommandSyntaxException {
        return createFakePlayer(source, playerName, pos, source.getPlayerOrException().getYRot(), source.getPlayerOrException().getXRot(), source.getLevel());
    }

    public static int createFakePlayer(CommandSourceStack source, String playerName) throws CommandSyntaxException {
        return createFakePlayer(source, playerName, source.getPlayerOrException().position(), source.getPlayerOrException().getYRot(), source.getPlayerOrException().getXRot(), source.getLevel());
    }

    private static int createFakePlayer(CommandSourceStack source, String playerName, Vec3 pos, float yaw, float pitch, ServerLevel in) throws CommandSyntaxException {
        FakePlayerConfig fakePlayerConfig = GuGuGu.INSTANCE.getFakePlayerConfig();

        playerName = fakePlayerConfig.getFakePlayerNamePrefix()
                + playerName
                + fakePlayerConfig.getFakePlayerNameSuffix();

        if (source.getServer().getPlayerList().getPlayerByName(playerName) != null) {
            throw PLAYER_EXIST.create();
        }

        GameType gameMode;

        if (source.isPlayer()) {
            gameMode = source.getPlayerOrException().gameMode.getGameModeForPlayer();
        } else {
            gameMode = GameType.SURVIVAL;
        }

        RIFakeServerPlayerFactory.createFakeServerPlayer(source.getServer(), playerName, in, gameMode, pos.x, pos.y, pos.z, yaw, pitch);
        return 0;
    }

    public static void removePlayerControl(UUID uuid) {
        PlayerControl remove = controlTickTask.remove(uuid);
        if (remove != null)
            MinecraftServerEvents.SERVER_TICK.removeCallback(remove);
    }

    private static Behavior buildJumpBehavior(ServerPlayer player, String behaviorType, Map<String, Object> map) {
        Jump jump = new Jump(player);
        switch (behaviorType) {
            case "continue" -> {
                int count = Integer.parseInt(map.getOrDefault("count", "-1").toString());
                jump.setCount(count);
            }
            case "interval" -> {
                int count = Integer.parseInt(map.getOrDefault("count", "-1").toString());
                int tick = Integer.parseInt(map.getOrDefault("tick", "1").toString());

                jump.setCount(count);
                jump.setInterval(tick);
            }
        }
        return jump;
    }

    private static Behavior buildAttackBehavior(ServerPlayer player, String behaviorType, Map<String, Object> map) {

        Attack attack = new Attack(player);
        switch (behaviorType) {
            case "continue" -> {
                int count = Integer.parseInt(map.getOrDefault("count", "-1").toString());
                attack.setCount(count);
            }
            case "interval" -> {
                int count = Integer.parseInt(map.getOrDefault("count", "-1").toString());
                int tick = Integer.parseInt(map.getOrDefault("tick", "1").toString());

                attack.setCount(count);
                attack.setInterval(tick);
            }
        }
        return attack;
    }

    private static Behavior buildUseBehavior(ServerPlayer player, String behaviorType, Map<String, Object> map) {
        Use use = new Use(player);
        switch (behaviorType) {
            case "continue" -> {
                int count = Integer.parseInt(map.getOrDefault("count", "-1").toString());
                use.setCount(count);
            }
            case "interval" -> {
                int count = Integer.parseInt(map.getOrDefault("count", "-1").toString());
                int tick = Integer.parseInt(map.getOrDefault("tick", "1").toString());

                use.setCount(count);
                use.setInterval(tick);
            }
        }

        return use;
    }

    private static Behavior buildDropBehavior(ServerPlayer player, String behaviorType, Map<String, Object> map) {
        Drop drop = new Drop(player);
        boolean dropGroup = Boolean.parseBoolean(map.getOrDefault("drop_group", "false").toString());
        drop.setDropGroup(dropGroup);

        switch (behaviorType) {
            case "continue" -> {
                int count = Integer.parseInt(map.getOrDefault("count", "-1").toString());
                drop.setCount(count);
            }
            case "interval" -> {
                int count = Integer.parseInt(map.getOrDefault("count", "-1").toString());
                int tick = Integer.parseInt(map.getOrDefault("tick", "1").toString());

                drop.setCount(count);
                drop.setInterval(tick);
            }
        }

        return drop;
    }

    private static int addBehavior(ServerPlayer player, String action, String type, int count, int delay, Boolean dropGroup) {
        Map<String, Object> data = new HashMap<>();
        data.put("drop_group", dropGroup);

        if (delay > 0 || delay == -1) {
            data.put("tick", delay);
        }

        if (count > 0 || count == -1) {
            data.put("count", count);
        }

        Behavior behavior = switch (action) {
            case "jump" -> buildJumpBehavior(player, type, data);
            case "attack" -> buildAttackBehavior(player, type, data);
            case "use" -> buildUseBehavior(player, type, data);
            case "drop" -> buildDropBehavior(player, type, data);
            default -> throw new IllegalStateException("Unexpected value: " + action);
        };

        PlayerControl control = controlTickTask.get(player.getUUID());

        if (control == null) {
            control = new PlayerControl(player);
            control.getSynthesizer().add(behavior.action(), behavior);
            controlTickTask.put(player.getUUID(), control);
            MinecraftServerEvents.SERVER_TICK.addCallback(control);
            return 0;
        }

        control.getSynthesizer().add(behavior.action(), behavior);
        return 0;
    }

    private static int stopAllBehavior(ServerPlayer player) {
        PlayerControl removed = controlTickTask.remove(player.getUUID());
        if (removed != null) {
            MinecraftServerEvents.SERVER_TICK.removeCallback(removed);
        }

        return 0;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> drop() {
        LiteralArgumentBuilder<CommandSourceStack> literal = Commands.literal("drop");
        literal = dropGroupLiteral(literal);
        literal = dropGroupContinueLiteral(literal);
        literal = dropGroupIntervalLiteral(literal);
        return literal;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> dropGroupContinueLiteral(LiteralArgumentBuilder<CommandSourceStack> literal) {
        return literal.then(Commands.literal("continue")
                .executes(s -> addBehavior(EntityArgument.getPlayer(s, "player"), "drop", "continue", 0, 0, false))
                .then(Commands.literal("dropGroup")
                        .executes(s -> addBehavior(EntityArgument.getPlayer(s, "player"), "drop", "continue", 0, 0, true)))
                .then(Commands.literal("count")
                        .then(Commands.argument("count", IntegerArgumentType.integer())
                                .executes(s -> addBehavior(EntityArgument.getPlayer(s, "player"), "drop", "continue", IntegerArgumentType.getInteger(s, "count"), 0, false))
                                .then(Commands.literal("dropGroup")
                                        .executes(s -> addBehavior(EntityArgument.getPlayer(s, "player"), "drop", "continue", IntegerArgumentType.getInteger(s, "count"), 0, true))
                                )
                        )
                )
        );
    }

    private static LiteralArgumentBuilder<CommandSourceStack> dropGroupIntervalLiteral(LiteralArgumentBuilder<CommandSourceStack> literal) {
        return literal.then(Commands.literal("interval")
                .executes(s -> addBehavior(EntityArgument.getPlayer(s, "player"), "drop", "interval", 0, 0, false))
                .then(Commands.literal("dropGroup")
                        .executes(s -> addBehavior(EntityArgument.getPlayer(s, "player"), "drop", "interval", 0, 0, true))
                )
                .then(Commands.literal("count")
                        .then(Commands.argument("count", IntegerArgumentType.integer())
                                .executes(s -> addBehavior(EntityArgument.getPlayer(s, "player"), "drop", "interval", IntegerArgumentType.getInteger(s, "count"), 0, false))
                                .then(Commands.literal("dropGroup")
                                        .executes(s -> addBehavior(EntityArgument.getPlayer(s, "player"), "drop", "interval", IntegerArgumentType.getInteger(s, "count"), 0, true))
                                )
                                .then(Commands.literal("delay")
                                        .then(Commands.argument("delay", IntegerArgumentType.integer())
                                                .executes(s -> addBehavior(EntityArgument.getPlayer(s, "player"), "drop", "interval", IntegerArgumentType.getInteger(s, "count"), IntegerArgumentType.getInteger(s, "delay"), false))
                                                .then(Commands.literal("dropGroup")
                                                        .executes(s -> addBehavior(EntityArgument.getPlayer(s, "player"), "drop", "interval", IntegerArgumentType.getInteger(s, "count"), IntegerArgumentType.getInteger(s, "delay"), true))
                                                )
                                        )
                                )
                        )
                )
                .then(Commands.literal("delay")
                        .then(Commands.argument("delay", IntegerArgumentType.integer())
                                .executes(s -> addBehavior(EntityArgument.getPlayer(s, "player"), "drop", "interval", 0, IntegerArgumentType.getInteger(s, "delay"), false))
                                .then(Commands.literal("dropGroup")
                                        .executes(s -> addBehavior(EntityArgument.getPlayer(s, "player"), "drop", "interval", 0, IntegerArgumentType.getInteger(s, "delay"), true))
                                )
                        )
                )
        );
    }

    private static LiteralArgumentBuilder<CommandSourceStack> dropGroupLiteral(LiteralArgumentBuilder<CommandSourceStack> literal) {
        return literal.then(Commands.literal("dropGroup")
                .executes(s -> addBehavior(EntityArgument.getPlayer(s, "player"), "drop", "continue", 0, 0, true)));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> continueLiteral(LiteralArgumentBuilder<CommandSourceStack> literal, String name) {
        return literal.then(Commands.literal("continue")
                .executes(s -> addBehavior(EntityArgument.getPlayer(s, "player"), name, "continue", 0, 0, null))
                .then(Commands.literal("count")
                        .then(Commands.argument("count", IntegerArgumentType.integer())
                                .executes(s -> addBehavior(EntityArgument.getPlayer(s, "player"), name, "continue", IntegerArgumentType.getInteger(s, "count"), 0, null))
                        )
                )
        );
    }

    private static LiteralArgumentBuilder<CommandSourceStack> intervalLiteral(LiteralArgumentBuilder<CommandSourceStack> literal, String name) {
        return literal.then(Commands.literal("interval")
                .executes(s -> addBehavior(EntityArgument.getPlayer(s, "player"), name, "interval", 0, 0, null))
                .then(Commands.literal("count")
                        .then(Commands.argument("count", IntegerArgumentType.integer())
                                .executes(s -> addBehavior(EntityArgument.getPlayer(s, "player"), name, "interval", IntegerArgumentType.getInteger(s, "count"), 0, null))
                                .then(Commands.literal("delay")
                                        .then(Commands.argument("delay", IntegerArgumentType.integer())
                                                .executes(s -> addBehavior(EntityArgument.getPlayer(s, "player"), name, "interval", IntegerArgumentType.getInteger(s, "count"), IntegerArgumentType.getInteger(s, "delay"), null))
                                        )
                                )
                        )
                )
                .then(Commands.literal("delay")
                        .then(Commands.argument("delay", IntegerArgumentType.integer())
                                .executes(s -> addBehavior(EntityArgument.getPlayer(s, "player"), name, "interval", 0, IntegerArgumentType.getInteger(s, "delay"), null))
                        )
                )
        );
    }

    private static LiteralArgumentBuilder<CommandSourceStack> intervalAndContinue(String name) {
        LiteralArgumentBuilder<CommandSourceStack> literal = Commands.literal(name)
                .executes(s -> addBehavior(EntityArgument.getPlayer(s, "player"), name, "once", 1, 0, null));

        literal = continueLiteral(literal, name);
        literal = intervalLiteral(literal, name);
        return literal;
    }
}
