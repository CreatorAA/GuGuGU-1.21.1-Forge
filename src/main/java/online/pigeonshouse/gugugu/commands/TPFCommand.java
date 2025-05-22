package online.pigeonshouse.gugugu.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.level.ServerPlayer;

public class TPFCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> then = Commands.literal("tpf")
                .then(Commands.argument("player", EntityArgument.player())
                        .executes(source -> tpf(source.getSource(), EntityArgument.getPlayer(source, "player")))
                );
        dispatcher.register(then);
    }

    private static int tpf(CommandSourceStack source, ServerPlayer friend) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();

        player.teleportTo(friend.serverLevel(),
                friend.getX(), friend.getY(), friend.getZ(),
                friend.getYRot(), friend.getXRot());
        return 0;
    }
}
