package online.pigeonshouse.gugugu.mixin;

import net.minecraft.network.chat.ChatDecorator;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import online.pigeonshouse.gugugu.event.MinecraftServerEvents;
import online.pigeonshouse.gugugu.utils.MinecraftUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerImplMixin {
    @Redirect(method = "lambda$handleChat$6",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/network/chat/ChatDecorator;decorate(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/network/chat/Component;)Lnet/minecraft/network/chat/Component;")
    )
    private Component playerChat(ChatDecorator instance, ServerPlayer player, Component component) {
        MinecraftServer server = MinecraftUtil.getServer();
        Component decorate = server.getChatDecorator().decorate(player, component);
        MinecraftServerEvents.PlayerChatEvent event = new MinecraftServerEvents.PlayerChatEvent(decorate, player);
        MinecraftServerEvents.PLAYER_CHAT.dispatch(event);
        return event.getComponent() == null ? decorate : event.getComponent();
    }
}
