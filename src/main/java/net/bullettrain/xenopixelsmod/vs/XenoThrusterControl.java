package net.bullettrain.xenopixelsmod.vs;

import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.platform.SableEventPlatform;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.config.XenoServerConfig;
import org.joml.Vector3d;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/** Runtime thruster controller for Sable moving sub-levels. */
public final class XenoThrusterControl {
    private static final AtomicBoolean REGISTERED = new AtomicBoolean();
    private static final Map<UUID, XenoThrusterControl> CONTROLS = new ConcurrentHashMap<>();
    /**
     * Stale-sweep cadence for {@link #CONTROLS}. A control is kept while its ship is seen alive in
     * any dimension's physics tick and dropped once it has gone unseen this long (ship
     * disassembled, or its chunks unloaded past the window).
     */
    private static final AtomicLong lastSweepNanos = new AtomicLong();
    private static final long SWEEP_INTERVAL_NANOS = 30_000_000_000L;
    private static final long STALE_AFTER_NANOS = 300_000_000_000L;
    private static final long THRUSTER_STALE_AFTER_NANOS = 60_000_000_000L;

    private final Map<String, ThrusterForce> thrusters = new ConcurrentHashMap<>();
    private volatile ThrusterForce[] physicsSnapshot = new ThrusterForce[0];
    private final Vector3d localImpulse = new Vector3d();
    private final Vector3d localPoint = new Vector3d();
    /** Last physics tick this ship was seen in a loaded sub-level container; drives the sweep. */
    private volatile long lastSeenNanos = System.nanoTime();

    public static void ensureRegistered() {
        if (!REGISTERED.compareAndSet(false, true)) return;
        SableEventPlatform.INSTANCE.onPhysicsTick((system, deltaSeconds) -> {
            long now = System.nanoTime();
            for (var candidate : SubLevelContainer.getContainer(system.getLevel()).getAllSubLevels()) {
                if (!(candidate instanceof ServerSubLevel subLevel)) continue;
                XenoThrusterControl control = get(subLevel);
                if (control == null) continue;
                // Alive in this dimension → renew. An empty control still means a live ship, so
                // mark it seen before the isEmpty short-circuit (or an idle hull would go stale).
                control.lastSeenNanos = now;
                if (control.isEmpty()) continue;
                RigidBodyHandle handle = system.getPhysicsHandle(subLevel);
                if (handle != null && handle.isValid()) control.physicsTick(subLevel, handle, deltaSeconds);
            }
            // Cleanup by staleness, not by diffing against THIS dimension's container: the callback
            // fires once per dimension's physics system, so "drop anything not in this container"
            // wiped controls for ships in every other dimension (thrust flicker). A live ship is
            // seen by its OWN dimension's tick, so anything unseen for this long is genuinely gone.
            if (now - lastSweepNanos.get() >= SWEEP_INTERVAL_NANOS) {
                lastSweepNanos.set(now);
                CONTROLS.values().removeIf(control -> {
                    control.removeStaleThrusters(now);
                    return now - control.lastSeenNanos > STALE_AFTER_NANOS;
                });
            }
        });
        XenoPixelsMod.LOGGER.info("Registered Sable thruster physics callback");
    }

    public static XenoThrusterControl getOrCreate(ServerSubLevel subLevel) {
        ensureRegistered();
        return CONTROLS.computeIfAbsent(subLevel.getUniqueId(), ignored -> new XenoThrusterControl());
    }

    public static XenoThrusterControl get(ServerSubLevel subLevel) {
        return subLevel == null ? null : CONTROLS.get(subLevel.getUniqueId());
    }

    public void setThruster(String key, double posX, double posY, double posZ,
                            double forceX, double forceY, double forceZ, double power) {
        if (power <= 0.001) {
            removeThruster(key);
            return;
        }
        long now = System.nanoTime();
        ThrusterForce force = thrusters.get(key);
        if (force != null) {
            force.state = new ForceState(posX, posY, posZ, forceX, forceY, forceZ, power);
            force.lastPublishedNanos = now;
            return;
        }
        thrusters.put(key, new ThrusterForce(
                new ForceState(posX, posY, posZ, forceX, forceY, forceZ, power), now));
        rebuildSnapshot();
    }

