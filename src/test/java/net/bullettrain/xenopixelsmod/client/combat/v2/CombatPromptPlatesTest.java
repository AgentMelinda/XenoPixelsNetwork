package net.bullettrain.xenopixelsmod.client.combat.v2;

import net.bullettrain.xenopixelsmod.client.ui.atlas.XenoAtlasSprites;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayDeque;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The combat-prompt plates as shipped: every one exists at every width in the green palette, is
 * one clean shape, and has room for a line of text exactly where {@link PromptPlate} says.
 *
 * <p>The first v2 prompt art was cropped out of a larger sheet and shipped with stray pixels and
 * a faint halo around it that only showed in game, on a dark background. These plates come from
 * the panel generator instead, and the two checks that would have caught the old art are here:
 * no partly transparent pixels, and a single connected shape.
 */
class CombatPromptPlatesTest {

    private static final int[] WIDTHS = {56, 72, 88, 104, 120};
    /** A line of Minecraft text is eight pixels tall. */
    private static final int TEXT_HEIGHT = 8;

    private static BufferedImage load(PromptPlate plate, int width) throws IOException {
        String shape = XenoAtlasSprites.combatPrompt(plate.kind(), width);
        String path = "assets/xenopixelsmod/"
                + XenoAtlasSprites.get(shape, XenoAtlasSprites.Theme.GREEN).rl().getPath();
        try (InputStream in = CombatPromptPlatesTest.class.getClassLoader().getResourceAsStream(path)) {
            assertNotNull(in, "missing plate: " + path);
            return ImageIO.read(in);
        }
    }

    private static int alpha(BufferedImage image, int x, int y) {
        return image.getRGB(x, y) >>> 24;
    }

    /** The dark green the panel generator fills a green plate with, as opposed to its pale border. */
    private static boolean fill(BufferedImage image, int x, int y) {
        int argb = image.getRGB(x, y);
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        return (argb >>> 24) == 0xFF && r < 24 && b < 24 && g >= 40;
    }

    @Test
    void everyPlateIsRegisteredAtItsNativeSize() throws IOException {
        for (PromptPlate plate : PromptPlate.values()) {
            for (int width : WIDTHS) {
                String shape = XenoAtlasSprites.combatPrompt(plate.kind(), width);
                XenoAtlasSprites.Sprite sprite = XenoAtlasSprites.get(shape, XenoAtlasSprites.Theme.GREEN);
                BufferedImage image = load(plate, width);
                assertEquals(width, sprite.width(), shape);
                assertEquals(plate.height(), sprite.height(), shape);
                assertEquals(sprite.width(), image.getWidth(), shape + " is blitted 1:1");
                assertEquals(sprite.height(), image.getHeight(), shape + " is blitted 1:1");
            }
        }
    }

    @Test
    void thePickerReturnsTheNarrowestPlateThatHoldsTheText() {
        assertTrue(XenoAtlasSprites.combatPrompt("light", 1).endsWith("_w56"));
        assertTrue(XenoAtlasSprites.combatPrompt("light", 56).endsWith("_w56"));
        assertTrue(XenoAtlasSprites.combatPrompt("light", 57).endsWith("_w72"));
        assertTrue(XenoAtlasSprites.combatPrompt("alert", 9999).endsWith("_w120"),
                "text too long for any plate gets the widest, never a missing sprite");
        assertEquals(120, XenoAtlasSprites.maxCombatPromptWidth());
    }

    @Test
    void noPlateHasPartlyTransparentPixels() throws IOException {
        for (PromptPlate plate : PromptPlate.values()) {
            for (int width : WIDTHS) {
                BufferedImage image = load(plate, width);
                for (int y = 0; y < image.getHeight(); y++) {
                    for (int x = 0; x < image.getWidth(); x++) {
                        int a = alpha(image, x, y);
                        assertTrue(a == 0 || a == 0xFF,
                                plate + " w" + width + " has a soft pixel at " + x + "," + y);
                    }
                }
            }
        }
    }

