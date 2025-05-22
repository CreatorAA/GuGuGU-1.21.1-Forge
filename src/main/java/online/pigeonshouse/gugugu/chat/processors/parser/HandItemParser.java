package online.pigeonshouse.gugugu.chat.processors.parser;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import online.pigeonshouse.gugugu.chat.ItemSlot;
import online.pigeonshouse.gugugu.chat.MessageContext;
import online.pigeonshouse.gugugu.chat.MessageElement;
import online.pigeonshouse.gugugu.chat.MessageProcessor;
import online.pigeonshouse.gugugu.chat.elements.ItemInfoElement;
import online.pigeonshouse.gugugu.chat.elements.TextElement;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HandItemParser implements MessageProcessor {
    private static final Pattern ITEM_PATTERN = Pattern.compile("\\[(i[otjkx]?)]");

    @Override
    public void process(MessageContext context) {
        List<MessageElement> newElements = new ArrayList<>();
        ServerPlayer player = context.getSender();

        for (MessageElement element : context.getElements()) {
            if (element instanceof TextElement) {
                processTextElement((TextElement) element, player, newElements);
            } else {
                newElements.add(element);
            }
        }

        context.getElements().clear();
        context.getElements().addAll(newElements);
    }

    private void processTextElement(TextElement element, ServerPlayer player, List<MessageElement> output) {
        String text = element.toComponent().getString();
        Matcher matcher = ITEM_PATTERN.matcher(text);
        int lastEnd = 0;

        while (matcher.find()) {
            if (lastEnd < matcher.start()) {
                output.add(new TextElement(text.substring(lastEnd, matcher.start())));
            }

            String tag = matcher.group(1);

            ItemStack item = ItemSlot.fromTag(tag)
                    .getItemGetter()
                    .apply(player);

            output.add(new ItemInfoElement(item));
            lastEnd = matcher.end();
        }

        if (lastEnd < text.length()) {
            output.add(new TextElement(text.substring(lastEnd)));
        }
    }
}