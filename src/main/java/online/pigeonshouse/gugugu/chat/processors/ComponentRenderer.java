package online.pigeonshouse.gugugu.chat.processors;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import online.pigeonshouse.gugugu.chat.MessageContext;
import online.pigeonshouse.gugugu.chat.MessageElement;
import online.pigeonshouse.gugugu.chat.MessageProcessor;

public class ComponentRenderer implements MessageProcessor {
    @Override
    public int getPriority() {
        return LOW;
    }

    @Override
    public void process(MessageContext context) {
        MutableComponent component = Component.literal("");

        for (MessageElement element : context.getElements()) {
            component.append(element.toComponent());
        }

        context.setResult(component);
    }
}