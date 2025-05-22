package online.pigeonshouse.gugugu.utils;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.storage.ChunkScanAccess;
import net.minecraft.world.level.chunk.storage.IOWorker;
import net.minecraft.world.level.chunk.storage.RegionFileStorage;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class MapUtil {
    public static Optional<ChunkAccess> getChunk(Level level, int x, int z) {
        ChunkAccess chunk = level.getChunk(x, z, ChunkStatus.FULL, false);
        return Optional.ofNullable(chunk);
    }

    public static RegionFileStorage getStorage(IOWorker worker) {
        return storageGetter(worker).getStorage();
    }

    public static Path getRegionFileStorageFolder(IOWorker worker) {
        return getRegionFileStorageFolder(getStorage(worker));
    }

    public static Path getRegionFileStorageFolder(RegionFileStorage storage) {
        return pathGetter(storage).getPath();
    }

    public static RegionStorageInfo getRegionStorageInfo(RegionFileStorage storage) {
        return regionStorageInfoGetter(storage).getRegionStorageInfo();
    }

    public static StorageGetter storageGetter(Object object) {
        return (StorageGetter) object;
    }

    public static PathGetter pathGetter(Object object) {
        return (PathGetter) object;
    }

    public static RegionStorageInfoGetter regionStorageInfoGetter(Object object) {
        return (RegionStorageInfoGetter) object;
    }

    private static final Pattern REGION_FILE_PATTERN = Pattern.compile("r\\.(-?\\d+)\\.(-?\\d+)\\.mca");

    public static Map<String, List<ChunkPos>> parseRegionFiles(Path regionDir) throws IOException {
        Map<String, List<ChunkPos>> result = new ConcurrentHashMap<>();
        ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

        try (Stream<Path> files = Files.list(regionDir)) {
            List<Future<?>> futures = files
                    .filter(path -> REGION_FILE_PATTERN.matcher(path.getFileName().toString()).matches())
                    .map(path -> executor.submit(() -> {
                        List<ChunkPos> chunkPositions = parseRegionFile(path);
                        result.put(path.getFileName().toString(), chunkPositions);
                    }))
                    .collect(Collectors.toList());

            for (Future<?> future : futures) {
                try {
                    future.get();
                } catch (InterruptedException | ExecutionException e) {
                    // Handle exceptions appropriately
                    e.printStackTrace();
                }
            }
        } finally {
            executor.shutdown();
        }

        return result;
    }

    public static List<ChunkPos> parseRegionFile(Path mcFile) {
        String fileName = mcFile.getFileName().toString();
        Matcher matcher = REGION_FILE_PATTERN.matcher(fileName);
        if (matcher.matches()) {
            int regionX = Integer.parseInt(matcher.group(1));
            int regionZ = Integer.parseInt(matcher.group(2));
            List<ChunkPos> chunkPositions = new ArrayList<>();
            for (int x = 0; x < 32; x++) {
                for (int z = 0; z < 32; z++) {
                    int chunkX = (regionX << 5) + x;
                    int chunkZ = (regionZ << 5) + z;
                    chunkPositions.add(new ChunkPos(chunkX, chunkZ));
                }
            }
            return chunkPositions;
        }

        return null;
    }

    public static RegionFileStorage createRegionFileStorage(RegionFileStorage parent) {
        return new RegionFileStorage(getRegionStorageInfo(parent), getRegionFileStorageFolder(parent), false);
    }

    public static List<RegionFileStorage> createRegionFileStorages(RegionFileStorage parent, int numberOfStorages) {
        return IntStream.range(0, numberOfStorages)
                .mapToObj(i -> createRegionFileStorage(parent))
                .collect(Collectors.toList());
    }

    public static Path getWorldPath(ChunkScanAccess scanAccess) {
        IOWorker worker = (IOWorker) scanAccess;
        return getRegionFileStorageFolder(worker).toAbsolutePath().getParent();
    }

    public static Path getWorldPath(ServerLevel level) {
        return getWorldPath(level.getChunkSource().chunkScanner());
    }
}
