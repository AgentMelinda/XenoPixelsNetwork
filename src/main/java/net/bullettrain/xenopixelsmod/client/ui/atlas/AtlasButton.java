package net.bullettrain.xenopixelsmod.client.ui.atlas;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/**
 * A button whose face is one atlas sprite, drawn at that sprite's native size.
 *
 * <p>Pick the shape that is already the size you want rather than asking for a size and letting the
 * art stretch into it. The bundle ships a separate PNG per size for exactly this reason and bakes
 * the border proportions in at generation time, so a stretched face reads as a distorted frame.
 *
 * <p>Hover is a palette swap to the gold cut of the same shape, which is the convention the
 * bundle's integration notes suggest in place of dedicated lit artwork.
 */
public class AtlasButton extends AbstractWidget {
    private final String sprite;
    private final Consumer<AtlasButton> action;
    private final float groupScale;

    /** Sizes the button from the sprite - the preferred form. */
    public AtlasButton(int x, int y, Component message, String sprite,
                       Consumer<AtlasButton> action) {
        this(x, y, XenoAtlasSprites.get(sprite).width(), XenoAtlasSprites.get(sprite).height(),
                message, sprite, action);
    }

    /**
     * Explicit-bounds form. The face still blits at its native size and is centred in the bounds;
     * any extra area only widens the click target.
     */
    public AtlasButton(int x, int y, int width, int height, Component message,
                       String sprite, Consumer<AtlasButton> action) {
        this(x, y, width, height, message, sprite, action, Float.NaN);
    }

    /** Explicit sibling-group scale; {@link Float#NaN} keeps the standalone fitting behavior. */
    public AtlasButton(int x, int y, Component message, String sprite,
                       Consumer<AtlasButton> action, float groupScale) {
        this(x, y, XenoAtlasSprites.get(sprite).width(), XenoAtlasSprites.get(sprite).height(),
                message, sprite, action, groupScale);
    }

    public AtlasButton(int x, int y, int width, int height, Component message,
                       String sprite, Consumer<AtlasButton> action, float groupScale) {
        super(x, y, width, height, message);
        this.sprite = sprite;
        this.action = action;
        this.groupScale = groupScale;
    }

    /** Native width of the given shape, so callers can lay out a row of buttons. */
    public static int nativeWidth(String sprite) {
        return XenoAtlasSprites.get(sprite).width();
    }

    /** Native height of the given shape. */
    public static int nativeHeight(String sprite) {
        return XenoAtlasSprites.get(sprite).height();
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        XenoAtlasSprites.Sprite s = XenoAtlasSprites.get(sprite);
        int faceX = getX() + (getWidth() - s.width()) / 2;
        int faceY = getY() + (getHeight() - s.height()) / 2;

        boolean lit = isHoveredOrFocused() && active;
        if (lit) {
            XenoAtlasSprites.blit(graphics, sprite, XenoAtlasSprites.Theme.GOLD, faceX, faceY);
        } else {
            XenoAtlasSprites.blit(graphics, sprite, faceX, faceY);
        }

        var font = Minecraft.getInstance().font;
        String fullLabel = getMessage().getString();
        int available = Math.max(4, s.width() - 8);
        float textScale = Float.isNaN(groupScale)
                ? AtlasTextFit.scale(font.width(fullLabel), available) : groupScale;
        String label = AtlasTextFit.fit(fullLabel, available, textScale, font::width);
        if (!label.equals(fullLabel) && getTooltip() == null) {
            setTooltip(Tooltip.create(getMessage()));
        }

        int color = !active ? 0xFF7A8A96 : lit ? 0xFFFFFFFF : 0xFFE0F0FF;
        graphics.pose().pushPose();
        graphics.pose().translate(getX() + getWidth() / 2.0,
                getY() + getHeight() / 2.0, 0.0);
        graphics.pose().scale(textScale, textScale, 1.0f);
        graphics.drawCenteredString(font, label, 0, -font.lineHeight / 2, color);
        graphics.pose().popPose();
    }

    /**
     * Silences vanilla's click.
     *
     * <p>{@code AbstractWidget.mouseClicked} plays {@code UI_BUTTON_CLICK} before {@code onClick}
     * runs, so without this every control would click twice - vanilla's, then DragonMineZ's. The
     * DMZ sound is played from {@code onClick} instead, via {@link AtlasSound}.
     */
    @Override
    public void playDownSound(net.minecraft.client.sounds.SoundManager handler) {
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        AtlasSound.click();
        if (action != null) {
            action.accept(this);
        }
    }
}
