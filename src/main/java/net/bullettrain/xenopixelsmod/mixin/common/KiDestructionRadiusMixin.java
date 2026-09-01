package net.bullettrain.xenopixelsmod.mixin.common;

import com.dragonminez.common.init.entities.ki.AbstractKiProjectile;
import net.bullettrain.xenopixelsmod.config.XenoServerConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Keeps enormous visual Ki from turning DMZ's cubic block scans into a server stall.
 *
 * <p><b>Only applies where Xeno raised the ceiling.</b> The clamp exists because overcharged ki can
 * reach radii that make a synchronous {@code (2r+1)^3} scan unusable. It is not an opinion about
 * how big a stock Kamehameha's crater should be — and left ungated it silently shrank the terrain
 * destruction of every vanilla DMZ technique on any server that merely installed this build.
 */
@Mixin(value = AbstractKiProjectile.class, remap = false)
public abstract class KiDestructionRadiusMixin {
    @Inject(method = "scaledDestructionRadius", at = @At("RETURN"), cancellable = true)
    private void xenopixels$boundDestruction(float baseRadius, CallbackInfoReturnable<Float> cir) {
        if (!XenoServerConfig.kiOverchargeEnabled) return;
        // Entity collision, damage and visuals may scale fully. Block work stays independently
        // bounded even in that mode: radius 1,024 would otherwise perform billions of checks.
        cir.setReturnValue(Math.min(cir.getReturnValue(), XenoServerConfig.kiDestructionRadiusLimit()));
    }
}
