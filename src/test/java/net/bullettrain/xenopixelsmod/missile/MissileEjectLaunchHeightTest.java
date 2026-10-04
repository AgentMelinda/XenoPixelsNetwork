package net.bullettrain.xenopixelsmod.missile;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 2026-09-28 owner: "V3 guidance can't hit a block 10 m away". A tube facing sideways with a launch
 * height above it never reached that height, so its rail ran the whole 40 s cap (~5,600 blocks)
 * before guidance could start. Launch height is a climb target, so it only applies to rails that
 * point up; a sideways or downward rail ends at its clearance.
 */
class MissileEjectLaunchHeightTest {
    private static final Vec3 ORIGIN = new Vec3(0, 164, 0);

    @Test
    void aSidewaysRailEndsAtItsClearanceWhateverTheLaunchHeight() {
        assertTrue(MissileEject.finished(20, ORIGIN, ORIGIN.add(30, 0, 0), new Vec3(1, 0, 0), 24, 240));
        assertTrue(MissileEject.finished(20, ORIGIN, ORIGIN.add(0, -30, 0), new Vec3(0, -1, 0), 24, 240));
    }

    @Test
    void anUpwardRailStillClimbsToTheLaunchHeight() {
        assertFalse(MissileEject.finished(20, ORIGIN, ORIGIN.add(0, 30, 0), new Vec3(0, 1, 0), 24, 240));
        assertTrue(MissileEject.finished(60, ORIGIN, new Vec3(0, 241, 0), new Vec3(0, 1, 0), 24, 240));
    }

    @Test
    void aSteepButNotVerticalRailCountsAsUpward() {
        Vec3 steep = new Vec3(0.3, 0.95, 0).normalize();
        assertFalse(MissileEject.finished(20, ORIGIN, ORIGIN.add(steep.scale(30)), steep, 24, 240));
    }
}
