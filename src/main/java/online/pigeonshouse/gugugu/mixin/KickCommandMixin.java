package online.pigeonshouse.gugugu.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.server.commands.KickCommand;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import online.pigeonshouse.gugugu.fakeplayer.RIFakeServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(KickCommand.class)
public class KickCommandMixin {
    @Redirect(method = "kickPlayers",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/network/ServerGamePacketListenerImpl;disconnect(Lnet/minecraft/network/chat/Component;)V")
    )
    private static void kickRIFakePlayer(ServerGamePacketListenerImpl instance, Component component) {
        if (instance.player instanceof RIFakeServerPlayer fakeServerPlayer) {
            fakeServerPlayer.disconnect(component);
            return;
        }

        instance.disconnect(component);
    }
}
