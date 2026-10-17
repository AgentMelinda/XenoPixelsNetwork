package net.bullettrain.xenopixelsmod.space.math;

import org.junit.jupiter.api.Test;
import javax.imageio.ImageIO;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class PlanetSkyAtlasTest {
    @Test void hdAtlasHasThreeSquareCellsAndTransparentPadding() throws Exception {
        var path = Path.of(System.getProperty("xenopixels.projectDir", "."),
                "src/main/resources/assets/xenopixelsmod/textures/space/planets_hd_v1.png");
        var image = ImageIO.read(path.toFile());
        assertNotNull(image);
        assertTrue(image.getColorModel().hasAlpha(), "sky must show through sprite padding");
        assertEquals(image.getHeight() * 3, image.getWidth(), "three square globe cells");
        assertTrue(image.getHeight() >= 512, "detail must exceed the old vertex-color mesh");
        assertTrue(image.getWidth() <= 4096, "keep authored texture size bounded");
        int size = image.getHeight();
        for (int slot = 0; slot < 3; slot++) {
            int x = slot * size;
            assertTrue((image.getRGB(x + size/2, size/2) >>> 24) >= 250, "nearly opaque globe center");
            for (int dx : new int[]{4, size-5}) for (int y : new int[]{4, size-5}) {
                assertEquals(0, image.getRGB(x+dx, y) >>> 24, "transparent square corners");
            }
        }
    }
}
