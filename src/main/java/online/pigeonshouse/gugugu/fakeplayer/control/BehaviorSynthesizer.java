package online.pigeonshouse.gugugu.fakeplayer.control;

import net.minecraft.server.level.ServerPlayer;
import online.pigeonshouse.gugugu.fakeplayer.commands.RIFakePlayerCommands;

import java.util.*;

/**
 * 行为合成器
 */
public class BehaviorSynthesizer {
    private final Map<String, Behavior> behaviors = new HashMap<>();
    private final List<Behavior> behaviorsList = new LinkedList<>();
    private final ServerPlayer player;

    public BehaviorSynthesizer(ServerPlayer player) {
        this.player = player;
    }

    public void add(String action, Behavior behavior) {
        Behavior behavior1 = behaviors.get(action);
        if (behavior1 == null) {
            behaviors.put(action, behavior);
            behaviorsList.add(behavior);
            behaviorsList.sort(Comparator.comparingInt(Behavior::priority));
        } else {
            behavior1.andThen(behavior);
        }
    }

    public void remove(String action) {
        Behavior remove = behaviors.remove(action);
        if (remove != null) {
            behaviorsList.remove(remove);
            if (isEmpty()) {
                RIFakePlayerCommands.removePlayerControl(player.getUUID());
            }
        }
    }

    public boolean isEmpty() {
        return behaviors.isEmpty();
    }

    public void handle() {
        if (behaviorsList.isEmpty() || player.hasDisconnected()) {
            RIFakePlayerCommands.removePlayerControl(player.getUUID());
            return;
        }

        for (Behavior behavior : behaviorsList) {
            behavior.behavior();
            if (!behavior.isContinue()) {
                remove(behavior.action());
            }
        }
    }
}