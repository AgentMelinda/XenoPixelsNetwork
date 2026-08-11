package net.bullettrain.xenopixelsmod.aero.gravity;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.platform.SableEventPlatform;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.mixin.ConditionalMixinPlugin;
import net.bullettrain.xenopixelsmod.vs.ShipGravityControl;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Applies per-dimension gravity to Sable ships.
 *
 * <p>This is our replacement for AeroStar's {@code OrbitGravitySystem}. That class resolved
 * planets through {@code NorthstarDimensions}, so when Northstar Redux 0.6 moved the class it
 * threw {@code NoClassDefFoundError} inside Sable's pre-physics-tick event and killed the
 * server thread every tick a sub-level existed. We read dimension ids from
 * {@link OrbitalGravityConfig} instead and touch no space-mod classes at all, so a Northstar
 * update cannot break ship gravity again.
 *
 * <p>The actual force is left to {@link ShipGravityControl}, which already runs a Sable
 * physics-tick correction; this system only decides what each ship's target gravity should be.
 */
public final class OrbitalGravitySystem {
    private static final AtomicBoolean REGISTERED = new AtomicBoolean();
    /** Physics ticks between passes — gravity changes only when a ship changes dimension. */
    private static final int INTERVAL = 20;

    /**
     * Last value this system wrote per sub-level.
     *
     * <p>Used so we never clobber a gravity a server owner set by hand (via the ship gravity
     * command): we only overwrite a value we ourselves put there, or one still sitting at the
     * untouched baseline.
     */
    private static final Map<UUID, Double> APPLIED = new ConcurrentHashMap<>();

    /** Resolved once — mod presence cannot change at runtime. */
    private static final boolean AEROSTAR_PRESENT =
            ConditionalMixinPlugin.isModLoaded("aerostarcomp");
    private static final AtomicBoolean YIELD_LOGGED = new AtomicBoolean();

    private static int counter;

    private OrbitalGravitySystem() {
    }

    public static void ensureRegistered() {
        if (!REGISTERED.compareAndSet(false, true)) return;
        SableEventPlatform.INSTANCE.onPhysicsTick((system, deltaSeconds) -> {
            if (!OrbitalGravityConfig.enabled) return;
            if (++counter < INTERVAL) return;
            counter = 0;
            try {
                apply(system);
            } catch (Throwable t) {
                // Never let a gravity pass take down the physics tick — the failure mode we
                // are replacing did exactly that.
                XenoPixelsMod.LOGGER.error("Orbital gravity pass failed", t);
            }
        });
        XenoPixelsMod.LOGGER.info("Registered orbital gravity system (replaces AeroStar OrbitGravitySystem)");
    }

    private static void apply(dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem system) {
        if (aeroStarOwnsGravity()) return;
        var level = system.getLevel();
        if (level == null) return;
        String dimensionId = level.dimension().location().toString();
        if (!OrbitalGravityConfig.hasDimension(dimensionId)) return;

        double target = OrbitalGravityConfig.gravityFor(dimensionId, ShipGravityControl.NORMAL_GRAVITY);
        for (var candidate : SubLevelContainer.getContainer(level).getAllSubLevels()) {
            if (!(candidate instanceof ServerSubLevel subLevel)) continue;
            ShipGravityControl control = ShipGravityControl.getOrCreate(subLevel);
            double current = control.getGravitySi();
            if (Math.abs(current - target) < 1.0e-6) continue;

            Double previouslyApplied = APPLIED.get(subLevel.getUniqueId());
            boolean ours = previouslyApplied != null
                    && Math.abs(current - previouslyApplied) < 1.0e-6;
            boolean untouched = Math.abs(current - ShipGravityControl.NORMAL_GRAVITY) < 1.0e-6;
            if (!ours && !untouched) continue; // a manual override owns this ship

            control.setGravitySi(target);
            APPLIED.put(subLevel.getUniqueId(), control.getGravitySi());
        }
    }

    /**
     * True when AeroStar is installed and still running its own gravity, so we must stay out.
     *
     * <p>With the {@code NorthstarDimensions} shim in place AeroStar no longer crashes, which
     * means it applies gravity again. If our mixin also cancelled its handler we own gravity;
     * if the mixin never matched — which has happened, and is not visible from the crash report
     * — AeroStar owns it and a second set of corrections would fight the first.
     *
     * <p>Deciding on an observed cancel rather than on "is the mixin present" is deliberate:
     * presence proved to be no evidence at all.
     */
    private static boolean aeroStarOwnsGravity() {
        if (!AEROSTAR_PRESENT) return false;
        if (AeroStarState.hasCancelledHandler()) return false;
        if (YIELD_LOGGED.compareAndSet(false, true)) {
            XenoPixelsMod.LOGGER.info(
                    "AeroStar is installed and its gravity handler was not suppressed; "
                            + "leaving orbital gravity to AeroStar to avoid double-applying");
        }
        return true;
    }

    /** Drop tracking for ships that no longer exist. Safe to call on world unload. */
    public static void forget(UUID subLevelId) {
        if (subLevelId != null) APPLIED.remove(subLevelId);
    }

    public static void clearAll() {
        APPLIED.clear();
    }
}
