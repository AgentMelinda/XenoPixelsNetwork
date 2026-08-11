package net.bullettrain.xenopixelsmod.mixin.compat.aerostar;

import dev.ryanhcode.sable.api.event.SablePrePhysicsTickEvent;
import net.bullettrain.xenopixelsmod.aero.gravity.AeroStarState;
import net.bullettrain.xenopixelsmod.aero.gravity.OrbitalGravityConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Stops AeroStar's orbital gravity handler from running.
 *
 * <p>AeroStar 1.0.1 pins Northstar Redux 0.5.4 and resolves planets through
 * {@code com.lightning.northstar.world.dimension.NorthstarDimensions}. Redux 0.6 moved that
 * class, so on 0.6.x this handler throws {@code NoClassDefFoundError} inside Sable's
 * pre-physics-tick event, which kills the server thread on the first tick any sub-level
 * exists — an unrecoverable crash loop on a server with ships.
 *
 * <p>{@code net.bullettrain.xenopixelsmod.aero.gravity.OrbitalGravitySystem} provides the same
 * behavior driven by dimension ids from config, so cancelling this costs no gameplay beyond
 * AeroStar's own gravity numbers. Everything else AeroStar does (the dimensional drive, ship
 * restoration) is untouched.
 *
 * <p>Applied only when AeroStar is installed — see {@code ConditionalMixinPlugin} — and
 * further gated at runtime on {@code overrideAeroStar} so a server on the supported Redux
 * 0.5.4 can keep AeroStar's implementation.
 *
 * <p><b>This mixin is now an optimization, not the crash fix.</b> The crash is fixed by the
 * shim at {@code com.lightning.northstar.world.dimension.NorthstarDimensions}, which makes
 * AeroStar's handler resolve and run normally. Cancelling it here is only about preferring our
 * config-tunable gravity over AeroStar's fixed numbers, so {@code require = 0} is correct: a
 * failure to match now costs nothing beyond AeroStar keeping its own gravity.
 *
 * <p>Two earlier versions of this mixin failed. The first took only {@code CallbackInfo} — an
 * {@code @Inject} handler must repeat the target's parameters, so it matched nothing, and
 * {@code require = 0} swallowed that while the crash report still listed the mixin as applied
 * to the class. The second used {@code require = 1} and still crashed, meaning the injector
 * matched something other than the invoked method. Because we have never been able to inspect
 * AeroStar's jar, the design no longer depends on winning that guess.
 *
 * <p>A real cancel is reported through {@link AeroStarState#markHandlerCancelled()}, which
 * {@code OrbitalGravitySystem} reads to decide who owns gravity. The mixin's mere presence is
 * not evidence of anything, as the crash reports proved.
 */
@Mixin(targets = "com.bega.aerostarcomp.physics.OrbitGravitySystem", remap = false)
public class OrbitGravitySystemMixin {

    @Inject(method = "onPrePhysicsTick", at = @At("HEAD"), cancellable = true,
            remap = false, require = 0)
    private void xeno$disableBrokenOrbitGravity(SablePrePhysicsTickEvent event, CallbackInfo ci) {
        if (OrbitalGravityConfig.enabled && OrbitalGravityConfig.overrideAeroStar) {
            AeroStarState.markHandlerCancelled();
            ci.cancel();
        }
    }
}
