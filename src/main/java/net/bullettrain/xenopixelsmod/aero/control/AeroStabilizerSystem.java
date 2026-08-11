package net.bullettrain.xenopixelsmod.aero.control;

import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.platform.SableEventPlatform;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.joml.Quaterniond;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Attitude stabilization for Aero-controlled ships.
 *
 * <p>Runs inside Sable's physics tick, like {@code XenoThrusterControl} and
 * {@code ShipGravityControl}, because impulses must be applied there rather than on the game
 * thread.
 *
 * <p>Commands are absolute world attitudes. Quaternion proportional feedback supplies the
 * shortest rotation and world angular-velocity feedback damps it. The requested world angular
 * acceleration is transformed back into body axes before applying Sable's body inertia tensor.
 *
 * <p>Torque is scaled by the ship's inertia tensor so that heavy hulls get proportionally more
 * authority — without it, gains tuned on a small ship do nothing on a capital hull.
 */
public final class AeroStabilizerSystem {
    private static final AtomicBoolean REGISTERED = new AtomicBoolean();
    private static final Map<UUID, Entry> ENTRIES = new ConcurrentHashMap<>();

    private static final java.util.concurrent.atomic.AtomicLong lastSweepNanos =
            new java.util.concurrent.atomic.AtomicLong();
    private static final long SWEEP_INTERVAL_NANOS = 30L * 1_000_000_000L;
    private static final long STALE_AFTER_NANOS = 60L * 1_000_000_000L;

    private static final double KP = 5.0;
    private static final double KD = 3.2;
    private static final double MAX_ANGULAR_ACCEL = 5.0;

    private AeroStabilizerSystem() {
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
                // A stabilizer fault must never take down the physics tick.
                XenoPixelsMod.LOGGER.error("Aero stabilizer pass failed", t);
            }
            // Entries are otherwise only removed by an explicit clear(), so a sub-level deleted
            // while engaged — or one whose controller was destroyed with the chunk unloaded —
            // would sit in the map for the rest of the session. A live entry is re-commanded
            // every server tick, so anything untouched for this long no longer has a ship.
            if (now - lastSweepNanos.get() >= SWEEP_INTERVAL_NANOS) {
                lastSweepNanos.set(now);
                ENTRIES.values().removeIf(entry -> now - entry.lastSeenNanos > STALE_AFTER_NANOS);
            }
        });
        XenoPixelsMod.LOGGER.info("Registered Aero attitude stabilizer");
    }

    /** Command an absolute Minecraft-world attitude in degrees. */
    public static void setTargetAttitude(ServerSubLevel subLevel, double yawDeg, double pitchDeg,
                                         double rollDeg, Vector3dc bodyNose, Vector3dc bodyUp,
                                         boolean enabled) {
        if (subLevel == null) return;
        ensureRegistered();
        Entry entry = ENTRIES.computeIfAbsent(subLevel.getUniqueId(), ignored -> new Entry());
        entry.targetYawDeg = yawDeg;
        entry.targetPitchDeg = pitchDeg;
        entry.targetRollDeg = rollDeg;
        if (bodyNose != null) {
            entry.bodyNoseX = bodyNose.x(); entry.bodyNoseY = bodyNose.y(); entry.bodyNoseZ = bodyNose.z();
        }
        if (bodyUp != null) {
            entry.bodyUpX = bodyUp.x(); entry.bodyUpY = bodyUp.y(); entry.bodyUpZ = bodyUp.z();
        }
        entry.enabled = enabled;
    }

    /** Compatibility shutdown entry point for old call sites. */
    public static void setCommand(ServerSubLevel subLevel, double ignoredPitch, double ignoredYaw,
                                  double ignoredRoll, boolean enabled) {
        if (!enabled) clear(subLevel);
    }

    public static void clear(ServerSubLevel subLevel) {
        if (subLevel != null) ENTRIES.remove(subLevel.getUniqueId());
    }

    public static void clearAll() {
        ENTRIES.clear();
    }

    /** Latest per-axis error, for the diagnostics tab. */
    public static double[] lastError(ServerSubLevel subLevel) {
        Entry entry = subLevel == null ? null : ENTRIES.get(subLevel.getUniqueId());
        if (entry == null) return new double[] {0, 0, 0};
        return new double[] {entry.errorX, entry.errorY, entry.errorZ};
    }

    private static final class Entry {
        private volatile double targetYawDeg;
        private volatile double targetPitchDeg;
        private volatile double targetRollDeg;
        private volatile boolean enabled;

        private volatile double bodyNoseX, bodyNoseY = 1.0, bodyNoseZ;
        private volatile double bodyUpX, bodyUpY, bodyUpZ = -1.0;
        private volatile long lastSeenNanos = System.nanoTime();
        /**
         * Published copy of {@link #lastError} for the diagnostics tab. The vector itself is
         * written on the physics thread and rewritten in place every tick, so reading its three
         * components from the game thread can mix two different ticks; these are written once,
         * after the vector is complete.
         */
        private volatile double errorX, errorY, errorZ;
        private final Vector3d lastError = new Vector3d();
        private final Vector3d angularAcceleration = new Vector3d();
        private final Vector3d torque = new Vector3d();
        private final Vector3d sampledNose = new Vector3d();
        private final Vector3d sampledUp = new Vector3d();
        private final Quaterniond current = new Quaterniond();

        private void tick(ServerSubLevel subLevel, RigidBodyHandle handle, double deltaSeconds) {
            Vector3dc worldRate = handle.getAngularVelocity();
            if (worldRate == null) return;
            double step = Math.max(0.0, Math.min(deltaSeconds, 0.1));
            if (step <= 0.0) return;
            current.set(subLevel.logicalPose().orientation()).normalize();
            Quaterniond desired = SableAttitudeMath.desiredOrientation(
                    targetYawDeg, targetPitchDeg, targetRollDeg,
                    sampledNose.set(bodyNoseX, bodyNoseY, bodyNoseZ),
                    sampledUp.set(bodyUpX, bodyUpY, bodyUpZ));
            SableAttitudeMath.errorVectorWorld(current, desired, lastError);
            errorX = lastError.x;
            errorY = lastError.y;
            errorZ = lastError.z;
            angularAcceleration.set(lastError).mul(KP).sub(
                    worldRate.x() * KD, worldRate.y() * KD, worldRate.z() * KD);
            double length = angularAcceleration.length();
            if (length > MAX_ANGULAR_ACCEL) angularAcceleration.mul(MAX_ANGULAR_ACCEL / length);

            // Sable consumes model/body-space torque impulses.
            torque.set(angularAcceleration);
            current.transformInverse(torque);
            if (torque.lengthSquared() < 1.0e-10) return;
            try {
                subLevel.getMassTracker().getInertiaTensor().transform(torque);
            } catch (Throwable ignored) {
            }
            torque.mul(step);
            handle.applyTorqueImpulse(torque);
        }
    }
}
