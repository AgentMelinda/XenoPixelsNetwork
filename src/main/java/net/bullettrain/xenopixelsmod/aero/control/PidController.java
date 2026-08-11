package net.bullettrain.xenopixelsmod.aero.control;

/**
 * Minimal PID with output clamping and integral anti-windup.
 *
 * <p>One instance per controlled axis. Not thread-safe: each is owned by a single sub-level's
 * stabilizer and only touched from Sable's physics tick.
 *
 * <p>The integral term is clamped separately from the output. Without that, a saturated
 * actuator lets the integral grow without bound while the error persists, and the controller
 * then overshoots badly when authority returns — very visible on a ship whose thrusters were
 * power-starved and then recovered.
 */
public final class PidController {
    private final double kp;
    private final double ki;
    private final double kd;
    private final double outputLimit;
    private final double integralLimit;

    private double integral;
    private double lastError;
    private boolean primed;

    public PidController(double kp, double ki, double kd, double outputLimit, double integralLimit) {
        this.kp = kp;
        this.ki = ki;
        this.kd = kd;
        this.outputLimit = Math.abs(outputLimit);
        this.integralLimit = Math.abs(integralLimit);
    }

    /** Damping-only controller: proportional on the error with no integral term. */
    public static PidController damping(double kp, double kd, double outputLimit) {
        return new PidController(kp, 0.0, kd, outputLimit, 0.0);
    }

    /**
     * @param error       setpoint minus measurement
     * @param deltaSeconds physics step; clamped internally against pathological values
     * @return clamped control output
     */
    public double step(double error, double deltaSeconds) {
        if (!Double.isFinite(error)) return 0.0;
        double dt = Math.max(1.0e-4, Math.min(deltaSeconds, 0.1));

        double derivative = 0.0;
        if (primed) {
            derivative = (error - lastError) / dt;
        }
        lastError = error;
        primed = true;

        if (ki != 0.0) {
            integral += error * dt;
            if (integralLimit > 0.0) {
                integral = Math.max(-integralLimit, Math.min(integralLimit, integral));
            }
        }

        double output = kp * error + ki * integral + kd * derivative;
        if (!Double.isFinite(output)) {
            reset();
            return 0.0;
        }
        return outputLimit > 0.0
                ? Math.max(-outputLimit, Math.min(outputLimit, output))
                : output;
    }

    /** True when the last output hit the clamp — surfaced in diagnostics. */
    public boolean saturated() {
        return outputLimit > 0.0 && Math.abs(kp * lastError) >= outputLimit;
    }

    public double lastError() {
        return lastError;
    }

    public void reset() {
        integral = 0.0;
        lastError = 0.0;
        primed = false;
    }
}
