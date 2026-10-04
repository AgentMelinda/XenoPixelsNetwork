package net.bullettrain.xenopixelsmod.client.ui.atlas;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * The small square beside a colour field: the generated swatch frame in blue, gold while its
 * inline picker is open, filled with the field's current colour. Clicking opens the picker.
 */
public final class ColorSwatch extends AbstractWidget {
    public static final int W = 16;
    public static final int H = 18;

    private final Supplier<String> hexSource;
    private final Runnable onPick;
    private final BooleanSupplier picking;

    public ColorSwatch(int x, int y, Supplier<String> hexSource, Runnable onPick, BooleanSupplier picking) {
        super(x, y, W, H, Component.literal("Pick colour"));
        this.hexSource = hexSource;
        this.onPick = onPick;
        this.picking = picking == null ? () -> false : picking;
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        boolean gold = picking.getAsBoolean() || (isHoveredOrFocused() && isActive());
        XenoAtlasSprites.blit(g, "xeno_swatch_frame",
                gold ? XenoAtlasSprites.Theme.GOLD : XenoAtlasSprites.Theme.BLUE, getX(), getY());
        int rgb = net.bullettrain.xenopixelsmod.compat.npc.NpcCombatProfile
                .parseHexColor(hexSource.get()).orElse(0xFFFFFF);
        int fill = isActive() ? 0xFF000000 | rgb : 0xFF808080 | (rgb & 0x7F7F7F);
        g.fill(getX() + 3, getY() + 3, getX() + W - 3, getY() + H - 3, fill);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && isActive() && isMouseOver(mouseX, mouseY)) {
            playDownSound(Minecraft.getInstance().getSoundManager());
            onPick.run();
            return true;
        }
        return false;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narration) {
        defaultButtonNarrationText(narration);
    }
}
