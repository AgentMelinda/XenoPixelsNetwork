package net.bullettrain.xenopixelsmod.mixin.common;

import net.bullettrain.xenopixelsmod.space.smallplanet.SmallPlanetPhysicalizer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProgressListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Flushes accepted edited-small-planet placements before Sable serializes sub-levels during level save. */
@Mixin(ServerLevel.class)
public abstract class SmallPlanetPlacementSaveMixin {
    @Inject(method = "save(Lnet/minecraft/util/ProgressListener;ZZ)V", at = @At("HEAD"), require = 1)
    private void xenopixels$flushSmallPlanetPlacements(ProgressListener progress, boolean flush, boolean skipSave,
                                                        CallbackInfo ci) {
        SmallPlanetPhysicalizer.beforeHostSave((ServerLevel) (Object) this);
    }
}
