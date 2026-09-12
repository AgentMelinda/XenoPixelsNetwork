package net.bullettrain.xenopixelsmod.plot;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlotAreaTest {

    private static final ResourceLocation OVERWORLD = ResourceLocation.parse("minecraft:overworld");
    private static final ResourceLocation NETHER = ResourceLocation.parse("minecraft:the_nether");
    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private static PlotArea plot(ResourceLocation dimension, int minX, int minZ, int maxX, int maxZ) {
        return new PlotArea(dimension, minX, minZ, maxX, maxZ, OWNER, PlotFlags.DEFAULT);
    }

    @Test
    void normalizesCornersRegardlessOfClickOrder() {
        PlotArea a = PlotArea.of(OVERWORLD, 10, 20, -5, -8, OWNER, PlotFlags.DEFAULT);
        assertEquals(-5, a.minX());
        assertEquals(-8, a.minZ());
        assertEquals(10, a.maxX());
        assertEquals(20, a.maxZ());
    }

    @Test
    void measuresFootprintWithoutY() {
        PlotArea area = plot(OVERWORLD, 0, 0, 9, 4);
        assertEquals(10, area.width());
        assertEquals(5, area.length());
        assertEquals(50, area.area());
    }

    @Test
    void containsIsInclusiveOfBounds() {
        PlotArea area = plot(OVERWORLD, 0, 0, 10, 10);
        assertTrue(area.contains(0, 0));
        assertTrue(area.contains(10, 10));
        assertTrue(area.contains(5, 5));
        assertFalse(area.contains(11, 5));
        assertFalse(area.contains(5, -1));
    }

    @Test
    void detectsOverlapAcrossDimensions() {
        PlotArea a = plot(OVERWORLD, 0, 0, 10, 10);
        assertTrue(a.overlaps(plot(OVERWORLD, 5, 5, 15, 15)));
        assertTrue(a.overlaps(plot(OVERWORLD, -5, -5, 0, 0)));
        assertFalse(a.overlaps(plot(OVERWORLD, 11, 0, 20, 10)));
        assertFalse(a.overlaps(plot(NETHER, 0, 0, 10, 10)));
        assertFalse(a.overlaps(null));
    }

    @Test
    void touchingEdgesAreNotAnOverlap() {
        PlotArea a = plot(OVERWORLD, 0, 0, 10, 10);
        assertFalse(a.overlaps(plot(OVERWORLD, 11, 0, 20, 10)));
        assertFalse(a.overlaps(plot(OVERWORLD, 0, 11, 10, 20)));
    }

    @Test
    void enclosesOnlyFullyContainedPlots() {
        PlotArea outer = plot(OVERWORLD, 0, 0, 20, 20);
        assertTrue(outer.encloses(plot(OVERWORLD, 5, 5, 10, 10)));
        assertTrue(outer.encloses(outer));
        assertFalse(outer.encloses(plot(OVERWORLD, 5, 5, 25, 10)));
        assertFalse(outer.encloses(plot(NETHER, 5, 5, 10, 10)));
    }

    @Test
    void ownershipComparesById() {
        assertTrue(plot(OVERWORLD, 0, 0, 1, 1).ownedBy(OWNER));
        assertFalse(plot(OVERWORLD, 0, 0, 1, 1).ownedBy(UUID.randomUUID()));
    }

    @Test
    void flagsPackAndReadBack() {
        int flags = PlotFlags.set(PlotFlags.DEFAULT, PlotFlags.ALLOW_BUILD, true);
        assertTrue(PlotFlags.has(flags, PlotFlags.ALLOW_BUILD));
        assertFalse(PlotFlags.has(flags, PlotFlags.ALLOW_CONTAINERS));
        int cleared = PlotFlags.set(flags, PlotFlags.ALLOW_BUILD, false);
        assertFalse(PlotFlags.has(cleared, PlotFlags.ALLOW_BUILD));
    }
}