package net.bullettrain.xenopixelsmod.command;

import dev.ryanhcode.sable.companion.math.Pose3d;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ThrusterForceCommandTest {
    @Test
    void projectsFarPlotCoordinateIntoNearbyWorldPosition() {
        BlockPos plotPos = new BlockPos(30_000_000, 80, -30_000_000);
        Pose3d pose = new Pose3d(
                new Vector3d(125, 70, -45),
                new Quaterniond(),
                new Vector3d(30_000_000.5, 80.5, -29_999_999.5),
                new Vector3d(1, 1, 1));

        Vec3 world = ThrusterForceCommand.transformedBlockCenter(pose, plotPos);

        assertEquals(125.0, world.x, 1.0e-9);
        assertEquals(70.0, world.y, 1.0e-9);
        assertEquals(-45.0, world.z, 1.0e-9);
    }

    @Test
    void projectsRotatedShipBlockAroundItsRotationPoint() {
        BlockPos plotPos = new BlockPos(101, 20, 100);
        Pose3d pose = new Pose3d(
                new Vector3d(10, 20, 30),
                new Quaterniond().rotateY(Math.PI / 2.0),
                new Vector3d(100.5, 20.5, 100.5),
                new Vector3d(1, 1, 1));

        Vec3 world = ThrusterForceCommand.transformedBlockCenter(pose, plotPos);

        assertEquals(10.0, world.x, 1.0e-9);
        assertEquals(20.0, world.y, 1.0e-9);
        assertEquals(29.0, world.z, 1.0e-9);
    }

    /**
     * {@code /xenothruster force <v> at <x y z>} names a parent-world position. A ship's thrusters
     * are stored in plot space, which can sit tens of millions of blocks from where the hull is
     * actually parked, so the raw plot coordinates must never be treated as a match for the typed
     * world coordinates - only the pose-transformed world centre counts.
     */
    @Test
    void atRejectsAShipThrusterThatOnlyMatchesByItsRawPlotCoordinates() {
        BlockPos plotPos = new BlockPos(30_000_000, 80, -30_000_000);
        Pose3d pose = new Pose3d(
                new Vector3d(125, 70, -45),
                new Quaterniond(),
                new Vector3d(30_000_000.5, 80.5, -29_999_999.5),
                new Vector3d(1, 1, 1));

        // The player types the plot numbers verbatim; the ship is parked at (125, 70, -45).
        Vec3 worldCenter = ThrusterForceCommand.transformedBlockCenter(pose, plotPos);
        double distanceSquared = worldCenter.distanceToSqr(Vec3.atCenterOf(plotPos));

        assertTrue(distanceSquared > ThrusterForceCommand.AT_MATCH_RADIUS
                        * ThrusterForceCommand.AT_MATCH_RADIUS,
                "raw plot coordinates must fall outside the world-space `at` tolerance");
    }

    @Test
    void atAcceptsAShipThrusterParkedOnTheRequestedWorldPosition() {
        BlockPos plotPos = new BlockPos(30_000_000, 80, -30_000_000);
        Pose3d pose = new Pose3d(
                new Vector3d(125, 70, -45),
                new Quaterniond(),
                new Vector3d(30_000_000.5, 80.5, -29_999_999.5),
                new Vector3d(1, 1, 1));
        BlockPos requestedWorldPos = new BlockPos(125, 70, -45);

        Vec3 worldCenter = ThrusterForceCommand.transformedBlockCenter(pose, plotPos);
        double distanceSquared = worldCenter.distanceToSqr(Vec3.atCenterOf(requestedWorldPos));

        assertTrue(distanceSquared <= ThrusterForceCommand.AT_MATCH_RADIUS
                        * ThrusterForceCommand.AT_MATCH_RADIUS,
                "a thruster sitting on the requested world position must stay selectable");
    }

    @Test
    void rayDistanceUsesFiniteSegmentRatherThanInfiniteLine() {
        Vec3 start = new Vec3(0, 0, 0);
        Vec3 end = new Vec3(5, 0, 0);

        assertEquals(0.25, ThrusterForceCommand.distanceToSegmentSquared(
                new Vec3(3, 0.5, 0), start, end), 1.0e-9);
        assertEquals(4.0, ThrusterForceCommand.distanceToSegmentSquared(
                new Vec3(7, 0, 0), start, end), 1.0e-9);
        assertTrue(ThrusterForceCommand.projectionParameter(
                new Vec3(-1, 0, 0), start, end) < 0.0);
    }
}
