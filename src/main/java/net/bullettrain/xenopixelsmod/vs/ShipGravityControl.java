package net.bullettrain.xenopixelsmod.vs;

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

/** Per-sub-level gravity override. Positive values pull downward. */
public final class ShipGravityControl {
    public static final double NORMAL_GRAVITY = 10.0;
    private static final AtomicBoolean REGISTERED = new AtomicBoolean();
    private static final Map<UUID, ShipGravityControl> CONTROLS = new ConcurrentHashMap<>();
    /**
     * Stale-sweep cadence for {@link #CONTROLS}. Kept long enough that a ship whose chunks unload
     * and reload within the window keeps its (possibly owner-set) gravity, yet short enough that a
     * disassembled ship does not linger for the server's whole uptime.
     */
    private static final AtomicLong lastSweepNanos = new AtomicLong();
    private static final long SWEEP_INTERVAL_NANOS = 30_000_000_000L;
    private static final long STALE_AFTER_NANOS = 300_000_000_000L;

    private volatile double gravitySi = NORMAL_GRAVITY;
    private volatile boolean suppressed;
    private final Vector3d correctionImpulse = new Vector3d();
    /** Last physics tick this ship was seen in a loaded sub-level container; drives the sweep. */
    private volatile long lastSeenNanos = System.nanoTime();

    public static void ensureRegistered() {
        if (!REGISTERED.compareAndSet(false, true)) return;
        SableEventPlatform.INSTANCE.onPhysicsTick((system, deltaSeconds) -> {
            long now = System.nanoTime();
            for (var candidate : SubLevelContainer.getContainer(system.getLevel()).getAllSubLevels()) {
                if (!(candidate instanceof ServerSubLevel subLevel)) continue;
                ShipGravityControl control = get(subLevel);
                if (control == null) continue;
                // Alive in this dimension → renew. A suppressed control still belongs to a live
                // ship, so mark it seen before the suppressed short-circuit.
                control.lastSeenNanos = now;
                if (control.suppressed) continue;
                double difference = NORMAL_GRAVITY - control.gravitySi;
                if (Math.abs(difference) < 1.0e-6) continue;
                RigidBodyHandle handle = system.getPhysicsHandle(subLevel);
                if (handle == null || !handle.isValid()) continue;
                double mass = Math.max(1.0, subLevel.getMassTracker().getMass());
                double step = Math.max(0.0, Math.min(deltaSeconds, 0.1));
                control.correctionImpulse.set(0.0, mass * difference * step, 0.0);
                // World -> body: Sable's impulse API takes body-space vectors (its own
                // FloatingBlockController transformInverse()s world gravity into body space
                // before building the impulses it passes to this same call). Without this the
                // correction tilted with the hull instead of staying vertical.
                subLevel.logicalPose().orientation().transformInverse(control.correctionImpulse);
                handle.applyLinearImpulse(control.correctionImpulse);
            }
            // Cleanup by staleness, not by diffing against THIS dimension's container: the callback
            // fires once per dimension's physics system, so a live ship is seen by its OWN
            // dimension's tick and survives the sweep, while a disassembled ship is not.
            if (now - lastSweepNanos.get() >= SWEEP_INTERVAL_NANOS) {
                lastSweepNanos.set(now);
                CONTROLS.values().removeIf(control -> now - control.lastSeenNanos > STALE_AFTER_NANOS);
            }
        });
        XenoPixelsMod.LOGGER.info("Registered Sable gravity override callback");
    }

    public static ShipGravityControl getOrCreate(ServerSubLevel subLevel) {
        ensureRegistered();
        return CONTROLS.computeIfAbsent(subLevel.getUniqueId(), ignored -> new ShipGravityControl());
    }

    public static ShipGravityControl get(ServerSubLevel subLevel) {
        return subLevel == null ? null : CONTROLS.get(subLevel.getUniqueId());
    }

    public double getGravitySi() { return gravitySi; }

    public void setGravitySi(double gravitySi) {
        this.gravitySi = Math.max(-100.0, Math.min(100.0,
                Double.isFinite(gravitySi) ? gravitySi : NORMAL_GRAVITY));
    }

    public boolean isSuppressed() { return suppressed; }
    public void setSuppressed(boolean suppressed) { this.suppressed = suppressed; }
}
