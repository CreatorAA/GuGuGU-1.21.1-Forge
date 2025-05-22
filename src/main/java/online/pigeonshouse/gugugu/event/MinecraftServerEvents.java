package online.pigeonshouse.gugugu.event;

import com.mojang.brigadier.CommandDispatcher;
import lombok.Data;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public class MinecraftServerEvents {
    public static final EventManage<ServerTickEvent> SERVER_TICK = EventManage.of(ServerTickEvent.class);
    public static final EventManage<PlayerChatEvent> PLAYER_CHAT = EventManage.of(PlayerChatEvent.class);
    public static final EventManage<CommandRegisterEvent> COMMAND_REGISTER = EventManage.of(CommandRegisterEvent.class);
    public static final EventManage<ServerStartedEvent> SERVER_STARTED = EventManage.of(ServerStartedEvent.class);
    public static final EventManage<ServerStoppedEvent> SERVER_STOPPED = EventManage.of(ServerStoppedEvent.class);

    @Data
    public static class ServerTickEvent implements BaseEvent {
        private final MinecraftServer server;
    }

    @Data
    public static class PlayerChatEvent implements BaseEvent {
        private final Component originalComponent;
        private final ServerPlayer player;
        private Component component;

        public PlayerChatEvent(Component component, ServerPlayer player) {
            this.component = component.copy();
            this.originalComponent = component;
            this.player = player;
        }
    }

    @Data
    public static class CommandRegisterEvent implements BaseEvent {
        private final CommandDispatcher<CommandSourceStack> dispatcher;
        private final Commands.CommandSelection commandSelection;
        private final CommandBuildContext commandBuildContext;
    }

    @Data
    public static class ServerStartedEvent implements BaseEvent {
        private final MinecraftServer server;
    }

    @Data
    public static class ServerStoppedEvent implements BaseEvent {
        private final MinecraftServer server;
    }
}
