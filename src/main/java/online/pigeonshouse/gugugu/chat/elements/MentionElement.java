package online.pigeonshouse.gugugu.chat.elements;

import lombok.Getter;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.level.ServerPlayer;
import online.pigeonshouse.gugugu.chat.MessageElement;

public class MentionElement implements MessageElement {
    @Getter
    private final ServerPlayer mentionedPlayer;
    private final String rawText;

    public MentionElement(String rawText, ServerPlayer mentionedPlayer) {
        this.rawText = rawText;
        this.mentionedPlayer = mentionedPlayer;
    }

    @Override
    public Component toComponent() {
        return Component.literal(rawText)
                .withStyle(ChatFormatting.GOLD)
                .withStyle(style -> style
                        .withHoverEvent(new HoverEvent(
                                HoverEvent.Action.SHOW_TEXT,
                                Component.literal("提及玩家: " + mentionedPlayer.getName().getString())
                        ))
                );
    }
}