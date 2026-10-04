package net.bullettrain.xenopixelsmod.missile;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MissileFlightCorridorTest {

    @Test
    void lookaheadScalesWithSpeedAndCaps() {
        assertEquals(4, MissileFlightCorridor.lookaheadChunks(0));
        assertTrue(MissileFlightCorridor.lookaheadChunks(16) >= 6);
        assertEquals(MissileFlightCorridor.MAX_LOOKAHEAD_CHUNKS,
                MissileFlightCorridor.lookaheadChunks(400));
    }

    @Test
    void corridorIncludesCurrentLookaheadAndTargetWithoutSpammingTheWholePath() {
        Vec3 pos = new Vec3(8, 80, 8);
        Vec3 vel = new Vec3(20, 0, 0);
        Vec3 target = new Vec3(100_000, 64, 8);
        Set<Long> chunks = collect(pos, vel, target);
        assertTrue(chunks.contains(MissileFlightCorridor.chunkKey(pos)));
        assertTrue(chunks.contains(MissileFlightCorridor.chunkKey(target)));
        assertTrue(chunks.contains(MissileFlightCorridor.chunkKey(pos.add(16, 0, 0))));
        assertTrue(chunks.size() <= 1 + MissileFlightCorridor.MAX_LOOKAHEAD_CHUNKS
                + MissileFlightCorridor.MAX_PATH_SAMPLES + 1);
        assertTrue(chunks.size() < 40, "must not force-load a 100 km path");
    }

    @Test
    void verticalBoostStillAimsLookaheadAtTheTarget() {
        Set<Long> chunks = collect(new Vec3(8, 10, 8), new Vec3(0, 12, 0), new Vec3(200, 64, 8));
        assertTrue(chunks.contains(MissileFlightCorridor.chunkKey(new Vec3(8 + 16, 10, 8))));
    }

    @Test
    void targetNearUsesApexRadiusWindow() {
        assertTrue(MissileFlightCorridor.targetIsNear(new Vec3(0, 80, 0), new Vec3(50, 64, 0)));
        assertFalse(MissileFlightCorridor.targetIsNear(new Vec3(0, 80, 0), new Vec3(400, 64, 0)));
    }

    private static Set<Long> collect(Vec3 pos, Vec3 vel, Vec3 target) {
        LinkedHashSet<Long> chunks = new LinkedHashSet<>();
        MissileFlightCorridor.visitChunks(pos, vel, target,
                (cx, cz) -> chunks.add(net.minecraft.world.level.ChunkPos.asLong(cx, cz)));
        return chunks;
    }
}
