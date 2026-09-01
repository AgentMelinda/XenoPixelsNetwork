package net.bullettrain.xenopixelsmod.aero.control;

import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.platform.SableEventPlatform;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import org.joml.Vector3d;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Applies the aerodynamic lift/drag acceleration to Aero-controlled ships, inside Sable's
 * physics tick — the only place impulses may be applied, for the same reason
 * {@link AeroStabilizerSystem} and {@code XenoThrusterControl} live here.
 *
 * <p>The game thread publishes a <b>world-space</b> acceleration each flight tick
 * ({@link #setAeroAcceleration}); this system rotates it into body space and turns it into a
 * linear impulse of {@code mass * accel * dt}. The world→body rotation is mandatory: Sable's
 * impulse API takes body-space vectors ({@code ShipGravityControl} transformInverse's its world
 * gravity correction for exactly this reason).
 *
 * <p>Entries are keyed by sub-level UUID and re-renewed every tick the ship is present in the
 * dimension's container, then swept by staleness — the proven pattern that avoids wiping
 * other-dimension ships (the callback fires once per dimension).
 */
public final class AeroAeroForceSystem {
    private static final AtomicBoolean REGISTERED = new AtomicBoolean();
    private static final Map<UUID, Entry> ENTRIES = new ConcurrentHashMap<>();

    private static final AtomicLong lastSweepNanos = new AtomicLong();
    private static final long SWEEP_INTERVAL_NANOS = 30L * 1_000_000_000L;
    private static final long STALE_AFTER_NANOS = 60L * 1_000_000_000L;

    private AeroAeroForceSystem() {
    }

    public static void ensureRegistered() {
        if (!REGISTERED.compareAndSet(false, true)) return;
        SableEventPlatform.INSTANCE.onPhysicsTick((system, deltaSeconds) -> {
            if (ENTRIES.isEmpty()) return;
            long now = System.nanoTime();
            try {
                for (var candidate : SubLevelContainer.getContainer(system.getLevel()).getAllSubLevels()) {
                    if (!(candidate instanceof ServerSubLevel subLevel)) continue;
                    Entry entry = ENTRIES.get(subLevel.getUniqueId());
                    if (entry == null) continue;
                    entry.lastSeenNanos = now;
                    if (!entry.enabled) continue;
                    RigidBodyHandle handle = system.getPhysicsHandle(subLevel);
                    if (handle == null || !handle.isValid()) continue;
                    entry.tick(subLevel, handle, deltaSeconds);
                }
            } catch (Throwable t) {
                // An aero fault must never take down the physics tick.
                XenoPixelsMod.LOGGER.error("Aero force pass failed", t);
            }
            if (now - lastSweepNanos.get() >= SWEEP_INTERVAL_NANOS) {
                lastSweepNanos.set(now);
                ENTRIES.values().removeIf(entry -> now - entry.lastSeenNanos > STALE_AFTER_NANOS);
            }
        });
        XenoPixelsMod.LOGGER.info("Registered Aero lift/drag force system");
    }

    /** Publish a world-space aerodynamic acceleration to apply to this ship. */
    public static void setAeroAcceleration(ServerSubLevel subLevel,
                                           double worldAx, double worldAy, double worldAz,
                                           boolean enabled) {
        if (subLevel == null) return;
        ensureRegistered();
        Entry entry = ENTRIES.computeIfAbsent(subLevel.getUniqueId(), ignored -> new Entry());
        entry.ax = worldAx;
        entry.ay = worldAy;
        entry.az = worldAz;
        entry.enabled = enabled;
    }

    public static void clear(ServerSubLevel subLevel) {
        if (subLevel != null) ENTRIES.remove(subLevel.getUniqueId());
    }

    public static void clearAll() {
        ENTRIES.clear();
    }

    private static final class Entry {
        private volatile double ax, ay, az;
        private volatile boolean enabled;
        private volatile long lastSeenNanos = System.nanoTime();

        private final Vector3d impulse = new Vector3d();

        private void tick(ServerSubLevel subLevel, RigidBodyHandle handle, double deltaSeconds) {
            double step = Math.max(0.0, Math.min(deltaSeconds, 0.1));
            if (step <= 0.0) return;
            // Snapshot the published acceleration once; the game thread rewrites these fields
            // while this runs on the physics thread.
            double wx = this.ax, wy = this.ay, wz = this.az;
            if (wx == 0.0 && wy == 0.0 && wz == 0.0) return;
            if (!Double.isFinite(wx) || !Double.isFinite(wy) || !Double.isFinite(wz)) return;

            // World -> body: Sable's impulse API takes body-space vectors.
            impulse.set(wx, wy, wz);
            subLevel.logicalPose().orientation().transformInverse(impulse);

            double mass = Math.max(1.0, subLevel.getMassTracker().getMass());
            impulse.mul(mass * step);
            if (impulse.lengthSquared() < 1.0e-10) return;
            handle.applyLinearImpulse(impulse);
        }
    }
}
