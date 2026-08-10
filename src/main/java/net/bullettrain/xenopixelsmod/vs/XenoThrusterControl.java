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

/** Runtime thruster controller for Sable moving sub-levels. */
public final class XenoThrusterControl {
    private static final AtomicBoolean REGISTERED = new AtomicBoolean();
    private static final Map<UUID, XenoThrusterControl> CONTROLS = new ConcurrentHashMap<>();

    private final Map<String, ThrusterForce> thrusters = new ConcurrentHashMap<>();
    private volatile ThrusterForce[] physicsSnapshot = new ThrusterForce[0];
    private final Vector3d localImpulse = new Vector3d();
    private final Vector3d localPoint = new Vector3d();

    public static void ensureRegistered() {
        if (!REGISTERED.compareAndSet(false, true)) return;
        SableEventPlatform.INSTANCE.onPhysicsTick((system, deltaSeconds) -> {
            for (var candidate : SubLevelContainer.getContainer(system.getLevel()).getAllSubLevels()) {
                if (!(candidate instanceof ServerSubLevel subLevel)) continue;
                XenoThrusterControl control = get(subLevel);
                if (control == null || control.isEmpty()) continue;
                RigidBodyHandle handle = system.getPhysicsHandle(subLevel);
                if (handle != null && handle.isValid()) control.physicsTick(subLevel, handle, deltaSeconds);
            }
            // No map cleanup here: this callback fires once per DIMENSION's physics system, so
            // a removeIf against the currently ticking dimension's container wiped controls for
            // ships in every OTHER dimension, making thrust flicker as the game thread raced to
            // re-register it. Entries are tiny, re-created by the thruster BEs, and emptied by
            // clearAll().
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
        ThrusterForce force = thrusters.get(key);
        if (force != null) {
            force.state = new ForceState(posX, posY, posZ, forceX, forceY, forceZ, power);
            return;
        }
        thrusters.put(key, new ThrusterForce(new ForceState(posX, posY, posZ, forceX, forceY, forceZ, power)));
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
            // Argument order is (position, force) — these were passed the other way round.
            handle.applyImpulseAtPoint(localPoint, localImpulse);
        }
    }

    private static final class ThrusterForce {
        private volatile ForceState state;
        private ThrusterForce(ForceState state) { this.state = state; }
    }

    private record ForceState(double posX, double posY, double posZ,
                              double forceX, double forceY, double forceZ, double power) {}
}
