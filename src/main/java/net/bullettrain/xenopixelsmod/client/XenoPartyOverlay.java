package net.bullettrain.xenopixelsmod.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.bullettrain.xenopixelsmod.client.config.XenoClientConfig;
import net.bullettrain.xenopixelsmod.client.hud.AnimUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.scores.PlayerTeam;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Xenoverse-2-style "party" strip: compact HP/KI chips for nearby teammates
 * (same vanilla scoreboard team, per user-directed scope decision — no new
 * server-side party system was authored). Uses {@link DmzClientStats#read}
 * (widened to accept any {@link Player}) with graceful degradation: vanilla
 * HP is always shown (guaranteed synced for every entity); KI/Stamina only
 * render when DMZ data has actually synced for that player.
 *
 * <p>Purely additive — does not touch the main {@link XenoHudOverlay} or
 * {@link XenoTechniqueHotbarOverlay}. Gated by
 * {@link XenoClientConfig#partyHudEnabled}.</p>
 */
@OnlyIn(Dist.CLIENT)
public class XenoPartyOverlay {
    private static final int MAX_MEMBERS = 4;
    private static final int CHIP_W = 150;
    private static final int CHIP_H = 30;
    private static final int CHIP_GAP = 5;
    private static final int PORTRAIT = 24;
    private static final int MARGIN_X = 10;
    private static final int MARGIN_TOP = 10;

    /** Per-player fade progress (0..1), keyed by UUID string, so chips ease in/out of range/team. */
    private static final Map<UUID, Float> FADE = new HashMap<>();
    private static final List<Player> CACHED_MEMBERS = new ArrayList<>(MAX_MEMBERS);
    private static long lastMemberScan = Long.MIN_VALUE;
    private static Object cachedLevel;

    public void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.options.hideGui) return;
        if (!XenoClientConfig.partyHudEnabled || !XenoClientConfig.xenoHudEnabled) return;

        Player self = mc.player;
        PlayerTeam team = self.getTeam() instanceof PlayerTeam pt ? pt : null;

        long gameTime = mc.level.getGameTime();
        if (cachedLevel != mc.level || gameTime - lastMemberScan >= 10 || gameTime < lastMemberScan) {
            cachedLevel = mc.level;
            lastMemberScan = gameTime;
            CACHED_MEMBERS.clear();
            if (team != null) {
                for (Player p : mc.level.players()) {
                    if (p != self && p.getTeam() == team) CACHED_MEMBERS.add(p);
                }
                // Refresh twice per second; positions do not need an FPS-rate full sort.
                CACHED_MEMBERS.sort((a, b) -> Double.compare(self.distanceToSqr(a), self.distanceToSqr(b)));
                if (CACHED_MEMBERS.size() > MAX_MEMBERS) {
                    CACHED_MEMBERS.subList(MAX_MEMBERS, CACHED_MEMBERS.size()).clear();
                }
            }
        }
        List<Player> members = CACHED_MEMBERS;

        // Advance fade for currently visible members and anything still easing out.
        for (Player p : members) {
            UUID id = p.getUUID();
            float cur = FADE.getOrDefault(id, 0f);
            FADE.put(id, AnimUtil.ease(cur, 1f, 0.15f));
        }
        Iterator<Map.Entry<UUID, Float>> fades = FADE.entrySet().iterator();
        while (fades.hasNext()) {
            Map.Entry<UUID, Float> entry = fades.next();
            boolean visible = false;
            for (Player p : members) {
                if (p.getUUID().equals(entry.getKey())) {
                    visible = true;
                    break;
                }
            }
            if (visible) continue;
            float cur = AnimUtil.ease(entry.getValue(), 0f, 0.15f);
            if (cur < 0.02f) fades.remove();
            else entry.setValue(cur);
        }

        if (members.isEmpty() && FADE.isEmpty()) return;

        Font font = mc.font;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        int y = MARGIN_TOP;
        for (Player p : members) {
            if (p.isRemoved()) continue;
            float fade = FADE.getOrDefault(p.getUUID(), 1f);
            drawChip(graphics, font, p, MARGIN_X, y, fade);
            y += CHIP_H + CHIP_GAP;
        }

        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }

    private static void drawChip(GuiGraphics g, Font font, Player p, int x, int y, float fade) {
        int alpha = Math.round(255 * Math.max(0f, Math.min(1f, fade)));
        if (alpha <= 2) return;
        int a8 = alpha << 24;

        g.fill(x - 1, y - 1, x + CHIP_W + 1, y + CHIP_H + 1, a8 | 0x000000);
        g.fill(x, y, x + CHIP_W, y + CHIP_H, a8 | 0x0A1428);

        int px = x + 3, py = y + 3, ps = PORTRAIT;
        g.fill(px - 1, py - 1, px + ps + 1, py + ps + 1, a8 | 0x1E6BB8);
        if (p instanceof net.minecraft.client.player.AbstractClientPlayer acp) {
            ResourceLocation skin = acp.getSkin().texture();
            RenderSystem.setShaderColor(1f, 1f, 1f, fade);
            RenderSystem.setShaderTexture(0, skin);
            PlayerFaceRenderer.draw(g, skin, px, py, ps);
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        }

        int contentX = px + ps + 6;
        int contentW = x + CHIP_W - contentX - 4;

        String name = p.getName().getString();
        if (font.width(name) > contentW) {
            name = font.plainSubstrByWidth(name, Math.max(0, contentW - font.width(".."))) + "..";
        }
        g.drawString(font, name, contentX, y + 3, a8 | 0xFFFFFF, false);

        float hpPct = p.getMaxHealth() > 0f ? Math.max(0f, Math.min(1f, p.getHealth() / p.getMaxHealth())) : 0f;
        DmzClientStats.Snapshot snap = DmzClientStats.read(p);

        int barY = y + 13;
        boolean lowHp = hpPct < 0.25f;
        int hpColor = lowHp
                ? AnimUtil.lerpColor(0xE53935, 0xFF8A80, AnimUtil.pulse01(500L))
                : 0xE53935;
        drawMiniBar(g, contentX, barY, contentW, 5, hpPct, a8 | 0x330A08, a8 | (hpColor & 0xFFFFFF));

        if (snap.present) {
            int kiY = barY + 7;
            drawMiniBar(g, contentX, kiY, contentW, 4, snap.energyPercent(), a8 | 0x2A2408, a8 | 0xFDD835);
        }
    }

    private static void drawMiniBar(GuiGraphics g, int x, int y, int w, int h, float percent, int empty, int fillColor) {
        percent = Math.max(0f, Math.min(1f, percent));
        g.fill(x, y, x + w, y + h, empty);
        int filled = Math.round(w * percent);
        if (filled > 0) {
            g.fill(x, y, x + filled, y + h, fillColor);
        }
    }
}
