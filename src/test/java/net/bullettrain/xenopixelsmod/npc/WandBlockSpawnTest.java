package net.bullettrain.xenopixelsmod.npc;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** 2026-09-28 owner: right-clicking a block with the NPC wand places an NPC on it. */
class WandBlockSpawnTest {
    @Test
    void clickingTheTopOfABlockStandsTheNpcOnIt() {
        assertEquals(new Vec3(10.5, 65.0, -3.5),
                XenoNpcNearbyService.spawnPositionOn(new BlockPos(10, 64, -4), Direction.UP));
    }

    @Test
    void clickingASideStandsItInFrontOfThatSide() {
        assertEquals(new Vec3(11.5, 64.0, -3.5),
                XenoNpcNearbyService.spawnPositionOn(new BlockPos(10, 64, -4), Direction.EAST));
    }

    @Test
    void theNpcFacesThePlayerWhoPlacedIt() {
        assertEquals(180.0f, XenoNpcNearbyService.facingPlacer(0.0f), 1e-6);
        assertEquals(-90.0f, XenoNpcNearbyService.facingPlacer(90.0f), 1e-6);
    }
}
