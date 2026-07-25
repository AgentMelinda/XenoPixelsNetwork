package net.bullettrain.xenopixelsmod.client;

/**
 * Client mirror of server DMZ HUD toggle.
 * Not {@code @OnlyIn}: referenced from common network packets; stripping it crashes dedicated servers.
 */
public final class DmzHudClientState {
    /** Mirrors server-wide default; false = hide DMZ HUD for this client. */
    private static boolean dmzHudEnabled = false;

    private DmzHudClientState() {}

    public static boolean isDmzHudEnabled() {
        return dmzHudEnabled;
    }

    public static void setDmzHudEnabled(boolean enabled) {
        dmzHudEnabled = enabled;
    }
}
