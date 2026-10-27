package net.bullettrain.xenopixelsmod.gametest;

import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.ticket.SubLevelLoadingTicketType;
import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
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
 * Measures whether {@code RigidBodyHandle.teleport} changes a moving body's velocity, synchronously and across the
 * next physics tick, instead of assuming it does not.
 *
 * <p>The pod handoff places a loaded copy with a teleport and then puts its captured motion back. Whether that
 * order is safe depends on what the teleport does to the motion it is about to restore, and the pinned bytecode
 * only shows the native call plus two logical-pose writes; it cannot show what the solver does to the velocity.
 * This test injects linear and angular velocity, reads both before and immediately after the teleport, and reads
 * both again after one tick.
 *
 * <p>No velocity is asserted, on purpose. A passing run is not evidence that placement preserves motion; the
 * product of this test is the logged readings. It fails only if the teleport does not land, or the capsule is not
 * live to be read, or cleanup leaves it behind.
 *
 * <p>How to read the numbers, and the limit a reader must not overlook: the tick-level linear change is not a
 * retention result, because free fall alters linear velocity on every tick. Angular velocity is unaffected by
 * gravity, so the tick-level angular change is the retention signal. The before and after pair taken without a
 * step between them is the only reading that isolates the teleport. If the linear reading gains a downward
 * component on the tick, that is also the proof the integrator stepped at all, which the synchronous pair cannot
 * show on its own.
 */
