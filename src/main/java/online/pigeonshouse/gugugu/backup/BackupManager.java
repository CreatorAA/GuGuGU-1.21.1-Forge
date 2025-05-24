package online.pigeonshouse.gugugu.backup;

import com.google.gson.reflect.TypeToken;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.storage.ChunkScanAccess;
import net.minecraft.world.level.chunk.storage.IOWorker;
import net.minecraft.world.level.chunk.storage.RegionFileStorage;
import online.pigeonshouse.gugugu.GuGuGu;
import online.pigeonshouse.gugugu.event.MinecraftServerEvents;
import online.pigeonshouse.gugugu.utils.*;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public class BackupManager {
    public static final Path BACKUP_ROOT = Path.of("GBackups");
    public static final String INCREMENTAL = "inc";
    public static final String FULL = "full";
    public static final String OVERWORLD_SAFE_NAME = FileUtil.safeFileName("minecraft:overworld");
    private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("yyyy_MM_dd-HH_mm_ss")
            .withZone(ZoneOffset.UTC);
    ;

    private final Map<String, String> levelDirMap = new ConcurrentHashMap<>();
    private final Map<String, String> regionHashMap = new ConcurrentHashMap<>();

    private MinecraftServer server;
    @Getter
    private BackupConfig config;
    private Path incRoot;
    private Path fullRoot;
    private Path worldRoot;
    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> autoBackupFuture;

    public BackupManager() {
        GuGuGu.INSTANCE.runIfConfigTrue("enableBackup", this::step);
    }

    public void step() {
        MinecraftServerEvents.SERVER_STARTED.addCallback(this::startup);
        MinecraftServerEvents.SERVER_STOPPED.addCallback(this::shutdown);
        MinecraftServerEvents.COMMAND_REGISTER.addCallback(BackupCommands::register);
    }

    public Path getCofnigPath() {
        return worldRoot.resolve("gbackup.json");
    }

    protected void startup(MinecraftServerEvents.ServerStartedEvent evt) {
        this.server = evt.getServer();
        try {
            initDirectoryLayout();
            loadConfigAndHashes();
            firstBackupIfNeeded();

            if (config.isEnableAutoBackup()) {
                scheduleAutoBackup();
            }
        } catch (Exception ex) {
            throw new RuntimeException("备份系统初始化失败", ex);
        }
    }

    private void shutdown(MinecraftServerEvents.ServerStoppedEvent evt) {
        this.server = null;
        if (autoBackupFuture != null) autoBackupFuture.cancel(false);
        if (scheduler != null) scheduler.shutdownNow();
    }


    private void initDirectoryLayout() throws IOException {
        worldRoot = BACKUP_ROOT.resolve(MapUtil.getSaveName());

        incRoot = worldRoot.resolve(INCREMENTAL);
        fullRoot = worldRoot.resolve(FULL);

        FileUtil.createDirectory(incRoot);
        FileUtil.createDirectory(fullRoot);

        log.info("GBackup: Initializing backup directory layout: {}", worldRoot);
        for (ServerLevel level : server.getAllLevels()) {
            String levelName = MinecraftUtil.getLevelName(level);
            String dirName = FileUtil.safeFileName(levelName);
            levelDirMap.put(levelName, dirName);
            FileUtil.createDirectory(incRoot.resolve(dirName));
        }
    }

    private void loadConfigAndHashes() throws IOException {
        this.config = BackupConfig.loadOrCreate(getCofnigPath());

        Path regionsFile = incRoot.resolve("regions.json");
        FileUtil.createFile(regionsFile);
        Type mapType = new TypeToken<Map<String, String>>() {
        }.getType();
        try (Reader r = Files.newBufferedReader(regionsFile)) {
            Map<String, String> m = BackupConfig.GSON.fromJson(r, mapType);
            if (m != null) regionHashMap.putAll(m);
        }
    }

    private void saveHashes() {
        Path f = incRoot.resolve("regions.json");
        try (Writer w = Files.newBufferedWriter(f)) {
            BackupConfig.GSON.toJson(regionHashMap, w);
        } catch (IOException e) {
            log.error("写入 regions.json 失败", e);
        }
    }

    private void firstBackupIfNeeded() throws IOException {
        try (var s = Files.list(incRoot.resolve(OVERWORLD_SAFE_NAME))) {
            if (s.findAny().isEmpty()) {
                server.saveAllChunks(true, true, false);
                backupIncremental("Incremental backup file not found, performing first backup");
            }
        }
    }

    /**
     * 执行全量备份（可手动或计划任务调用）
     *
     * @param reason 日志标识
     */
    public CompletableFuture<Path> backupFull(String reason) {
        String ts = TS_FMT.format(Instant.now());
        Path target = fullRoot.resolve(ts);
        log.info("Full backup: {}, at {}", reason, target);
        return CompletableFuture.supplyAsync(() -> {
            Path target1 = createFullBackup(target);
            log.info("Full backup: {}, at {}", reason, target1);
            return target1;
        });
    }

    @SneakyThrows
    private Path createFullBackup(Path target) {
        String format = TS_FMT.format(Instant.now());
        Path backupFile = fullRoot.resolve(format);
        if (config.isCompressFull()) {
            backupFile = backupFile.resolve(format + ".zip");
            FileUtil.compressDirectoryParallel(MapUtil.getSavePath(), backupFile, List.of("session.lock"),
                    Runtime.getRuntime().availableProcessors() , 1024 * 1024 * 16);
        } else {
            FileUtil.copyDirectoryAtomic(MapUtil.getSavePath(), backupFile);
        }

        purgeOld(config.getKeepFull());
        return target;
    }

    /**
     * 执行增量备份 —— 仅复制新/发生变化的 region 文件
     */
    public void backupIncremental(String reason) throws IOException {
        if (!server.isSameThread()) {
            server.executeIfPossible(() -> {
                try {
                    backupIncremental(reason);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
            return;
        }

        log.info("Incremental backup: {}, at {}", reason, incRoot);
        doCopyChangedRegions(incRoot);
    }

    private void doCopyChangedRegions(Path targetBase) throws IOException {
        for (ServerLevel level : server.getAllLevels()) {
            Path regionDir = MapUtil.getRegionFileStorageFolder(
                    MapUtil.getStorage((IOWorker) level.getChunkSource().chunkScanner())
            );
            FileUtil.createDirectory(regionDir);
            for (Path mca : Files.list(regionDir).filter(p -> p.toString().endsWith(".mca")).toList()) {
                String key = key(level, mca.getFileName().toString());
                String newHash = FileUtil.hashFile(mca);
                if (!newHash.equals(regionHashMap.getOrDefault(key, ""))) {
                    Path dst = targetBase.resolve(levelDirMap.get(MinecraftUtil.getLevelName(level)))
                            .resolve(mca.getFileName().toString());

                    FileUtil.copyAtomic(mca, dst);
                    regionHashMap.put(key, newHash);
                }
            }
        }
        saveHashes();
    }

    private static String key(ServerLevel level, String mcaName) {
        return MinecraftUtil.getLevelName(level) + "/" + mcaName;
    }

    private void purgeOld(int keep) throws IOException {
        List<Path> list = Files.list(fullRoot)
                .sorted(Comparator.reverseOrder())
                .toList();
        for (int i = keep; i < list.size(); i++) {
            FileUtil.createDirectory(list.get(i));
            Files.walk(list.get(i))
                    .sorted(Comparator.reverseOrder())
                    .forEach(p -> p.toFile().delete());
        }
    }

    public Optional<Path> getLatestFullBackup() {
        try {
            return Files.list(fullRoot)
                    .max(Comparator.naturalOrder())
                    .map(p -> p.resolve(p.getFileName() + ".zip"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private BackupHotSource getBackupHotSource(String hotSource, ServerLevel level) {
        if (INCREMENTAL.equals(hotSource)) {
            String levelDir = levelDirMap.computeIfPresent(MinecraftUtil.getLevelName(level), (k, v) ->
                    FileUtil.safeFileName(MinecraftUtil.getLevelName(level)));
            Path levelDirPath = incRoot.resolve(levelDir);
            return new BackupHotSource(null, levelDirPath, false);
        } else if (FULL.equals(hotSource)) {
            try {
                Path tempDir = Files.createTempDirectory(worldRoot, "backup");
                Path levelDir = FileUtil.unzipToDirectoryParallel(getLatestFullBackup().orElseThrow(), tempDir)
                        .resolve(MapUtil.getLevelDir(level).getFileName());
                return new BackupHotSource(tempDir, levelDir, true);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        throw new IllegalArgumentException("Unknown hot source: " + hotSource);
    }

    public boolean rollbackChunkHot(ServerLevel level,
                                    ChunkPos first,
                                    ChunkPos second,
                                    String hotSource) throws IOException {
        int minX = Math.min(first.x, second.x);
        int maxX = Math.max(first.x, second.x);
        int minZ = Math.min(first.z, second.z);
        int maxZ = Math.max(first.z, second.z);

        BackupHotSource source = getBackupHotSource(hotSource, level);
        Map<String, List<ChunkPos>> query;
        try {
            query = collectChunksAndCheckFiles(source.levelDir(), minX, maxX, minZ, maxZ);
        }catch (Exception e) {
            source.clean();
            throw e;
        }

        WorldManage wm = buildWorldManage(level, source.levelDir(), query);

        int minY = level.getMinBuildHeight();
        int maxY = level.getMaxBuildHeight();

        List<BlockUpdate> cache = Collections.synchronizedList(
                new ArrayList<>( (maxX - minX + 1) * (maxZ - minZ + 1) * 16 * 16 * (maxY - minY) )
        );

        int threads = Math.min(query.size(), Runtime.getRuntime().availableProcessors());
        ExecutorService pool = Executors.newFixedThreadPool(threads, r -> {
            Thread t = new Thread(r, "GBackup-PreFetch-" + r.hashCode());
            t.setDaemon(true);
            return t;
        });

        List<Future<?>> tasks = new ArrayList<>();
        for (int cx = minX; cx <= maxX; cx++) {
            for (int cz = minZ; cz <= maxZ; cz++) {
                final int chunkX = cx;
                final int chunkZ = cz;
                tasks.add(pool.submit(() -> {
                    List<BlockUpdate> local = new ArrayList<>(16 * 16 * (maxY - minY));
                    for (int dx = 0; dx < 16; dx++) {
                        for (int dz = 0; dz < 16; dz++) {
                            int gx = chunkX * 16 + dx;
                            int gz = chunkZ * 16 + dz;
                            for (int y = minY; y < maxY; y++) {
                                local.add(new BlockUpdate(
                                        new BlockPos(gx, y, gz),
                                        wm.getBlockAt(gx, y, gz)));
                            }
                        }
                    }
                    cache.addAll(local);
                }));
            }
        }

        try {
            for (Future<?> f : tasks) {
                try {
                    f.get();
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Rollback 中断", ie);
                } catch (ExecutionException ee) {
                    if (ee.getCause() instanceof RuntimeException ex && ex.getMessage().startsWith("Chunk data not found:")) {
                        return false;
                    }
                    throw new RuntimeException("Rollback 预取任务异常", ee.getCause());
                }
            }
        } finally {
            pool.shutdown();
            source.clean();
        }

        level.getServer().executeBlocking(() -> {
            for (BlockUpdate u : cache) {
                level.setBlock(u.pos, u.state, Block.UPDATE_NONE | Block.UPDATE_CLIENTS);
            }
        });

        return true;
    }

    private Map<String, List<ChunkPos>> collectChunksAndCheckFiles(Path dir,
                                                                   int minX, int maxX,
                                                                   int minZ, int maxZ) throws IOException {
        Map<String, List<ChunkPos>> query = new HashMap<>();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                ChunkPos pos = new ChunkPos(x, z);
                String regionFileName = WorldManage.getRegionFileName(pos);

                if (!Files.exists(dir.resolve(regionFileName))) {
                    throw new IOException("缺少 region 文件: " + regionFileName);
                }
                query.computeIfAbsent(regionFileName, k -> new ArrayList<>()).add(pos);
            }
        }
        return query;
    }

    private WorldManage buildWorldManage(ServerLevel level,
                                         Path dir,
                                         Map<String, List<ChunkPos>> query) throws IOException {
        ChunkScanAccess scanAccess = level.getChunkSource().chunkScanner();
        IOWorker original = (IOWorker) scanAccess;

        RegionFileStorage backupStorage = new RegionFileStorage(
                MapUtil.getRegionStorageInfo(MapUtil.getStorage(original)), dir, false);

        try (ReadRegionExecutorService svc = new ReadRegionExecutorService(backupStorage)) {
            Map<String, List<ReadRegionExecutorService.ScanResult>> scanMap = svc.scanRegion(query);
            return new WorldManage(level, scanMap);
        } catch (Exception e) {
            throw new RuntimeException("扫描备份 region 文件失败", e);
        } finally {
            backupStorage.close();
        }
    }

    private void scheduleAutoBackup() {
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "GBackup-AutoBackup");
            t.setDaemon(true);
            return t;
        });

        long period = Math.max(1, config.getAutoBackupMinutes());
        autoBackupFuture = scheduler.scheduleAtFixedRate(() -> {
            try {
                server.getPlayerList().broadcastSystemMessage(Component.literal("[GBackup] ")
                                .withStyle(ChatFormatting.GOLD)
                                .append("自动备份任务开始..."),
                        false);

                if (config.isAutoBackupWithIncremental()) {
                    backupIncremental("Auto incremental backup");
                }

                backupFull("Auto full backup")
                        .thenRun(() -> server.getPlayerList().broadcastSystemMessage(Component.literal("[GBackup] ")
                                .withStyle(ChatFormatting.GOLD)
                                .append("自动备份任务完成")
                                .withStyle(ChatFormatting.GREEN), false)
                        )
                        .exceptionally(t -> {
                            log.error("自动全量备份失败", t);
                            return null;
                        });
            } catch (Exception ex) {
                log.error("自动备份任务执行异常", ex);
            }
        }, period, period, TimeUnit.MINUTES);

        log.info("GBackup: 自动备份已启用，每 {} 分钟执行一次。", period);
    }

    private static final class BlockUpdate {
        final BlockPos pos;
        final BlockState state;
        BlockUpdate(BlockPos pos, BlockState state) {
            this.pos = pos;
            this.state = state;
        }
    }

    private record BackupHotSource(Path backupDir, Path levelDir, boolean isClean) {
        public void clean() {
            if (!isClean) return;
            FileUtil.deleteDirectory(backupDir);
        }
    }
}
