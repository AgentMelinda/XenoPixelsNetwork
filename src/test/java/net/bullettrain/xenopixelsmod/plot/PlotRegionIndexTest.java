package net.bullettrain.xenopixelsmod.plot;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class PlotRegionIndexTest {

    private static final ResourceLocation OVERWORLD = ResourceLocation.parse("minecraft:overworld");
    private static final ResourceLocation NETHER = ResourceLocation.parse("minecraft:the_nether");
    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000000004");

    private static PlotArea plot(ResourceLocation dimension, int minX, int minZ) {
        return new PlotArea(dimension, minX, minZ, minX + 10, minZ + 10, OWNER, PlotFlags.DEFAULT);
    }

    @Test
    void theKeyCarriesTheDimensionAndBothCoordinates() {
        assertEquals("minecraft:overworld#4,8", PlotRegionIndex.key(plot(OVERWORLD, 4, 8)));
    }

    @Test
    void negativeAndPositiveCoordinatesCannotCollide() {
        assertNotEquals(PlotRegionIndex.key(plot(OVERWORLD, -5, 0)),
                PlotRegionIndex.key(plot(OVERWORLD, 5, 0)));
        assertNotEquals(PlotRegionIndex.key(plot(OVERWORLD, 0, -5)),
                PlotRegionIndex.key(plot(OVERWORLD, 0, 5)));
    }

    @Test
    void theSameTupleInTwoDimensionsIsTwoKeys() {
        assertNotEquals(PlotRegionIndex.key(plot(OVERWORLD, 1, 2)),
                PlotRegionIndex.key(plot(NETHER, 1, 2)));
    }

    @Test
    void keyIsStableAcrossEqualButDistinctPlots() {
        assertEquals(PlotRegionIndex.key(plot(OVERWORLD, 4, 8)),
                PlotRegionIndex.key(plot(OVERWORLD, 4, 8)));
    }

    @Test
    void anEntryRecordsTheRegionNameAndTick() {
        PlotRegionIndex.Entry entry = new PlotRegionIndex.Entry("xenoplot_minecraft_overworld_4_8", 1200L);
        assertEquals("xenoplot_minecraft_overworld_4_8", entry.regionName());
        assertEquals(1200L, entry.tick());
    }

    @Test
    void theThreeStatesTheCommandReportsAreRepresentable() {
        assertNotNull(PlotRegionIndex.State.valueOf("NEVER_SYNCED"));
        assertNotNull(PlotRegionIndex.State.valueOf("PRESENT"));
        assertNotNull(PlotRegionIndex.State.valueOf("ABSENT"));
    }
}