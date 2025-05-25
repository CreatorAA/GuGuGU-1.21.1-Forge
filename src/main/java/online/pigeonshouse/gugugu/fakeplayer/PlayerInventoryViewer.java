package online.pigeonshouse.gugugu.fakeplayer;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import online.pigeonshouse.gugugu.event.EventCallback;
import online.pigeonshouse.gugugu.event.MinecraftServerEvents;
import online.pigeonshouse.gugugu.utils.MinecraftUtil;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

public class PlayerInventoryViewer extends SimpleContainer {
    private static final List<Integer> FORBIDDEN_SLOTS = List.of(0, 1, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17);

    private final ServerPlayer target, viewer;
    private final boolean allowModify;

    public PlayerInventoryViewer(ServerPlayer viewer, ServerPlayer target, boolean allowModify) {
        super(54);
        this.target = target;
        this.viewer = viewer;
        this.allowModify = allowModify;

        MinecraftServerEvents.SERVER_TICK.addCallback(eventCallback);
    }

    public static void openFor(ServerPlayer viewer, ServerPlayer target, boolean allowModify) {
        PlayerInventoryViewer inventoryViewer = new PlayerInventoryViewer(viewer, target, allowModify);
        SimpleMenuProvider provider = new SimpleMenuProvider((i, inventory, player) ->
                new InventoryMenu(MenuType.GENERIC_9x6, i, inventory, inventoryViewer, 6),
                target.getName());

        viewer.openMenu(provider);
        inventoryViewer.updateContainer(null);
    }    EventCallback<MinecraftServerEvents.ServerTickEvent> eventCallback = this::updateContainer;

    private static ItemStack buildGrayGlass() {
        return MinecraftUtil.setHoverName(Items.GRAY_STAINED_GLASS_PANE.getDefaultInstance(), Component.literal("填充物")
                .withStyle(Style.EMPTY.withColor(ChatFormatting.WHITE)));
    }

    private static ItemStack buildWhiteGlass() {
        return Items.WHITE_STAINED_GLASS_PANE.getDefaultInstance();
    }

    private static ItemStack buildYellowGlass() {
        return MinecraftUtil.setHoverName(Items.YELLOW_STAINED_GLASS_PANE.getDefaultInstance(), Component.literal("当前栏位")
                .withStyle(Style.EMPTY.withColor(ChatFormatting.GOLD)));
    }

    private static ItemStack buildCloseGlass() {
        return MinecraftUtil.setHoverName(Items.RED_STAINED_GLASS_PANE.getDefaultInstance(), Component.literal("关闭菜单")
                .withStyle(Style.EMPTY.withColor(ChatFormatting.RED)));
    }

    private static ItemStack[] buildMainHandGlass(int mainHand) {
        ItemStack[] itemStacks = new ItemStack[9];
        for (int i = 0; i < 9; i++) {
            if (i == mainHand) {
                itemStacks[i] = buildYellowGlass();
            } else {
                itemStacks[i] = MinecraftUtil.setHoverName(buildWhiteGlass(), Component.literal("点击切换至" + (i + 1) + "号栏位")
                        .withStyle(Style.EMPTY.withColor(ChatFormatting.GRAY)));
            }
        }
        return itemStacks;
    }

    public void updateContainer(MinecraftServerEvents.ServerTickEvent event) {
        if (!viewer.hasContainerOpen() || !viewer.isAlive()) {
            MinecraftServerEvents.SERVER_TICK.removeCallback(eventCallback);
        }

        ItemStack[] itemStacks = buildContainer();
        for (int i = 0; i < itemStacks.length; i++) {
            setContainerItem(i, itemStacks[i]);
        }
    }

    private void setContainerItem(int i, ItemStack itemStack) {
        super.setItem(i, itemStack);
    }

    @Override
    public void setItem(int i, ItemStack itemStack) {
        super.setItem(i, itemStack);
        if (i >= 18) {
            i = i - 18;
            target.getInventory().setItem(i, itemStack);
        } else {
            handleArmorAndOffHand(i, itemStack);
        }
    }

    private ItemStack[] buildContainer() {
        ItemStack[] oneLine = new ItemStack[]{
                buildInfoSign(), buildGrayGlass(), getPlayerOffHand(),
                target.getItemBySlot(EquipmentSlot.HEAD), target.getItemBySlot(EquipmentSlot.CHEST), target.getItemBySlot(EquipmentSlot.LEGS), target.getItemBySlot(EquipmentSlot.FEET),
                buildGrayGlass(), buildCloseGlass()
        };

        int selected = target.getInventory().selected;
        ItemStack[] twoLine = buildMainHandGlass(selected);

        ItemStack[] inventory = new ItemStack[36];
        for (int i = 0; i < 36; i++) {
            inventory[i] = target.getInventory().items.get(i);
        }

        ItemStack[] container = new ItemStack[54];
        System.arraycopy(oneLine, 0, container, 0, oneLine.length);
        System.arraycopy(twoLine, 0, container, oneLine.length, twoLine.length);
        System.arraycopy(inventory, 0, container, oneLine.length + twoLine.length, inventory.length);
        return container;
    }

