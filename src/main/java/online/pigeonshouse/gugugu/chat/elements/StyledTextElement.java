package online.pigeonshouse.gugugu.chat.elements;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.*;
import net.minecraft.world.item.ItemStack;
import online.pigeonshouse.gugugu.chat.MessageElement;
import org.jetbrains.annotations.Nullable;

public class StyledTextElement implements MessageElement {
    private final String text;
    @Nullable
    private final TextColor color;
    private final boolean bold;
    private final boolean italic;
    private final boolean underlined;
    private final boolean strikethrough;
    @Nullable
    private final HoverEvent hoverEvent;
    @Nullable
    private final ClickEvent clickEvent;

    private StyledTextElement(Builder builder) {
        this.text = builder.text;
        this.color = builder.color;
        this.bold = builder.bold;
        this.italic = builder.italic;
        this.underlined = builder.underlined;
        this.strikethrough = builder.strikethrough;
        this.hoverEvent = builder.hoverEvent;
        this.clickEvent = builder.clickEvent;
    }

    public static Builder builder(String text) {
        return new Builder(text);
    }

    @Override
    public Component toComponent() {
        Style style = Style.EMPTY
                .withColor(color)
                .withBold(bold)
                .withItalic(italic)
                .withUnderlined(underlined)
                .withStrikethrough(strikethrough)
                .withHoverEvent(hoverEvent)
                .withClickEvent(clickEvent);

        return Component.literal(text).setStyle(style);
    }

    public static class Builder {
        private final String text;
        @Nullable
        private TextColor color;
        private boolean bold;
        private boolean italic;
        private boolean underlined;
        private boolean strikethrough;
        @Nullable
        private HoverEvent hoverEvent;
        @Nullable
        private ClickEvent clickEvent;

        public Builder(String text) {
            this.text = text;
        }

        // 颜色设置方法（支持多种格式）
        public Builder color(ChatFormatting formatting) {
            this.color = TextColor.fromLegacyFormat(formatting);
            return this;
        }

        public Builder color(int rgb) {
            this.color = TextColor.fromRgb(rgb);
            return this;
        }

        public Builder color(String hexColor) {
            this.color = TextColor.parseColor(hexColor).getOrThrow();
            return this;
        }

        public Builder bold(boolean bold) {
            this.bold = bold;
            return this;
        }

        public Builder italic(boolean italic) {
            this.italic = italic;
            return this;
        }

        public Builder underlined(boolean underlined) {
            this.underlined = underlined;
            return this;
        }

        public Builder strikethrough(boolean strikethrough) {
            this.strikethrough = strikethrough;
            return this;
        }

        public Builder hoverText(String text) {
            this.hoverEvent = new HoverEvent(
                    HoverEvent.Action.SHOW_TEXT,
                    Component.literal(text)
            );
            return this;
        }

        public Builder hoverItem(ItemStack stack) {
            this.hoverEvent = new HoverEvent(
                    HoverEvent.Action.SHOW_ITEM,
                    new HoverEvent.ItemStackInfo(stack)
            );
            return this;
        }

        public Builder clickOpenUrl(String url) {
            this.clickEvent = new ClickEvent(
                    ClickEvent.Action.OPEN_URL,
                    url
            );
            return this;
        }

        public Builder clickRunCommand(String command) {
            this.clickEvent = new ClickEvent(
                    ClickEvent.Action.RUN_COMMAND,
                    command
            );
            return this;
        }

        public Builder clickCopyToClipboard(String text) {
            this.clickEvent = new ClickEvent(
                    ClickEvent.Action.COPY_TO_CLIPBOARD,
                    text
            );
            return this;
        }

        public StyledTextElement build() {
            return new StyledTextElement(this);
        }
    }
}