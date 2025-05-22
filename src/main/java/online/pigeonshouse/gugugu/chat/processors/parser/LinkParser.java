package online.pigeonshouse.gugugu.chat.processors.parser;

import online.pigeonshouse.gugugu.chat.MessageContext;
import online.pigeonshouse.gugugu.chat.MessageElement;
import online.pigeonshouse.gugugu.chat.MessageProcessor;
import online.pigeonshouse.gugugu.chat.elements.LinkElement;
import online.pigeonshouse.gugugu.chat.elements.TextElement;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LinkParser implements MessageProcessor {
    private static final Pattern URL_PATTERN = Pattern.compile(
            "https?://(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,}(?:/[^\\s]*)?"
    );

    @Override
    public int getPriority() {
        return HIGH;
    }

    @Override
    public void process(MessageContext context) {
        List<MessageElement> newElements = new ArrayList<>();

        for (MessageElement element : context.getElements()) {
            if (element instanceof TextElement) {
                String text = element.toComponent().getString();
                Matcher matcher = URL_PATTERN.matcher(text);
                int lastEnd = 0;

                while (matcher.find()) {
                    if (lastEnd < matcher.start()) {
                        newElements.add(new TextElement(text.substring(lastEnd, matcher.start())));
                    }
                    newElements.add(new LinkElement(matcher.group()));
                    lastEnd = matcher.end();
                }

                if (lastEnd < text.length()) {
                    newElements.add(new TextElement(text.substring(lastEnd)));
                }
            } else {
                newElements.add(element);
            }
        }
        context.getElements().clear();
        context.getElements().addAll(newElements);
    }
}