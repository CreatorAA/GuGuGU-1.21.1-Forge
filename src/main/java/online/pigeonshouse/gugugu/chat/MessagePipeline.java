package online.pigeonshouse.gugugu.chat;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class MessagePipeline {
    private final List<MessageProcessor> processors = new CopyOnWriteArrayList<>();
    private final Map<MessageProcessor, MessageProcessorInfo> processorInfoMap = new ConcurrentHashMap<>();
    private final Map<String, MessageProcessorInfo> processorNameMap = new ConcurrentHashMap<>();
    private final List<MessageProcessor> finalProcessors = new CopyOnWriteArrayList<>();

    public synchronized MessagePipeline registerFinalProcessor(MessageProcessor processor, MessageProcessorInfo info) {
        registerProcessor(processor, info);
        finalProcessors.add(processor);
        return this;
    }

    public synchronized MessagePipeline registerProcessor(MessageProcessor processor, MessageProcessorInfo info) {
        if (processorNameMap.containsKey(info.getName())) {
            throw new IllegalArgumentException("Processor '" + info.getName() + "' already exists");
        }

        processorInfoMap.put(processor, info);
        processorNameMap.put(info.getName(), info);
        info.setInstance(processor);

        if (info.isEnabled()) {
            addAndSortProcessor(processor);
        }
        return this;
    }

    private void addAndSortProcessor(MessageProcessor processor) {
        processors.add(processor);
        processors.sort(Comparator.comparingInt(MessageProcessor::getPriority));
    }

    public Component processMessage(ServerPlayer sender, String message) {
        MessageContext context = new MessageContext(sender, message);
        for (MessageProcessor processor : processors) {
            processor.process(context);
        }
        return context.getResult();
    }

    public Component test(ServerPlayer sender, String processorName, String message) {
        MessageContext context = new MessageContext(sender, message);
        MessageProcessorInfo info = processorNameMap.get(processorName);

        if (info == null) {
            return errorComponent("Processor not found: " + processorName);
        }

        List<MessageProcessor> testProcessors = new ArrayList<>(finalProcessors);
        testProcessors.add(info.getInstance());

        for (String dependencyName : info.getInstance().testProcessors()) {
            MessageProcessorInfo dependency = processorNameMap.get(dependencyName);
            if (dependency != null) {
                testProcessors.add(dependency.getInstance());
            }
        }

        testProcessors.sort(Comparator.comparingInt(MessageProcessor::getPriority));
        testProcessors.forEach(processor -> processor.test(context));

        return context.getResult();
    }

    private Component errorComponent(String message) {
        return Component.literal(message)
                .withStyle(ChatFormatting.RED);
    }

    public MessageProcessorInfo[] getProcessorInfos() {
        return processorNameMap.values().toArray(new MessageProcessorInfo[0]);
    }
}