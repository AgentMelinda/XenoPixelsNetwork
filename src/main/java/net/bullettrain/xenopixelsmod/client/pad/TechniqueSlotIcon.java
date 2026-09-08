package net.bullettrain.xenopixelsmod.client.pad;

import dev.isxander.controlify.api.bind.RadialIcon;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/**
 * The icon a technique slot shows in Controlify's radial menu.
 *
 * <p>Draws the slot's number rather than the technique's own artwork. The technique in a slot is
 * chosen in game and changes freely, while a radial icon is registered once at startup, so an icon
 * that tried to show the current technique would be wrong most of the time. The number is what
 * stays true, and it matches the numbering the slots already carry on the keyboard.
 */
public record TechniqueSlotIcon(int slot) implements RadialIcon {

    @Override
    public void draw(GuiGraphics graphics, int x, int y, float delta) {
        Minecraft mc = Minecraft.getInstance();
        String label = String.valueOf(slot);
        // x and y are the icon's top-left corner in a 16x16 cell; centre the glyph in it.
        int width = mc.font.width(label);
        graphics.drawString(mc.font, label,
                x + (16 - width) / 2, y + (16 - mc.font.lineHeight) / 2 + 1,
                0xFFFFFFFF, true);
    }
}
