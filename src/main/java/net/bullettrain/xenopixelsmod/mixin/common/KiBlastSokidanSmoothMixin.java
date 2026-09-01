package net.bullettrain.xenopixelsmod.mixin.common;

import com.dragonminez.common.init.entities.ki.KiBlastEntity;
import net.bullettrain.xenopixelsmod.combat.technique.KiGuidanceMath;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Target: {@code KiBlastEntity#isLocallyPredictedSokidan} and {@code onKiTick}
 * Reason: Sokidan slams velocity at the parked look-point. {@code tick()} only
 *         calls {@code setDeltaMovement(DDD)}; the Vec3 follow is in
 *         {@code onKiTick}. A miss on {@code tick} crashed dedicated boot.
 * Version: NeoForge 1.21.1 / DMZ 2.1.3
 * Side: common. Sokidan toggle / parked-in-front stay.
 */
@Mixin(value = KiBlastEntity.class, remap = false)
public abstract class KiBlastSokidanSmoothMixin {

    @Inject(method = "isLocallyPredictedSokidan", at = @At("HEAD"), cancellable = true)
    private void xenopixels$kienzanLerp(CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(false);
    }

    // onKiTick calls setDeltaMovement as a *self-call* (owner = KiBlastEntity, not the
    // inherited Entity). Mixin matches @At("INVOKE") on exact owner+name+desc, so targeting
    // Entity matched 0 of the 2 real call sites and silently no-op'd (and, before require=0,
    // crashed boot under required:true). Target the concrete owner; require enforcement is
    // restored (default 1) so a future DMZ rename fails loudly instead of disabling smoothing.
    @Redirect(
            method = "onKiTick",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/dragonminez/common/init/entities/ki/KiBlastEntity;setDeltaMovement(Lnet/minecraft/world/phys/Vec3;)V"
            )
    )
    private void xenopixels$smoothParkedVel(KiBlastEntity self, Vec3 want) {
        if (self.isActivelyControlledSokidan() && want != null) {
            Vec3 cur = self.getDeltaMovement();
            double[] next = KiGuidanceMath.lerpVec(
                    cur.x, cur.y, cur.z, want.x, want.y, want.z, KiGuidanceMath.cameraRate());
            self.setDeltaMovement(new Vec3(next[0], next[1], next[2]));
            return;
        }
        self.setDeltaMovement(want);
    }
}