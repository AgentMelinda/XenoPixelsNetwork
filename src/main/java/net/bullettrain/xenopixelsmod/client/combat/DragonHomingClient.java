package net.bullettrain.xenopixelsmod.client.combat;

/**
 * Local ~2s arm after releasing a charged kick so a single W press can request
 * dragon homing. Server still owns the victim window.
 */
public final class DragonHomingClient {
    private static long untilMs;

    private DragonHomingClient() {}

    public static void open() {
        untilMs = System.currentTimeMillis() + 2000L;
    }

    public static boolean isLive() {
        return System.currentTimeMillis() < untilMs;
    }

    public static void close() {
        untilMs = 0L;
    }
}
