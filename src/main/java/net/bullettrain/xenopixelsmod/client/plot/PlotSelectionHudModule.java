package net.bullettrain.xenopixelsmod.client.plot;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Shows the plot selection state in the corner of the screen.
 *
 * <p>Reads only what the client already knows: the WorldEdit selection is queried on demand for the
 * local player, and the resulting footprint is reported as width × length. Y is never shown
 * because Y is never part of a plot.</p>
 *
 * <p>The module degrades silently: with WorldEdit absent, or with no finished selection, it draws
 * nothing rather than reporting an error the player cannot act on.</p>
 */
public final class PlotSelectionHudModule implements PlotUiModule {

    public static final String ID = "plot_selection_hud";

    private static final int MARGIN = 4;

    private volatile Component status;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.player == null || minecraft.level == null) {
            status = null;
            return;
        }
        status = PlotClientSelection.describe(minecraft.player, minecraft.level);
    }

    @Override
    public void render(GuiGraphics graphics, float partialTick) {
        Component text = status;
        if (text == null || graphics == null) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.font == null) {
            return;
        }
        graphics.drawString(minecraft.font, text, MARGIN, MARGIN, 0xFFFFFF, true);
    }
}