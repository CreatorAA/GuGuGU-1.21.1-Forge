package online.pigeonshouse.gugugu.chat.elements;

import lombok.Getter;
import lombok.Setter;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import online.pigeonshouse.gugugu.chat.MessageElement;

import java.util.ArrayList;
import java.util.List;

public class TextElement implements MessageElement {
    private final String text;
    @Getter
    @Setter
    private List<ChatFormatting> formatting = new ArrayList<>();

    public TextElement(String text) {
        this.text = text;
    }

    @Override
    public Component toComponent() {
        return Component.literal(text);
    }
}
