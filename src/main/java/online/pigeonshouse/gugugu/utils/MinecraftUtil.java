package online.pigeonshouse.gugugu.utils;

import lombok.Getter;
import lombok.Setter;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.CollisionContext;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class MinecraftUtil {
    @Getter
    private static MinecraftServer server;

    private static Map<String, ServerLevel> levels;

    public static void init(MinecraftServer server) {
        MinecraftUtil.server = server;
        if (levels != null && !levels.isEmpty()) levels.clear();

        levels = new ConcurrentHashMap<>();
        for (ServerLevel level : server.getAllLevels()) {
            levels.put(getLevelName(level), level);
        }
    }

    public static String getLevelName(Level level) {
        return level.dimension().location().toString();
    }

    public static ServerLevel getServerLevelByName(String levelName) {
        return levels.get(levelName);
    }

    /**
     * 获取Display tag，不存在则创建
     */
    public static ItemStack setHoverName(ItemStack itemStack, Component name) {
        itemStack.set(DataComponents.ITEM_NAME, name);
        return itemStack;
    }

    public static ItemStack setHoverName(ItemStack itemStack, String name) {
        return setHoverName(itemStack, Component.literal(name));
    }

    public static ServerPlayer getPlayerForName(String name) {
        return server.getPlayerList().getPlayerByName(name);
    }

    /**
     * 添加一条Lore
     *
     * @param itemStack 物品
     * @param component lore
     * @return lore ListTag
     */
    public static ItemLore addLore(ItemStack itemStack, Component component) {
        ItemLore itemLore = itemStack.get(DataComponents.LORE);

        if (itemLore == null) {
            itemLore = new ItemLore(List.of());
            return itemStack.set(DataComponents.LORE, itemLore);
        }

        return itemStack.set(DataComponents.LORE, itemLore.withLineAdded(component));
    }

    public static ItemLore addLore(ItemStack itemStack, Component... components) {
        ItemLore itemLore = itemStack.get(DataComponents.LORE);

        if (itemLore == null) {
            itemLore = new ItemLore(List.of(components));
            return itemStack.set(DataComponents.LORE, itemLore);
        }

        for (Component component : components) {
            itemLore = itemLore.withLineAdded(component);
        }

        return itemStack.set(DataComponents.LORE, itemLore);
    }

    public static HitResult rayTrace(Entity entity, double d, boolean bl) {
        Vec3 vec3 = entity.getEyePosition();
        Vec3 vec32 = entity.getViewVector(0);
        Vec3 vec33 = vec3.add(vec32.x * d, vec32.y * d, vec32.z * d);

        HitResult result = entity.level().clip(new ClipContext(vec3, vec33,
                ClipContext.Block.OUTLINE, bl ? ClipContext.Fluid.ANY : ClipContext.Fluid.NONE,
                entity));

        double maxSqDist = d * d;
        if (result.getType() == HitResult.Type.MISS) {
            maxSqDist = result.getLocation().distanceToSqr(entity.getEyePosition());
        }

        EntityHitResult hitResult =
                getEntityHitResult(entity.level(), entity, vec3, vec33,
                        new AABB(vec3, vec33).inflate(maxSqDist),
                        e -> !e.isSpectator() && e.isPickable());

        return hitResult != null ? hitResult : result;
    }

    /**
     * 根据给定的参数计算并返回一个碰撞结果。
     *
     * @param level 世界
     * @param x     起始点的X坐标
     * @param y     起始点的Y坐标
     * @param z     起始点的Z坐标
     * @param yaw   水平方向的旋转角度（以度为单位）
     * @param pitch 垂直方向的旋转角度（以度为单位）
     * @param d     射线的长度
     * @param bl    是否考虑流体（true 表示考虑，false 表示不考虑）
     * @return 碰撞结果对象
     */
    public static HitResult rayTrace(Level level, double x, double y, double z, float yaw, float pitch, double d, boolean bl) {
        Vec3 start = new Vec3(x, y, z);
        Vec3 direction = Vec3.directionFromRotation(pitch, yaw).scale(d);
        Vec3 end = start.add(direction);

        ClipContext context = new ClipContext(start, end,
                ClipContext.Block.OUTLINE,
                bl ? ClipContext.Fluid.ANY : ClipContext.Fluid.NONE,
                CollisionContext.empty());

        BlockHitResult clip = level.clip(context);

        double maxSqDist = d * d;
        if (clip.getType() == HitResult.Type.MISS) {
            maxSqDist = clip.getLocation().distanceToSqr(start);
        }

        EntityHitResult hitResult =
                getEntityHitResult(level, null, start, end, new AABB(start, end).inflate(maxSqDist), entity -> !entity.isSpectator() && entity.isPickable());

        return hitResult != null ? hitResult : clip;
    }

    public static EntityHitResult getEntityHitResult(Level level, Entity entity, Vec3 vec3, Vec3 vec32, AABB aABB, Predicate<Entity> predicate) {
        double d = Double.MAX_VALUE;
        Entity entity2 = null;

        for (Entity entity3 : level.getEntities(entity, aABB, predicate)) {
            AABB aABB2 = entity3.getBoundingBox().inflate(entity3.getPickRadius());
            Optional<Vec3> optional = aABB2.clip(vec3, vec32);
            if (optional.isPresent()) {
                double e = vec3.distanceToSqr(optional.get());
                if (e < d) {
                    entity2 = entity3;
                    d = e;
                }
            }
        }

        return entity2 == null ? null : new EntityHitResult(entity2);
    }

    /**
     * Iterable转Stream
     */
    public static <T> Stream<T> iterableToStream(Iterable<T> iterable) {
        return StreamSupport.stream(iterable.spliterator(), false);
    }

    /**
     * Iterable转List
     */
    public static <T> List<T> iterableToList(Iterable<T> iterable) {
        return StreamSupport.stream(iterable.spliterator(), false).collect(Collectors.toList());
    }
}
