package net.bullettrain.xenopixelsmod.client;

import net.bullettrain.xenopixelsmod.config.XenoServerConfig;

/**
 * Client-side handlers for S2C packets.
 * Lives in client package but is NOT {@code @OnlyIn} so the class exists on dedicated
 * servers (network classes reference it). Methods only run on physical client via DistExecutor.
 */
public final class ClientPacketHandlers {
    private ClientPacketHandlers() {}

    public static void handleDmzHudState(boolean dmzHudEnabled) {
        DmzHudClientState.setDmzHudEnabled(dmzHudEnabled);
    }

    public static void handleServerConfig(XenoServerConfig.Data data) {
        XenoServerClientState.apply(data);
        // Keep form scales available for the common StatsData mixin (client-side prediction)
        if (data != null) {
            // Re-apply full form scale maps (apply() on server Data may not run on client)
            XenoServerConfig.applyFormScaleMaps(data);
            // The charge-cap mixin reads these statics on both sides when TechniqueChargeSyncS2C
            // writes the percent. Leave them stale and a 1000% charge is clamped back to 200
            // on the client HUD even though the server accepted it.
            XenoServerConfig.applySyncedKiCombat(data);
        }
    }

    public static void handleXenoStats(float health, float maxHealth, float ki, float maxKi,
                                      float stamina, float maxStamina) {
        XenoClientData.update(health, maxHealth, ki, maxKi, stamina, maxStamina);
    }
}
