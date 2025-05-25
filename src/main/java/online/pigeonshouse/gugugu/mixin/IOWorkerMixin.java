package online.pigeonshouse.gugugu.mixin;

import net.minecraft.world.level.chunk.storage.IOWorker;
import net.minecraft.world.level.chunk.storage.RegionFileStorage;
import online.pigeonshouse.gugugu.utils.StorageGetter;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(IOWorker.class)
public abstract class IOWorkerMixin implements StorageGetter {
    @Shadow
    @Final
    private RegionFileStorage storage;

    @Override
    public RegionFileStorage getStorage() {
        return storage;
    }
}