    @Test
    void everyPlateIsOneConnectedShape() throws IOException {
        for (PromptPlate plate : PromptPlate.values()) {
            for (int width : WIDTHS) {
                BufferedImage image = load(plate, width);
                int w = image.getWidth();
                int h = image.getHeight();
                boolean[] seen = new boolean[w * h];
                int shapes = 0;
                for (int start = 0; start < w * h; start++) {
                    if (seen[start] || alpha(image, start % w, start / w) == 0) continue;
                    shapes++;
                    ArrayDeque<Integer> open = new ArrayDeque<>();
                    open.add(start);
                    seen[start] = true;
                    while (!open.isEmpty()) {
                        int at = open.poll();
                        int x = at % w;
                        int y = at / w;
                        for (int dy = -1; dy <= 1; dy++) {
                            for (int dx = -1; dx <= 1; dx++) {
                                int nx = x + dx;
                                int ny = y + dy;
                                if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue;
                                int next = ny * w + nx;
                                if (seen[next] || alpha(image, nx, ny) == 0) continue;
                                seen[next] = true;
                                open.add(next);
                            }
                        }
                    }
                }
                assertEquals(1, shapes, plate + " w" + width + " has stray pixels off the plate");
            }
        }
    }

    /** The box a label is drawn in lies wholly on the dark fill, never on the frame. */
    @Test
    void theTextBoxSitsOnTheFillOfEveryPlate() throws IOException {
        for (PromptPlate plate : PromptPlate.values()) {
            for (int width : WIDTHS) {
                BufferedImage image = load(plate, width);
                for (int y = plate.textY(); y < plate.textY() + TEXT_HEIGHT; y++) {
                    for (int x = plate.insetLeft(); x < width - plate.insetRight(); x++) {
                        assertTrue(fill(image, x, y), plate + " w" + width
                                + ": text would cross the frame at " + x + "," + y);
                    }
                }
            }
        }
    }

    /** And the insets are not padded: one column further out is already the frame somewhere. */
    @Test
    void theInsetsAreAsTightAsTheArtAllows() throws IOException {
        for (PromptPlate plate : PromptPlate.values()) {
            BufferedImage image = load(plate, 88);
            boolean leftFrame = false;
            boolean rightFrame = false;
            for (int y = plate.textY(); y < plate.textY() + TEXT_HEIGHT; y++) {
                leftFrame |= !fill(image, plate.insetLeft() - 1, y);
                rightFrame |= !fill(image, 88 - plate.insetRight(), y);
            }
            assertTrue(leftFrame, plate + ": left inset is wider than the frame needs");
            assertTrue(rightFrame, plate + ": right inset is wider than the frame needs");
        }
    }

    @Test
    void textIsCentredInTheFillAndAPlateIsSizedToHoldIt() {
        for (PromptPlate plate : PromptPlate.values()) {
            int text = 40;
            int width = plate.widthFor(text);
            assertEquals(text, plate.textRoom(width), plate.name());
            int x = plate.textX(width, text);
            assertEquals(plate.insetLeft() + PromptPlate.TEXT_PAD, x, plate.name());
            assertEquals(width - plate.insetRight() - PromptPlate.TEXT_PAD, x + text, plate.name());
            // Text wider than the plate starts at the fill's edge rather than left of it.
            assertEquals(plate.insetLeft(), plate.textX(56, 500), plate.name());
        }
    }

    @Test
    void theShortPromptsFitTheNarrowPlates() {
        // "LMB Punch" is about 50 pixels in the default font; a pill for it must not need the
        // widest plate, or four branches side by side would not fit a small window.
        assertTrue(PromptPlate.PUNCH.widthFor(50) <= 72);
        assertFalse(PromptPlate.ALERT.height() == PromptPlate.PUNCH.height(),
                "the alert plate is the tall one");
    }
}
