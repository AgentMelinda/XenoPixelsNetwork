package net.bullettrain.xenopixelsmod.client.screen;

import net.bullettrain.xenopixelsmod.api.network.AddonNetwork;
import net.bullettrain.xenopixelsmod.space.dragonball.WishChoice;
import net.bullettrain.xenopixelsmod.space.dragonball.WishOffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The dragon's wish list after a summon (design-doc 16 v1). One button per catalog wish, description as tooltip; picking
 * one sends {@link WishChoice}. Closing the screen without choosing keeps the balls (the offer expires on the server).
 */
public class WishScreen extends Screen {
    private static final int COLUMN = 200, ROW = 24;
    private final WishOffer offer;

    private WishScreen(WishOffer offer) {
        super(Component.translatable("screen.xenopixelsmod.wish.title"));
        this.offer = offer;
    }

    public static void open(WishOffer offer) { Minecraft.getInstance().setScreen(new WishScreen(offer)); }

    @Override protected void init() {
        int columns = offer.entries().size() > 8 ? 2 : 1;
        int rows = (offer.entries().size() + columns - 1) / columns;
        int left = width / 2 - (columns * (COLUMN + 8) - 8) / 2, top = Math.max(40, height / 2 - rows * ROW / 2);
        for (int i = 0; i < offer.entries().size(); i++) {
            var entry = offer.entries().get(i);
            int x = left + (i / rows) * (COLUMN + 8), y = top + (i % rows) * ROW;
            var button = Button.builder(Component.literal(entry.name()), b -> {
                AddonNetwork.sendToServer(new WishChoice(entry.id()));
                onClose();
            }).bounds(x, y, COLUMN, 20).build();
            if (!entry.description().isEmpty()) button.setTooltip(Tooltip.create(Component.literal(entry.description())));
            addRenderableWidget(button);
        }
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, 16, 0xFFB347);
        graphics.drawCenteredString(font, Component.translatable("screen.xenopixelsmod.wish.subtitle"), width / 2, 28, 0xD0D0D0);
    }

    @Override public boolean isPauseScreen() { return false; }
}
