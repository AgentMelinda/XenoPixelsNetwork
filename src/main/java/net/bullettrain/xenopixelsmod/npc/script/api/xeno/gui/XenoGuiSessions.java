package net.bullettrain.xenopixelsmod.npc.script.api.xeno.gui;

import net.minecraft.server.level.ServerPlayer;
import java.util.UUID;

/**
 * Per-player scripted GUI session admission.
 *
 * <p>Minimal compile/runtime stub so combat work can build while the full native GUI
 * session model is finished. Inputs are validated for shape then discarded until a real
 * owner-bound session map lands.
 */
public final class XenoGuiSessions {
    private XenoGuiSessions() {}

    public static void input(ServerPlayer player, UUID session, int revision, long sequence,
                             UUID component, String action, String value, int width, int height) {
        if (player == null || session == null || component == null || action == null || value == null) return;
        if (revision < 1 || sequence < 1 || !XenoGuiWire.ACTIONS.contains(action)) return;
        if (value.length() > XenoGuiWire.MAX_TEXT || width < 0 || width > 8192 || height < 0 || height > 8192) return;
        // Full session lookup/callback dispatch is not implemented in this stub.
    }
}
