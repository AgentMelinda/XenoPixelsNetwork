package net.bullettrain.xenopixelsmod.client.hud;

import com.mojang.blaze3d.systems.RenderSystem;
import net.bullettrain.xenopixelsmod.client.XenoHudSnapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.player.AbstractClientPlayer;

/**
 * Modern HUD renderer: blits the generated chrome atlas instead of drawing flat polygons.
 *
 * <p>Replaces the flat-tinted-rectangle path described in {@link XenoHudTextures}. Bars are
 * filled by width-clipping the full-bar texture, so the baked gradient, gloss band and lit
 * leading tip survive — tinting a 1×1 white pixel, which is what the old path did, cannot
 * produce any of those.
 *
 * <p>Selected at runtime with {@code /xenohud renderer modern}; the legacy procedural view
 * remains the default until this is verified in-game.
 */
public final class XenoModernHudView {

    private XenoHudSnapshot snapshot;
    private int boundsX;
    private int boundsY;
    private float scale = 1f;
    private boolean editorMode;

    // Eased display values so bars glide rather than snap. Purely visual.
    private float displayedHp = 1f;
    private float displayedKi = 1f;
    private float displayedStm = 1f;
    private long lastNanos;
    private boolean firstFrame = true;

    public void setSnapshot(XenoHudSnapshot snapshot) {
        this.snapshot = snapshot;
    }

    public void setBounds(int x, int y, float scale) {
        this.boundsX = x;
        this.boundsY = y;
        this.scale = scale <= 0f ? 1f : scale;
    }

    public void setEditorMode(boolean editorMode) {
        this.editorMode = editorMode;
    }

    public void render(GuiGraphics graphics) {
        XenoHudSnapshot snap = snapshot;
        if (snap == null) return;

        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(boundsX, boundsY, 0);
        pose.scale(scale, scale, 1f);

        RenderSystem.enableBlend();
        // Backing plate behind the whole cluster.
        drawPanel(graphics);
        renderContent(graphics);
        RenderSystem.disableBlend();
        pose.popPose();
    }

