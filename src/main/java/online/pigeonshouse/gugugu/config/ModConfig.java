package online.pigeonshouse.gugugu.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.Expose;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

@Slf4j
public class ModConfig {
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .excludeFieldsWithoutExposeAnnotation()
            .create();

    private final File configFile;
    /**
     * 启用假人
     */
    @Getter
    @Setter
    @Expose
    private boolean enableFakePlayer;
    /**
     * 启用消息处理
     */
    @Getter
    @Setter
    @Expose
    private boolean enableMessageHandler;
    /**
     * 禁用的消息处理器列表
     */
    @Getter
    @Setter
    @Expose
    private List<String> disabledMessageHandlers;
    /**
     * 启用传送命令tpf
     */
    @Getter
    @Setter
    @Expose
    private boolean enableTeleport;
    /**
     * 白名单禁用uid检查
     */
    @Getter
    @Setter
    @Expose
    private boolean whiteListDisableUidCheck;

    public ModConfig(File configFile) {
        this.configFile = configFile;
    }

    public Object get(String key) {
        return switch (key) {
            case "enableFakePlayer" -> enableFakePlayer;
            case "enableMessageHandler" -> enableMessageHandler;
            case "disabledMessageHandlers" -> disabledMessageHandlers;
            case "enableTeleport" -> enableTeleport;
            case "whiteListDisableUidCheck" -> whiteListDisableUidCheck;
            default -> null;
        };
    }

    public void load() {
        if (!configFile.exists() || configFile.length() == 0) {
            log.info("No configuration file found, creating default configuration");
            createDefaultConfig();
            save();
            return;
        }

        try (FileReader reader = new FileReader(configFile)) {
            ModConfig loaded = GSON.fromJson(reader, ModConfig.class);
            enableFakePlayer = loaded.enableFakePlayer;
            enableMessageHandler = loaded.enableMessageHandler;
            disabledMessageHandlers = loaded.disabledMessageHandlers;
            enableTeleport = loaded.enableTeleport;
            whiteListDisableUidCheck = loaded.whiteListDisableUidCheck;

            log.info("Configuration loaded successfully");
        } catch (Exception e) {
            log.error("Error loading configuration", e);
            createDefaultConfig();
            save();
        }
    }

    public void save() {
        try {
            configFile.getParentFile().mkdirs();
            try (FileWriter writer = new FileWriter(configFile)) {
                GSON.toJson(this, writer);
            }
            log.info("Configuration saved successfully");
        } catch (IOException e) {
            log.error("Error saving configuration", e);
        }
    }

    private void createDefaultConfig() {
        enableFakePlayer = false;
        enableMessageHandler = false;
        disabledMessageHandlers = List.of();
        enableTeleport = false;
        whiteListDisableUidCheck = false;
    }
}
