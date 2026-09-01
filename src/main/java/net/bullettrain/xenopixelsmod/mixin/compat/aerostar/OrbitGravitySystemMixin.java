package net.bullettrain.xenopixelsmod.mixin.compat.aerostar;

import dev.ryanhcode.sable.neoforge.event.ForgeSablePrePhysicsTickEvent;
import net.bullettrain.xenopixelsmod.aero.gravity.AeroStarState;
import net.bullettrain.xenopixelsmod.aero.gravity.OrbitalGravityConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Stops AeroStar's orbital gravity handler from running, so ours owns ship gravity.
 *
 * <p><b>Target:</b> {@code com.bega.aerostarcomp.physics.OrbitGravitySystem#onPrePhysicsTick},
 * verified against AeroStar 1.0.1 as
 * {@code public static void onPrePhysicsTick(dev.ryanhcode.sable.neoforge.event.ForgeSablePrePhysicsTickEvent)}.
 * <b>Written for:</b> Minecraft 1.21.1 / NeoForge 21.1.238, AeroStar 1.0.1,
 * sable-neoforge 2.0.3. <b>Side:</b> common — Sable physics ticks are server-side.
 *
 * <p><b>Why a mixin and not an event.</b> Both supported alternatives were checked and neither
 * works. {@code ForgeSablePrePhysicsTickEvent} extends {@code net.neoforged.bus.api.Event}
 * without implementing {@code ICancellableEvent}, so a listener at a higher priority cannot
 * suppress AeroStar's. And AeroStar registers through
 * {@code NeoForge.EVENT_BUS.addListener(<lambda>)} rather than {@code @EventBusSubscriber}, so
 * there is no object or class we could hand to {@code IEventBus#unregister}. Cancelling the
 * callback is the only remaining hook.
 *
 * <p><b>What is lost by cancelling.</b> Nothing but AeroStar's own gravity numbers.
 * {@code net.bullettrain.xenopixelsmod.aero.gravity.OrbitalGravitySystem} provides the same
 * behavior driven by dimension ids from config; the rest of AeroStar — the dimensional drive,
 * ship restoration, and the dimension transfers in its {@code onServerTick} — is untouched.
 * Cancelling is also cheaper than letting it run: AeroStar's handler walks every sub-level and
 * allocates a {@code Vector3d} per ship on every physics tick.
 *
 * <p>Applied only when AeroStar is installed — see {@code ConditionalMixinPlugin} — and further
 * gated at runtime on {@code overrideAeroStar} so a server can keep AeroStar's implementation.
 *
 * <p><b>History.</b> Three earlier versions failed, all because the target's signature was
 * being guessed rather than read. The first took only {@code CallbackInfo}; the second used
 * {@code require = 1} against a still-wrong signature; the third was a non-static handler
 * declaring the platform-agnostic {@code SablePrePhysicsTickEvent}, which Mixin rejected
 * outright ("non-static callback method targets a static method"). Reading the shipped jar
 * settled both questions: the target is static and takes the NeoForge wrapper type. Hence
 * {@code expect = 1} — if this stops matching, the log should say so loudly instead of failing
 * silently a fourth time — while {@code require = 0} keeps a future AeroStar update from
 * turning into a startup crash, since {@link AeroStarState} already degrades gracefully by
 * handing gravity back to AeroStar.
 *
 * <p>A real cancel is reported through {@link AeroStarState#markHandlerCancelled()}, which
 * {@code OrbitalGravitySystem} reads to decide who owns gravity. The mixin's mere presence is
 * not evidence of anything, as the earlier crash reports proved.
 */
@Mixin(targets = "com.bega.aerostarcomp.physics.OrbitGravitySystem", remap = false)
public class OrbitGravitySystemMixin {

    @Inject(method = "onPrePhysicsTick", at = @At("HEAD"), cancellable = true,
            remap = false, expect = 1, require = 0)
    private static void xeno$disableBrokenOrbitGravity(ForgeSablePrePhysicsTickEvent event,
                                                       CallbackInfo ci) {
        if (OrbitalGravityConfig.enabled && OrbitalGravityConfig.overrideAeroStar) {
            AeroStarState.markHandlerCancelled();
            ci.cancel();
        }
    }
}
