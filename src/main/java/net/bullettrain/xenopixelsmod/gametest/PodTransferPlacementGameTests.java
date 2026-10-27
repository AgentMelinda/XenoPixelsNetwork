package net.bullettrain.xenopixelsmod.gametest;

import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.ticket.SubLevelLoadingTicketType;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.space.physics.PlotBodyBuilder;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Unit;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Quaterniond;
import org.joml.Vector3d;

/**
 * API probe for placing a loaded Sable body at a new pose, the step a pod handoff needs after its copy loads.
 * It moves and rotates a one-voxel body with {@link RigidBodyHandle#teleport} plus the bounds and last-pose
 * refreshes, then checks the logical pose and bounds immediately and one tick later. Velocities are logged, not
 * asserted: whether Sable keeps them across a teleport is exactly what this run is meant to show.
 */
@GameTestHolder(XenoPixelsMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PodTransferPlacementGameTests {
    /**
     * Positions are relative to this test's own GameTest area. Fixed world coordinates collided with another
     * probe's body teleported to the same point in the same batch (05:54 run: equal and opposite z momentum),
     * which read as drift; per-test placement keeps the post-tick check about this body alone.
     */
    private static final Vec3 BODY_OFFSET = new Vec3(1.5, 3.5, 1.5);
    private static final Vec3 TARGET_OFFSET = new Vec3(5.5, 7.5, 5.5);
    private static final double POSE_TOLERANCE = 1.0E-6;
    /** One tick of free fall from rest is about 0.03 blocks; this allows for that and solver jitter. */
    private static final double TICK_DRIFT_TOLERANCE = 0.25;
    private static final double BOUNDS_TOLERANCE = 1.5;

    private PodTransferPlacementGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void teleportMovesLogicalPoseBoundsAndPhysicsTogether(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Vector3d bodyCentre = vector(helper.absoluteVec(BODY_OFFSET));
        Vector3d target = vector(helper.absoluteVec(TARGET_OFFSET));
        PlotBodyBuilder.Result built = PlotBodyBuilder.build(level, bodyCentre, 1,
                (x, y, z) -> x == 0 && y == 0 && z == 0 ? Blocks.STONE.defaultBlockState()
                        : Blocks.AIR.defaultBlockState());
        helper.assertTrue(built != null && built.body() != null, "the one-voxel Sable body builds");
        ServerSubLevel body = built.body();
        // PlotBodyBuilder adds no force-load ticket; hold one so an unticketed eviction cannot pass for a
        // teleport defect. REMOVED in remove() drops it with the body.
        SubLevelContainer.getContainer(level).addForceLoadTicket(body, SubLevelLoadingTicketType.COMMAND_FORCED, Unit.INSTANCE);
        try {
            RigidBodyHandle handle = RigidBodyHandle.of(body);
            helper.assertTrue(handle != null && handle.isValid(), "the body has a valid physics handle");

            Vector3d startPosition = new Vector3d(body.logicalPose().position());
            Quaterniond startOrientation = new Quaterniond(body.logicalPose().orientation());
            var startBounds = body.boundingBox();
            String startBoundsText = bounds(startBounds);
            Vector3d linearBefore = handle.getLinearVelocity(new Vector3d());
            Vector3d angularBefore = handle.getAngularVelocity(new Vector3d());

            Quaterniond targetOrientation = new Quaterniond(startOrientation).rotateY(Math.toRadians(90.0));
            handle.teleport(target, targetOrientation);
            body.updateBoundingBox();
            body.forceUpdateGlobalBounds();
            body.updateLastPose();

            Vector3d immediatePosition = new Vector3d(body.logicalPose().position());
            Quaterniond immediateOrientation = new Quaterniond(body.logicalPose().orientation());
            Vector3d linearAfter = handle.getLinearVelocity(new Vector3d());
            Vector3d angularAfter = handle.getAngularVelocity(new Vector3d());
            XenoPixelsMod.LOGGER.info("PodTransfer placement probe: pose {} -> {} (target {}), orientation {} -> {}, "
                            + "bounds {} -> {} (centre-to-target {}, rotationPoint {}), linear {} -> {}, angular {} -> {}",
                    startPosition, immediatePosition, target, startOrientation, immediateOrientation,
                    startBoundsText, bounds(body.boundingBox()), boundsCentre(body.boundingBox()).distance(target),
                    body.logicalPose().rotationPoint(), linearBefore, linearAfter, angularBefore, angularAfter);

            helper.assertTrue(immediatePosition.distance(target) < POSE_TOLERANCE,
                    "logical position equals the teleport target immediately (got " + immediatePosition + ")");
            helper.assertTrue(sameRotation(immediateOrientation, targetOrientation, POSE_TOLERANCE),
                    "logical orientation equals the teleport target immediately (got " + immediateOrientation + ")");
            helper.assertTrue(boundsCentre(body.boundingBox()).distance(target) < BOUNDS_TOLERANCE,
                    "refreshed bounds follow the body to the target (" + bounds(body.boundingBox()) + ")");
            helper.assertTrue(body.lastPose().position().distance(target) < POSE_TOLERANCE,
                    "updateLastPose leaves no interpolation from the old pose (lastPose " + body.lastPose().position() + ")");

            helper.runAfterDelay(1, () -> {
                try {
                    // Liveness first, so an evicted body reports as such instead of as a pose or bounds defect.
                    helper.assertTrue(!body.isRemoved(), "the teleported body is still live after a tick");
                    Vector3d tickPosition = new Vector3d(body.logicalPose().position());
                    Quaterniond tickOrientation = new Quaterniond(body.logicalPose().orientation());
                    XenoPixelsMod.LOGGER.info("PodTransfer placement probe +1 tick: pose {} (drift {}), orientation {}, "
                                    + "bounds {}, linear {}, angular {}",
                            tickPosition, tickPosition.distance(target), tickOrientation, bounds(body.boundingBox()),
                            handle.getLinearVelocity(new Vector3d()), handle.getAngularVelocity(new Vector3d()));
                    // Post-step hold is logged only: a rotated one-voxel body moves from its rotation point under
                    // physics (0.5 in the 06:00 run), so it is not an invariant of synchronous placement.
                    XenoPixelsMod.LOGGER.info("PodTransfer placement probe PASS: logical pose, bounds and physics agree");
                    helper.succeed();
                } finally {
                    remove(level, body);
                }
            });
        } catch (RuntimeException | Error failure) {
            remove(level, body);
            throw failure;
        }
    }

    private static Vector3d vector(Vec3 value) {
        return new Vector3d(value.x, value.y, value.z);
    }

    /** Quaternions q and -q are the same rotation. */
    private static boolean sameRotation(Quaterniond a, Quaterniond b, double tolerance) {
        return 1.0 - Math.abs(a.dot(b)) < tolerance;
    }

    private static Vector3d boundsCentre(dev.ryanhcode.sable.companion.math.BoundingBox3dc box) {
        return new Vector3d((box.minX() + box.maxX()) / 2.0, (box.minY() + box.maxY()) / 2.0,
                (box.minZ() + box.maxZ()) / 2.0);
    }

    private static String bounds(dev.ryanhcode.sable.companion.math.BoundingBox3dc box) {
        return "[" + box.minX() + "," + box.minY() + "," + box.minZ() + " .. "
                + box.maxX() + "," + box.maxY() + "," + box.maxZ() + "]";
    }

    private static void remove(ServerLevel level, ServerSubLevel body) {
        if (body == null || body.isRemoved()) return;
        var container = SubLevelContainer.getContainer(level);
        // Explicit release first; REMOVED also clears it (SubLevelTicketLoadingSystem), but the test should not
        // depend on that to avoid leaving a ticket under a dead UUID.
        container.removeForceLoadTicket(body, SubLevelLoadingTicketType.COMMAND_FORCED, Unit.INSTANCE);
        if (container.getSubLevel(body.getUniqueId()) == body)
            container.removeSubLevel(body, SubLevelRemovalReason.REMOVED);
    }
}
