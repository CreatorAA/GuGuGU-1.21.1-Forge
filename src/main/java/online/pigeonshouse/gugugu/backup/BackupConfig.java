package online.pigeonshouse.gugugu.backup;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import lombok.Data;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 备份系统可扩展配置。
 */
@Data
public class BackupConfig {
    /** 自动增量备份时间间隔（分钟） */
    private int autoBackupMinutes = 30;

    /** 启用定时任务 */
    private boolean enableAutoBackup = false;

    /** 最大保留全量备份数 */
    private int keepFull = 3;

    /** 是否对全量备份启用 ZIP 压缩 */
    private boolean compressFull = true;

    /** 热回档时使用的数据来源："inc"（增量）或 "full"（全量），默认增量 */
    private String hotRollbackSource = BackupManager.FULL;

    /** 指令白名单 —— 权限不足但被允许使用 /gbackup 的玩家名称列表 */
    private List<String> commandWhitelist = new ArrayList<>();

    /* ------------------ 序列化 / 反序列化 ------------------ */

    public static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .create();

    /**
     * 从文件加载配置；若文件不存在则写入默认配置并返回。
     */
    public static BackupConfig loadOrCreate(Path file) throws IOException {
        if (Files.notExists(file)) {
            BackupConfig cfg = new BackupConfig();
            cfg.save(file);
            return cfg;
        }
        try (var reader = Files.newBufferedReader(file)) {
            return GSON.fromJson(reader, BackupConfig.class);
        }
    }

    /**
     * 将当前配置保存到文件。
     */
    public void save(Path file) throws IOException {
        Files.createDirectories(file.getParent());
        try (var writer = Files.newBufferedWriter(file)) {
            GSON.toJson(this, writer);
        }
    }
}
