package online.pigeonshouse.gugugu.fakeplayer.control;

import lombok.Getter;
import net.minecraft.server.level.ServerPlayer;
import online.pigeonshouse.gugugu.event.EventCallback;
import online.pigeonshouse.gugugu.event.MinecraftServerEvents;

@Getter
public class PlayerControl implements EventCallback<MinecraftServerEvents.ServerTickEvent> {
    private final ServerPlayer player;
    private final BehaviorSynthesizer synthesizer;

    public PlayerControl(ServerPlayer player) {
        this.player = player;
        this.synthesizer = new BehaviorSynthesizer(player);
    }

    @Override
    public void onEvent(MinecraftServerEvents.ServerTickEvent event) {
        synthesizer.handle();
    }
}
