package net.bullettrain.xenopixelsmod.mixin.common;

import com.dragonminez.common.init.entities.ki.KiLaserEntity;
import net.bullettrain.xenopixelsmod.combat.beam.KiBeamBore;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Target: {@code KiLaserEntity#tick} wall detonation
 * Reason: lasers explode-and-die on first block; waves bore a tunnel. Same-size BEAM/LASER
 *         therefore barely grief, which reads as "other players' beams don't break blocks".
 * Version: NeoForge 1.21.1 / DMZ 2.1.x
 * Side: common. The redirected call is already server-only.
 */
@Mixin(value = KiLaserEntity.class, remap = false)
public abstract class KiLaserBoreMixin {

    @Redirect(method = "tick", at = @At(value = "INVOKE",
            target = "Lcom/dragonminez/common/init/entities/ki/KiLaserEntity;explodeAndDie(Lnet/minecraft/world/phys/Vec3;)V"))
    private void xenopixels$boreInsteadOfDetonate(KiLaserEntity self, Vec3 worldHit) {
        KiBeamBore.boreLaser(self, worldHit);
    }
}
