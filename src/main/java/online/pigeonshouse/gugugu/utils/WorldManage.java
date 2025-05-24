package online.pigeonshouse.gugugu.utils;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.PalettedContainer;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * WorldManage 用于管理加载的 Minecraft 区域数据，
 * 提供跨区域、跨区块的查询工具。
 */
public class WorldManage {
    private static final PalettedContainer.Strategy STRATEGY = PalettedContainer.Strategy.SECTION_STATES;

    private static final PalettedContainer<BlockState> EMPTY_SECTION =
            new PalettedContainer<>(Block.BLOCK_STATE_REGISTRY, Blocks.AIR.defaultBlockState(), STRATEGY);

    private static final Codec<PalettedContainer<BlockState>> BLOCK_STATE_CODEC =
            PalettedContainer.codecRW(
                    Block.BLOCK_STATE_REGISTRY,
                    BlockState.CODEC,
                    STRATEGY,
                    Blocks.AIR.defaultBlockState()
            );

    private static final Pattern REGION_NAME_PATTERN = Pattern.compile("r\\.(?<x>-?\\d+)\\.(?<z>-?\\d+)\\.m(?:ca|cr)");

    private final Map<String, List<ReadRegionExecutorService.ScanResult>> regionData;
    @Getter
    private final ServerLevel level;

    /**
     * 构造函数：传入区域文件名到扫描结果列表的映射
     * @param regionData 键为区域文件名（如 r.0.0.mca），值为该区域所有区块的 ScanResult 列表
     */
    public WorldManage(ServerLevel level, Map<String, List<ReadRegionExecutorService.ScanResult>> regionData) {
        this.regionData = Objects.requireNonNull(regionData);
        this.level = level;
    }

    /**
     * 传入ChunkPos 计算此Chunk位于哪一个区块文件内，返回一个String表示改文件
     */
    public static String getRegionFileName(ChunkPos chunkPos) {
        return getRegionFileName(regionCoord(chunkPos.x), regionCoord(chunkPos.z));
    }

    /**
     * 根据区块坐标计算区域坐标（右移5位，相当于除以32）
     */
    public static int regionCoord(int chunkCoord) {
        return chunkCoord >> 5;
    }

    /**
     * 根据区域坐标格式化区域文件名
     * @param regionX 区域 X 坐标
     * @param regionZ 区域 Z 坐标
     * @return 格式如 "r.x.z.mca"
     */
    public static String getRegionFileName(int regionX, int regionZ) {
        return String.format("r.%d.%d.mca", regionX, regionZ);
    }

    /**
     * 解析区域文件名，获取区域坐标
     * @param name 区域文件名，如 r.2.-1.mca 或 r.2.-1.mcr
     * @return RegionCoords 记录 x, z
     * @throws IllegalArgumentException 文件名格式不合法时抛出
     */
    public static RegionCoords parseRegionName(String name) {
        Matcher m = REGION_NAME_PATTERN.matcher(name);
        if (!m.matches()) {
            throw new IllegalArgumentException("无效的区域文件名: " + name);
        }
        int x = Integer.parseInt(m.group("x"));
        int z = Integer.parseInt(m.group("z"));
        return new RegionCoords(x, z);
    }

    /**
     * 获取指定区块的原始 NBT 标签（如果已加载）
     * @param pos 区块位置
     * @return 包含 NBT 的 Optional，未加载时为空
     */
    public Optional<CompoundTag> getChunkTag(ChunkPos pos) {
        int rx = regionCoord(pos.x);
        int rz = regionCoord(pos.z);
        String fname = getRegionFileName(rx, rz);
        List<ReadRegionExecutorService.ScanResult> scans = regionData.get(fname);
        if (scans == null) return Optional.empty();
        return scans.stream()
                .filter(r -> r.pos().equals(pos))
                .map(ReadRegionExecutorService.ScanResult::tag)
                .findFirst();
    }

    /**
     * 列出所有已加载的区块位置
     */
    public List<ChunkPos> getLoadedChunks() {
        return regionData.values().stream()
                .flatMap(List::stream)
                .map(ReadRegionExecutorService.ScanResult::pos)
                .toList();
    }

    /**
     * 列出所有已加载的区域文件名
     */
    public Set<String> getLoadedRegionNames() {
        return Collections.unmodifiableSet(regionData.keySet());
    }

    /**
     * 根据全局世界坐标查询方块状态
     * @param x 世界坐标 X
     * @param y 世界坐标 Y
     * @param z 世界坐标 Z
     */
    public BlockState getBlockAt(int x, int y, int z) {
        BlockPos blockPos = new BlockPos(x, y, z);
        ChunkPos cp = new ChunkPos(blockPos);
        Optional<CompoundTag> oc = getChunkTag(cp);
        if (oc.isEmpty()) {
            throw new RuntimeException("Chunk data not found: " + cp);
        }
        return getBlockAt(oc.get(), x, y, z);
    }

    /**
     * 在指定区块的 NBT 中，根据世界坐标获取方块状态
     */
    public BlockState getBlockAt(CompoundTag chunkTag, int x, int y, int z) {
        int sectionY = SectionPos.blockToSectionCoord(y);
        ListTag sections = chunkTag.getList("sections", 10);
        for (int i = 0; i < sections.size(); i++) {
            CompoundTag section = sections.getCompound(i);
            if (section.getByte("Y") == (byte) sectionY) {
                PalettedContainer<BlockState> container;
                if (section.contains("block_states", 10)) {
                    DataResult<PalettedContainer<BlockState>> dr =
                            BLOCK_STATE_CODEC.parse(NbtOps.INSTANCE, section.getCompound("block_states"));
                    container = dr.result().orElse(EMPTY_SECTION);
                } else {
                    container = EMPTY_SECTION;
                }
                int lx = SectionPos.sectionRelative(x);
                int ly = SectionPos.sectionRelative(y);
                int lz = SectionPos.sectionRelative(z);
                return container.get(lx, ly, lz);
            }
        }
        return Blocks.AIR.defaultBlockState();
    }

    /**
     * 返回与 (x,y,z) 直接相邻的六个方块状态
     */
    public Map<Direction, BlockState> getAdjacentBlockStates(int x, int y, int z) {
        Map<Direction, BlockState> map = new EnumMap<>(Direction.class);
        for (Direction dir : Direction.values()) {
            BlockPos np = new BlockPos(x, y, z).relative(dir);
            map.put(dir, getBlockAt(np.getX(), np.getY(), np.getZ()));
        }
        return map;
    }


    /**
     * 获取指定区块某一节的原始 NBT 标签（如果存在）
     */
    public Optional<CompoundTag> getSectionTag(ChunkPos pos, int sectionY) {
        return getChunkTag(pos).flatMap(chunkTag -> {
            ListTag secs = chunkTag.getList("sections", 10);
            for (int i = 0; i < secs.size(); i++) {
                CompoundTag sec = secs.getCompound(i);
                if (sec.getByte("Y") == (byte) sectionY) return Optional.of(sec);
            }
            return Optional.empty();
        });
    }

    /**
     * 区域坐标记录，包含 x, z
     */
    public record RegionCoords(int x, int z) {}
}
