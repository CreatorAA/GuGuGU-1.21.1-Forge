package online.pigeonshouse.gugugu.chat;

import lombok.Getter;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Getter
public enum ItemSlot {
    MAINHAND("i", p -> p.getMainHandItem()),
    OFFHAND("io", p -> p.getOffhandItem()),
    HELMET("it", p -> p.getItemBySlot(EquipmentSlot.HEAD)),
    CHESTPLATE("ij", p -> p.getItemBySlot(EquipmentSlot.CHEST)),
    LEGGINGS("ik", p -> p.getItemBySlot(EquipmentSlot.LEGS)),
    BOOTS("ix", p -> p.getItemBySlot(EquipmentSlot.FEET));

    private static final Map<String, ItemSlot> TAG_MAP = Arrays.stream(values())
            .collect(Collectors.toMap(e -> e.tag, e -> e));
    private final String tag;
    private final Function<Player, ItemStack> itemGetter;

    ItemSlot(String tag, Function<Player, ItemStack> itemGetter) {
        this.tag = tag;
        this.itemGetter = itemGetter;
    }

    public static ItemSlot fromTag(String tag) {
        return TAG_MAP.getOrDefault(tag, MAINHAND);
    }
}