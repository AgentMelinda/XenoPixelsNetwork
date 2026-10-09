package net.bullettrain.xenopixelsmod.mixin.common;

import com.dragonminez.common.init.entities.ki.AbstractKiProjectile;
import com.dragonminez.common.init.entities.ki.KiWaveEntity;
import net.bullettrain.xenopixelsmod.combat.v2.UltimateFinisher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Apply the owned finisher impulse after vanilla damage knockback has completed. */
@Mixin(value = AbstractKiProjectile.class, remap = false)
public abstract class FinisherWaveKnockbackMixin {
    @Inject(method = "applyDamageOrHeal", at = @At("RETURN"))
    private void xeno$finishWaveDamage(Entity target, float amount, CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof KiWaveEntity wave) {
            UltimateFinisher.afterWaveDamage(wave, target, cir.getReturnValueZ());
        }
    }
}