    /**
     * Draw the cluster contents at the current pose origin, without its own backing plate.
     *
     * <p>Split out for the unified renderer, which draws one plate covering both this cluster
     * and the combat cooldown strip. Two stacked plates with their own bevels read as two
     * panels that happen to touch, which is the thing being unified away.
     */
    public void renderContent(GuiGraphics graphics) {
        XenoHudSnapshot snap = snapshot;
        if (snap == null) return;
        Minecraft minecraft = Minecraft.getInstance();
        Font font = minecraft.font;

        ease(snap);

        // Portrait: generated frame, player face inset.
        HudDraw.blitRegion(graphics, XenoHudTextures.HUD_ATLAS, XenoHudLayout.PORTRAIT_FRAME,
                XenoHudLayout.PORTRAIT_X, XenoHudLayout.PORTRAIT_Y);
        if (minecraft.player instanceof AbstractClientPlayer player) {
            int inset = 6;
            PlayerFaceRenderer.draw(graphics, player.getSkin().texture(),
                    XenoHudLayout.PORTRAIT_X + inset, XenoHudLayout.PORTRAIT_Y + inset,
                    XenoHudLayout.PORTRAIT - inset * 2);
        }
        // Transform charge, the red ring the legacy view has always drawn. On the portrait
        // frame's own footprint rather than 4px outside it as the legacy view does: this cluster
        // sits at x=2 inside a plate, so an outset ring would hang off the left edge. The face is
        // inset 6px, so a 4px ring reads as the frame lighting up without covering it.
        if (snap.transforming) {
            HudDraw.transformChargeBorder(graphics,
                    XenoHudLayout.PORTRAIT_X, XenoHudLayout.PORTRAIT_Y,
                    XenoHudLayout.PORTRAIT, XenoHudLayout.PORTRAIT, snap.transformChargePercent);
        }

        // Below the threshold the HP bar swaps to the hot variant, so low health reads as a
        // colour change and not only as a shorter bar.
        boolean critical = displayedHp <= XenoHudLayout.HP_CRIT_FRACTION;
        drawBar(graphics, XenoHudLayout.HP_Y, XenoHudLayout.HP_EMPTY,
                critical ? XenoHudLayout.HP_CRIT : XenoHudLayout.HP_FULL, displayedHp,
                critical ? 0xFFFFC46E : 0xFFF0605C);
        drawBar(graphics, XenoHudLayout.KI_Y, XenoHudLayout.KI_EMPTY, XenoHudLayout.KI_FULL,
                displayedKi, 0xFF80E0FF);
        drawStamina(graphics);
        drawSparkPips(graphics, snap);

        // Text last so it sits above the chrome.
        String name = snap.name == null ? "" : snap.name;
        graphics.drawString(font, name,
                XenoHudLayout.CONTENT_LEFT, XenoHudLayout.NAME_Y, 0xFFDDEEFF, true);
        // DMZ power-release percentage, sat beside the name the way DMZ's own HUD shows it.
        // The legacy views already did this; the modern one was dropping the field entirely.
        if (snap.dmzPresent && snap.releaseText != null) {
            graphics.drawString(font, snap.releaseText,
                    XenoHudLayout.CONTENT_LEFT + font.width(name) + XenoHudLayout.RELEASE_GAP,
                    XenoHudLayout.NAME_Y, XenoHudLayout.RELEASE_COLOR, true);
        }
        drawBarValue(graphics, font, XenoHudLayout.HP_Y, formatPair(snap.curHp, snap.maxHp));
        drawBarValue(graphics, font, XenoHudLayout.KI_Y, formatPair(snap.curKi, snap.maxKi));

        if (editorMode) {
            HudDraw.borderRect(graphics, 0, 0, XenoHudLayout.width(), XenoHudLayout.height(),
                    0x88FFFFFF, 1);
        }
    }

    private void drawPanel(GuiGraphics graphics) {
        // Nine-sliced: the plate's bevel lives in its corner band and must not be stretched.
        HudDraw.blitNineSlice(graphics, XenoHudTextures.HUD_ATLAS, XenoHudLayout.PANEL,
                0, 0, XenoHudLayout.width(), XenoHudLayout.height(), XenoHudLayout.PANEL_CORNER);
    }

    /**
     * Track, fill, leading-edge cap, frame — in that order.
     *
     * <p>The cap is a separate stamp rather than part of the fill sprite: clipping the fill to
     * a fraction necessarily cuts off anything baked at its right end, so a bar below 100%
     * would otherwise lose the lit tip entirely.
     */
    private void drawBar(GuiGraphics graphics, int y, XenoHudLayout.Region empty,
                         XenoHudLayout.Region full, float fraction, int tipTint) {
        int x = XenoHudLayout.CONTENT_LEFT;
        HudDraw.blitRegion(graphics, XenoHudTextures.HUD_ATLAS, empty, x, y);
        HudDraw.blitClipped(graphics, XenoHudTextures.HUD_ATLAS, full, x, y, fraction);

        int filled = Math.round(XenoHudLayout.BAR_W * Math.max(0f, Math.min(1f, fraction)));
        if (filled > XenoHudLayout.BAR_TIP_W && filled < XenoHudLayout.BAR_W) {
            HudDraw.blitTinted(graphics, XenoHudTextures.HUD_ATLAS, XenoHudLayout.BAR_TIP,
                    x + filled - XenoHudLayout.BAR_TIP_W, y, tipTint);
        }
        HudDraw.blitRegion(graphics, XenoHudTextures.HUD_ATLAS, XenoHudLayout.BAR_FRAME, x, y);
    }

