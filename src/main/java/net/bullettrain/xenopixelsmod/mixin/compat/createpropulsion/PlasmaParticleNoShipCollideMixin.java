package net.bullettrain.xenopixelsmod.mixin.compat.createpropulsion;

import net.minecraft.client.particle.Particle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Target: Create Propulsion {@code PlasmaParticle.tick}
 *         (javap of createpropulsion-1.1.5).
 * Reason: Spark mYjF2cjFbf — {@code tick} steps {@code move()} in 0.5-block
 *         slices. Each {@code move} is Sable {@code sable$moveWithSubLevels}
 *         (ship clip + PalettedContainer SAT) then vanilla
 *         {@code collideBoundingBox}. Plasma is a visual trail; flying through
 *         the hull is cheaper than 27% of the Render thread.
 * Version: createpropulsion 1.1.5. {@code move(DDD)V} on Particle.
 * Side: client. Gated on createpropulsion.
 */
@Mixin(
        targets = "dev.propulsionteam.propulsionsimulated.particles.plasma.PlasmaParticle",
        remap = false
)
public abstract class PlasmaParticleNoShipCollideMixin {

    @Shadow
    public double x;
    @Shadow
    public double y;
    @Shadow
    public double z;

    @Redirect(
            method = "tick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/particle/Particle;move(DDD)V",
                    remap = true
            ),
            require = 0
    )
    private void xenopixels$glidePastHull(Particle particle, double dx, double dy, double dz) {
        particle.setPos(this.x + dx, this.y + dy, this.z + dz);
    }
}
