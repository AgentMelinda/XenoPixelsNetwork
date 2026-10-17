package net.bullettrain.xenopixelsmod.client.space;

import net.bullettrain.xenopixelsmod.space.dragonball.CosmicDragonBalls;
import net.bullettrain.xenopixelsmod.space.dragonball.DragonBallIdentity;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class PlanetSurfaceTextureTest {
    private static final ResourceLocation SDB_SURFACE = ResourceLocation.fromNamespaceAndPath(
            "xenopixelsmod", "textures/space/sdb_planet_surface_hd_v1.png");

    @Test
    void allPhysicalSuperDragonBallsResolveToTheShippedHdSurface() throws IOException {
        for (int slot = 1; slot <= CosmicDragonBalls.COUNT; slot++) {
            var identity = DragonBallIdentity.forSlot(slot);
            assertEquals(SDB_SURFACE, PlanetSurfaceTexture.forBody(identity.planetKey()),
                    "SDB slot " + slot + " should use the physical planet surface map");
        }

        try (var stream = getClass().getResourceAsStream("/assets/xenopixelsmod/textures/space/sdb_planet_surface_hd_v1.png")) {
            assertNotNull(stream, "the renderer's SDB map must be packaged as a main resource");
            var image = ImageIO.read(stream);
            assertNotNull(image);
            assertEquals(2048, image.getWidth());
            assertEquals(1024, image.getHeight());
        }
    }
}
