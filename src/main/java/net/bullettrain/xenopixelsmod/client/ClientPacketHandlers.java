package net.bullettrain.xenopixelsmod.client;

import net.bullettrain.xenopixelsmod.config.XenoServerConfig;

/**
 * Client-side handlers for S2C packets.
 * Lives in client package but is NOT {@code @OnlyIn} so the class exists on dedicated
 * servers (network classes reference it). Methods only run on physical client via DistExecutor.
 */
public final class ClientPacketHandlers {
    public static void handleCombatV3KiVisual(net.bullettrain.xenopixelsmod.network.packet.CombatV3KiVisualPacket packet) {
        net.bullettrain.xenopixelsmod.client.ki.V3NativeKiVisuals.apply(packet);
    }
    private ClientPacketHandlers() {}

    public static void handleCombatV3Camera(net.bullettrain.xenopixelsmod.network.packet.CombatV3CameraPacket packet) {
        net.bullettrain.xenopixelsmod.client.camera.V3TechniqueCamera.apply(packet);
    }

    public static void handleCombatV3Config(net.bullettrain.xenopixelsmod.network.packet.CombatV3ConfigPacket packet) {
        net.bullettrain.xenopixelsmod.client.combat.v3.V3ClientState.apply(packet);
    }

    public static void handleCombatV3State(net.bullettrain.xenopixelsmod.network.packet.CombatV3StatePacket packet) {
        net.bullettrain.xenopixelsmod.client.combat.v3.V3ClientState.apply(packet);
    }

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
    /**
     * Shows a speech bubble over an NPC.
     *
     * <p>Driven by {@code XenoNpcSpeechPacket} rather than local interaction, so every player
     * tracking the entity sees the same line - a client-only bubble would be invisible to everyone
     * but the person who clicked.
     */
    /**
     * Opens a conversation from the dialogue the server sent.
     *
     * <p>This used to look the id up in {@code XenoDialogues}, on the assumption that the client
     * had the same datapack. It does not: datapacks are server data, so on a dedicated server that
     * map is empty and every conversation resolved to null. The server now sends the tree, and a
     * null one here means it had nothing to send - nothing opens, rather than an empty screen that
     * reads as the NPC having nothing to say.
     */
    public static void openNpcDialogue(int entityId,
                                       net.bullettrain.xenopixelsmod.npc.dialog.XenoDialogue dialogue,
                                       String npcName) {
        if (dialogue == null) {
            return;
        }
        if (net.bullettrain.xenopixelsmod.client.config.XenoClientConfig.dialogueBubbles) {
            net.bullettrain.xenopixelsmod.client.npc.dialog.DialogueBubbleScreen.open(
                    entityId, dialogue, npcName);
            return;
        }
        net.bullettrain.xenopixelsmod.client.npc.XenoNpcDialogueScreen.open(
                entityId, dialogue, npcName);
    }

    public static void handleNpcSpeech(int entityId, String text, int durationTicks,
                                       String palette, int shape) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        net.bullettrain.xenopixelsmod.client.npc.speech.SpeechBubbleQueue.show(
                entityId, text, mc.level.getGameTime(), durationTicks, palette,
                net.bullettrain.xenopixelsmod.npc.lines.BubbleShape.byOrdinal(shape));
    }

    public static void handleNpcProfileSaveResult(boolean saved, String reason) {
        net.minecraft.client.Minecraft current = net.minecraft.client.Minecraft.getInstance();
        if (current != null && current.screen instanceof
                net.bullettrain.xenopixelsmod.client.npc.XenoNpcEditorScreen editor
                && editor.receiveStoreWriteResult(saved, reason)) {
            return;
        }
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

    public static void handleXenoNpcEditorSaveResult(int entityId, int expectedRevision, int newRevision,
                                                      boolean saved, String reason) {
        net.minecraft.client.Minecraft minecraft = net.minecraft.client.Minecraft.getInstance();
        if (minecraft.screen instanceof net.bullettrain.xenopixelsmod.client.npc.XenoNpcEditorScreen editor
                && editor.receiveNpcSaveResult(entityId, expectedRevision, newRevision, saved, reason)) {
            return;
        }
        if (!saved && minecraft.gui != null) {
            minecraft.gui.getChat().addMessage(net.minecraft.network.chat.Component.literal(
                    reason == null || reason.isBlank() ? "NPC save rejected" : reason));
        }
    }

    public static void handleQuestCompletionPopup(String title, String description, String reward,
                                                  String palette, String frame) {
        net.minecraft.client.Minecraft.getInstance().setScreen(
                new net.bullettrain.xenopixelsmod.client.npc.quest.QuestCompletionScreen(
                        title, description, reward, palette, frame));
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
