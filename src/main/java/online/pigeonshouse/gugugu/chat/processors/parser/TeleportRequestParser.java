package online.pigeonshouse.gugugu.chat.processors.parser;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import online.pigeonshouse.gugugu.chat.MessageContext;
import online.pigeonshouse.gugugu.chat.MessageProcessor;
import online.pigeonshouse.gugugu.chat.elements.TeleportElement;

public class TeleportRequestParser implements MessageProcessor {
    @Override
    public void process(MessageContext context) {
        String message = context.getOriginalMessage().trim();

        if (message.contains(" ") || message.isEmpty()) {
            return;
        }

        ServerPlayer currentPlayer = context.getSender();
        ServerPlayer target = findPlayerByName(message, currentPlayer.getServer());

        if (target != null) {
            context.getElements().clear();
            context.addElement(new TeleportElement(message, target));
        }
    }

    private ServerPlayer findPlayerByName(String name, MinecraftServer server) {
        return server.getPlayerList().getPlayerByName(name);
    }

    @Override
    public void test(MessageContext context) {
        String message = context.getOriginalMessage().trim();
        ServerPlayer currentPlayer = context.getSender();

        if (message.contains(" ") || message.isEmpty()) {
            message = currentPlayer.getName().getString();
        }

        ServerPlayer target = findPlayerByName(message, currentPlayer.getServer());
        context.getElements().clear();

        if (target != null) {
            context.addElement(new TeleportElement(message, target));
        } else {
            context.addElement(new TeleportElement(currentPlayer.getName().getString(),
                    currentPlayer));
        }
    }
}