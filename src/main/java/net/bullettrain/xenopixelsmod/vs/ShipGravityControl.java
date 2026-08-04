package net.bullettrain.xenopixelsmod.vs;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import org.joml.Vector3d;
import org.valkyrienskies.core.api.ships.LoadedServerShip;
import org.valkyrienskies.core.api.ships.PhysShip;
import org.valkyrienskies.core.api.ships.ShipPhysicsListener;
import org.valkyrienskies.core.api.world.PhysLevel;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

import java.util.concurrent.atomic.AtomicBoolean;

/** Persistent, per-ship gravity override. Positive values pull downward. */
public final class ShipGravityControl implements ShipPhysicsListener {
    public static final double NORMAL_GRAVITY = 10.0;
    private static final AtomicBoolean REGISTERED = new AtomicBoolean(false);

    private double gravitySi = NORMAL_GRAVITY;
    /** Runtime handoff to controllers which already compensate gravity themselves. */
    private transient boolean suppressed;
    private final transient Vector3d force = new Vector3d();

    public ShipGravityControl() { }

    public static void ensureRegistered() {
        if (!REGISTERED.compareAndSet(false, true)) return;
        try {
            var core = VSGameUtilsKt.getVsCore();
            core.registerAttachment(core.newAttachmentRegistrationBuilder(ShipGravityControl.class).build());
            XenoPixelsMod.LOGGER.info("Registered persistent ShipGravityControl attachment");
        } catch (Throwable t) {
            REGISTERED.set(false);
            XenoPixelsMod.LOGGER.warn("Could not register ship gravity attachment: {}", t.toString());
        }
    }

    public static ShipGravityControl getOrCreate(LoadedServerShip ship) {
        ensureRegistered();
        ShipGravityControl control = ship.getAttachment(ShipGravityControl.class);
        if (control != null) return control;
        control = new ShipGravityControl();
        ship.setAttachment(ShipGravityControl.class, control);
        return control;
    }

    public double getGravitySi() { return gravitySi; }

    public void setGravitySi(double gravitySi) {
        this.gravitySi = Math.max(-100.0, Math.min(100.0,
                Double.isFinite(gravitySi) ? gravitySi : NORMAL_GRAVITY));
    }

    public boolean isSuppressed() { return suppressed; }

    public void setSuppressed(boolean suppressed) { this.suppressed = suppressed; }

    @Override
    public void physTick(PhysShip ship, PhysLevel level) {
        if (suppressed) return;
        double delta = NORMAL_GRAVITY - gravitySi;
        if (Math.abs(delta) < 1.0e-6) return;
        double mass;
        try { mass = Math.max(1.0, ship.getMass()); }
        catch (Throwable ignored) { return; }
        force.set(0.0, mass * delta, 0.0);
        try { ship.applyInvariantForce(force); }
        catch (Throwable ignored) { }
    }
}
