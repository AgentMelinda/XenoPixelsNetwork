package net.bullettrain.xenopixelsmod.space;

import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.space.physics.PlotBodyBuilder;
import net.bullettrain.xenopixelsmod.space.pod.transfer.PodRiderBinding;
import net.bullettrain.xenopixelsmod.space.pod.transfer.PodTransferDestination;
import net.bullettrain.xenopixelsmod.space.pod.transfer.PodTransferTransaction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Vector3d;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The handoff's pod leg ({@link SpaceHandoffPass#podLeg}) on a disposable capsule inside one level. A mock
 * player cannot change dimension in this harness (a logged-in mock ServerPlayer trips the Simulated addon's
 * end_sea payload), so the rider mover is a same-level stand-in: these prove ordering, placement, frame velocity
 * and failure handling of the leg, not a real dimension change or the seat graph.
 */
@GameTestHolder(XenoPixelsMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SpaceHandoffPodLegGameTests {
    private static final String POD_ID = "xenopixelsmod:space_pod_id";
    private static final String POD_HULL = "xenopixelsmod:space_pod_hull";
    private static final String POD_SCHEMA = "xenopixelsmod:space_pod_schema";
    private static final Vec3 CAPSULE_OFFSET = new Vec3(1.5, 3.5, 1.5);
    private static final Vec3 SEAT_OFFSET = new Vec3(1.5, 4.0, 1.5);

    private SpaceHandoffPodLegGameTests() {}

    /** Legacy path: a player not riding anything, or riding a body without pod identity, is not a pod rider. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void handoffKeepsThePlayerOnlyPathWithoutAPod(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerSubLevel plain = buildBody(helper, level);
        try {
            Player rider = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
            rider.setPos(helper.absoluteVec(SEAT_OFFSET));
            helper.assertTrue(SpaceHandoffPass.trackedPod(rider) == null, "an unbound player has no pod");
            PodRiderBinding.restore(rider, plain, PodRiderBinding.capture(rider, plain));
            helper.assertTrue(PodRiderBinding.trackedBody(rider) == plain, "the player rides the plain body");
            helper.assertTrue(SpaceHandoffPass.trackedPod(rider) == null,
                    "a body without pod identity keeps the player-only handoff");
            XenoPixelsMod.LOGGER.info("PodHandoff leg GameTest PASS: no pod keeps the player-only path");
            helper.succeed();
        } finally {
            cleanup(level, plain);
        }
    }

    /** The pod moves first, lands with the seat on the rider destination, carries the frame velocity, re-boards. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void podLegMovesThePodFirstAndReboardsTheRider(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        UUID podId = UUID.randomUUID();
        ServerSubLevel pod = stamp(buildBody(helper, level), podId);
        ServerSubLevel copy = null;
        try {
            Player rider = boardedRider(helper, pod);
            helper.assertTrue(SpaceHandoffPass.trackedPod(rider) == pod, "the rider's tracked body is the pod");
            Vector3d before = RigidBodyHandle.of(pod).getLinearVelocity(new Vector3d());
            Vec3 riderDestination = helper.absoluteVec(new Vec3(5.5, 9.0, 5.5));
            Vector3d sourceOrbit = new Vector3d(10.0, 0.0, 0.0), targetOrbit = new Vector3d(0.0, 0.0, -5.0);
            SpaceHandoffPass.PodLeg leg = SpaceHandoffPass.podLeg(rider, pod, level, level, riderDestination,
                    sourceOrbit, targetOrbit, position -> {
                        rider.setPos(position);
                        return rider;
                    });
            copy = leg.transfer() == null ? null : leg.transfer().destination();
            XenoPixelsMod.LOGGER.info("PodHandoff leg GameTest: moved={} bound={} transfer={} problem={}",
                    leg.moved(), leg.riderBound(), leg.transfer() == null ? null : leg.transfer().status(), leg.problem());
            helper.assertTrue(leg.moved() && leg.riderBound()
                            && leg.transfer().status() == PodTransferTransaction.Status.TRANSFERRED,
                    "the leg commits and re-boards (" + leg.problem() + ")");
            helper.assertTrue(PodRiderBinding.trackedBody(rider) == copy, "the rider rides the transferred pod");
            Vec3 at = rider.position();
            helper.assertTrue(at.distanceToSqr(riderDestination) < 1.0E-6,
                    "the rider sits at the handoff destination (got " + at + ")");
            Vector3d after = RigidBodyHandle.of(copy).getLinearVelocity(new Vector3d());
            Vector3d expected = new Vector3d(before).add(sourceOrbit).sub(targetOrbit);
            helper.assertTrue(after.distance(expected) < 1.0E-3,
                    "the pod carries the frame change in blocks per second (expected " + expected + ", got " + after + ")");
            helper.assertTrue(!PodTransferDestination.holds(level, copy.getUniqueId(), podId.toString()),
                    "the leg released its transfer ticket once the rider re-boarded");
            XenoPixelsMod.LOGGER.info("PodHandoff leg GameTest PASS: pod first, seat on destination, frame velocity");
            helper.succeed();
        } finally {
            cleanup(level, copy != null ? copy : pod);
        }
    }

    /** A pod transfer that is refused cancels the leg; the rider is never moved and stays aboard the pod. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void refusedPodTransferCancelsTheLegWithTheRiderAboard(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        UUID podId = UUID.randomUUID();
        ServerSubLevel pod = stamp(buildBody(helper, level), podId);
        try {
            Player rider = boardedRider(helper, pod);
            Vec3 beforePosition = rider.position();
            Vec3 tooHigh = new Vec3(beforePosition.x, level.getMaxBuildHeight() + 50.0, beforePosition.z);
            AtomicBoolean moverCalled = new AtomicBoolean();
            SpaceHandoffPass.PodLeg leg = SpaceHandoffPass.podLeg(rider, pod, level, level, tooHigh, null, null,
                    position -> {
                        moverCalled.set(true);
                        return rider;
                    });
            XenoPixelsMod.LOGGER.info("PodHandoff refused-leg GameTest: moved={} bound={} problem={}",
                    leg.moved(), leg.riderBound(), leg.problem());
            helper.assertTrue(!leg.moved() && !moverCalled.get(), "a refused pod never moves the rider");
            helper.assertTrue(leg.transfer() != null && leg.transfer().status() == PodTransferTransaction.Status.REFUSED,
                    "the pod transfer is REFUSED (got " + (leg.transfer() == null ? null : leg.transfer().status()) + ")");
            helper.assertTrue(leg.riderBound() && PodRiderBinding.trackedBody(rider) == pod,
                    "the rider is back aboard the untouched pod, not bound to a removed body");
            XenoPixelsMod.LOGGER.info("PodHandoff refused-leg GameTest PASS: leg cancelled, rider aboard");
            helper.succeed();
        } finally {
            cleanup(level, pod);
        }
    }

    /** If the rider does not arrive after the pod committed, the pod is brought back and re-boarded. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void riderThatDoesNotArriveGetsThePodBack(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        UUID podId = UUID.randomUUID();
        ServerSubLevel pod = stamp(buildBody(helper, level), podId);
        UUID bodyId = pod.getUniqueId();
        Vector3d departure = new Vector3d(pod.logicalPose().position());
        ServerSubLevel home = null;
        try {
            Player rider = boardedRider(helper, pod);
            Vec3 riderDestination = helper.absoluteVec(new Vec3(5.5, 9.0, 5.5));
            SpaceHandoffPass.PodLeg leg = SpaceHandoffPass.podLeg(rider, pod, level, level, riderDestination,
                    null, null, position -> null);
            var registered = SubLevelContainer.getContainer(level).getSubLevel(bodyId);
            home = registered instanceof ServerSubLevel body ? body : null;
            XenoPixelsMod.LOGGER.info("PodHandoff no-arrival GameTest: moved={} bound={} problem={}",
                    leg.moved(), leg.riderBound(), leg.problem());
            helper.assertTrue(!leg.moved(), "a rider that did not arrive is not reported as moved");
            helper.assertTrue(home != null && home.logicalPose().position().distance(departure) < 1.0E-6,
                    "the pod is back at its departure position (got "
                            + (home == null ? null : home.logicalPose().position()) + ")");
            helper.assertTrue(leg.riderBound() && PodRiderBinding.trackedBody(rider) == home,
                    "the rider is re-boarded on the returned pod");
            helper.assertTrue(!PodTransferDestination.holds(level, bodyId, podId.toString()),
                    "no transfer ticket is left on the returned pod");
            XenoPixelsMod.LOGGER.info("PodHandoff no-arrival GameTest PASS: pod returned and re-boarded");
            helper.succeed();
        } finally {
            cleanup(level, home != null ? home : pod);
        }
    }

    private static Player boardedRider(GameTestHelper helper, ServerSubLevel pod) {
        Player rider = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        rider.setPos(helper.absoluteVec(SEAT_OFFSET));
        helper.assertTrue(PodRiderBinding.restore(rider, pod, PodRiderBinding.capture(rider, pod)),
                "the mock rider boards the pod");
        return rider;
    }

    private static ServerSubLevel buildBody(GameTestHelper helper, ServerLevel level) {
        Vec3 centre = helper.absoluteVec(CAPSULE_OFFSET);
        PlotBodyBuilder.Result result = PlotBodyBuilder.build(level, new Vector3d(centre.x, centre.y, centre.z), 1,
                (x, y, z) -> x == 0 && y == 0 && z == 0 ? Blocks.STONE.defaultBlockState()
                        : Blocks.AIR.defaultBlockState());
        helper.assertTrue(result != null && result.body() != null,
                "the one-voxel Sable capsule builds in " + level.dimension().location());
        return result.body();
    }

    private static ServerSubLevel stamp(ServerSubLevel body, UUID podId) {
        CompoundTag data = body.getUserDataTag();
        if (data == null) data = new CompoundTag();
        data.putUUID(POD_ID, podId);
        data.putString(POD_HULL, "xenopixelsmod:pod/hull/classic_egg");
        data.putInt(POD_SCHEMA, 1);
        body.setUserDataTag(data);
        return body;
    }

    private static void cleanup(ServerLevel level, ServerSubLevel body) {
        if (body == null || body.isRemoved()) return;
        var container = SubLevelContainer.getContainer(level);
        if (container.getSubLevel(body.getUniqueId()) == body)
            container.removeSubLevel(body, SubLevelRemovalReason.REMOVED);
    }
}
