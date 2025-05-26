package online.pigeonshouse.gugugu.utils;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.PalettedContainerRO;
import net.minecraft.world.level.chunk.storage.ChunkSerializer;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * WorldManage 用于管理加载的 Minecraft 区域数据，
 * 提供跨区域、跨区块的查询工具。
 */
public class WorldManage {
    public static final BlockState AIR = Blocks.AIR.defaultBlockState();
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
    @Getter
    private final ServerLevel level;
    private Map<ChunkPos, Map<Integer, LevelChunkSection>> sections = new HashMap<>();

    /**
     * 构造函数：传入区域文件名到扫描结果列表的映射
     *
     * @param regionData 键为区域文件名（如 r.0.0.mca），值为该区域所有区块的 ScanResult 列表
     */
    public WorldManage(ServerLevel level, Map<String, List<ReadRegionExecutorService.ScanResult>> regionData) {
        this.level = level;
        init(regionData.values());
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
     *
     * @param regionX 区域 X 坐标
     * @param regionZ 区域 Z 坐标
     * @return 格式如 "r.x.z.mca"
     */
    public static String getRegionFileName(int regionX, int regionZ) {
        return String.format("r.%d.%d.mca", regionX, regionZ);
    }

    /**
     * 解析区域文件名，获取区域坐标
     *
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

    public static Codec<PalettedContainerRO<Holder<Biome>>> makeBiomeCodec(Registry<Biome> biomeRegistry) {
        return PalettedContainer.codecRO(
                biomeRegistry.asHolderIdMap(), biomeRegistry.holderByNameCodec(),
                PalettedContainer.Strategy.SECTION_BIOMES,
                biomeRegistry.getHolderOrThrow(Biomes.PLAINS)
        );
    }

    public static Map<Integer, LevelChunkSection> createAllLevelChunkSection(ServerLevel level, CompoundTag chunkTag) {
        Registry<Biome> registry = level.registryAccess()
                .registryOrThrow(Registries.BIOME);
        Codec<PalettedContainerRO<Holder<Biome>>> codec = makeBiomeCodec(registry);
        Map<Integer, LevelChunkSection> chunkSections = new HashMap<>();

        ListTag sections = chunkTag.getList("sections", 10);

        for (int i = 0; i < sections.size(); i++) {
            CompoundTag section = sections.getCompound(i);

            PalettedContainerRO<Holder<Biome>> palettedcontainerro;
            PalettedContainer<BlockState> container;

            if (section.contains("block_states", 10)) {
                DataResult<PalettedContainer<BlockState>> dr =
                        BLOCK_STATE_CODEC.parse(NbtOps.INSTANCE, section.getCompound("block_states"));
                container = dr.result()
                        .orElse(EMPTY_SECTION);
            } else {
                container = EMPTY_SECTION;
            }

            if (chunkTag.contains("biomes", 10)) {
                palettedcontainerro = codec.parse(NbtOps.INSTANCE, chunkTag.getCompound("biomes"))
                        .getOrThrow(ChunkSerializer.ChunkReadException::new);
            } else {
                palettedcontainerro = new PalettedContainer<>(
                        registry.asHolderIdMap(), registry.getHolderOrThrow(Biomes.PLAINS),
                        PalettedContainer.Strategy.SECTION_BIOMES
                );
            }

            chunkSections.put(section.getByte("Y") + 4, new LevelChunkSection(container, palettedcontainerro));
        }

        return chunkSections;
    }

    private void init(Collection<List<ReadRegionExecutorService.ScanResult>> scanResults) {
        Map<ChunkPos, Map<Integer, LevelChunkSection>> sections = new HashMap<>();
        for (List<ReadRegionExecutorService.ScanResult> scanResult : scanResults) {
            for (ReadRegionExecutorService.ScanResult result : scanResult) {
                CompoundTag tag = result.tag();
                ChunkPos chunkPos = result.pos();
                Map<Integer, LevelChunkSection> chunkSection = createAllLevelChunkSection(level, tag);
                sections.put(chunkPos, chunkSection);
            }
        }

        this.sections = sections;
    }

    /**
     * 根据全局世界坐标查询方块状态
     *
     * @param x 世界坐标 X
     * @param y 世界坐标 Y
     * @param z 世界坐标 Z
     */
    public BlockState getBlockAt(int x, int y, int z) {
        BlockPos blockPos = new BlockPos(x, y, z);
        ChunkPos cp = new ChunkPos(blockPos);
        Map<Integer, LevelChunkSection> chunkSection = sections.get(cp);
        if (chunkSection == null) {
            throw new RuntimeException("BlockState not found at " + x + ", " + y + ", " + z);
        }

        int sectionIndex = level.getSectionIndex(y);
        LevelChunkSection section = chunkSection.get(sectionIndex);

        if (section == null) {
            throw new RuntimeException("BlockState not found at " + x + ", " + y + ", " + z);
        }

        return section.getBlockState(blockPos.getX() & 15, blockPos.getY() & 15, blockPos.getZ() & 15);
    }

    public Map<Integer, LevelChunkSection> getChunkSection(ChunkPos pos) {
        return sections.get(pos);
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
     * 区域坐标记录，包含 x, z
     */
    public record RegionCoords(int x, int z) {
    }
}
