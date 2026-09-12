package net.bullettrain.xenopixelsmod.dmz.form;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DmzFormPlaceholderIconsTest {
    @Test
    void allFiftyPlaceholdersExistInRadialAndSkillLayouts() throws IOException {
        for (int index = 1; index <= 50; index++) {
            String name = "xeno_form_%02d.png".formatted(index);
            BufferedImage radial = read("/assets/xenopixelsmod/textures/gui/radial/" + name);
            BufferedImage skill = read("/assets/xenopixelsmod/textures/gui/icons/" + name);
            assertEquals(64, radial.getWidth(), name + " radial width");
            assertEquals(64, radial.getHeight(), name + " radial height");
            assertEquals(64, skill.getWidth(), name + " skill width");
            assertEquals(64, skill.getHeight(), name + " skill height");
            assertTrue(hasTransparentAndVisiblePixels(radial), name + " needs usable alpha");
            assertTrue(hasTransparentAndVisiblePixels(skill), name + " needs usable alpha");
        }
    }

    private static BufferedImage read(String path) throws IOException {
        try (InputStream stream = DmzFormPlaceholderIconsTest.class.getResourceAsStream(path)) {
            assertNotNull(stream, "missing resource: " + path);
            return ImageIO.read(stream);
        }
    }

    private static boolean hasTransparentAndVisiblePixels(BufferedImage image) {
        boolean transparent = false;
        boolean visible = false;
        for (int y = 0; y < image.getHeight() && !(transparent && visible); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int alpha = image.getRGB(x, y) >>> 24;
                transparent |= alpha == 0;
                visible |= alpha > 0;
            }
        }
        return transparent && visible;
    }
}
