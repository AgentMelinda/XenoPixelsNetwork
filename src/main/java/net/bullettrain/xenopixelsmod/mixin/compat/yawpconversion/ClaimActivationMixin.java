package net.bullettrain.xenopixelsmod.mixin.compat.yawpconversion;

import net.bullettrain.xenopixelsmod.compat.linearreader.LinearClaimCoverage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "de.z0rdak.yawp.core.region.ProtectedRegion", remap = false)
public abstract class ClaimActivationMixin {
    @Inject(method = "setIsActive", at = {@At("HEAD"), @At("RETURN")}, require = 2)
    private void xeno$invalidate(CallbackInfo ci) { LinearClaimCoverage.invalidate(); }
}
