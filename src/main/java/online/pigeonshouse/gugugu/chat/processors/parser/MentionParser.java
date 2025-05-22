package online.pigeonshouse.gugugu.chat.processors.parser;

import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerPlayer;
import online.pigeonshouse.gugugu.chat.MessageContext;
import online.pigeonshouse.gugugu.chat.MessageElement;
import online.pigeonshouse.gugugu.chat.MessageProcessor;
import online.pigeonshouse.gugugu.chat.elements.MentionElement;
import online.pigeonshouse.gugugu.chat.elements.StyledTextElement;
import online.pigeonshouse.gugugu.chat.elements.TextElement;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MentionParser implements MessageProcessor {
    private static final Pattern AT_PATTERN = Pattern.compile("@(?<name>[\\w\\d]+|all)");

    @Override
    public int getPriority() {
        return HIGH;
    }

    @Override
    public void process(MessageContext context) {
        List<MessageElement> newElements = new ArrayList<>();
        ServerPlayer player = context.getSender();

        for (MessageElement element : context.getElements()) {
            if (element instanceof TextElement) {
                processTextElement((TextElement) element, player, context, newElements);
            } else {
                newElements.add(element);
            }
        }

        context.getElements().clear();
        context.getElements().addAll(newElements);
    }

    private void processTextElement(TextElement element, ServerPlayer player, MessageContext context, List<MessageElement> output) {
        String text = element.toComponent().getString();
        Matcher matcher = AT_PATTERN.matcher(text);
        int lastEnd = 0;

        while (matcher.find()) {
            int start = matcher.start();
            if (start > lastEnd) {
                output.add(new TextElement(text.substring(lastEnd, start)));
            }

            String name = matcher.group("name");

            if ("all".equalsIgnoreCase(name)) {
                context.getMentionedPlayers().addAll(
                        player.getServer().getPlayerList().getPlayers()
                );
                output.add(StyledTextElement.builder("@all")
                        .color(ChatFormatting.GOLD)
                        .build());
            } else {
                ServerPlayer mentioned = player.getServer().getPlayerList().getPlayerByName(name);
                if (mentioned != null) {
                    output.add(new MentionElement("@" + name, mentioned));
                } else {
                    output.add(new TextElement("@" + name));
                }
            }
            lastEnd = matcher.end();
        }

        if (lastEnd < text.length()) {
            output.add(new TextElement(text.substring(lastEnd)));
        }
    }
}