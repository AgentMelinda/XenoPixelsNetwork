package net.bullettrain.xenopixelsmod.client.maker;

import com.dragonminez.client.gui.character.util.ScaledScreen;
import net.bullettrain.xenopixelsmod.client.ui.atlas.AtlasButton;
import net.bullettrain.xenopixelsmod.client.ui.atlas.AtlasPanel;
import net.bullettrain.xenopixelsmod.client.ui.atlas.XenoAtlasSprites;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Unified Maker Studio hub ({@code /xenomaker}).
 *
 * <p>Chrome: gold outer frame + gold {@code banner_top}. Left nav uses wide row buttons with
 * clear vertical gaps (owner 2026-10-03: overlapping pill labels). Right: green preview well
 * with live {@link MakerPreviewController} (true local-player model).
 *
 * <p><b>Not verified in a running game.</b>
 */
public final class XenoMakerHubScreen extends ScaledScreen {
    private static final String FRAME = "xeno_editor_panel";
    private static final String BANNER = "banner_top";
    /** Wide row face — taller hit/label clarity than {@code pill_button} (90×24). */
    private static final String NAV = "mynpcs_button_row_w128";
    private static final XenoAtlasSprites.Theme CHROME = XenoAtlasSprites.Theme.GOLD;
    private static final XenoAtlasSprites.Theme INNER = XenoAtlasSprites.Theme.GREEN;

    private static final int GOLD_TEXT = 0xFFFFC14A;
    /** Extra air between stacked nav buttons so labels never ghost into the next face. */
    private static final int NAV_GAP = 14;

    private final Screen parent;
    private final MakerPreviewController preview = new MakerPreviewController();

    private int frameX;
    private int frameY;
    private int frameW;
    private int frameH;
    private int bannerX;
    private int bannerY;
    private int bannerW;
    private int bannerH;
    private int previewX;
    private int previewY;
    private int previewW;
    private int previewH;
    private int navX;
    private int navY;

    public XenoMakerHubScreen(Screen parent) {
        super(Component.literal("Xeno Maker Studio"));
        this.parent = parent;
    }

    @Override
    protected int getMinGuiWidth() {
        return 640;
    }

    @Override
    protected int getMinGuiHeight() {
        // Banner + large xeno_maker_hair_preview (280×240) + footer.
        return 420;
    }

    @Override
    protected void init() {
        super.init();
        preview.bindLocalPlayer(minecraft);
        preview.setGlow(MakerPreviewController.GlowTarget.RACE_CARD, "hub");

        int[] fitted = XenoAtlasSprites.fittedSize(FRAME, getUiWidth() - 8, getUiHeight() - 8);
        frameW = fitted[0];
        frameH = fitted[1];
        frameX = (getUiWidth() - frameW) / 2;
        frameY = (getUiHeight() - frameH) / 2;

        bannerW = XenoAtlasSprites.get(BANNER).width();
        bannerH = XenoAtlasSprites.get(BANNER).height();
        bannerX = frameX + (frameW - bannerW) / 2;
        bannerY = frameY + 12;

        int btnW = AtlasButton.nativeWidth(NAV);
        int btnH = AtlasButton.nativeHeight(NAV);
        navX = frameX + 36;
        navY = bannerY + bannerH + 28;

        // Preview is the remaining inner rect of the gold frame — never the 280×240 sprite
        // which overflows the orange panel (hair + well past the border).
        int innerPad = 20;
        previewX = navX + btnW + 16;
        previewY = bannerY + bannerH + 22;
        previewW = Math.max(96, frameX + frameW - innerPad - previewX);
        previewH = Math.max(96, frameY + frameH - 28 - previewY);

        clearWidgets();

        int y = navY;
        addRenderableWidget(navButton(navX, y, "Race Maker", b -> openRace()));
        y += btnH + NAV_GAP;
        addRenderableWidget(navButton(navX, y, "Form Maker", b -> openForms()));
        y += btnH + NAV_GAP;
        addRenderableWidget(navButton(navX, y, "Hair Editor", b -> openHair()));
        y += btnH + NAV_GAP + 8;
        addRenderableWidget(navButton(navX, y, "Close", b -> onClose()));

        preview.markDirty();
    }

    private AtlasButton navButton(int x, int y, String label, java.util.function.Consumer<AtlasButton> action) {
        // Explicit bounds = native face size so click target matches the art (no shared overlap).
        int w = AtlasButton.nativeWidth(NAV);
        int h = AtlasButton.nativeHeight(NAV);
        return new AtlasButton(x, y, w, h, Component.literal(label), NAV, action);
    }

    private void openRace() {
        if (minecraft != null) {
            minecraft.setScreen(new RaceCharacterMakerScreen(this));
        }
    }

    private void openForms() {
        if (minecraft != null) {
            minecraft.setScreen(new FormMakerScreen(this));
        }
    }

    private void openHair() {
        if (minecraft != null) {
            minecraft.setScreen(new HairMakerScreen(this));
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xCC030712);
        XenoAtlasSprites.Theme previous = XenoAtlasSprites.theme();
        XenoAtlasSprites.setTheme(CHROME);
        try {
            beginUiScale(graphics);
            AtlasPanel.fittedInto(FRAME, frameX, frameY, getUiWidth() - 8, getUiHeight() - 8)
                    .render(graphics);
            XenoAtlasSprites.blit(graphics, BANNER, CHROME, bannerX, bannerY);
            graphics.drawCenteredString(font, "XENO MAKER STUDIO",
                    bannerX + bannerW / 2, bannerY + bannerH / 2 - 4, GOLD_TEXT);

            graphics.drawString(font, "Editors", navX, navY - 14, GOLD_TEXT, false);

            XenoAtlasSprites.setTheme(CHROME);
            super.render(graphics, (int) toUiX(mouseX), (int) toUiY(mouseY), partialTick);

            // Title sits above the well. The green box is the 3D model only.
            graphics.drawString(font, "Your character", previewX, previewY - 12, GOLD_TEXT, false);
            XenoAtlasSprites.setTheme(INNER);
            preview.render(graphics, previewX, previewY, previewW, previewH, partialTick);

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

    public static XenoMakerHubScreen create(Screen parent) {
        return new XenoMakerHubScreen(parent);
    }
}
