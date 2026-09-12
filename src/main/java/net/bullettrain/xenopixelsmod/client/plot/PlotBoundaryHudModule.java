package net.bullettrain.xenopixelsmod.client.plot;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;

/**
 * Draws a corner diagram of the current plot selection's X/Z boundary.
 *
 * <p><b>Scope, stated plainly.</b> Claimed plots are server-authoritative and no packet carries
 * them to the client, so a world-space overlay of somebody's claim is not possible without a
 * protocol change — and the repository contract keeps {@code ModNetwork}'s channel stable, so that
 * change is out of scope here. What the client genuinely knows is its own WorldEdit selection,
 * which is exactly the boundary a player is about to claim. This module draws that.</p>
 *
 * <p>The diagram is a top-down rectangle rather than a projected world overlay: it needs no camera
 * matrices, so it cannot drift, flicker or mis-project, and it reads the same at any view angle.
 * Width and length are drawn to scale within a fixed box, with the player's own position marked so
 * the player can see where they stand relative to the edges.</p>
 */
public final class PlotBoundaryHudModule implements PlotUiModule {

    public static final String ID = "plot_boundary_hud";

    private static final int BOX = 64;
    private static final int MARGIN = 6;
    private static final int BORDER = 0xE0FFFFFF;
    private static final int FILL = 0x30FFFFFF;
    private static final int PLAYER_DOT = 0xFFFF4040;
    private static final int LABEL = 0xFFFFFF;

    private volatile PlotClientSelection.Bounds bounds;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.player == null || minecraft.level == null) {
            bounds = null;
            return;
        }
        bounds = PlotClientSelection.bounds(minecraft.player, minecraft.level);
    }

    @Override
    public void render(GuiGraphics graphics, float partialTick) {
        PlotClientSelection.Bounds current = bounds;
        if (current == null || graphics == null) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.font == null || minecraft.getWindow() == null) {
            return;
        }
        int width = current.width();
        int length = current.length();
        float scale = Math.min((float) BOX / width, (float) BOX / length);
        int drawnWidth = Math.max(1, Math.round(width * scale));
        int drawnLength = Math.max(1, Math.round(length * scale));
        int left = minecraft.getWindow().getGuiScaledWidth() - BOX - MARGIN
                + (BOX - drawnWidth) / 2;
        int top = MARGIN + (BOX - drawnLength) / 2;

        graphics.fill(left, top, left + drawnWidth, top + drawnLength, FILL);
        graphics.fill(left, top, left + drawnWidth, top + 1, BORDER);
        graphics.fill(left, top + drawnLength - 1, left + drawnWidth, top + drawnLength, BORDER);
        graphics.fill(left, top, left + 1, top + drawnLength, BORDER);
        graphics.fill(left + drawnWidth - 1, top, left + drawnWidth, top + drawnLength, BORDER);

        LocalPlayer player = minecraft.player;
        if (player != null) {
            int dotX = left + Math.round(clamp((float) (player.getX() - current.minX()) * scale,
                    0.0F, drawnWidth - 2.0F));
            int dotZ = top + Math.round(clamp((float) (player.getZ() - current.minZ()) * scale,
                    0.0F, drawnLength - 2.0F));
            graphics.fill(dotX, dotZ, dotX + 2, dotZ + 2, PLAYER_DOT);
        }

        graphics.drawString(minecraft.font, width + " x " + length,
                left, top + drawnLength + 2, LABEL, true);
    }

    private static float clamp(float value, float min, float max) {
        return value < min ? min : Math.min(value, max);
    }
}