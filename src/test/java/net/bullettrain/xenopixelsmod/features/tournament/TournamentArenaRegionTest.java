package net.bullettrain.xenopixelsmod.features.tournament;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class TournamentArenaRegionTest {

    @Test
    void aroundCellArenaUsesStructurePad() {
        BlockPos origin = new BlockPos(100, 64, -50);
        TournamentArenaRegion region = TournamentArenaRegion.aroundCellArena(
                "minecraft:overworld", origin);
        assertEquals("cell_arena", region.label());
        assertEquals(100 - TournamentArenaRegion.CELL_PAD_XZ, region.min().getX());
        assertEquals(100 + TournamentArenaRegion.CELL_PAD_XZ, region.max().getX());
        assertTrue(region.contains(new Vec3(100.5, 65.0, -49.5)));
        assertFalse(region.contains(new Vec3(100 + TournamentArenaRegion.CELL_PAD_XZ + 2, 65, -50)));
    }

    @Test
    void roundTripNbtPreservesBoundsAndSpawn() {
        TournamentArenaRegion region = TournamentArenaRegion.fromWorldEdit(
                "minecraft:overworld", "we_selection",
                new BlockPos(10, 70, 20),
                new BlockPos(0, 60, 0),
                new BlockPos(30, 90, 40));
        CompoundTag tag = region.save();
        TournamentArenaRegion loaded = TournamentArenaRegion.load(tag);
        assertEquals(region.spawn(), loaded.spawn());
        assertEquals(region.min(), loaded.min());
        assertEquals(region.max(), loaded.max());
        assertEquals(region.label(), loaded.label());
        assertTrue(loaded.containsBlock(new BlockPos(10, 70, 20)));
        assertFalse(loaded.containsBlock(new BlockPos(100, 70, 20)));
    }

    @Test
    void withSpawnClampsInsideBounds() {
        TournamentArenaRegion region = TournamentArenaRegion.fromWorldEdit(
                "minecraft:overworld", "box",
                new BlockPos(5, 65, 5),
                new BlockPos(0, 60, 0),
                new BlockPos(10, 70, 10));
        TournamentArenaRegion moved = region.withSpawn(new BlockPos(100, 200, 100));
        assertTrue(moved.containsBlock(moved.spawn()));
        assertEquals(10, moved.spawn().getX());
        assertEquals(70, moved.spawn().getY());
        assertEquals(10, moved.spawn().getZ());
    }
}
