package online.pigeonshouse.gugugu;

import com.mojang.brigadier.CommandDispatcher;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import online.pigeonshouse.gugugu.backup.BackupConfig;
import online.pigeonshouse.gugugu.backup.BackupManager;
import online.pigeonshouse.gugugu.chat.ChatEventHandler;
import online.pigeonshouse.gugugu.chat.commands.ChatCommand;
import online.pigeonshouse.gugugu.commands.StatusMessageCommand;
import online.pigeonshouse.gugugu.commands.TPFCommand;
import online.pigeonshouse.gugugu.config.ModConfig;
import online.pigeonshouse.gugugu.event.MinecraftServerEvents;
import online.pigeonshouse.gugugu.fakeplayer.FakePlayerManager;
import online.pigeonshouse.gugugu.fakeplayer.commands.RIFakePlayerCommands;
import online.pigeonshouse.gugugu.fakeplayer.config.FakePlayerConfig;
import online.pigeonshouse.gugugu.utils.MinecraftUtil;

import java.io.File;
import java.nio.file.Path;

@Slf4j
@Mod(GuGuGu.MOD_ID)
public class GuGuGu {
    public static final String MOD_ID = "gugugu";
    @Getter
    public static GuGuGu INSTANCE;
    @Getter
    private FakePlayerConfig fakePlayerConfig;
    @Getter
    private ModConfig config;
    @Getter
    private ChatEventHandler chatEventHandler;
    @Getter
    private FakePlayerManager fakePlayerManager;
    @Getter
    private BackupManager backupManager;

    public GuGuGu() {
        INSTANCE = this;

        Path configDir = FMLPaths.CONFIGDIR.get().resolve(MOD_ID);
        File configDirectory = configDir.toFile();
        if (!configDirectory.exists()) {
            configDirectory.mkdirs();
        }

        this.config = new ModConfig(configDir.resolve("config.json").toFile());
        this.fakePlayerConfig = new FakePlayerConfig(configDir.resolve("fakeplayer_config.json").toFile());
        onInitialize();
    }

    public void onInitialize() {
        config.load();
        fakePlayerConfig.load();

        chatEventHandler = new ChatEventHandler();
        fakePlayerManager = new FakePlayerManager(fakePlayerConfig);
        backupManager = new BackupManager();

        MinecraftServerEvents.COMMAND_REGISTER.addCallback(this::registerCommands);
        MinecraftServerEvents.COMMAND_REGISTER.addCallback(event ->
                ChatCommand.register(event.getDispatcher(), chatEventHandler.getPipeline()));

        runIfConfigTrue("enableMessageHandler", () -> {
            MinecraftServerEvents.PLAYER_CHAT.addCallback(chatEventHandler);
        });

        MinecraftServerEvents.SERVER_STARTED.addCallback(this::setup);
        MinecraftServerEvents.SERVER_STOPPED.addCallback(this::stopped);

        log.info("[GuGuGu] Initialized!");
    }

    private void registerCommands(MinecraftServerEvents.CommandRegisterEvent event) {
        CommandDispatcher<CommandSourceStack> commandDispatcher = event.getDispatcher();

        runIfConfigTrue("enableFakePlayer", () ->
                RIFakePlayerCommands.register(commandDispatcher, fakePlayerConfig));

        runIfConfigTrue("enableTeleport", () ->
                TPFCommand.register(commandDispatcher));

        StatusMessageCommand.register(commandDispatcher);
    }

    private void setup(MinecraftServerEvents.ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        fakePlayerManager.loginPersisted(server);
        log.info("[GuGuGu] Setup!");
    }

    private void stopped(MinecraftServerEvents.ServerStoppedEvent event) {
        fakePlayerManager.recordAndSave(event.getServer());
        config.save();
        fakePlayerConfig.save();
    }

    public void runIfConfigTrue(String configName, Runnable runnable) {
        Object o = config.get(configName);
        if (o == null) return;
        if (o instanceof Boolean bool && !bool) return;

        runnable.run();
    }
}
