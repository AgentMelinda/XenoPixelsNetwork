package net.bullettrain.xenopixelsmod.client;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
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
