package net.bullettrain.xenopixelsmod.mixin.common;

import com.dragonminez.common.init.entities.ki.AbstractKiProjectile;
import net.bullettrain.xenopixelsmod.combat.technique.KiGuidance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Target: {@code AbstractKiProjectile#applyHomingSteering}
 * Reason: while Guidance is held, replace stock homing with lock-home or camera steer.
 * Version: NeoForge 1.21.1 / DMZ 2.1.x
 * Side: common. The method is already server-only.
 */
@Mixin(value = AbstractKiProjectile.class, remap = false)
public abstract class KiGuidanceSteeringMixin {

    @Shadow
    private int homingTargetId;

    @Shadow
    private int firingStartTick;

    @Inject(method = "applyHomingSteering", at = @At("HEAD"), cancellable = true)
    private void xenopixels$guide(CallbackInfo ci) {
        AbstractKiProjectile self = (AbstractKiProjectile) (Object) this;
        if (KiGuidance.steer(self, this.homingTargetId, this.firingStartTick,
                id -> this.homingTargetId = id,
                tick -> this.firingStartTick = tick)) {
            ci.cancel();
        }
    }
}
