package net.bullettrain.xenopixelsmod.aero.gravity;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.platform.SableEventPlatform;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.vs.ShipGravityControl;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Applies per-dimension gravity to Sable ships.
 *
 * <p>This system owns ship gravity outright. It used to yield to AeroStar's {@code OrbitGravitySystem}
 * whenever that mod was installed and its handler had not been suppressed, because two systems both
 * correcting gravity every physics tick would fight each other and exactly one had to own it. That
 * compat was removed: AeroStar pinned Northstar Redux 0.5.4 and broke on the 0.6 line, and keeping a
 * shim class plus a cancel-mixin plus an ownership negotiation alive to support it cost more than it
 * returned. There is now one owner, which removes a whole class of "whose gravity is this" ambiguity.
 *
 * <p><b>Behaviour change for servers that still run AeroStar:</b> with the negotiation gone, this
 * system applies its own per-dimension values instead of deferring. Ships will fly differently than
 * they did under AeroStar's numbers. {@link OrbitalGravityConfig} is the place to tune that, and its
 * {@code enabled} flag turns this system off entirely for a server that wants no per-dimension
 * gravity at all.
 *
 * <p>Like the system it replaced, this reads dimension ids from {@link OrbitalGravityConfig} and
 * touches no space-mod classes at all, so a space mod renaming or repackaging anything cannot break
 * ship gravity again.
 *
 * <p>The actual force is left to {@link ShipGravityControl}, which already runs a Sable physics-tick
 * correction; this system only decides what each ship's target gravity should be.
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
                // Never let a gravity pass take down the physics tick — the failure mode this system
                // was written to replace did exactly that, every tick a sub-level existed.
                XenoPixelsMod.LOGGER.error("Orbital gravity pass failed", t);
            }
        });
        XenoPixelsMod.LOGGER.info("Registered orbital gravity system (owns ship gravity per dimension)");
    }

    private static void apply(dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem system) {
        var level = system.getLevel();
        if (level == null) return;
        String dimensionId = level.dimension().location().toString();
        if (!OrbitalGravityConfig.hasDimension(dimensionId)) return;

        double target = OrbitalGravityConfig.gravityFor(dimensionId, ShipGravityControl.NORMAL_GRAVITY);
        for (var candidate : SubLevelContainer.getContainer(level).getAllSubLevels()) {
            if (!(candidate instanceof ServerSubLevel subLevel)) continue;
            if (net.bullettrain.xenopixelsmod.space.physics.PhysicalEarth.isManaged(subLevel)) continue;
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

    /** Drop tracking for ships that no longer exist. Safe to call on world unload. */
    public static void forget(UUID subLevelId) {
        if (subLevelId != null) APPLIED.remove(subLevelId);
    }

    public static void clearAll() {
        APPLIED.clear();
    }
}
