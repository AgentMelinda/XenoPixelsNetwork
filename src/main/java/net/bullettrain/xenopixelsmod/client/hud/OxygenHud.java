package net.bullettrain.xenopixelsmod.client.hud;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.space.oxygen.OxygenRules;
import net.bullettrain.xenopixelsmod.space.oxygen.OxygenStatus;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

/**
 * Design-doc §6 oxygen HUD: a ring of segments around the crosshair that drains with breath (cyan while holding, red
 * while suffocating), the capsule supply as minutes:seconds under it, and a red edge vignette that pulses while
 * suffocating. Hidden in breathable air with full lungs.
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID, value = Dist.CLIENT)
public final class OxygenHud {
    private static final int SEGMENTS = 32, RADIUS = 14;
    private static volatile OxygenStatus status;

    private OxygenHud() {}

    public static void apply(OxygenStatus packet) { status = packet; }

    public static void render(GuiGraphics g, DeltaTracker delta) {
        OxygenStatus s = status;
        Minecraft mc = Minecraft.getInstance();
        if (s == null || mc.player == null || mc.options.hideGui || s.phaseValue() == OxygenRules.Phase.SAFE) return;
        int cx = g.guiWidth() / 2, cy = g.guiHeight() / 2;
        OxygenRules.Phase phase = s.phaseValue();
        double fraction = Math.max(0.0, Math.min(1.0, s.breath() / (double) s.maxBreath()));
        int filled = (int) Math.round(fraction * SEGMENTS);
        int on = switch (phase) {
            case SUFFOCATING -> 0xFFE04848;
            case SUPPLIED -> 0xFF7CD6FF;
            case RECOVERING -> 0xFF9FE6B0;
            case AURA -> 0xFFE18BFF;
            default -> 0xFF4FC3F7;
        };
        for (int i = 0; i < SEGMENTS; i++) {
            double angle = -Math.PI / 2 + i * (Math.PI * 2 / SEGMENTS);
            int x = cx + (int) Math.round(Math.cos(angle) * RADIUS), y = cy + (int) Math.round(Math.sin(angle) * RADIUS);
            g.fill(x - 1, y - 1, x + 1, y + 1, i < filled ? on : 0x80202630);
        }
        if (phase == OxygenRules.Phase.AURA) {
            g.drawCenteredString(mc.font, Component.translatable("hud.xenopixelsmod.oxygen_aura"), cx, cy + RADIUS + 6, 0xFFE18BFF);
        } else if (s.reserve() > 0) {
            int seconds = s.reserve() / 20;
            String text = Component.translatable("hud.xenopixelsmod.oxygen").getString() + " " + (seconds / 60) + ":" + String.format("%02d", seconds % 60);
            g.drawCenteredString(mc.font, text, cx, cy + RADIUS + 6, 0xFFBFEAFF);
        }
        if (phase == OxygenRules.Phase.SUFFOCATING) {
            float pulse = (float) (0.5 + 0.5 * Math.sin(System.currentTimeMillis() / 180.0));
            int alpha = (int) (40 + 60 * pulse) << 24;
            int w = g.guiWidth(), h = g.guiHeight(), edge = Math.max(8, Math.min(w, h) / 12);
            g.fill(0, 0, w, edge, alpha | 0x8A0000);
            g.fill(0, h - edge, w, h, alpha | 0x8A0000);
            g.fill(0, edge, edge, h - edge, alpha | 0x8A0000);
            g.fill(w - edge, edge, w, h - edge, alpha | 0x8A0000);
        }
    }

    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { status = null; }
}
