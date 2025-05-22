package online.pigeonshouse.gugugu.mixin;

import com.mojang.authlib.GameProfile;
import net.minecraft.server.players.UserWhiteList;
import online.pigeonshouse.gugugu.GuGuGu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(UserWhiteList.class)
public class UserWhiteListFixMixin {
    @Inject(method = "getKeyForUser*", at = @At("HEAD"), cancellable = true)
    private void getKeyForUser(GameProfile gameProfile, CallbackInfoReturnable<String> cir) {
        if (GuGuGu.INSTANCE.getConfig().isWhiteListDisableUidCheck()) {
            cir.setReturnValue(gameProfile.getName());
        }
    }
}
