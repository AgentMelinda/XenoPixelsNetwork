package net.bullettrain.xenopixelsmod.mixin.common;

import com.dragonminez.common.init.entities.ki.KiExplosionEntity;
import net.bullettrain.xenopixelsmod.config.XenoServerConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Caps {@code KiExplosionEntity} radius after overcharge / charge scaling. Distinct from
 * {@link net.bullettrain.xenopixelsmod.mixin.common.KiDestructionRadiusMixin} which only bounds
 * synchronous block scans.
 */
@Mixin(value = KiExplosionEntity.class, remap = false)
public abstract class KiExplosionMaxRadiusMixin {
    @ModifyVariable(method = "setMaxRadius", at = @At("HEAD"), argsOnly = true)
    private float xenopixels$clampMaxRadius(float radius) {
        return XenoServerConfig.clampKiExplosionRadius(radius);
    }
}
