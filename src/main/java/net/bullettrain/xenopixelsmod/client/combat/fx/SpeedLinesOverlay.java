package net.bullettrain.xenopixelsmod.client.combat.fx;

import com.mojang.blaze3d.systems.RenderSystem;
import net.bullettrain.xenopixelsmod.client.config.XenoClientConfig;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Radial speed lines from the screen centre while moving fast.
 *
 * <p>Driven entirely off the local player's own velocity rather than off a dash packet. Speed is
 * something the client already knows exactly, every frame, for free — routing it through the
 * server would add a packet, a protocol bump and a round trip of latency to tell the client
 * something it can measure itself. It also means the effect covers every way of moving quickly,
 * including ones no combat move triggers.
 *
 * <p>Lines are drawn as thin quads radiating outward, thickening toward the screen edge and
 * fading toward the centre, so the middle of the view — the part the player is actually reading —
 * stays clear. Same principle as the impact flash being a vignette.
 */
@OnlyIn(Dist.CLIENT)
public final class SpeedLinesOverlay {

    /** Blocks per tick at which lines begin to appear. Above a hard sprint, below any dash. */
    private static final double MIN_SPEED = 0.55;
    /** Speed at which the effect is at full strength. */
    private static final double FULL_SPEED = 1.6;

    private static final int LINES = 28;
    /** Fraction of the half-diagonal a line spans at full strength. */
    private static final float LENGTH = 0.34f;
    /** Fraction of the half-diagonal kept clear around the crosshair. */
    private static final float CLEAR_CENTRE = 0.30f;
    private static final int COLOR = 0xFFFFFF;
    private static final float MAX_ALPHA = 0.34f;

    /** Eased so lines fade in and out instead of snapping on at the threshold. */
    private static float displayed;

    public void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.options.hideGui) return;
        if (!XenoClientConfig.speedLinesEnabled) return;

        displayed += (target(player) - displayed) * 0.25f;
        if (displayed <= 0.01f) return;

        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        float cx = width * 0.5f;
        float cy = height * 0.5f;
        float half = (float) Math.sqrt(cx * cx + cy * cy);

        RenderSystem.enableBlend();
        for (int i = 0; i < LINES; i++) {
            // Deterministic pseudo-scatter: a fixed irrational step never repeats a pattern the
            // eye can lock onto, and needs no random state carried between frames.
            double angle = i * 2.39996323;
            double jitter = ((i * 37) % 11) / 11.0;
            float inner = half * (CLEAR_CENTRE + 0.10f * (float) jitter);
            float outer = inner + half * LENGTH * displayed * (0.6f + 0.4f * (float) jitter);

            float dx = (float) Math.cos(angle);
            float dy = (float) Math.sin(angle);
            int alpha = Math.round(MAX_ALPHA * displayed * (0.5f + 0.5f * (float) jitter) * 255f);
            if (alpha <= 2) continue;
            drawRadial(graphics, cx, cy, dx, dy, inner, outer, (alpha << 24) | COLOR);
        }
        RenderSystem.disableBlend();
    }

    /**
     * One radial line, stepped as short segments.
     *
     * <p>{@code GuiGraphics.fill} only takes axis-aligned rectangles, so an arbitrary-angle line
     * has to be walked. Segments are cheap and the alternative — a rotated pose per line — costs
     * a matrix push and pop 28 times a frame for a two-pixel mark.
     */
    private static void drawRadial(GuiGraphics graphics, float cx, float cy,
                                   float dx, float dy, float inner, float outer, int argb) {
        int steps = Math.max(2, Math.round((outer - inner) / 3f));
        for (int s = 0; s < steps; s++) {
            float t = inner + (outer - inner) * (s / (float) steps);
            int x = Math.round(cx + dx * t);
            int y = Math.round(cy + dy * t);
            // Thicken with distance so lines taper toward the centre.
            int thickness = 1 + Math.round((t - inner) / Math.max(1f, outer - inner) * 2f);
            graphics.fill(x, y, x + thickness, y + thickness, argb);
        }
    }

    /** Strength 0..1 from horizontal speed; vertical motion is excluded so falling is exempt. */
    private static float target(LocalPlayer player) {
        var motion = player.getDeltaMovement();
        double speed = Math.sqrt(motion.x * motion.x + motion.z * motion.z);
        if (speed <= MIN_SPEED) return 0f;
        return (float) Math.min(1.0, (speed - MIN_SPEED) / (FULL_SPEED - MIN_SPEED));
    }
}
