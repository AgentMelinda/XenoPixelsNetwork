package net.bullettrain.xenopixelsmod.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Preserves the sharp in-world menu background used before Minecraft 1.21 added
 * a post-process blur to {@link Screen#renderBackground}.
 */
public abstract class UnblurredScreen extends Screen {
    protected UnblurredScreen(Component title) {
        super(title);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // renderTransparentBackground applies Minecraft's post-process blur. Keep the world
        // sharp and add only the usual translucent menu dim behind the atlas screen.
        graphics.fill(0, 0, width, height, 0x90000000);
    }
}
