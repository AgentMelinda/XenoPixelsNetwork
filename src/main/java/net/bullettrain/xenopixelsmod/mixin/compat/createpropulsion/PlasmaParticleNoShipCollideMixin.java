package net.bullettrain.xenopixelsmod.mixin.compat.createpropulsion;

import net.minecraft.client.particle.Particle;
import org.spongepowered.asm.mixin.Mixin;
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
public abstract class PlasmaParticleNoShipCollideMixin extends Particle {

    /**
     * Declared {@code extends Particle} rather than shadowing {@code x}/{@code y}/{@code z}.
     *
     * <p>Those fields are {@code protected} on {@code net.minecraft.client.particle.Particle}, not
     * on {@code PlasmaParticle}, and {@code @Shadow} resolves against the target class alone — it
     * does not walk the hierarchy. Shadowing them threw
     * {@code InvalidMixinException: @Shadow field x was not located in the target class} on every
     * launch, and because this config is {@code required: false} that was logged as a warning and
     * ignored: the mixin never applied and plasma particles went on colliding with ships, silently,
     * for as long as it has existed. Matching the real ancestry makes them inherited fields and
     * removes the {@code remap = false} question along with the shadows.
     */
    protected PlasmaParticleNoShipCollideMixin() {
        super(null, 0.0, 0.0, 0.0);
    }

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
