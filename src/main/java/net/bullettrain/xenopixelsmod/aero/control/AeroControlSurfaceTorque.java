package net.bullettrain.xenopixelsmod.aero.control;

import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.platform.SableEventPlatform;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.aero.AeroConfig;
import net.bullettrain.xenopixelsmod.config.XenoServerConfig;
import net.bullettrain.xenopixelsmod.vs.VsShipHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Real per-panel aerodynamic torque: each linked, role-assigned wing panel's own deflection
 * pushes at its own body-space position, applied on top of {@link AeroStabilizerSystem}'s lighter
 * attitude-hold assist — the ship rotates because its control surfaces are physically deflected,
 * not only because an autopilot has decided it should.
 *
 * <p>Registration shape, per-ship map, and physics-thread hookup mirror
 * {@link net.bullettrain.xenopixelsmod.vs.XenoThrusterControl} exactly — including the point and
 * impulse convention: {@link RigidBodyHandle#applyImpulseAtPoint} takes a body-space block-center
 * point, and Sable's own impulse API subtracts the ship's center of mass internally, so the lever
 * arm (and therefore the torque) comes out of the physics for free from where the point is.
 *
 * <p>Unlike the thruster controller, a panel's whole set is republished wholesale — not updated
 * key by key — because {@link AeroFlightCore#updatePanelDeflections} already rebuilds its entire
 * relevant-panel list from scratch every scan; syncing this system's snapshot at that same
 * cadence is one array swap, cheaper than tracking incremental adds/removes for something that
 * already gets fully recomputed anyway.
 *
 * <p>The force magnitude is a tunable approximation, not a CFD result — same philosophy as
 * {@link AeroAeroModel}'s own javadoc. {@link AeroConfig#controlSurfaceTorqueScale} is the one
 * knob meant to be adjusted against how a real ship actually handles in-game.
 */
public final class AeroControlSurfaceTorque {
    private static final AtomicBoolean REGISTERED = new AtomicBoolean();
    private static final Map<UUID, AeroControlSurfaceTorque> CONTROLS = new ConcurrentHashMap<>();
    private static final AtomicLong lastSweepNanos = new AtomicLong();
    private static final long SWEEP_INTERVAL_NANOS = 30L * 1_000_000_000L;
    private static final long STALE_AFTER_NANOS = 60L * 1_000_000_000L;

    /** Pre-allocated unit vectors, indexed by {@link Direction.Axis#ordinal()} — a panel's own
     * aerodynamic normal, the same direction {@code WingPanelBlock.sable$getNormal} already uses
     * for Sable's own lift pass. Never mutated; the physics tick only reads these. */
    private static final Vector3dc[] AXIS_UNIT = {
            new Vector3d(1, 0, 0), new Vector3d(0, 1, 0), new Vector3d(0, 0, 1)
    };

    private static final PanelState[] EMPTY = new PanelState[0];

    private volatile PanelState[] snapshot = EMPTY;
    private volatile long lastSeenNanos = System.nanoTime();

    private final Vector3d localImpulse = new Vector3d();
    private final Vector3d localPoint = new Vector3d();
    private final Vector3d velocityExcess = new Vector3d();
    private final Vector3d angularExcess = new Vector3d();

    private AeroControlSurfaceTorque() {
    }

    public static void ensureRegistered() {
        if (!REGISTERED.compareAndSet(false, true)) return;
        SableEventPlatform.INSTANCE.onPhysicsTick((system, deltaSeconds) -> {
            if (CONTROLS.isEmpty()) return;
            long now = System.nanoTime();
            try {
                for (var candidate : SubLevelContainer.getContainer(system.getLevel()).getAllSubLevels()) {
                    if (!(candidate instanceof ServerSubLevel subLevel)) continue;
                    AeroControlSurfaceTorque control = CONTROLS.get(subLevel.getUniqueId());
                    if (control == null) continue;
                    control.lastSeenNanos = now;
                    if (control.snapshot.length == 0) continue;
                    RigidBodyHandle handle = system.getPhysicsHandle(subLevel);
                    if (handle == null || !handle.isValid()) continue;
                    control.physicsTick(subLevel, handle, deltaSeconds);
                }
            } catch (Throwable t) {
                XenoPixelsMod.LOGGER.error("Aero control-surface torque pass failed", t);
            }
            if (now - lastSweepNanos.get() >= SWEEP_INTERVAL_NANOS) {
                lastSweepNanos.set(now);
                CONTROLS.values().removeIf(control -> now - control.lastSeenNanos > STALE_AFTER_NANOS);
            }
        });
        XenoPixelsMod.LOGGER.info("Registered Aero control-surface torque callback");
    }

    /**
     * Replace this ship's entire panel set for the next physics ticks — called from
     * {@link AeroFlightCore#updatePanelDeflections} right after it computes each panel's
     * deflection, at that same scan cadence.
     */
    public static void sync(ServerSubLevel ship, List<PanelState> panels) {
        if (ship == null) return;
        ensureRegistered();
        AeroControlSurfaceTorque control = CONTROLS.computeIfAbsent(
                ship.getUniqueId(), ignored -> new AeroControlSurfaceTorque());
        control.snapshot = panels.isEmpty() ? EMPTY : panels.toArray(PanelState[]::new);
    }

    /** Hand the ship back to plain physics — pairs with {@link AeroFlightCore#release}. */
    public static void clearShip(ServerSubLevel ship) {
        if (ship != null) CONTROLS.remove(ship.getUniqueId());
    }

    /** Ships already warned about, so the physics tick never spams the log — same pattern as
     * {@link net.bullettrain.xenopixelsmod.vs.XenoThrusterControl}. */
    private final Set<UUID> warnedShips = ConcurrentHashMap.newKeySet();

    private void physicsTick(ServerSubLevel subLevel, RigidBodyHandle handle, double deltaSeconds) {
        double step = Math.max(0.0, Math.min(deltaSeconds, 0.1));
        Vector3dc rawVelocity = handle.getLinearVelocity();
        if (rawVelocity == null) return;
        clampFlightVelocity(handle, rawVelocity, handle.getAngularVelocity());
        Vector3dc velocity = handle.getLinearVelocity();
        if (velocity == null) return;
        double speed = velocity.length();
        // Authority stops growing past AeroConfig.controlAuthoritySpeed. Dynamic pressure scales
        // with speed SQUARED, so without this cap a fast ship gets a wildly sharper roll/pitch
        // rate than a slow one — at 128 blocks/s against an 18 blocks/s cap that is over fifty
        // times the control force, which no amount of rate damping can absorb and which reads
        // in play as the hull spinning up and circling on the lightest input. An arcade-flight
        // property, deliberately not physical: the lift/drag model still uses true speed for
        // everything else.
        double effectiveSpeed = Math.min(speed, AeroConfig.controlAuthoritySpeed);
        // Same q shape AeroAeroModel already uses (AeroAeroModel.java): airDensity * speed^2,
        // no separate area term here — controlSurfaceTorqueScale is this system's own area/gain
        // stand-in, since a per-panel area isn't tracked anywhere.
        double dynamicPressure = AeroConfig.airDensity * effectiveSpeed * effectiveSpeed;
        if (dynamicPressure < 1.0e-6) return;

        for (PanelState state : snapshot) {
            if (Math.abs(state.deflectDeg) < 0.05) continue;
            double magnitude = dynamicPressure * (state.deflectDeg / AeroFlightCore.MAX_DEFLECT_DEG)
                    * AeroConfig.controlSurfaceTorqueScale * step;
            if (!Double.isFinite(magnitude)) continue;
            localImpulse.set(AXIS_UNIT[state.axis.ordinal()]).mul(magnitude);
            localPoint.set(state.x, state.y, state.z);
            if (!sane(subLevel, localImpulse, localPoint)) continue;
            handle.applyImpulseAtPoint(localPoint, localImpulse);
        }
    }

    /**
     * Pull the ship back under {@link XenoServerConfig#maxFlightSpeed}/
     * {@link XenoServerConfig#maxFlightAngularVelocity} before this tick's control-surface
     * impulses add more. Keyboard-mode flight has no steering assist to bleed off excess speed —
     * {@link AeroStabilizerSystem} is deliberately disabled there (see {@code AeroFlightCore.tick}) —
     * so nothing else bounds what holding a key for a long time adds up to; {@code sane()} below
     * only caps a single tick's impulse, not the ship's cumulative velocity.
     *
     * <p>{@link RigidBodyHandle} exposes no absolute velocity setter — confirmed against the real
     * compiled class, only {@link RigidBodyHandle#addLinearAndAngularVelocity} exists alongside
     * the getters — so an over-cap ship is corrected by subtracting exactly its overage rather
     * than being assigned a new value outright.
     */
    private void clampFlightVelocity(RigidBodyHandle handle, Vector3dc linear, Vector3dc angular) {
        double maxSpeed = XenoServerConfig.maxFlightSpeed;
        double speed = linear.length();
        boolean overLinear = maxSpeed > 0.0 && Double.isFinite(speed) && speed > maxSpeed;
        if (overLinear) {
            velocityExcess.set(linear).mul(1.0 - maxSpeed / speed);
        } else {
            velocityExcess.set(0.0, 0.0, 0.0);
        }

        double maxAngular = XenoServerConfig.maxFlightAngularVelocity;
        double angularSpeed = angular != null ? angular.length() : 0.0;
        boolean overAngular = angular != null && maxAngular > 0.0
                && Double.isFinite(angularSpeed) && angularSpeed > maxAngular;
        if (overAngular) {
            angularExcess.set(angular).mul(1.0 - maxAngular / angularSpeed);
        } else {
            angularExcess.set(0.0, 0.0, 0.0);
        }

        if (overLinear || overAngular) {
            handle.addLinearAndAngularVelocity(velocityExcess.negate(), angularExcess.negate());
        }
    }

    /**
     * Refuse a non-finite impulse outright, and clamp an over-large one — the same guard
     * {@code XenoThrusterControl.sane()} applies to thrusters, and for the identical reason:
     * dynamic pressure grows with speed <b>squared</b>, so a fast-moving ship pitching or rolling
     * hard could otherwise hand Sable an unbounded impulse. Rapier integrates a bad impulse
     * straight into the body's velocity — the sub-level does not just move oddly, it can tear
     * itself apart in a way that looks like an explosion. Reuses
     * {@link XenoServerConfig#thrusterMaxImpulse} rather than a second config knob: both systems
     * feed the exact same {@link RigidBodyHandle#applyImpulseAtPoint}, so one "how big can a
     * single impulse from this mod be" ceiling is the more honest knob than two that could drift
     * out of sync.
     */
    private boolean sane(ServerSubLevel subLevel, Vector3d impulse, Vector3d point) {
        if (!XenoServerConfig.thrusterImpulseGuardEnabled) return true;
        if (!Double.isFinite(impulse.x) || !Double.isFinite(impulse.y) || !Double.isFinite(impulse.z)
                || !Double.isFinite(point.x) || !Double.isFinite(point.y) || !Double.isFinite(point.z)) {
            warnOnce(subLevel, "non-finite control-surface impulse {} at {} — skipped", impulse, point);
            return false;
        }
        double max = XenoServerConfig.thrusterMaxImpulse;
        double length = impulse.length();
        if (max > 0.0 && length > max) {
            impulse.mul(max / length);
            warnOnce(subLevel, "control-surface impulse {} exceeded cap {} — clamped", length, max);
        }
        return true;
    }

    private void warnOnce(ServerSubLevel subLevel, String message, Object... args) {
        if (!warnedShips.add(subLevel.getUniqueId())) return;
        XenoPixelsMod.LOGGER.warn("[ship " + VsShipHelper.getShipId(subLevel) + "] " + message, args);
    }

    /** One panel's published state for the physics thread: body-space block-center point (already
     * offset by 0.5), its aerodynamic axis, and its current deflection in degrees. */
    public record PanelState(double x, double y, double z, Direction.Axis axis, double deflectDeg) {
        public static PanelState of(BlockPos pos, Direction.Axis axis, double deflectDeg) {
            return new PanelState(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, axis, deflectDeg);
        }
    }
}
