package net.bullettrain.xenopixelsmod.client.hud;

import com.lowdragmc.lowdraglib.gui.texture.ResourceTexture;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Reusable LDLib-texture-backed chrome for one technique hotbar slot (Phase 6
 * of the LDLib HUD migration plan). Draws only the background/outline/badge
 * rectangles — all technique data (name, cooldown text, key label, slot
 * number) is still drawn by the caller via vanilla {@code GuiGraphics} text,
 * unchanged, since LDLib's texture primitives don't render text.
 *
 * <p>Gated behind {@code XenoHudConfig.legacyTechniqueRenderer}; when that
 * flag is true (default) {@link net.bullettrain.xenopixelsmod.client.XenoTechniqueHotbarOverlay}
 * doesn't use this class at all and renders exactly as before.</p>
 */
public final class TechniqueSlotWidget {
    // Reused every frame/slot (mutate-then-draw immediate rendering; no per-frame allocation).
    private final ResourceTexture fill = new ResourceTexture(XenoHudTextures.WHITE);
    private final ResourceTexture badgeFill = new ResourceTexture(XenoHudTextures.WHITE).setColor(0xEE000000);

    /** Solid tinted background rectangle (replaces the legacy hand-rolled rounded fill). */
    public void drawBackground(GuiGraphics g, int x, int y, int w, int h, int color) {
        fill.setColor(color);
        fill.draw(g, 0, 0, (float) x, (float) y, w, h);
    }

    /**
     * Thin tinted border made of four solid strips (LDLib has no dedicated
     * outline-only texture primitive in the verified API surface, so this
     * composes one from {@link ResourceTexture} fills — still LDLib-drawn,
     * not a vanilla {@code GuiGraphics.renderOutline} call).
     */
    public void drawOutline(GuiGraphics g, int x, int y, int w, int h, int color, int thickness) {
        fill.setColor(color);
        fill.draw(g, 0, 0, (float) x, (float) y, w, thickness);
        fill.draw(g, 0, 0, (float) x, (float) (y + h - thickness), w, thickness);
        fill.draw(g, 0, 0, (float) x, (float) y, thickness, h);
        fill.draw(g, 0, 0, (float) (x + w - thickness), (float) y, thickness, h);
    }

    public void drawBadgeBackground(GuiGraphics g, int x, int y, int w, int h) {
        badgeFill.draw(g, 0, 0, (float) x, (float) y, w, h);
    }
}
