package online.pigeonshouse.gugugu.mixin;

import net.minecraft.world.level.chunk.storage.RegionFileStorage;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
import online.pigeonshouse.gugugu.utils.PathGetter;
import online.pigeonshouse.gugugu.utils.RegionStorageInfoGetter;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.nio.file.Path;

@Mixin(RegionFileStorage.class)
public abstract class RegionFileStorageMixin implements PathGetter, RegionStorageInfoGetter {
    @Shadow @Final private Path folder;

    @Shadow @Final private RegionStorageInfo info;

    @Override
    public Path getPath() {
        return folder;
    }

    @Override
    public RegionStorageInfo getRegionStorageInfo() {
        return info;
    }
}