    public void removeThruster(String key) {
        if (thrusters.remove(key) != null) rebuildSnapshot();
    }

    public void clearAll() {
        if (thrusters.isEmpty()) return;
        thrusters.clear();
        physicsSnapshot = new ThrusterForce[0];
    }

    public int thrusterCount() { return thrusters.size(); }
    public boolean isEmpty() { return thrusters.isEmpty(); }

    private void rebuildSnapshot() {
        physicsSnapshot = thrusters.values().toArray(ThrusterForce[]::new);
    }

    private void removeStaleThrusters(long now) {
        boolean changed = thrusters.entrySet().removeIf(
                entry -> now - entry.getValue().lastPublishedNanos > THRUSTER_STALE_AFTER_NANOS);
        if (changed) rebuildSnapshot();
    }

    private void physicsTick(ServerSubLevel subLevel, RigidBodyHandle handle, double deltaSeconds) {
        double step = Math.max(0.0, Math.min(deltaSeconds, 0.1));
        for (ThrusterForce thruster : physicsSnapshot) {
            ForceState state = thruster.state;
            if (state == null || state.power <= 0.0) continue;
            // Everything stays in BODY/MODEL space: Sable's impulse API takes body-space vectors
            // (its own FloatingBlockController transformInverse()s world velocity and gravity into
            // body space before filling the impulse vectors it passes to that same call), and the
            // center of mass it subtracts from this point is body-space too. A thruster's facing
            // is already ship-local, so converting to world was a spurious extra rotation.
            localImpulse.set(state.forceX, state.forceY, state.forceZ).mul(state.power * step);
            localPoint.set(state.posX + 0.5, state.posY + 0.5, state.posZ + 0.5);
            if (!sane(subLevel, localImpulse, localPoint)) continue;
            // Argument order is (position, force) — these were passed the other way round.
            handle.applyImpulseAtPoint(localPoint, localImpulse);
        }
    }

    /**
     * Reject or clamp an impulse before Rapier ever sees it.
     *
     * <p>Rapier integrates a bad impulse straight into the body's velocity, so a single NaN or
     * runaway value does not merely misplace the ship for one tick — the sub-level comes apart,
     * and correcting the input afterwards cannot undo it. Guarding the input is the only point
     * at which this is recoverable.
     *
     * <p>The warning is throttled to once per ship: this runs inside the physics tick, and an
     * unthrottled log line here would itself become the performance problem.
     *
     * @return false when the impulse must not be applied at all
     */
    private boolean sane(ServerSubLevel subLevel, Vector3d impulse, Vector3d point) {
        if (!XenoServerConfig.thrusterImpulseGuardEnabled) return true;

        if (!Double.isFinite(impulse.x) || !Double.isFinite(impulse.y) || !Double.isFinite(impulse.z)
                || !Double.isFinite(point.x) || !Double.isFinite(point.y) || !Double.isFinite(point.z)) {
            warnOnce(subLevel, "non-finite thruster impulse {} at {} — skipped", impulse, point);
            return false;
        }
        double max = XenoServerConfig.thrusterMaxImpulse;
        double length = impulse.length();
        if (max > 0.0 && length > max) {
            // Clamp rather than skip: a legitimately over-tuned thruster should still push,
            // just not hard enough to tear its own hull apart.
            impulse.mul(max / length);
            warnOnce(subLevel, "thruster impulse {} exceeded cap {} — clamped", length, max);
        }
        return true;
    }

    private void warnOnce(ServerSubLevel subLevel, String message, Object... args) {
        if (!warnedShips.add(subLevel.getUniqueId())) return;
        XenoPixelsMod.LOGGER.warn("[ship " + VsShipHelper.getShipId(subLevel) + "] " + message, args);
    }

    /** Ships already warned about, so the physics tick never spams the log. */
    private final java.util.Set<java.util.UUID> warnedShips = java.util.concurrent.ConcurrentHashMap.newKeySet();

    private static final class ThrusterForce {
        private volatile ForceState state;
        private volatile long lastPublishedNanos;

        private ThrusterForce(ForceState state, long lastPublishedNanos) {
            this.state = state;
            this.lastPublishedNanos = lastPublishedNanos;
        }
    }

    private record ForceState(double posX, double posY, double posZ,
                              double forceX, double forceY, double forceZ, double power) {}
}
