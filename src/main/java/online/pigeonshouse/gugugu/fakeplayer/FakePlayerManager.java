package online.pigeonshouse.gugugu.fakeplayer;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import online.pigeonshouse.gugugu.fakeplayer.config.FakePlayerConfig;
import online.pigeonshouse.gugugu.fakeplayer.config.PersistedFakePlayer;
import online.pigeonshouse.gugugu.utils.MinecraftUtil;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

public class FakePlayerManager {
    private final FakePlayerConfig config;

    public FakePlayerManager(FakePlayerConfig config) {
        this.config = config;
    }

    public void loginPersisted(MinecraftServer server) {
        AtomicReference<CompletableFuture<RIFakeServerPlayer>> fakeServerPlayer = new AtomicReference<>();

        for (PersistedFakePlayer pfp : config.getAutoLoginList()) {
            ServerLevel level = MinecraftUtil.getServerLevelByName(pfp.getDimension());
            if (level == null) continue;

            if (fakeServerPlayer.get() != null) {
                fakeServerPlayer.get().thenAcceptAsync(fp -> {
                    fakeServerPlayer.set(RIFakeServerPlayerFactory.createFakeServerPlayer(
                            server, pfp.getName(), level, pfp.getGameModeByName(),
                            pfp.getX(), pfp.getY(), pfp.getZ(),
                            pfp.getYaw(), pfp.getPitch()
                    ));
                });
            } else {
                fakeServerPlayer.set(RIFakeServerPlayerFactory.createFakeServerPlayer(
                        server, pfp.getName(), level, pfp.getGameModeByName(),
                        pfp.getX(), pfp.getY(), pfp.getZ(),
                        pfp.getYaw(), pfp.getPitch()
                ));
            }
        }
    }

    public void recordAndSave(MinecraftServer server) {
        PlayerList list = server.getPlayerList();
        Set<String> loginNames = config.getAutoLoginNames();
        Set<PersistedFakePlayer> newPersisted = new HashSet<>();

        for (ServerPlayer player : list.getPlayers()) {
            if (isOurFakePlayer(player) && loginNames.contains(player.getName().getString())) {
                PersistedFakePlayer pfp = new PersistedFakePlayer(
                        player.getName().getString(),
                        MinecraftUtil.getLevelName(player.serverLevel()),
                        PersistedFakePlayer.getGameModeName(player.gameMode.getGameModeForPlayer()),
                        player.getX(), player.getY(), player.getZ(),
                        player.getYRot(), player.getXRot()
                );

                newPersisted.add(pfp);
            }
        }

        config.setPersisted(newPersisted);
        config.save();
    }

    private boolean isOurFakePlayer(ServerPlayer player) {
        return player instanceof RIFakeServerPlayer;
    }
}
