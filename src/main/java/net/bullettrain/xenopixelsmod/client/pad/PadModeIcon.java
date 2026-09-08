package net.bullettrain.xenopixelsmod.client.pad;

import dev.isxander.controlify.api.bind.RadialIcon;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/** Compact text icon for controller-mode and flight-mode radial actions. */
record PadModeIcon(String label, int color) implements RadialIcon {
    @Override
    public void draw(GuiGraphics graphics, int x, int y, float delta) {
        Minecraft minecraft = Minecraft.getInstance();
        int width = minecraft.font.width(label);
        graphics.drawString(minecraft.font, label,
                x + (16 - width) / 2, y + (16 - minecraft.font.lineHeight) / 2 + 1,
                color, true);
    }
}
