package online.pigeonshouse.gugugu.mixin;

import net.minecraft.server.MinecraftServer;
import online.pigeonshouse.gugugu.event.MinecraftServerEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.BooleanSupplier;

@Mixin(value = MinecraftServer.class)
public class MinecraftServerMixin {
    @Inject(method = "runServer", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;buildServerStatus()Lnet/minecraft/network/protocol/status/ServerStatus;", ordinal = 0))
    private void afterSetupServer(CallbackInfo info) {
        MinecraftServerEvents.SERVER_STARTED.dispatch(new MinecraftServerEvents.ServerStartedEvent((MinecraftServer) (Object) this));
    }

    @Inject(method = "stopServer", at = @At("HEAD"))
    private void beforeShutdownServer(CallbackInfo info) {
        MinecraftServerEvents.SERVER_STOPPED.dispatch(new MinecraftServerEvents.ServerStoppedEvent((MinecraftServer) (Object) this));
    }

    @Inject(method = "tickServer", at = @At("TAIL"))
    private void afterServerTick(BooleanSupplier booleanSupplier, CallbackInfo info) {
        MinecraftServerEvents.SERVER_TICK.dispatch(new MinecraftServerEvents.ServerTickEvent((MinecraftServer) (Object) this));
    }
}
