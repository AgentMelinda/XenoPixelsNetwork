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

    /**
     * Sink for {@code NpcProfileSaveResultPacket}. A rejected wand save is otherwise invisible:
     * the editor closes on Apply regardless, so the only signal the player gets is this notice.
     */
    public static void handleNpcProfileSaveResult(boolean saved, String reason) {
        NpcProfileSaveClientState.accept(saved, reason);
        if (saved || !NpcProfileSaveClientState.consumeNotice()) {
            return;
        }
        net.minecraft.client.Minecraft minecraft = net.minecraft.client.Minecraft.getInstance();
        if (minecraft != null && minecraft.gui != null) {
            String text = reason == null || reason.isEmpty() ? "NPC save rejected" : reason;
            minecraft.gui.getChat().addMessage(net.minecraft.network.chat.Component.literal(text));
        }
    }

    public static void openDmzTrainer(int entityId, String name,
                                      net.bullettrain.xenopixelsmod.dmz.form.DmzTrainerMenu menu) {
        var resolved = menu == null
                ? net.bullettrain.xenopixelsmod.dmz.form.DmzTrainerMenu.EMPTY : menu;
        String locale = locale();
        net.minecraft.client.Minecraft.getInstance().setScreen(
                new net.bullettrain.xenopixelsmod.client.screen.DmzFormTrainerScreen(entityId, name,
                        resolved.resolveTitle(locale, name), resolved.resolveBody(locale),
                        resolved.entries()));
    }

    /** The player's selected language; the client is the only side that knows it. */
    private static String locale() {
        net.minecraft.client.Minecraft minecraft = net.minecraft.client.Minecraft.getInstance();
        return minecraft == null || minecraft.getLanguageManager() == null
                ? "en_us" : minecraft.getLanguageManager().getSelected();
    }
}
