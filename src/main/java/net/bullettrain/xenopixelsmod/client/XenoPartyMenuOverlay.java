package net.bullettrain.xenopixelsmod.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.bullettrain.xenopixelsmod.client.config.XenoClientConfig;
import net.bullettrain.xenopixelsmod.client.hud.AnimUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

import java.util.*;

/**
 * Xenoverse-2-style party menu/HUD overlay that shows when player is in a party.
 * Displays party members with HP bars, leader indicator, and quest sharing status.
 * Only renders when party exists (via {@link PartyClientState#hasParty()}).
 */
@OnlyIn(Dist.CLIENT)
public class XenoPartyMenuOverlay implements IGuiOverlay {
    private static final int MAX_DISPLAY = 4;
    private static final int CARD_W = 160;
    private static final int CARD_H = 36;
    private static final int CARD_GAP = 6;
    private static final int PORTRAIT_SIZE = 28;
    private static final int MARGIN_X = 12;
    private static final int MARGIN_Y = 12;

    private static final Map<String, Float> FADE = new HashMap<>();

    @Override
    public void render(ForgeGui gui, GuiGraphics graphics, float partialTick, int screenWidth, int screenHeight) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.options.hideGui) return;
        if (!XenoClientConfig.partyHudEnabled || !XenoClientConfig.xenoHudEnabled) return;
        if (!PartyClientState.hasParty()) return;

        Player self = mc.player;
        UUID selfUuid = self.getUUID();

        Set<UUID> memberUuids = PartyClientState.getMemberUuids();
        Map<UUID, String> memberNames = PartyClientState.getMemberNames();
        UUID leaderUuid = PartyClientState.getLeaderUuid();

        // Build list of members to display (excluding self)
        List<UUID> displayOrder = new ArrayList<>();
        for (UUID uuid : memberUuids) {
            if (!uuid.equals(selfUuid)) {
                displayOrder.add(uuid);
            }
        }

        // Sort by distance if players are in same level
        displayOrder.sort((a, b) -> {
            Player pa = mc.level.getPlayerByUUID(a);
            Player pb = mc.level.getPlayerByUUID(b);
            if (pa == null) return 1;
            if (pb == null) return -1;
            return Float.compare(self.distanceTo(pa), self.distanceTo(pb));
        });

        if (displayOrder.size() > MAX_DISPLAY) {
            displayOrder = displayOrder.subList(0, MAX_DISPLAY);
        }

        // Update fade states
        List<String> keep = new ArrayList<>();
        for (UUID uuid : displayOrder) {
            String id = uuid.toString();
            keep.add(id);
            float cur = FADE.getOrDefault(id, 0f);
            FADE.put(id, AnimUtil.ease(cur, 1f, 0.15f));
        }
        FADE.keySet().removeIf(id -> {
            if (keep.contains(id)) return false;
            float cur = AnimUtil.ease(FADE.get(id), 0f, 0.15f);
            FADE.put(id, cur);
            return cur < 0.02f;
        });

        if (displayOrder.isEmpty() && FADE.isEmpty()) return;

        Font font = mc.font;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        int y = MARGIN_Y;
        for (UUID uuid : displayOrder) {
            Player p = mc.level.getPlayerByUUID(uuid);
            float fade = FADE.getOrDefault(uuid.toString(), 1f);
            String name = memberNames.getOrDefault(uuid, "Unknown");
            boolean isLeader = leaderUuid != null && leaderUuid.equals(uuid);
            
            if (p != null) {
                drawMemberCard(graphics, font, p, name, isLeader, MARGIN_X, y, fade);
            } else {
                drawOfflineCard(graphics, font, name, isLeader, MARGIN_X, y, fade);
            }
            y += CARD_H + CARD_GAP;
        }

        // Draw party header if quest sharing enabled
        if (PartyClientState.isQuestSharingEnabled()) {
            drawQuestHeader(graphics, font, screenWidth, y + 4);
        }

        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }

    private static void drawMemberCard(GuiGraphics g, Font font, Player p, String name, 
                                       boolean isLeader, int x, int y, float fade) {
        int alpha = Math.round(255 * Math.max(0f, Math.min(1f, fade)));
        if (alpha <= 2) return;
        int a8 = alpha << 24;

        // Card background
        g.fill(x - 1, y - 1, x + CARD_W + 1, y + CARD_H + 1, a8 | 0x000000);
        g.fill(x, y, x + CARD_W, y + CARD_H, a8 | 0x0F1928);

        // Leader border accent
        if (isLeader) {
            g.fill(x, y, x + CARD_W, y + 2, a8 | 0xFFD700);
        }

        // Portrait
        int px = x + 4, py = y + 4;
        g.fill(px - 1, py - 1, px + PORTRAIT_SIZE + 1, py + PORTRAIT_SIZE + 1, a8 | 0x1E6BB8);
        if (p instanceof net.minecraft.client.player.AbstractClientPlayer acp) {
            ResourceLocation skin = acp.getSkinTextureLocation();
            RenderSystem.setShaderColor(1f, 1f, 1f, fade);
            RenderSystem.setShaderTexture(0, skin);
            PlayerFaceRenderer.draw(g, skin, px, py, PORTRAIT_SIZE);
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        }

        // Name and status
        int contentX = px + PORTRAIT_SIZE + 8;
        int contentW = x + CARD_W - contentX - 4;

        String displayName = name;
        if (font.width(displayName) > contentW) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < displayName.length(); i++) {
                sb.append(displayName.charAt(i));
                if (font.width(sb.toString() + "..") > contentW) break;
            }
            displayName = sb + "..";
        }

        int nameColor = isLeader ? (a8 | 0xFFD700) : (a8 | 0xFFFFFF);
        g.drawString(font, displayName, contentX, y + 4, nameColor, false);

        // HP bar
        float hpPct = p.getMaxHealth() > 0f ? Math.max(0f, Math.min(1f, p.getHealth() / p.getMaxHealth())) : 0f;
        int barY = y + 20;
        boolean lowHp = hpPct < 0.25f;
        int hpColor = lowHp
                ? AnimUtil.lerpColor(0xE53935, 0xFF8A80, AnimUtil.pulse01(500L))
                : 0xE53935;
        drawBar(g, contentX, barY, contentW, 6, hpPct, a8 | 0x330A08, a8 | (hpColor & 0xFFFFFF));

        // Distance indicator
        float dist = p.distanceTo(Minecraft.getInstance().player);
        String distStr = dist < 100 ? "§aNearby" : (dist < 500 ? "§e" + Math.round(dist) + "m" : "§7Far");
        g.drawString(font, distStr, contentX, y + 28, a8 | 0x88AA88, false);
    }

    private static void drawOfflineCard(GuiGraphics g, Font font, String name, 
                                        boolean isLeader, int x, int y, float fade) {
        int alpha = Math.round(255 * Math.max(0f, Math.min(1f, fade)));
        if (alpha <= 2) return;
        int a8 = alpha << 24;

        g.fill(x - 1, y - 1, x + CARD_W + 1, y + CARD_H + 1, a8 | 0x000000);
        g.fill(x, y, x + CARD_W, y + CARD_H, a8 | 0x141414);

        if (isLeader) {
            g.fill(x, y, x + CARD_W, y + 2, a8 | 0x888888);
        }

        int px = x + 4, py = y + 4;
        g.fill(px - 1, py - 1, px + PORTRAIT_SIZE + 1, py + PORTRAIT_SIZE + 1, a8 | 0x333333);

        int contentX = px + PORTRAIT_SIZE + 8;
        String displayName = name + " §7(Offline)";
        g.drawString(font, displayName, contentX, y + 12, a8 | 0x888888, false);
    }

    private static void drawQuestHeader(GuiGraphics g, Font font, int screenWidth, int y) {
        int w = 200;
        int x = screenWidth - w - 12;
        int h = 24;

        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xFF000000);
        g.fill(x, y, x + w, y + h, 0xFF1A2838);
        g.fill(x, y, x + w, y + 2, 0xFF4CAF50);

        g.drawString(font, "§aQuest Sharing Active", x + 8, y + 6, 0xFFFFFFFF, false);
    }

    private static void drawBar(GuiGraphics g, int x, int y, int w, int h, 
                                float percent, int empty, int fillColor) {
        percent = Math.max(0f, Math.min(1f, percent));
        g.fill(x, y, x + w, y + h, empty);
        int filled = Math.round(w * percent);
        if (filled > 0) {
            g.fill(x, y, x + filled, y + h, fillColor);
        }
    }
}
