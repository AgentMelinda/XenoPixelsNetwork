package net.bullettrain.xenopixelsmod.client.combat;

/** Client mirror used to pause DMZ's competing flight movement only during Xeno chase. */
public final class ClientChaseFlightState {
    private static volatile boolean active;
    private static volatile boolean pending;

    public static void request() {
        pending = true;
    }

    public static boolean isPending() {
        return pending;
    }

    public static void reset() {
        active = false;
        pending = false;
    }

    private ClientChaseFlightState() {
    }

    public static boolean isActive() {
        return active;
    }

    public static void setActive(boolean value) {
        active = value;
        pending = false;
    }
}
