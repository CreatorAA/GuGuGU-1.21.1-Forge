package online.pigeonshouse.gugugu.chat;

import lombok.Getter;
import lombok.Setter;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import online.pigeonshouse.gugugu.chat.elements.MentionElement;
import online.pigeonshouse.gugugu.chat.elements.TextElement;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Getter
public class MessageContext {
    private final ServerPlayer sender;
    private final String originalMessage;
    private final List<MessageElement> elements = new ArrayList<>();
    private final Set<ServerPlayer> mentionedPlayers = new HashSet<>();
    @Setter
    private Component result;

    public MessageContext(ServerPlayer sender, String originalMessage) {
        this.sender = sender;
        this.originalMessage = originalMessage;
        elements.add(new TextElement(originalMessage));
    }

    public void addElement(MessageElement element) {
        elements.add(element);
        if (element instanceof MentionElement) {
            mentionedPlayers.add(((MentionElement) element).getMentionedPlayer());
        }
    }

}