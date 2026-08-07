package net.bullettrain.xenopixelsmod.client.combat;

import net.neoforged.fml.common.EventBusSubscriber;

import com.dragonminez.client.util.KeyBinds;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.StatsProvider;
import com.dragonminez.common.stats.character.Resources;
import com.dragonminez.common.stats.character.Status;
import com.dragonminez.common.stats.techniques.Techniques;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.client.config.XenoClientConfig;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.settings.KeyModifier;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;

/**
 * Helps KI technique slot usability when our tech HUD replaces DMZ's.
 * <p>
 * DMZ still owns cast input ({@code ClientStatsEvents.handleTechniqueSlotInput}).
 * This only:
 * <ul>
 *   <li>Surfaces silent DMZ activation failures (empty hand, low power release)</li>
 *   <li>Reports when a charge session is still active so other slots look "dead"</li>
 * </ul>
 * Does <b>not</b> send cast packets — that remains DMZ's job.
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID, value = Dist.CLIENT)
public final class TechniqueSlotAssist {
    private static final int SLOT_COUNT = 8;
    private static final long FEEDBACK_COOLDOWN_MS = 900L;

    private static final boolean[] wasChordDown = new boolean[SLOT_COUNT];
    private static long lastFeedbackMs;

    private TechniqueSlotAssist() {}

    /**
     * True when Alt/Ctrl technique-bar modifiers are held (same rules as DMZ HUD).
     * BT3 charge should not start during this window.
     */
    public static boolean isTechniqueBarModifierHeld() {
        KeyMapping[] keys = KeyBinds.TECHNIQUE_SLOTS;
        if (keys == null || keys.length < SLOT_COUNT) return false;
        KeyModifier modAlt = keys[0].getKeyModifier();
        KeyModifier modCtrl = keys[4].getKeyModifier();
        return KeyBinds.isBarModifierActive(modAlt) || KeyBinds.isBarModifierActive(modCtrl);
    }

    /** True when DMZ is mid technique charge (client stats). */
    public static boolean isDmzTechniqueCharging(LocalPlayer player) {
        if (player == null) return false;
        try {
            var opt = StatsProvider.get(StatsCapability.INSTANCE, player);
            if (!opt.isPresent()) return false;
            StatsData data = opt.orElse(null);
            if (data == null || !data.isDataLoaded()) return false;
            Techniques tech = data.getTechniques();
            if (tech == null) return false;
            return tech.isTechniqueCharging() || tech.isTechniqueChargeActive();
        } catch (Throwable t) {
            return false;
        }
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!XenoClientConfig.techniqueHotbarEnabled) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.screen != null) {
            clearEdgeState();
            return;
        }

        KeyMapping[] keys = KeyBinds.TECHNIQUE_SLOTS;
        if (keys == null || keys.length < SLOT_COUNT) return;

        LocalPlayer player = mc.player;
        int pressedMask = 0;
        for (int i = 0; i < SLOT_COUNT; i++) {
            boolean down = KeyBinds.isChordDown(keys[i]);
            if (down && !wasChordDown[i]) pressedMask |= 1 << i;
            wasChordDown[i] = down;
        }
        // Capability traversal is only useful on a new technique-slot key edge.
        if (pressedMask == 0 || !isTechniqueBarModifierHeld()) return;

        StatsData data = null;
        try {
            var opt = StatsProvider.get(StatsCapability.INSTANCE, player);
            if (opt.isPresent()) data = opt.orElse(null);
        } catch (Throwable ignored) {
            return;
        }
        if (data == null || !data.isDataLoaded()) return;
        Status status = data.getStatus();
        if (status == null || !status.isHasCreatedCharacter()) return;

        Techniques techniques = data.getTechniques();
        if (techniques == null) return;

        boolean charging = techniques.isTechniqueCharging() || techniques.isTechniqueChargeActive();
        String[] equipped = techniques.getEquippedSlots();

        for (int i = 0; i < SLOT_COUNT; i++) {
            if ((pressedMask & (1 << i)) == 0) continue;

            String id = slotId(equipped, i);
            if (id == null || id.isEmpty()) {
                feedback(player, Component.literal("Empty technique slot " + (i + 1))
                        .withStyle(ChatFormatting.GRAY));
                continue;
            }

            if (charging) {
                feedback(player, Component.literal(
                                "Finish or release the current KI charge (hold slot key, then release) before switching")
                        .withStyle(ChatFormatting.YELLOW));
                continue;
            }

            // Mirror DMZ canActivateTechnique silent fails (messages only; DMZ still handles cast)
            if (player.isSpectator()) continue;
            if (status.isFused() && !status.isFusionLeader()) {
                feedback(player, Component.literal("Fusion follower cannot use techniques")
                        .withStyle(ChatFormatting.RED));
                continue;
            }

            int kiCtrl = 0;
            try {
                kiCtrl = data.getSkills() != null ? data.getSkills().getSkillLevel("kicontrol") : 0;
            } catch (Throwable ignored) {
            }
            if (kiCtrl <= 0) {
                // DMZ already shows its own message for this path
                continue;
            }

            Resources res = data.getResources();
            int power = res != null ? res.getPowerRelease() : 0;
            if (power < 5) {
                feedback(player, Component.literal(
                                "Raise Power Release (≥5%) to use KI techniques — currently " + power + "%")
                        .withStyle(ChatFormatting.YELLOW));
                continue;
            }

            if (!player.getMainHandItem().isEmpty()) {
                feedback(player, Component.literal(
                                "Empty your main hand to use KI techniques (DMZ requires fist free)")
                        .withStyle(ChatFormatting.GOLD));
            }
            // If all gates pass, DMZ ClientStatsEvents will cast — no extra message
        }
    }

    private static String slotId(String[] equipped, int idx) {
        if (equipped == null || idx < 0 || idx >= equipped.length) return "";
        String s = equipped[idx];
        return s == null ? "" : s;
    }

    private static void feedback(LocalPlayer player, Component msg) {
        long now = System.currentTimeMillis();
        if (now - lastFeedbackMs < FEEDBACK_COOLDOWN_MS) return;
        lastFeedbackMs = now;
        player.displayClientMessage(msg, true);
    }

    private static void clearEdgeState() {
        for (int i = 0; i < wasChordDown.length; i++) {
            wasChordDown[i] = false;
        }
    }
}
