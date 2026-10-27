package net.bullettrain.xenopixelsmod.space;

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

/**
 * The H1 path of {@link SpaceHandoffPass#podLeg}: the pod committed, the rider did not arrive, and bringing the pod
 * back failed. The failure is forced deterministically by having the mover strip the copy's pod identity, so the
 * return transaction refuses at capture while the copy stays live. Same-level stand-in, as in
 * SpaceHandoffPodLegGameTests; it proves the leg's reporting and ticket policy, not a real dimension change.
 */
@GameTestHolder(XenoPixelsMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SpaceHandoffStrandedPodGameTests {
    private static final String POD_ID = "xenopixelsmod:space_pod_id";
    private static final String POD_HULL = "xenopixelsmod:space_pod_hull";
    private static final String POD_SCHEMA = "xenopixelsmod:space_pod_schema";
    private static final Vec3 CAPSULE_OFFSET = new Vec3(1.5, 3.5, 1.5);
    private static final Vec3 SEAT_OFFSET = new Vec3(1.5, 4.0, 1.5);

    private SpaceHandoffStrandedPodGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void failedReturnStrandsThePodWithItsTicketAndAnOperatorReport(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        UUID podId = UUID.randomUUID();
        ServerSubLevel pod = stamp(buildBody(helper, level), podId);
        UUID bodyId = pod.getUniqueId();
        ServerSubLevel stranded = null;
        try {
            Player rider = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
            rider.setPos(helper.absoluteVec(SEAT_OFFSET));
            helper.assertTrue(PodRiderBinding.restore(rider, pod, PodRiderBinding.capture(rider, pod)),
                    "the mock rider boards the pod");
            Vec3 riderDestination = helper.absoluteVec(new Vec3(5.5, 9.0, 5.5));
            SpaceHandoffPass.PodLeg leg = SpaceHandoffPass.podLeg(rider, pod, level, level, riderDestination,
                    null, null, position -> {
                        // Make the compensating return impossible: without its pod identity the copy cannot be
                        // snapshotted, so the return transaction refuses before touching it.
                        if (SubLevelContainer.getContainer(level).getSubLevel(bodyId) instanceof ServerSubLevel copy) {
                            CompoundTag data = copy.getUserDataTag();
                            if (data != null) data.remove(POD_ID);
                            copy.setUserDataTag(data);
                        }
                        return null;
                    });
            stranded = SubLevelContainer.getContainer(level).getSubLevel(bodyId) instanceof ServerSubLevel body
                    ? body : null;
            XenoPixelsMod.LOGGER.info("PodHandoff stranded GameTest: moved={} stranded={} bound={} problem={}",
                    leg.moved(), leg.stranded(), leg.riderBound(), leg.problem());
            helper.assertTrue(!leg.moved() && leg.stranded(), "a failed return is reported as stranded, not moved");
            helper.assertTrue(leg.transfer() != null
                            && leg.transfer().status() == PodTransferTransaction.Status.TRANSFERRED,
                    "the forward transfer had committed");
            helper.assertTrue(leg.problem() != null && leg.problem().contains("could not be returned"),
                    "the problem names the failed return: " + leg.problem());
            helper.assertTrue(stranded != null && !stranded.isRemoved(), "the stranded pod body is still live");
            helper.assertTrue(PodTransferDestination.holds(level, bodyId, podId.toString()),
                    "the stranded pod KEEPS its transfer ticket so it stays loaded for recovery");

            PodTransferTransaction.Result refusedReturn = PodTransferTransaction.transfer(level, level, stranded, null,
                    new Vector3d(stranded.logicalPose().position()));
            String report = SpaceHandoffPass.strandedPodReport(podId, stranded, level, level, rider, leg.riderBound(),
                    refusedReturn);
            helper.assertTrue(report.startsWith("POD STRANDED: pod " + podId) && report.contains(bodyId.toString())
                            && report.contains("transfer ticket kept") && report.contains(rider.getUUID().toString()),
                    "the operator line names the pod, body, kept ticket and rider: " + report);
            XenoPixelsMod.LOGGER.info("PodHandoff stranded GameTest PASS: stranded flag, ticket kept, operator line");
            helper.succeed();
        } finally {
            ServerSubLevel left = stranded != null ? stranded : pod;
            if (left != null && !left.isRemoved()) {
                PodTransferTransaction.releaseTicket(left, podId);
                var container = SubLevelContainer.getContainer(level);
                if (container.getSubLevel(left.getUniqueId()) == left)
                    container.removeSubLevel(left, SubLevelRemovalReason.REMOVED);
            }
        }
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
}
