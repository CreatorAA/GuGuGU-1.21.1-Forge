package online.pigeonshouse.gugugu.utils;

import lombok.Getter;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.IOWorker;
import net.minecraft.world.level.chunk.storage.RegionFileStorage;
import online.pigeonshouse.gugugu.utils.thread.GenericThreadPool;
import online.pigeonshouse.gugugu.utils.thread.Worker;
import online.pigeonshouse.gugugu.utils.thread.WorkerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

public class ReadRegionExecutorService implements WorkerFactory<ChunkPos, ReadRegionExecutorService.ScanResult>, AutoCloseable {
    private final AtomicInteger storageIndex = new AtomicInteger(0);
    private final GenericThreadPool<ChunkPos, ScanResult> pool;
    private final List<RegionFileStorage> storages;
    private final Path folder;

    public ReadRegionExecutorService(IOWorker worker) {
        this(MapUtil.getStorage(worker));
    }

    public ReadRegionExecutorService(IOWorker worker, int threadCount) {
        this(MapUtil.getStorage(worker), threadCount);
    }

    public ReadRegionExecutorService(RegionFileStorage parent) {
        this(parent, Runtime.getRuntime().availableProcessors());
    }

    public ReadRegionExecutorService(RegionFileStorage parent, int threadCount) {
        this.folder = MapUtil.getRegionFileStorageFolder(parent);
        storages = MapUtil.createRegionFileStorages(parent, threadCount);

        if (storages.isEmpty()) {
            throw new IllegalStateException("没有可用的 RegionFileStorage，无法启动 ReadRegionExecutorService");
        }

        this.pool = new GenericThreadPool.Builder<>(this)
                .threadCount(threadCount)
                .build();
    }


    public Map<String, List<ScanResult>> scanRegion(Map<String, List<ChunkPos>> map) throws IOException {
        Map<String, List<Future<ScanResult>>> result = new HashMap<>();
        for (Map.Entry<String, List<ChunkPos>> listEntry : map.entrySet()) {
            List<ChunkPos> value = listEntry.getValue();
            List<Future<ScanResult>> futures = pool.submitBatch(value);

            result.put(listEntry.getKey(), futures);
        }

        Map<String, List<ScanResult>> finalResult = new HashMap<>();
        for (Map.Entry<String, List<Future<ScanResult>>> entry : result.entrySet()) {
            List<Future<ScanResult>> value = entry.getValue();
            List<ScanResult> results = value.stream()
                    .map(future -> {
                        try {
                            return future.get();
                        } catch (ExecutionException | InterruptedException e) {
                            return null;
                        }
                    })
                    .filter(scanResult -> scanResult != null && scanResult != ScanResult.EMPTY)
                    .toList();

            finalResult.put(entry.getKey(), results);

            value.clear();
        }

        result.clear();
        return finalResult;
    }

    @Override
    public Worker<ChunkPos, ScanResult> createWorker() {
        int size = storages.size();
        if (size == 0) {
            throw new IllegalStateException("没有可用的 RegionFileStorage，无法创建 Worker");
        }
        int idx = storageIndex.getAndIncrement() % size;
        return new ChunkWorker(idx);
    }


    @Override
    public void close() throws Exception {
        pool.shutdown();

        for (RegionFileStorage storage : storages) {
            storage.close();
        }

        storages.clear();
    }

    /**
     * 扫描结果封装：已加载区块或未加载区块的 NBT 数据
     */
    public record ScanResult(ChunkPos pos, CompoundTag tag) {
        public static final ScanResult EMPTY = new ScanResult(null, null);

        public static ScanResult loadedFullChunk(ChunkPos pos, CompoundTag tag) {
            if (tag != null && "minecraft:full".equals(tag.getString("Status"))) {
                return new ScanResult(pos, tag);
            }
            return EMPTY;
        }

        public CompoundTag getChunkTag() {
            return tag;
        }
    }

    private class ChunkWorker implements Worker<ChunkPos, ScanResult> {
        @Getter
        private final int index;
        private final RegionFileStorage storage;

        public ChunkWorker(int index) {
            this.index = index;
            this.storage = storages.get(index);
        }

        @Override
        public ScanResult process(ChunkPos task) throws Exception {
            CompoundTag tag = storage.read(task);
            return ScanResult.loadedFullChunk(task, tag);
        }
    }
}