    private ItemStack getPlayerOffHand() {
        return target.getOffhandItem();
    }

    private ItemStack buildInfoSign() {
        FoodData foodData = target.getFoodData();
        float experienceProgress = BigDecimal.valueOf(target.experienceProgress * 100)
                .setScale(2, RoundingMode.HALF_UP)
                .floatValue();

        ItemStack instance = Items.OAK_SIGN.getDefaultInstance();

        MinecraftUtil.addLore(instance, Component.literal("玩家名称: ")
                        .withStyle(Style.EMPTY.withColor(ChatFormatting.BLUE))
                        .append(Component.literal(target.getName().getString())
                                .withStyle(Style.EMPTY.withColor(ChatFormatting.GOLD))),
                Component.literal("玩家UID: ")
                        .withStyle(Style.EMPTY.withColor(ChatFormatting.BLUE))
                        .append(Component.literal(target.getUUID().toString())
                                .withStyle(Style.EMPTY.withColor(ChatFormatting.GOLD))),
                Component.literal("生命值: ")
                        .withStyle(Style.EMPTY.withColor(ChatFormatting.BLUE))
                        .append(Component.literal(String.valueOf(target.getHealth()))
                                .withStyle(Style.EMPTY.withColor(ChatFormatting.GREEN))
                                .append(Component.literal(" / ")
                                        .withStyle(Style.EMPTY.withColor(ChatFormatting.WHITE))
                                        .append(Component.literal(String.valueOf(target.getMaxHealth()))
                                                .withStyle(Style.EMPTY.withColor(ChatFormatting.GOLD))))),
                Component.literal("饥饿值: ")
                        .withStyle(Style.EMPTY.withColor(ChatFormatting.BLUE))
                        .append(Component.literal(String.valueOf(foodData.getFoodLevel()))
                                .withStyle(Style.EMPTY.withColor(ChatFormatting.GREEN))
                                .append(Component.literal(" / ")
                                        .withStyle(Style.EMPTY.withColor(ChatFormatting.WHITE))
                                        .append(Component.literal(String.valueOf(20))
                                                .withStyle(Style.EMPTY.withColor(ChatFormatting.GOLD))))),
                Component.literal("饱腹度: ")
                        .withStyle(Style.EMPTY.withColor(ChatFormatting.BLUE))
                        .append(Component.literal(String.valueOf(foodData.getSaturationLevel()))
                                .withStyle(Style.EMPTY.withColor(ChatFormatting.GREEN))),
                Component.literal("经验值: ")
                        .withStyle(Style.EMPTY.withColor(ChatFormatting.BLUE))
                        .append(Component.literal(String.valueOf(target.experienceLevel))
                                .withStyle(Style.EMPTY.withColor(ChatFormatting.GREEN))
                                .append(Component.literal(" (")
                                        .withStyle(Style.EMPTY.withColor(ChatFormatting.WHITE))
                                        .append(Component.literal(experienceProgress + "%")
                                                .withStyle(Style.EMPTY.withColor(ChatFormatting.GRAY)))
                                        .append(Component.literal(")"))
                                        .withStyle(Style.EMPTY.withColor(ChatFormatting.WHITE))))
        );


        return MinecraftUtil.setHoverName(instance, Component.literal("玩家信息")
                .withStyle(Style.EMPTY.withColor(ChatFormatting.GOLD)));
    }

    private void handleClick(int i) {
        switch (i) {
            case 8 -> viewer.closeContainer();
            case 9, 10, 11, 12, 13, 14, 15, 16, 17 -> {
                if (!allowModify) {
                    return;
                }

                i = i - 9;
                target.getInventory().selected = i;
            }
        }
    }

    private void handleArmorAndOffHand(int i, ItemStack itemStack) {
        switch (i) {
            case 2 -> target.setItemSlot(EquipmentSlot.OFFHAND, itemStack);
            case 3 -> target.setItemSlot(EquipmentSlot.HEAD, itemStack);
            case 4 -> target.setItemSlot(EquipmentSlot.CHEST, itemStack);
            case 5 -> target.setItemSlot(EquipmentSlot.LEGS, itemStack);
            case 6 -> target.setItemSlot(EquipmentSlot.FEET, itemStack);
        }
    }

    public static class InventoryMenu extends ChestMenu {
        private final PlayerInventoryViewer container;

        public InventoryMenu(MenuType<?> menuType, int i, Inventory inventory, PlayerInventoryViewer container, int j) {
            super(menuType, i, inventory, container, j);
            this.container = container;
        }

        @Override
        public void clicked(int i, int j, ClickType clickType, Player player) {
            if (FORBIDDEN_SLOTS.contains(i)) {
                container.handleClick(i);
                return;
            }

            if (!container.allowModify) {
                return;
            }

            super.clicked(i, j, clickType, player);
        }
    }




}