@GameTestHolder(XenoPixelsMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PodTransferPlacementVelocityRetentionGameTests {
    /** Relative to the test area so a sibling fixture cannot share the capsule's contact space. */
    private static final Vec3 BODY_OFFSET = new Vec3(0.5, 3.5, 0.5);
    /** The body offset plus the (8, 6, 8) displacement the old absolute pair carried. */
    private static final Vec3 TARGET_OFFSET = new Vec3(8.5, 9.5, 8.5);
    private static final Vector3d INJECTED_LINEAR = new Vector3d(2.0, 0.0, 0.0);
    private static final Vector3d INJECTED_ANGULAR = new Vector3d(0.0, 1.0, 0.0);
    /** Verified exact in bytecode: teleport assigns the target position to the logical pose. */
    private static final double PLACEMENT_TOLERANCE = 1.0E-6;
    /** Below this a logged delta is reported as unchanged; it never gates the test. */
    private static final double REPORTING_EPSILON = 1.0E-9;
    private static final SubLevelLoadingTicketType<Unit> KEEP_ALIVE = SubLevelLoadingTicketType.COMMAND_FORCED;

    private PodTransferPlacementVelocityRetentionGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 120)
    public static void teleportVelocityEffectIsMeasuredRatherThanAssumed(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        Vec3 bodyAt = helper.absoluteVec(BODY_OFFSET);
        Vec3 targetAt = helper.absoluteVec(TARGET_OFFSET);
        final Vector3d bodyCentre = new Vector3d(bodyAt.x, bodyAt.y, bodyAt.z);
        final Vector3d target = new Vector3d(targetAt.x, targetAt.y, targetAt.z);
        PlotBodyBuilder.Result built = PlotBodyBuilder.build(level, bodyCentre, 1,
                (x, y, z) -> x == 0 && y == 0 && z == 0 ? Blocks.STONE.defaultBlockState()
                        : Blocks.AIR.defaultBlockState());
        helper.assertTrue(built != null && built.body() != null,
                "the one-voxel capsule builds in " + level.dimension().location());
        ServerSubLevel body = built.body();
        try {
            container.addForceLoadTicket(body, KEEP_ALIVE, Unit.INSTANCE);
            RigidBodyHandle handle = RigidBodyHandle.of(body);
            helper.assertTrue(handle != null && handle.isValid(),
                    "the capsule has a valid rigid body to measure (handle " + handle + ")");

            handle.addLinearAndAngularVelocity(new Vector3d(INJECTED_LINEAR), new Vector3d(INJECTED_ANGULAR));
            Vector3d linearBefore = handle.getLinearVelocity(new Vector3d());
            Vector3d angularBefore = handle.getAngularVelocity(new Vector3d());
            Quaterniond targetOrientation = new Quaterniond(body.logicalPose().orientation())
                    .rotateY(Math.toRadians(90.0));

            handle.teleport(target, targetOrientation);
            Vector3d linearSync = handle.getLinearVelocity(new Vector3d());
            Vector3d angularSync = handle.getAngularVelocity(new Vector3d());
            Vector3d placed = new Vector3d(body.logicalPose().position());
            // The refreshes follow the synchronous reading so that reading stays attributable to the teleport
            // alone; they maintain bounds and interpolation state and involve no velocity getter or setter.
            body.updateBoundingBox();
            body.forceUpdateGlobalBounds();
            body.updateLastPose();

            XenoPixelsMod.LOGGER.info("PodTransfer retention probe, teleport without a step: linear {} -> {}"
                            + " (delta {}), angular {} -> {} (delta {}); effect at teleport: {};"
                            + " pose {} -> target {}",
                    linearBefore, linearSync, delta(linearSync, linearBefore),
                    angularBefore, angularSync, delta(angularSync, angularBefore),
                    effect(linearSync, linearBefore, angularSync, angularBefore), placed, target);
            helper.assertTrue(placed.distance(target) < PLACEMENT_TOLERANCE,
                    "the teleport landed on the target so the readings describe a placed body (got " + placed + ")");

            helper.runAfterDelay(1, () -> {
                try {
                    helper.assertTrue(!body.isRemoved(),
                            "the teleported capsule is still live before its velocity is read again");
                    Vector3d linearTick = handle.getLinearVelocity(new Vector3d());
                    Vector3d angularTick = handle.getAngularVelocity(new Vector3d());
                    Vector3d tickPose = new Vector3d(body.logicalPose().position());
                    XenoPixelsMod.LOGGER.info("PodTransfer retention probe, +1 tick: linear {} -> {} (delta {}),"
                                    + " angular {} -> {} (delta {}); retention across the tick: {};"
                                    + " pose {} (drift {} from target), bounds {}",
                            linearSync, linearTick, delta(linearTick, linearSync),
                            angularSync, angularTick, delta(angularTick, angularSync),
                            retains(angularTick, angularSync), tickPose, tickPose.distance(target),
                            bounds(body.boundingBox()));
                    XenoPixelsMod.LOGGER.info("PodTransfer retention probe note: the tick-level linear change above"
                            + " includes free fall and is not a retention result; only the angular line is.");
                    releaseAndRemove(container, body);
                    helper.assertTrue(body.isRemoved(), "cleanup removed the capsule");
                    helper.succeed();
                } finally {
                    releaseAndRemove(container, body);
                }
            });
        } catch (RuntimeException | Error failure) {
            releaseAndRemove(container, body);
            throw failure;
        }
    }

    /** Log-only classification of the synchronous pair, where no step could have altered the motion. */
    private static String effect(Vector3d linearAfter, Vector3d linearBefore, Vector3d angularAfter,
                                 Vector3d angularBefore) {
        return "linear " + change(delta(linearAfter, linearBefore)) + ", angular "
                + change(delta(angularAfter, angularBefore));
    }

    /** Log-only statement of whether the gravity-free component of motion survived a step. */
    private static String retains(Vector3d angularAfter, Vector3d angularBefore) {
        double kept = angularBefore.length() <= REPORTING_EPSILON ? Double.NaN
                : angularAfter.length() / angularBefore.length();
        return change(delta(angularAfter, angularBefore)) + (Double.isNaN(kept) ? " (no spin was measured)"
                : ", kept " + String.format(java.util.Locale.ROOT, "%.3f", kept) + " of the spin");
    }

    private static String change(Vector3d delta) {
        return delta.length() <= REPORTING_EPSILON ? "unchanged" : "changed";
    }

    private static Vector3d delta(Vector3d after, Vector3d before) {
        return new Vector3d(after).sub(before);
    }

    private static String bounds(BoundingBox3dc box) {
        return "[" + box.minX() + "," + box.minY() + "," + box.minZ() + " .. "
                + box.maxX() + "," + box.maxY() + "," + box.maxZ() + "]";
    }

    private static void releaseAndRemove(ServerSubLevelContainer container, ServerSubLevel body) {
        if (body == null || body.isRemoved()) return;
        container.removeForceLoadTicket(body, KEEP_ALIVE, Unit.INSTANCE);
        if (container.getSubLevel(body.getUniqueId()) == body)
            container.removeSubLevel(body, SubLevelRemovalReason.REMOVED);
    }
}
