package net.bullettrain.xenopixelsmod.compat.npc;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;

/**
 * Runtime gate for My NPCs / CustomNPCs player scripts. The GUI Enabled flag is
 * not this gate: {@code runScript} reads {@code isEnabled()} on a per-player copy.
 */
public final class PlayerScriptsGate {
    private static volatile boolean serverStarted;
    private static boolean skipLogged;

    private PlayerScriptsGate() {}

    public static void markServerStarted() {
        serverStarted = true;
        skipLogged = false;
    }

    public static boolean serverStarted() {
        return serverStarted;
    }

    /**
     * Player scripts may run when this copy or the global handler is enabled, the
     * player is not a client-side copy, and either the NPC mod {@code HasStart} is set
     * or the dedicated / integrated server has already started.
     */
    public static boolean allow(boolean hasStart, boolean thisEnabled, boolean globalEnabled,
                                boolean clientPlayer) {
        if (clientPlayer) {
            return false;
        }
        if (!hasStart && !serverStarted) {
            return false;
        }
        return thisEnabled || globalEnabled;
    }

    public static void logSkipOnce(boolean hasStart, boolean thisEnabled, boolean globalEnabled,
                                   int scriptCount) {
        if (skipLogged) {
            return;
        }
        skipLogged = true;
        XenoPixelsMod.LOGGER.info(
                "NPC player script skipped: HasStart={} serverStarted={} enabled={} globalEnabled={} scripts={}",
                hasStart, serverStarted, thisEnabled, globalEnabled, scriptCount);
    }
}
