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
    }

    public static void handleXenoStats(float health, float maxHealth, float ki, float maxKi,
                                      float stamina, float maxStamina) {
        XenoClientData.update(health, maxHealth, ki, maxKi, stamina, maxStamina);
    }
}
