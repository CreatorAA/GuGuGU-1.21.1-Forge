package online.pigeonshouse.gugugu.chat.elements;

import lombok.Getter;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import online.pigeonshouse.gugugu.chat.MessageElement;

public class ItemInfoElement implements MessageElement {
    @Getter
    private final ItemStack stack;

    public ItemInfoElement(ItemStack stack) {
        this.stack = stack;
    }

    @Override
    public Component toComponent() {
        if (stack.is(Items.AIR)) {
            return Component.literal("[AIR]").withStyle(ChatFormatting.AQUA);
        }

        return stack.getDisplayName()
                .copy().withStyle(style ->
                        style.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_ITEM,
                                new HoverEvent.ItemStackInfo(stack))));
    }
}
