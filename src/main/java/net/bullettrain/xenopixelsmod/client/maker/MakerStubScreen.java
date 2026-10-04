package net.bullettrain.xenopixelsmod.client.maker;

import com.dragonminez.client.gui.character.util.ScaledScreen;
import net.bullettrain.xenopixelsmod.client.ui.atlas.AtlasButton;
import net.bullettrain.xenopixelsmod.client.ui.atlas.AtlasPanel;
import net.bullettrain.xenopixelsmod.client.ui.atlas.XenoAtlasSprites;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Placeholder maker mode retained for any remaining hub stubs.
 */
public final class MakerStubScreen extends ScaledScreen {
    private static final String FRAME = "xeno_editor_panel";
    private static final String PRIMARY = "pill_button";
    private static final XenoAtlasSprites.Theme THEME = XenoAtlasSprites.Theme.GREEN;

    private final Screen parent;
    private final String heading;
    private final String detail;

    private int frameX;
    private int frameY;
    private int frameW;
    private int frameH;

    public MakerStubScreen(Screen parent, String title, String heading, String detail) {
        super(Component.literal(title == null ? "Maker" : title));
        this.parent = parent;
        this.heading = heading == null ? "Coming soon" : heading;
        this.detail = detail == null ? "" : detail;
    }

    @Override
    protected void init() {
        super.init();
        int[] fitted = XenoAtlasSprites.fittedSize(FRAME, getUiWidth() - 8, getUiHeight() - 8);
        frameW = fitted[0];
        frameH = fitted[1];
        frameX = (getUiWidth() - frameW) / 2;
        frameY = (getUiHeight() - frameH) / 2;
        clearWidgets();
        addRenderableWidget(new AtlasButton(frameX + 24, frameY + frameH - 40,
                Component.literal("Back"), PRIMARY, b -> onClose()));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xCC030712);
        XenoAtlasSprites.Theme previous = XenoAtlasSprites.theme();
        XenoAtlasSprites.setTheme(THEME);
        try {
            beginUiScale(graphics);
            AtlasPanel.fittedInto(FRAME, frameX, frameY, getUiWidth() - 8, getUiHeight() - 8)
                    .render(graphics);
            graphics.drawString(font, heading, frameX + 24, frameY + 24, 0xFF9AFFB0, false);
            graphics.drawString(font, detail, frameX + 24, frameY + 56, 0xFFE7EDF3, false);
            graphics.drawString(font, "Existing open paths (/xenoraceformui, /xenohairui) stay unchanged.",
                    frameX + 24, frameY + 88, 0xFF6E9680, false);
            super.render(graphics, (int) toUiX(mouseX), (int) toUiY(mouseY), partialTick);
            endUiScale(graphics);
        } finally {
            XenoAtlasSprites.setTheme(previous);
        }
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
