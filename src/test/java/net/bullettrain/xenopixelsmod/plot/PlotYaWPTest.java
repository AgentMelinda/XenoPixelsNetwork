package net.bullettrain.xenopixelsmod.plot;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlotYaWPTest {

    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private static PlotArea plot(ResourceLocation dimension, int minX, int minZ, int maxX, int maxZ) {
        return new PlotArea(dimension, minX, minZ, maxX, maxZ, OWNER, PlotFlags.DEFAULT);
    }

    @Test
    void regionNameIsDeterministic() {
        PlotArea a = plot(ResourceLocation.parse("minecraft:overworld"), 10, -20, 40, 5);
        PlotArea b = plot(ResourceLocation.parse("minecraft:overworld"), 10, -20, 99, 99);
        assertEquals(PlotYaWP.regionName(a), PlotYaWP.regionName(b),
                "same dimension and min corner must map to the same region name");
    }

    @Test
    void regionNameDistinguishesDifferentPlots() {
        ResourceLocation overworld = ResourceLocation.parse("minecraft:overworld");
        assertNotEquals(
                PlotYaWP.regionName(plot(overworld, 0, 0, 10, 10)),
                PlotYaWP.regionName(plot(overworld, 11, 0, 20, 10)));
        assertNotEquals(
                PlotYaWP.regionName(plot(overworld, 0, 0, 10, 10)),
                PlotYaWP.regionName(plot(ResourceLocation.parse("minecraft:the_nether"), 0, 0, 10, 10)));
    }

    @Test
    void regionNameIsPrefixedAndSanitized() {
        String name = PlotYaWP.regionName(
                plot(ResourceLocation.parse("xeno_pixels:sky_islands"), -5, 7, 5, 17));
        assertTrue(name.startsWith("xenoplot_"), name);
        // ':' and every other non-alphanumeric become '_', and a coordinate sign is spelled out,
        // so the whole name stays inside a conservative character set.
        assertTrue(name.matches("[a-z0-9_]+"), name);
        assertTrue(name.contains("xeno_pixels"), name);
        assertTrue(name.contains("sky_islands"), name);
    }

    @Test
    void negativeCoordinatesAreEncodedWithoutAmbiguity() {
        ResourceLocation overworld = ResourceLocation.parse("minecraft:overworld");
        assertEquals("xenoplot_minecraft_overworld_m5_2",
                PlotYaWP.regionName(plot(overworld, -5, 2, 0, 4)));
        // -5 must not collide with 5, and the sign must not leave a '-' in the name.
        assertNotEquals(
                PlotYaWP.regionName(plot(overworld, -5, 2, 0, 4)),
                PlotYaWP.regionName(plot(overworld, 5, 2, 10, 4)));
    }

    @Test
    void sanitizeCollapsesNamespaceAndPathSeparator() {
        String name = PlotYaWP.regionName(plot(ResourceLocation.parse("minecraft:overworld"), 1, 2, 3, 4));
        assertEquals("xenoplot_minecraft_overworld_1_2", name);
    }

    @Test
    void removeAndSyncAreSafeWhenYaWPIsAbsent() {
        // YAWP is not on the unit-test classpath, so both must degrade to false rather than throw.
        assertFalse(PlotYaWP.sync(null, plot(ResourceLocation.parse("minecraft:overworld"), 0, 0, 1, 1)));
        assertFalse(PlotYaWP.remove(null, plot(ResourceLocation.parse("minecraft:overworld"), 0, 0, 1, 1)));
        assertFalse(PlotYaWP.present(null, plot(ResourceLocation.parse("minecraft:overworld"), 0, 0, 1, 1)));
    }
}