package net.bullettrain.xenopixelsmod.client.combat.fx;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * The impact flash: a brief tinted wash over the screen when a blow connects.
 *
 * <p>Drawn as a vignette rather than a flat fill — bright at the edges, clear in the middle.
 * A full-screen white flash is the obvious implementation and the wrong one: it hides the thing
 * the player is trying to watch at exactly the moment it matters. Pushing the brightness to the
 * periphery reads as the same hit while leaving the centre of the screen legible.
 *
 * <p>Purely decorative and fully skippable — {@link CombatFxClient#flashAlpha} returns 0 when the
 * player has turned it off, and this draws nothing.
 */
@OnlyIn(Dist.CLIENT)
public final class CombatFlashOverlay {

    /** Vignette bands from the screen edge inward. More bands is smoother and costs more fills. */
    private static final int BANDS = 6;
    /** Fraction of the smaller screen dimension the vignette reaches in from each edge. */
    private static final float REACH = 0.34f;

    public void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.options.hideGui) return;

        float alpha = CombatFxClient.flashAlpha(deltaTracker.getGameTimeDeltaPartialTick(false));
        if (alpha <= 0.002f) return;

        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        int color = CombatFxClient.flashColor() & 0xFFFFFF;
        int reach = Math.max(4, Math.round(Math.min(width, height) * REACH));

        RenderSystem.enableBlend();
        for (int band = 0; band < BANDS; band++) {
            // Outermost band is fully opaque at the current alpha; each step inward fades.
            float t = band / (float) BANDS;
            float bandAlpha = alpha * (1.0f - t) * (1.0f - t);
            int a = Math.round(Math.min(1.0f, bandAlpha) * 255.0f);
            if (a <= 1) continue;
            int argb = (a << 24) | color;

            int inset = Math.round(reach * t);
            int thickness = Math.max(1, reach / BANDS);

            // Four edges. Left and right are drawn full height, top and bottom inset to match,
            // so the corners get both bands and end up the brightest part of the frame.
            graphics.fill(inset, inset, inset + thickness, height - inset, argb);
            graphics.fill(width - inset - thickness, inset, width - inset, height - inset, argb);
            graphics.fill(inset + thickness, inset, width - inset - thickness, inset + thickness, argb);
            graphics.fill(inset + thickness, height - inset - thickness,
                    width - inset - thickness, height - inset, argb);
        }
        RenderSystem.disableBlend();
    }
}
