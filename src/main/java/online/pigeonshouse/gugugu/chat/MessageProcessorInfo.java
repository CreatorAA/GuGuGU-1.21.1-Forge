package online.pigeonshouse.gugugu.chat;


import lombok.Data;
import lombok.Getter;
import net.minecraft.network.chat.Component;
import online.pigeonshouse.gugugu.GuGuGu;
import online.pigeonshouse.gugugu.config.ModConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class MessageProcessorInfo {
    @Getter
    final String name;
    @Getter
    final Component description;
    @Getter
    private final List<Example> examples = new ArrayList<>();
    @Getter
    protected MessageProcessor instance;

    private MessageProcessorInfo(String name, Component description) {
        Objects.requireNonNull(name, "name cannot be null");
        Objects.requireNonNull(description, "description cannot be null");

        if (name.isBlank())
            throw new IllegalArgumentException("name cannot be blank");

        this.name = name;
        this.description = description;
    }

    public static MessageProcessorInfo of(String name, String description) {
        return new MessageProcessorInfo(name, Component.literal(description));
    }

    public static MessageProcessorInfo of(String name, Component description) {
        return new MessageProcessorInfo(name, description);
    }

    public static MessageProcessorInfo of(String name) {
        return new MessageProcessorInfo(name, Component.literal(""));
    }

    protected void setInstance(MessageProcessor instance) {
        this.instance = instance;
    }

    public boolean isEnabled() {
        if (instance == null) return false;
        ModConfig config = GuGuGu.INSTANCE.getConfig();

        if (config.get("disabledMessageHandlers") instanceof List) {
            List<String> disabled = (List<String>) config.get("disabledMessageHandlers");
            return !disabled.contains(name);
        }

        return true;
    }

    public MessageProcessorInfo addExample(Example example) {
        examples.add(example);
        return this;
    }

    @Data
    public static class Example {
        private final String example;
        private final Component description;

        public Example(String example, Component description) {
            this.example = example;
            this.description = description;
        }

        public Example(String example, String description) {
            this(example, Component.literal(description));
        }

        public Example(String example) {
            this(example, Component.literal(""));
        }
    }
}
