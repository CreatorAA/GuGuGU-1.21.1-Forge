package online.pigeonshouse.gugugu.fakeplayer.config;

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
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
public class FakePlayerConfig {
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .excludeFieldsWithoutExposeAnnotation()
            .create();

    private final File configFile;
    /**
     * FakePlayer的命令级别
     */
    @Getter
    @Setter
    @Expose
    private int commandLevel = 4;
    /**
     * 是否允许玩家右键打开FakePlayer的背包
     */
    @Getter
    @Setter
    @Expose
    private boolean allowOpenInventory = false;
    /**
     * 允许非管理员与FakePlayer的背包的背包互动
     */
    @Getter
    @Setter
    @Expose
    private boolean allowInventoryInteraction = true;
    /**
     * FakePlayer玩家名称前缀、后缀
     */
    @Getter
    @Setter
    @Expose
    private String fakePlayerNamePrefix = "";
    @Getter
    @Setter
    @Expose
    private String fakePlayerNameSuffix = "";
    /** 持久化假人信息列表 */
    @Getter
    @Setter
    @Expose
    private Set<PersistedFakePlayer> persisted;
    /** 自动登录的假人名称集合 */
    @Getter
    @Setter
    @Expose
    private Set<String> autoLoginNames;

    public FakePlayerConfig(File configFile) {
        this.configFile = configFile;
    }

    public void load() {
        if (!configFile.exists() || configFile.length() == 0) {
            log.info("No configuration file found, creating default configuration");
            createDefaultConfig();
            save();
            return;
        }

        try (FileReader reader = new FileReader(configFile)) {
            FakePlayerConfig loaded = GSON.fromJson(reader, FakePlayerConfig.class);
            commandLevel = loaded.commandLevel;
            allowOpenInventory = loaded.allowOpenInventory;
            allowInventoryInteraction = loaded.allowInventoryInteraction;
            fakePlayerNamePrefix = loaded.fakePlayerNamePrefix;
            fakePlayerNameSuffix = loaded.fakePlayerNameSuffix;
            persisted = Objects.requireNonNullElseGet(loaded.persisted, HashSet::new);
            autoLoginNames = Objects.requireNonNullElseGet(loaded.autoLoginNames, HashSet::new);

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
        commandLevel = 4;
        allowOpenInventory = false;
        allowInventoryInteraction = true;
        fakePlayerNamePrefix = "";
        fakePlayerNameSuffix = "";
        persisted = new HashSet<>();
        autoLoginNames = new HashSet<>();
    }

    /** 根据名称查找持久化记录 */
    public PersistedFakePlayer findByName(String name) {
        return persisted.stream()
                .filter(p -> p.getName().equals(name))
                .findFirst().orElse(null);
    }

    /** 获取所有自动登录的 PersistedFakePlayer 列表 */
    public List<PersistedFakePlayer> getAutoLoginList() {
        return persisted.stream()
                .filter(p -> autoLoginNames.contains(p.getName()))
                .collect(Collectors.toList());
    }
}
