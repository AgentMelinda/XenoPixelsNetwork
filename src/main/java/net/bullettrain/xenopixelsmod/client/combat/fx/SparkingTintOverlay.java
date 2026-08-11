package net.bullettrain.xenopixelsmod.client.combat.fx;

import com.mojang.blaze3d.systems.RenderSystem;
import net.bullettrain.xenopixelsmod.client.config.XenoClientConfig;
import net.bullettrain.xenopixelsmod.effect.ModEffects;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Warm vignette while the local player is sparking.
 *
 * <p>Reads the sparking mob effect directly rather than taking a packet. The effect is already
 * applied server-side and vanilla replicates active effects to their owner's client for free, so
 * a dedicated sync would be a second source of truth for something the client is already told —
 * and one that could disagree with the buff icon sitting in the corner of the screen.
 *
 * <p>Pulses slowly rather than sitting at a constant brightness. Sparking lasts many seconds, and
 * a static tint is something the eye filters out within about two of them; a slow breath keeps it
 * present without becoming noise. The pulse is driven from game time, so it does not drift with
 * frame rate.
 */
@OnlyIn(Dist.CLIENT)
public final class SparkingTintOverlay {

    private static final int COLOR = 0xFFB23C;
    /** Peak opacity at the screen edge. Low: this runs for seconds at a time. */
    private static final float MAX_ALPHA = 0.30f;
    /** Fraction of the smaller screen dimension the vignette reaches inward. */
    private static final float REACH = 0.30f;
    private static final int BANDS = 5;
    /** Radians per tick. A full breath every ~3.5 seconds. */
    private static final float PULSE_RATE = 0.09f;
    /** How much of the brightness the pulse swings, as a fraction. */
    private static final float PULSE_DEPTH = 0.35f;

    public void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.options.hideGui) return;
        if (!XenoClientConfig.sparkingTintEnabled) return;
        if (!player.hasEffect(ModEffects.SPARKING)) return;

        float time = player.tickCount + deltaTracker.getGameTimeDeltaPartialTick(false);
        float pulse = 1.0f - PULSE_DEPTH * (0.5f + 0.5f * (float) Math.sin(time * PULSE_RATE));
        float alpha = MAX_ALPHA * pulse;

        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        int reach = Math.max(4, Math.round(Math.min(width, height) * REACH));

        RenderSystem.enableBlend();
        for (int band = 0; band < BANDS; band++) {
            float t = band / (float) BANDS;
            int a = Math.round(Math.min(1.0f, alpha * (1.0f - t) * (1.0f - t)) * 255.0f);
            if (a <= 1) continue;
            int argb = (a << 24) | COLOR;

            int inset = Math.round(reach * t);
            int thickness = Math.max(1, reach / BANDS);
            graphics.fill(inset, inset, inset + thickness, height - inset, argb);
            graphics.fill(width - inset - thickness, inset, width - inset, height - inset, argb);
            graphics.fill(inset + thickness, inset, width - inset - thickness, inset + thickness, argb);
            graphics.fill(inset + thickness, height - inset - thickness,
                    width - inset - thickness, height - inset, argb);
        }
        RenderSystem.disableBlend();
    }
}
