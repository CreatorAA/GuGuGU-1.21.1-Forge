package online.pigeonshouse.gugugu.chat.elements;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.level.ServerPlayer;
import online.pigeonshouse.gugugu.chat.MessageElement;

public class TeleportElement implements MessageElement {
    private final ServerPlayer targetPlayer;
    private final String rawText;

    public TeleportElement(String rawText, ServerPlayer targetPlayer) {
        this.rawText = rawText;
        this.targetPlayer = targetPlayer;
    }

    @Override
    public Component toComponent() {
        return Component.literal(rawText)
                .withStyle(ChatFormatting.GREEN)
                .withStyle(style -> style
                        .withBold(true)
                        .withUnderlined(true)
                        .withClickEvent(new ClickEvent(
                                ClickEvent.Action.RUN_COMMAND,
                                "/tpf " + targetPlayer.getName().getString()
                        ))
                        .withHoverEvent(new HoverEvent(
                                HoverEvent.Action.SHOW_TEXT,
                                Component.literal("点击传送到 " + targetPlayer.getName().getString())
                        ))
                );
    }
}