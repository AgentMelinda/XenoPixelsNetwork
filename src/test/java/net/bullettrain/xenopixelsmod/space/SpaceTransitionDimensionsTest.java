package net.bullettrain.xenopixelsmod.space;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpaceTransitionDimensionsTest {
    @Test void sharedSmallPlanetSurfaceWorldIsRegisteredAsAPlanetSurface() {
        assertTrue(SpaceTransitionDimensions.PLANET_SURFACES.contains(
                ResourceLocation.fromNamespaceAndPath("xenopixelsmod", "planet_surfaces")));
    }

    @Test void transitionSurfaceRegistryDoesNotContainDuplicateDimensions() {
        assertEquals(SpaceTransitionDimensions.PLANET_SURFACES.size(),
                new HashSet<>(SpaceTransitionDimensions.PLANET_SURFACES).size());
    }
}
