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

    /** Commanded attitude in degrees. Clamped by the dispatcher. */
    record SetAttitude(double yawDeg, double pitchDeg, double rollDeg) implements AeroAction {}

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
