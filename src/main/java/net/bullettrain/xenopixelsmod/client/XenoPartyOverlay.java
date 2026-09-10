package net.bullettrain.xenopixelsmod.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.client.config.XenoClientConfig;
import net.bullettrain.xenopixelsmod.client.config.XenoPartyHudConfig;
import net.bullettrain.xenopixelsmod.client.config.XenoPartyHudConfig.Part;
import net.bullettrain.xenopixelsmod.network.packet.PartySyncPacket;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Nearby-only XV-style party cards rendered from the supplied transparent PNG artwork. */
@OnlyIn(Dist.CLIENT)
public final class XenoPartyOverlay {
    private static final ResourceLocation LEFT = ResourceLocation.fromNamespaceAndPath(
            XenoPixelsMod.MOD_ID, "textures/gui/xeno_party_member_left.png");
    private static final int MAX_VISIBLE = 4;
    private static final int GAP = 18;
    private static final double NEARBY_DISTANCE_SQR = 96.0 * 96.0;

    public void render(GuiGraphics graphics, DeltaTracker ignored) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.options.hideGui) return;
        if (!XenoClientConfig.partyHudEnabled || !XenoClientConfig.xenoHudEnabled
                || !XenoPartyHudConfig.visible) return;
        renderCards(graphics, false);
        renderPing(graphics, mc);
    }

    public static void renderCards(GuiGraphics graphics, boolean editing) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        List<Entry> visible = visibleMembers(mc);
        if (editing && visible.isEmpty()) {
            PartySyncPacket.Member demo = new PartySyncPacket.Member(mc.player.getUUID(),
                    "Party Member", true, true, mc.player.getId(), 45,
                    78f, 100f, 64f, 100f, 52f, 100f, 75, "Super Form", 80f, false);
            // A fresh list: the cache is shared with the live HUD and must not gain a fake member.
            visible = List.of(newEntry(mc.font, demo, mc.player));
        }

        // Clamp for this frame only. clampToScreen writes the persisted x/y, and its bound depends
        // on the card count, so calling it every frame silently walked the player's configured
        // position every time the party grew or shrank.
        int originX = clamp(XenoPartyHudConfig.x, graphics.guiWidth() - XenoPartyHudConfig.scaledWidth());
        int originY = clamp(XenoPartyHudConfig.y,
                graphics.guiHeight() - XenoPartyHudConfig.scaledHeight(Math.max(1, visible.size())));

        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(originX, originY, 0);
        pose.scale(XenoPartyHudConfig.scale, XenoPartyHudConfig.scale, 1f);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        int y = 0;
        for (Entry entry : visible) {
            pose.pushPose();
            pose.translate(0, y, 0);
            drawCard(graphics, mc.font, entry);
            if (editing) graphics.renderOutline(0, 0, XenoPartyHudConfig.CARD_W,
                    XenoPartyHudConfig.CARD_H, 0xFF42A5F5);
            pose.popPose();
            y += XenoPartyHudConfig.CARD_H + GAP;
        }
        RenderSystem.disableBlend();
        pose.popPose();
    }

    private static int clamp(int value, int max) {
        return Math.max(0, Math.min(Math.max(0, max), value));
    }

    /**
     * Resolved cards for the current tick.
     *
     * <p>Rebuilt once per client tick rather than once per frame, mirroring the {@code cachedGameTime}
     * pattern in {@link DmzClientStats} and {@link XenoHudSnapshotFactory}. At 200fps the old shape
     * ran this ten times per tick, each run allocating a list, a record per member and a comparator,
     * and walking every player in the level once per party member.
     */
    private static final List<Entry> CACHED = new ArrayList<>();
    private static long cachedGameTime = Long.MIN_VALUE;
    private static Comparator<Entry> byDistance;

    private static List<Entry> visibleMembers(Minecraft mc) {
        long gameTime = mc.level.getGameTime();
        if (gameTime == cachedGameTime) return CACHED;
        cachedGameTime = gameTime;
        CACHED.clear();

        for (PartySyncPacket.Member member : ClientParty.members()) {
            if (member.id().equals(mc.player.getUUID())) continue;
            // Direct UUID lookup instead of scanning level.players() once per member.
            Player player = mc.level.getPlayerByUUID(member.id());
            if (player != null && !player.isRemoved()
                    && mc.player.distanceToSqr(player) <= NEARBY_DISTANCE_SQR) {
                CACHED.add(newEntry(mc.font, member, player));
            }
        }
        if (CACHED.size() > 1) {
            if (byDistance == null) {
                byDistance = Comparator.comparingDouble(entry ->
                        Minecraft.getInstance().player.distanceToSqr(entry.player));
            }
            CACHED.sort(byDistance);
        }
        while (CACHED.size() > MAX_VISIBLE) CACHED.remove(CACHED.size() - 1);
        return CACHED;
    }

    private static int px(int part) {
        return XenoPartyHudConfig.partX(part);
    }

    private static int py(int part) {
        return XenoPartyHudConfig.partY(part);
    }

    /**
     * One live piece of the card, honouring its editor settings.
     *
     * <p>Coordinates are the positions the card art was drawn for; the editor's offset, scale,
     * colour, bold and font are applied on top, so a card with the override switched off renders
     * exactly as it always did.
     */
    private static void drawPart(GuiGraphics g, Font font, int part, String text, int x, int y) {
        if (text == null || text.isEmpty()) return;
        Component line = Component.literal(text).setStyle(Style.EMPTY
                .withBold(XenoPartyHudConfig.partBold[part])
                .withFont(XenoPartyHudConfig.partFontLocation(part)));
        float scale = XenoPartyHudConfig.partScale(part);
        g.pose().pushPose();
        g.pose().translate(x + px(part), y + py(part), 0);
        g.pose().scale(scale, scale, 1f);
        g.drawString(font, line, 0, 0, XenoPartyHudConfig.partColor(part), true);
        g.pose().popPose();
    }

    private static void drawCard(GuiGraphics g, Font font, Entry entry) {
        PartySyncPacket.Member member = entry.member;
        Player player = entry.player;
        g.blit(LEFT, 0, 0, 0f, 0f, 765, 295, 765, 295);

        // Live skin face stays inside the dark circular well; the supplied glow ring remains visible.
        if (player instanceof AbstractClientPlayer clientPlayer) {
            PlayerFaceRenderer.draw(g, clientPlayer.getSkin().texture(),
                    54 + px(Part.PORTRAIT), 66 + py(Part.PORTRAIT), 142);
        }

        // Cover the baked demo fills, then paint live clipped gauges while retaining their chrome.
        drawGauge(g, 315 + px(Part.HP), 148 + py(Part.HP), 296, 25, member.hpPercent(), 0xFF16090B,
                member.hpPercent() < 0.25f ? 0xFFFF3D35 : XenoPartyHudConfig.partColor(Part.HP));
        drawSegments(g, 306 + px(Part.KI), 187 + py(Part.KI), 302, 24, member.kiPercent(),
                0xFF07182A, XenoPartyHudConfig.partColor(Part.KI), 8);
        drawSegments(g, 286 + px(Part.STM), 225 + py(Part.STM), 300, 23, member.staminaPercent(),
                0xFF071F26, XenoPartyHudConfig.partColor(Part.STM), 8);

        drawPart(g, font, Part.NAME, entry.displayName, 270, 88);
        drawPart(g, font, Part.LEVEL, entry.levelText, 632, 88);
        if (member.leader()) drawPart(g, font, Part.LEADER, "★", 244, 88);
        if (!entry.formText.isEmpty()) {
            drawPart(g, font, Part.FORM, entry.formText, 270, 111);
        }
        if (member.sparkingActive()) drawPart(g, font, Part.SPARKING, "SPARKING", 625, 228);
        else if (member.sparking() >= 99f) drawPart(g, font, Part.SPARKING, "READY", 642, 228);

        if (member.hpPercent() < 0.25f) {
            int pulse = 100 + (int) (70 * (0.5 + 0.5 * Math.sin(System.currentTimeMillis() / 130.0)));
            g.renderOutline(18, 18, 710, 242, (pulse << 24) | 0x00FF3028);
        }
    }

    private static void drawGauge(GuiGraphics g, int x, int y, int w, int h, float fraction,
                                  int empty, int fill) {
        fraction = Math.max(0f, Math.min(1f, fraction));
        g.fill(x, y, x + w, y + h, empty);
        int filled = Math.round(w * fraction);
        if (filled > 0) {
            g.fill(x, y, x + filled, y + h, fill);
            g.fill(x, y, x + filled, y + Math.max(2, h / 4), 0x88FFFFFF);
        }
    }

    private static void drawSegments(GuiGraphics g, int x, int y, int w, int h, float fraction,
                                     int empty, int fill, int segments) {
        int gap = 4;
        int segmentW = (w - gap * (segments - 1)) / segments;
        int lit = Math.round(Math.max(0f, Math.min(1f, fraction)) * segments);
        for (int i = 0; i < segments; i++) {
            int sx = x + i * (segmentW + gap);
            g.fill(sx, y, sx + segmentW, y + h, i < lit ? fill : empty);
            if (i < lit) g.fill(sx, y, sx + segmentW, y + 3, 0x77FFFFFF);
        }
    }

    /** The banner text only changes when the marker does, so it is not rebuilt per frame. */
    private static ClientParty.Ping cachedPing;
    private static String cachedPingText = "";
    private static int cachedPingWidth;

    private static void renderPing(GuiGraphics g, Minecraft mc) {
        ClientParty.Ping ping = ClientParty.ping(mc.level.getGameTime());
        if (ping == null) return;
        if (ping != cachedPing) {
            cachedPing = ping;
            cachedPingText = "◆ PARTY TARGET: " + ping.targetName() + "  [aim + H to lock]";
            cachedPingWidth = mc.font.width(cachedPingText) + 16;
        }
        int cx = g.guiWidth() / 2;
        String text = cachedPingText;
        int w = cachedPingWidth;
        g.fill(cx - w / 2, 12, cx + w / 2, 29, 0xCC071525);
        g.renderOutline(cx - w / 2, 12, w, 17, 0xFF20BFFF);
        g.drawCenteredString(mc.font, text, cx, 17, 0xFFB8ECFF);
    }

    /**
     * Built once per tick alongside the roster, so the text the card draws is not re-derived on
     * every frame. Name truncation and {@code Integer.toString} both allocate, and both produce
     * the same answer for the whole tick.
     */
    private static Entry newEntry(Font font, PartySyncPacket.Member member, Player player) {
        String name = member.name();
        if (font.width(name) > 250) name = font.plainSubstrByWidth(name, 238) + "…";
        String form = member.form().isBlank() ? "" : font.plainSubstrByWidth(member.form(), 170);
        return new Entry(member, player, name, Integer.toString(member.level()), form);
    }

    private record Entry(PartySyncPacket.Member member, Player player,
                         String displayName, String levelText, String formText) {}
}
