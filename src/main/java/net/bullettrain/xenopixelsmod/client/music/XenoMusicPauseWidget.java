package net.bullettrain.xenopixelsmod.client.music;

import net.bullettrain.xenopixelsmod.client.config.XenoClientConfig;
import net.bullettrain.xenopixelsmod.client.ui.atlas.AtlasSound;
import net.bullettrain.xenopixelsmod.client.ui.atlas.AtlasTextFit;
import net.bullettrain.xenopixelsmod.client.ui.atlas.XenoAtlasSprites;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * The compact music player drawn bottom-left of the pause menu only.
 *
 * <p>One generated plate ({@code xeno_music_panel}, 150x40, green) and four generated chips, all
 * blitted 1:1 per the atlas rule (docs/atlas-ui-doco.md). Hover is the gold palette swap. The
 * mouse wheel over the plate changes the volume. Nothing here is drawn while the pause menu is
 * closed; playback itself continues.
 */
@OnlyIn(Dist.CLIENT)
public final class XenoMusicPauseWidget extends AbstractWidget {
    public static final String PANEL = "xeno_music_panel";
    private static final String CHIP = "ui_chip_w20";
    private static final String WIDE_CHIP = "ui_chip_w32";
    /** Plate margin from the screen's bottom-left corner. */
    public static final int MARGIN = 6;
    private static final int ROW_Y = 19;
    private static final int[] CHIP_X = {6, 28, 50, 76};

    private enum Button { PREVIOUS, PLAY, NEXT, POWER }

    public XenoMusicPauseWidget(int x, int y) {
        super(x, y, XenoAtlasSprites.get(PANEL).width(), XenoAtlasSprites.get(PANEL).height(),
                Component.literal("Music player"));
    }

    /** Pure layout: which button a plate-relative point hits, or null. */
    static Button hit(double localX, double localY) {
        if (localY < ROW_Y || localY >= ROW_Y + 18) return null;
        for (int i = 0; i < CHIP_X.length; i++) {
            int w = i == 3 ? 32 : 20;
            if (localX >= CHIP_X[i] && localX < CHIP_X[i] + w) return Button.values()[i];
        }
        return null;
    }

    /** Exposed for tests: the index of the button under a plate-relative point, -1 for none. */
    public static int hitIndex(double localX, double localY) {
        Button button = hit(localX, localY);
        return button == null ? -1 : button.ordinal();
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        XenoAtlasSprites.blit(graphics, PANEL, XenoAtlasSprites.Theme.GREEN, getX(), getY());
        var font = Minecraft.getInstance().font;
        boolean on = XenoClientConfig.musicEnabled;
        var tracks = XenoClientConfig.musicTracks;
        int index = tracks.isEmpty() ? 0 : Math.floorMod(XenoClientConfig.musicTrack, tracks.size()) + 1;
        String title = (on ? "\u266A " : "\u266A off  ") + XenoMusicPlayer.currentTrackName()
                + "  " + index + "/" + tracks.size();
        int available = getWidth() - 12;
        float scale = AtlasTextFit.scale(font.width(title), available);
        String label = AtlasTextFit.fit(title, available, scale, font::width);
        graphics.pose().pushPose();
        graphics.pose().translate(getX() + 6, getY() + 5, 0);
        graphics.pose().scale(scale, scale, 1f);
        graphics.drawString(font, label, 0, 0, on ? 0xFFE0F0FF : 0xFF9AA8B4, false);
        graphics.pose().popPose();

        Button hovered = isHovered() ? hit(mouseX - getX(), mouseY - getY()) : null;
        for (Button button : Button.values()) {
            String sprite = button == Button.POWER ? WIDE_CHIP : CHIP;
            int bx = getX() + CHIP_X[button.ordinal()];
            int by = getY() + ROW_Y;
            if (button == hovered) XenoAtlasSprites.blit(graphics, sprite, XenoAtlasSprites.Theme.GOLD, bx, by);
            else XenoAtlasSprites.blit(graphics, sprite, XenoAtlasSprites.Theme.GREEN, bx, by);
            String text = switch (button) {
                case PREVIOUS -> "<<";
                case PLAY -> XenoMusicPlayer.playing() ? "||" : ">";
                case NEXT -> ">>";
                case POWER -> on ? "ON" : "OFF";
            };
            int w = button == Button.POWER ? 32 : 20;
            int color = button == hovered ? 0xFFFFFFFF : on ? 0xFFE0F0FF : 0xFF9AA8B4;
            graphics.drawCenteredString(font, text, bx + w / 2, by + 5, color);
        }
        if (hovered == null && isHovered()) {
            setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(
                    "Volume " + Math.round(XenoClientConfig.musicVolume * 100) + "%  (scroll to change)")));
        } else {
            setTooltip(null);
        }
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        Button button = hit(mouseX - getX(), mouseY - getY());
        if (button == null) return;
        AtlasSound.click();
        switch (button) {
            case PREVIOUS -> XenoMusicPlayer.previous();
            case PLAY -> XenoMusicPlayer.playPause();
            case NEXT -> XenoMusicPlayer.next();
            case POWER -> XenoMusicPlayer.toggle();
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!isMouseOver(mouseX, mouseY) || scrollY == 0) return false;
        XenoMusicPlayer.setVolume(XenoClientConfig.musicVolume + (float) (scrollY > 0 ? 0.05 : -0.05));
        return true;
    }

    /** Vanilla's click is silenced; {@link AtlasSound} plays DragonMineZ's from {@link #onClick}. */
    @Override
    public void playDownSound(SoundManager handler) {}

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
