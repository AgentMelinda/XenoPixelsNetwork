package net.bullettrain.xenopixelsmod.aero.seat;

import net.minecraft.util.Mth;

/**
 * The pilot's last commanded control positions, held server-side for one seat.
 *
 * <p>Everything here is what the client <i>asked</i> for, already clamped. It is not authority:
 * the values are handed to {@link net.bullettrain.xenopixelsmod.aero.AeroActionDispatcher},
 * which independently enforces flight mode, engagement and power tier and clamps again, and the
 * bus clamps a third time.
 *
 * <p>The staleness rule matters more than it looks. A pilot who crashes, times out or is
 * teleported away stops sending packets while the last throttle command is still latched on the
 * bus, and a ship at full thrust with nobody flying it does not stop on its own. So input that
 * has not been refreshed within {@link #STALE_TICKS} is treated as released rather than held.
 */
public final class AeroSeatInput {

    /** Roughly half a second at 20 tps: long enough to ride out a hiccup, short enough to be safe. */
    public static final int STALE_TICKS = 10;

    private double throttle;
    private double yawDeg;
    private double pitchDeg;
    private double rollDeg;
    private double pitchStick;
    private double rollStick;
    private double yawStick;
    private boolean mouseAim = false;
    private double flap;
    private boolean airBrake;
    private boolean autoLevel;
    private boolean flapCommanded;
    private int ticksSinceUpdate = STALE_TICKS;

    public double throttle() {
        return throttle;
    }

    public double yawDeg() {
        return yawDeg;
    }

    public double pitchDeg() {
        return pitchDeg;
    }

    public double rollDeg() {
        return rollDeg;
    }

    public double pitchStick() {
        return pitchStick;
    }

    public double rollStick() {
        return rollStick;
    }

    public double yawStick() {
        return yawStick;
    }

    public boolean mouseAim() {
        return mouseAim;
    }

    public double flap() {
        return flap;
    }

    public boolean airBrake() {
        return airBrake;
    }

    public boolean autoLevel() {
        return autoLevel;
    }

    /** True only while the pilot is actively moving the flap control. */
    public boolean flapCommanded() {
        return flapCommanded;
    }

    /** True once the pilot has stopped sending; the seat then commands idle. */
    public boolean isStale() {
        return ticksSinceUpdate >= STALE_TICKS;
    }

    /** Accept a validated control frame. Non-finite values are refused, not clamped to zero. */
    public boolean accept(double throttle, double yawDeg, double pitchDeg, double rollDeg,
                          double pitchStick, double rollStick, double yawStick, boolean mouseAim,
                          double flap, boolean airBrake, boolean autoLevel, boolean flapCommanded) {
        if (!Double.isFinite(throttle) || !Double.isFinite(yawDeg) || !Double.isFinite(pitchDeg)
                || !Double.isFinite(rollDeg) || !Double.isFinite(pitchStick)
                || !Double.isFinite(rollStick) || !Double.isFinite(yawStick) || !Double.isFinite(flap)) {
            return false;
        }
        this.throttle = Mth.clamp(throttle, 0.0, 1.0);
        this.yawDeg = Mth.wrapDegrees(yawDeg);
        this.pitchDeg = Mth.clamp(pitchDeg, -89.0, 89.0);
        this.rollDeg = Mth.wrapDegrees(rollDeg);
        this.pitchStick = Mth.clamp(pitchStick, -1.0, 1.0);
        this.rollStick = Mth.clamp(rollStick, -1.0, 1.0);
        this.yawStick = Mth.clamp(yawStick, -1.0, 1.0);
        this.mouseAim = mouseAim;
        this.flap = Mth.clamp(flap, 0.0, 1.0);
        this.airBrake = airBrake;
        this.autoLevel = autoLevel;
        this.flapCommanded = flapCommanded;
        this.ticksSinceUpdate = 0;
        return true;
    }

    /** Called once per server tick by the seat. */
    public void tick() {
        if (ticksSinceUpdate < STALE_TICKS) ticksSinceUpdate++;
    }

    /** Drop everything the pilot was holding — dismount, death, logout. */
    public void release() {
        throttle = 0.0;
        rollDeg = 0.0;
        pitchStick = 0.0;
        rollStick = 0.0;
        yawStick = 0.0;
        mouseAim = false;
        flap = 0.0;
        airBrake = false;
        autoLevel = false;
        flapCommanded = false;
        ticksSinceUpdate = STALE_TICKS;
    }
}
