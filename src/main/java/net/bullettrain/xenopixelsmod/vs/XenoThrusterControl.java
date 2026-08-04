package net.bullettrain.xenopixelsmod.vs;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import org.joml.Vector3d;
import org.valkyrienskies.core.api.ships.LoadedServerShip;
import org.valkyrienskies.core.api.ships.PhysShip;
import org.valkyrienskies.core.api.ships.ShipPhysicsListener;
import org.valkyrienskies.core.api.world.PhysLevel;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Per-ship physics attachment that applies thruster forces on the physics thread.
 * Thruster blocks register themselves with model-space positions + force vectors.
 *
 * <p>Hot-path optimized: mutates existing force entries (no alloc every BE tick).
 */
public final class XenoThrusterControl implements ShipPhysicsListener {
    private static final AtomicBoolean REGISTERED = new AtomicBoolean(false);

    /** thruster key → state (map for set/remove; dense snapshot for phys) */
    private final Map<String, ThrusterForce> thrusters = new ConcurrentHashMap<>();
    /** Phys-thread snapshot rebuilt when membership changes — avoids ConcurrentHashMap iteration. */
    private volatile ThrusterForce[] physSnapshot = new ThrusterForce[0];

    /** Call from common setup (or lazily). Safe to call repeatedly. */
    public static void ensureRegistered() {
        if (!REGISTERED.compareAndSet(false, true)) return;
        try {
            var core = VSGameUtilsKt.getVsCore();
            // Runtime-only thruster force map — do not Jackson-serialize on ship save
            var reg = core.newAttachmentRegistrationBuilder(XenoThrusterControl.class);
            reg.useTransientSerializer();
            core.registerAttachment(reg.build());
            try {
                core.registerAttachmentForRemoval(XenoThrusterControl.class.getName());
            } catch (Throwable ignored) {
            }
            XenoPixelsMod.LOGGER.info("Registered transient XenoThrusterControl attachment");
        } catch (Throwable t) {
            REGISTERED.set(false);
            XenoPixelsMod.LOGGER.warn("Could not register thruster attachment: {}", t.toString());
        }
    }

    public static XenoThrusterControl getOrCreate(LoadedServerShip ship) {
        ensureRegistered();
        XenoThrusterControl existing = ship.getAttachment(XenoThrusterControl.class);
        if (existing != null) return existing;
        XenoThrusterControl created = new XenoThrusterControl();
        ship.setAttachment(XenoThrusterControl.class, created);
        return created;
    }

    /**
     * Register / update thruster force. Mutates in place when the key already exists
     * so thruster BEs can call this every few ticks without GC thrash.
     */
    public void setThruster(String key, double posX, double posY, double posZ,
                            double forceX, double forceY, double forceZ, double power) {
        if (power <= 0.001) {
            if (thrusters.remove(key) != null) rebuildSnapshot();
            return;
        }
        ThrusterForce existing = thrusters.get(key);
        if (existing != null) {
            existing.update(posX, posY, posZ, forceX, forceY, forceZ, power);
            return; // membership unchanged — snapshot still valid
        }
        thrusters.put(key, new ThrusterForce(posX, posY, posZ, forceX, forceY, forceZ, power));
        rebuildSnapshot();
    }

    public void removeThruster(String key) {
        if (thrusters.remove(key) != null) rebuildSnapshot();
    }

    /** Drop all thruster forces (flight end / emergency stop). */
    public void clearAll() {
        if (thrusters.isEmpty()) return;
        thrusters.clear();
        physSnapshot = new ThrusterForce[0];
    }

    private void rebuildSnapshot() {
        physSnapshot = thrusters.values().toArray(new ThrusterForce[0]);
    }

    public int thrusterCount() {
        return thrusters.size();
    }

    public boolean isEmpty() {
        return thrusters.isEmpty();
    }

    /** Reused on phys thread to avoid alloc every force. */
    private final Vector3d scratchForce = new Vector3d();
    private final Vector3d scratchPos = new Vector3d();
    private final Vector3d scratchCom = new Vector3d();
    private static final AtomicBoolean MODEL_FORCE_FALLBACK_WARNED = new AtomicBoolean(false);

    @Override
    public void physTick(PhysShip physShip, PhysLevel physLevel) {
        ThrusterForce[] snap = physSnapshot;
        if (snap.length == 0) return;
        try {
            if (physShip.isStatic()) {
                physShip.setStatic(false);
            }
        } catch (Throwable ignored) {
        }

        for (ThrusterForce t : snap) {
            if (t == null) continue;
            // One volatile reference read gives physics a coherent position/vector/power tuple.
            ForceState state = t.state;
            double p = state.power;
            if (p <= 0.0) continue;
            scratchForce.set(state.forceX * p, state.forceY * p, state.forceZ * p);
            scratchPos.set(state.posX + 0.5, state.posY + 0.5, state.posZ + 0.5);
            try {
                physShip.applyModelForce(scratchForce, scratchPos);
            } catch (Throwable modelFailure) {
                try {
                    // Body positions are COM-relative. Preserve the thruster lever arm instead
                    // of silently applying every fallback force through the COM.
                    scratchCom.set(scratchPos).sub(physShip.getCenterOfMass());
                    physShip.applyBodyForce(scratchForce, scratchCom);
                    if (MODEL_FORCE_FALLBACK_WARNED.compareAndSet(false, true)) {
                        XenoPixelsMod.LOGGER.warn(
                                "VS applyModelForce failed; using COM-relative body-force fallback: {}",
                                modelFailure.toString());
                    }
                } catch (Throwable ignored2) {
                    if (MODEL_FORCE_FALLBACK_WARNED.compareAndSet(false, true)) {
                        XenoPixelsMod.LOGGER.warn(
                                "VS thruster force application failed in both model and body space: {}",
                                modelFailure.toString());
                    }
                }
            }
        }
    }

    /** Stable snapshot slot; the complete state is published atomically. */
    private static final class ThrusterForce {
        volatile ForceState state;

        ThrusterForce(double posX, double posY, double posZ,
                      double forceX, double forceY, double forceZ, double power) {
            update(posX, posY, posZ, forceX, forceY, forceZ, power);
        }

        void update(double posX, double posY, double posZ,
                    double forceX, double forceY, double forceZ, double power) {
            this.state = new ForceState(posX, posY, posZ, forceX, forceY, forceZ, power);
        }
    }

    private record ForceState(double posX, double posY, double posZ,
                              double forceX, double forceY, double forceZ,
                              double power) {
    }
}
