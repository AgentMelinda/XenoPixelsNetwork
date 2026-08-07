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
        this.renderTransparentBackground(graphics);
    }
}
