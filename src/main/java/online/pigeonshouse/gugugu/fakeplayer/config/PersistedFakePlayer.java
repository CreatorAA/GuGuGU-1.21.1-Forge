package online.pigeonshouse.gugugu.fakeplayer.config;

import com.google.gson.annotations.Expose;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import net.minecraft.world.level.GameType;

import java.util.Objects;

/**
 * 持久化假人玩家信息，存储其名称、所在维度、位置和朝向
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PersistedFakePlayer {
    /**
     * 假人名称
     */
    @Expose
    private String name;
    /**
     * 维度资源名称，例如 "minecraft:overworld"
     */
    @Expose
    private String dimension;
    /**
     * 游戏模式
     */
    @Expose
    private String gameMode;
    /**
     * X 轴坐标
     */
    @Expose
    private double x;
    /**
     * Y 轴坐标
     */
    @Expose
    private double y;
    /**
     * Z 轴坐标
     */
    @Expose
    private double z;
    /**
     * 朝向 Yaw
     */
    @Expose
    private float yaw;
    /**
     * 朝向 Pitch
     */
    @Expose
    private float pitch;

    public static String getGameModeName(GameType gameType) {
        return gameType.getName();
    }

    public static PersistedFakePlayer createName(String name) {
        return new PersistedFakePlayer(name, null, null, 0, 0, 0, 0, 0);
    }

    public GameType getGameModeByName() {
        return GameType.byName(gameMode);
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        PersistedFakePlayer that = (PersistedFakePlayer) o;
        return Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(name);
    }
}
