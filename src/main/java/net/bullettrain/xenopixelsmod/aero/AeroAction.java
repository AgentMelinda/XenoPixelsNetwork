package net.bullettrain.xenopixelsmod.aero;

/**
 * A single controller intent.
 *
 * <p>Actions are the only way state changes reach the flight stack. The GUI, the physical
 * panel on the block model, and peripherals all produce these and hand them to
 * {@link AeroActionDispatcher}, which is the one place that validates them. Nothing here is
 * trusted: values arriving from a client are clamped by the dispatcher, never by the sender.
 */
public sealed interface AeroAction {

    /** Switch the host block between missile guidance and flight control. */
    record SetMode(ControllerMode mode) implements AeroAction {}

    /** Commanded throttle, 0..1. Clamped by the dispatcher. */
    record SetThrottle(double throttle) implements AeroAction {}

    /**
     * Commanded attitude in degrees, plus raw pitch/roll/yaw stick position (-1..1) and whether
     * the pilot is in mouse-aim mode. Clamped by the dispatcher.
     *
     * <p>{@code pitchStick}/{@code rollStick}/{@code yawStick}/{@code mouseAim} only mean
     * anything for a seat's own per-tick push ({@code XenoPilotSeatEntity.applyInput}) — see
     * {@link net.bullettrain.xenopixelsmod.aero.control.AeroFlightCore#tick}: in keyboard mode
     * the stabilizer stops driving the ship and the sticks deflect PITCH/ROLL/YAW panels directly
     * instead, while mouse-aim mode keeps the previous attitude-hold behavior unchanged. Every
     * other source of an attitude command (the GUI, ComputerCraft, the physical panel, autopilot)
     * has no stick concept at all, so the 3-arg constructor below defaults to the old always-PD
     * behavior for them.
     */
    record SetAttitude(double yawDeg, double pitchDeg, double rollDeg,
                       double pitchStick, double rollStick, double yawStick,
                       boolean mouseAim) implements AeroAction {
        public SetAttitude(double yawDeg, double pitchDeg, double rollDeg) {
            this(yawDeg, pitchDeg, rollDeg, 0.0, 0.0, 0.0, true);
        }
    }

    /** Commanded flap extension, 0..1. Clamped by the dispatcher. */
    record SetFlap(double level) implements AeroAction {}

    /** Toggle automatic flap (auto-extend on approach/slow flight, retract on speed). */
    record ToggleAutoFlap() implements AeroAction {}

    /** Air brake held/released. Zeroes effective throttle while engaged and drives BRAKE-role panels. */
    record SetAirBrake(boolean engaged) implements AeroAction {}

    /** Select manual, direct-target, or waypoint-route flight guidance. */
    record SetAutopilot(AeroAutopilotMode mode) implements AeroAction {}

    /** Enable or disable an optional subsystem. */
    record ToggleSubsystem(AeroSubsystem subsystem, boolean enabled) implements AeroAction {}

    /** Link-management operation against the paired-device set. */
    record Link(Op op) implements AeroAction {
        public enum Op {
            /** Pair every compatible engine within the controller's search radius. */
            PAIR_NEARBY,
            /** Drop every pairing. */
            CLEAR_ALL,
            /** Re-check health of existing links without changing membership. */
            REFRESH
        }
    }

    /**
     * Immediate stop: zero throttle, release thrust authority, disengage flight.
     * Always accepted regardless of power state — an unpowered controller must still be
     * able to stop the ship.
     */
    record EmergencyStop() implements AeroAction {}
}
