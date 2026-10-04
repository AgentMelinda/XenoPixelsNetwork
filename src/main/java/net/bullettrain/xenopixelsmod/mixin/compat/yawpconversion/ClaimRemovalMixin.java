package net.bullettrain.xenopixelsmod.mixin.compat.yawpconversion;

import net.bullettrain.xenopixelsmod.compat.linearreader.LinearClaimCoverage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "de.z0rdak.yawp.data.region.LevelRegionData", remap = false)
public abstract class ClaimRemovalMixin {
    // Native delete-all commands mutate LevelRegionData directly, bypassing DimensionRegionApi.
    @Inject(method = {"removeLocal(Lde/z0rdak/yawp/core/region/IMarkableRegion;)V", "clearLocals()V",
            "addLocal(Lde/z0rdak/yawp/core/region/IMarkableRegion;)V",
            "addLocal(Lde/z0rdak/yawp/core/region/IProtectedRegion;Lde/z0rdak/yawp/core/region/IMarkableRegion;)V"},
            at = {@At("HEAD"), @At("RETURN")}, require = 8)
    private void xeno$invalidate(CallbackInfo ci) { LinearClaimCoverage.invalidate(); }
}
