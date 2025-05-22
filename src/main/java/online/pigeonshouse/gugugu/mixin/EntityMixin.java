package online.pigeonshouse.gugugu.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import online.pigeonshouse.gugugu.GuGuGu;
import online.pigeonshouse.gugugu.fakeplayer.PlayerInventoryViewer;
import online.pigeonshouse.gugugu.fakeplayer.RIFakeServerPlayer;
import online.pigeonshouse.gugugu.fakeplayer.config.FakePlayerConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public class EntityMixin {
    @Inject(method = "interactAt", at = @At("HEAD"))
    private void interactAt(Player player, Vec3 vec3, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        Entity entity = (Entity) (Object) this;
        FakePlayerConfig fakePlayerConfig = GuGuGu.getINSTANCE().getFakePlayerConfig();

        if (player instanceof ServerPlayer serverPlayer && entity instanceof RIFakeServerPlayer fakeServerPlayer
                && player.getItemInHand(hand).isEmpty()) {
            if (serverPlayer.hasPermissions(4)) {
                PlayerInventoryViewer.openFor(serverPlayer, fakeServerPlayer, true);
                return;
            }

            if (fakePlayerConfig.isAllowOpenInventory()) {
                PlayerInventoryViewer.openFor(serverPlayer, fakeServerPlayer,
                        fakePlayerConfig.isAllowInventoryInteraction());
            }
        }
    }
}
