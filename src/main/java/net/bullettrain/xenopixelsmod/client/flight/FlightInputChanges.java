package net.bullettrain.xenopixelsmod.client.flight;

/** Tracks every effective field, including zero crossings that must stop keyboard torque. */
final class FlightInputChanges {
    record Frame(int seat, double throttle, double flap, double yaw, double pitch, double roll,
                 double stickPitch, double stickRoll, double stickYaw,
                 boolean mouseAim, boolean airBrake, boolean autoLevel) {
        boolean differs(Frame previous) {
            return previous == null || seat != previous.seat
                    || axisChanged(throttle, previous.throttle) || axisChanged(flap, previous.flap)
                    || Math.abs(wrap(yaw - previous.yaw)) > 0.5
                    || Math.abs(pitch - previous.pitch) > 0.5
                    || Math.abs(wrap(roll - previous.roll)) > 0.5
                    || axisChanged(stickPitch, previous.stickPitch)
                    || axisChanged(stickRoll, previous.stickRoll)
                    || axisChanged(stickYaw, previous.stickYaw)
                    || mouseAim != previous.mouseAim || airBrake != previous.airBrake
                    || autoLevel != previous.autoLevel;
        }
    }

    private FlightInputChanges() {}

    private static boolean axisChanged(double value, double previous) {
        return Math.abs(value - previous) > 0.01 || (value == 0) != (previous == 0);
    }

    private static double wrap(double value) {
        return value - Math.floor((value + 180.0) / 360.0) * 360.0;
    }
}
