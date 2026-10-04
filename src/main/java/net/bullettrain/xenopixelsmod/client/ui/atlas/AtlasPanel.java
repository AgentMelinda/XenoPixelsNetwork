package net.bullettrain.xenopixelsmod.client.ui.atlas;

import net.minecraft.client.gui.GuiGraphics;

/**
 * A non-interactive background panel.
 *
 * <p>Use {@link #ofNative} wherever the sprite's own size is acceptable, which should be almost
 * everywhere - the atlas ships a separate PNG per size precisely so that panels never have to be
 * resized at runtime. {@link #fittedInto} exists only for the case a frame genuinely cannot fit the
 * viewport, and it scales uniformly rather than distorting the border.
 */
public record AtlasPanel(String sprite, int x, int y, int width, int height, boolean fitted) {

    /** A panel at the sprite's shipped size - the form to reach for first. */
    public static AtlasPanel ofNative(String sprite, int x, int y) {
        XenoAtlasSprites.Sprite s = XenoAtlasSprites.get(sprite);
        return new AtlasPanel(sprite, x, y, s.width(), s.height(), false);
    }

    /** A panel shrunk uniformly to fit a viewport too small for the sprite. */
    public static AtlasPanel fittedInto(String sprite, int x, int y, int maxW, int maxH) {
        int[] size = XenoAtlasSprites.fittedSize(sprite, maxW, maxH);
        return new AtlasPanel(sprite, x, y, size[0], size[1], size[0] != XenoAtlasSprites
                .get(sprite).width());
    }

    public void render(GuiGraphics graphics) {
        if (fitted) {
            XenoAtlasSprites.blitSized(graphics, sprite, x, y, width, height);
        } else {
            XenoAtlasSprites.blit(graphics, sprite, x, y);
        }
    }

    public boolean contains(double mouseX, double mouseY) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }
}