    /** The last lit segment gets the brighter cap, so the meter has a readable leading edge. */
    private void drawStamina(GuiGraphics graphics) {
        int lit = Math.round(XenoHudLayout.STM_SEGMENTS * displayedStm);
        for (int i = 0; i < XenoHudLayout.STM_SEGMENTS; i++) {
            int x = XenoHudLayout.CONTENT_LEFT + i * (XenoHudLayout.STM_SEG_W + XenoHudLayout.STM_GAP);
            XenoHudLayout.Region region = i >= lit ? XenoHudLayout.STM_OFF
                    : i == lit - 1 ? XenoHudLayout.STM_TIP : XenoHudLayout.STM_ON;
            HudDraw.blitRegion(graphics, XenoHudTextures.HUD_ATLAS, region, x, XenoHudLayout.STM_Y);
        }
    }

    /**
     * Sparking meter pips beside the portrait.
     *
     * <p>The sparking meter has been live in combat but never had a HUD element; this is the
     * first time a player can see it building.
     */
    private void drawSparkPips(GuiGraphics graphics, XenoHudSnapshot snap) {
        float meter = Math.max(0f, Math.min(1f, sparkMeter(snap)));
        int lit = Math.round(XenoHudLayout.SPARK_PIPS * meter);
        for (int i = 0; i < XenoHudLayout.SPARK_PIPS; i++) {
            int y = XenoHudLayout.SPARK_Y + i * XenoHudLayout.SPARK_STEP;
            HudDraw.blitRegion(graphics, XenoHudTextures.HUD_ATLAS,
                    i < lit ? XenoHudLayout.SPARK_ON : XenoHudLayout.SPARK_OFF,
                    XenoHudLayout.SPARK_X, y);
        }
    }

    /**
     * Sparking fraction from the snapshot, or 0 when the snapshot predates the field.
     *
     * <p>Read reflectively so this view compiles against snapshots that never carried it. The
     * lookup is resolved once and cached — including the failure — because this runs every
     * frame, and throwing and catching {@code NoSuchFieldException} 60+ times a second on a
     * snapshot without the field is a cost the HUD should not pay.
     */
    private static java.lang.reflect.Field sparkField;
    private static boolean sparkFieldResolved;

    private static float sparkMeter(XenoHudSnapshot snap) {
        if (!sparkFieldResolved) {
            sparkFieldResolved = true;
            try {
                sparkField = snap.getClass().getField("sparking");
            } catch (Throwable ignored) {
                sparkField = null;
            }
        }
        if (sparkField == null) return 0f;
        try {
            return sparkField.get(snap) instanceof Number number ? number.floatValue() : 0f;
        } catch (Throwable ignored) {
            return 0f;
        }
    }

    private void drawBarValue(GuiGraphics graphics, Font font, int y, String text) {
        int x = XenoHudLayout.CONTENT_LEFT + XenoHudLayout.BAR_W - 4 - font.width(text);
        graphics.drawString(font, text, x, y + 3, 0xFFFFFFFF, true);
    }

    private static String formatPair(double current, double max) {
        return HudNumbers.formatPair(current, max);
    }

    /** Exponential ease toward the true value, framerate-independent. */
    private void ease(XenoHudSnapshot snap) {
        long now = System.nanoTime();
        float dt = firstFrame ? 1f : (float) ((now - lastNanos) / 1_000_000_000.0);
        lastNanos = now;
        firstFrame = false;
        float k = Math.min(1f, dt * 8f);

        displayedHp += (safeFraction(snap.curHp, snap.maxHp) - displayedHp) * k;
        displayedKi += (safeFraction(snap.curKi, snap.maxKi) - displayedKi) * k;
        displayedStm += (safeFraction(snap.curStm, snap.maxStm) - displayedStm) * k;
    }

    private static float safeFraction(double current, double max) {
        if (max <= 0.0 || !Double.isFinite(max) || !Double.isFinite(current)) return 0f;
        return (float) Math.max(0.0, Math.min(1.0, current / max));
    }
}
