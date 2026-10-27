package net.bullettrain.xenopixelsmod.gametest;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.space.physics.PlotBodyBuilder;
import net.bullettrain.xenopixelsmod.space.pod.transfer.PodTransferTransaction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Vector3d;

import java.util.UUID;

/**
 * The build-height gate for a placed transfer applies to the FINAL bounds: a pod whose source Y lies outside the
 * destination layout transfers when its target fits, and is refused before teardown when the target does not.
 * GameTest areas sit near Y -60 in the test overworld (-2032..2032), below the End floor at 0, so a capsule in the
 * test's own area is naturally out of range for the End; each case asserts that precondition explicitly.
 *
 * <p>Own batch: Sable's fullyLoad allocates the copy at the SOURCE plot slot index, so any other body occupying that
 * index in the End (09:13 run: "destination plot slot 53,0 is held by another body") refuses the transfer. Running
 * apart from the default batch keeps other tests' End bodies out of the way.
 */
@GameTestHolder(XenoPixelsMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PodTransferTargetBoundsGameTests {
    private static final String POD_ID = "xenopixelsmod:space_pod_id";
    private static final String POD_HULL = "xenopixelsmod:space_pod_hull";
    private static final String POD_SCHEMA = "xenopixelsmod:space_pod_schema";
    private static final Vec3 CAPSULE_OFFSET = new Vec3(1.5, 3.5, 1.5);

    private PodTransferTargetBoundsGameTests() {}

    /** Source below the End floor, target inside it: TRANSFERRED, placed at the target, source removed. */
    @GameTest(template = "empty", timeoutTicks = 100, batch = "pod_transfer_end_bounds")
    public static void podFromOutsideTheDestinationLayoutTransfersWhenItsTargetFits(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerLevel end = level.getServer().getLevel(Level.END);
        helper.assertTrue(end != null, "The End is available to the GameTest server");
        UUID podId = UUID.randomUUID();
        ServerSubLevel source = stamp(buildBody(helper, level), podId);
        UUID bodyId = source.getUniqueId();
        ServerSubLevel copy = null;
        try {
            helper.assertTrue(source.boundingBox().minY() < end.getMinBuildHeight(),
                    "precondition: the source capsule sits below the End floor (minY " + source.boundingBox().minY() + ")");
            Vector3d target = new Vector3d(60.5, 64.5, 60.5);
            PodTransferTransaction.Result result = PodTransferTransaction.transfer(level, end, source, null, target);
            copy = result.status() == PodTransferTransaction.Status.TRANSFERRED ? result.destination() : null;
            XenoPixelsMod.LOGGER.info("PodTransfer target-bounds GameTest (fits): status={} phase={} reason={}",
                    result.status(), result.phase(), result.reason());
            helper.assertTrue(result.status() == PodTransferTransaction.Status.TRANSFERRED,
                    "an out-of-layout source with an in-range target transfers (got " + result.status() + ": "
                            + result.reason() + ")");
            helper.assertTrue(copy.logicalPose().position().distance(target) < 1.0E-6,
                    "the copy is placed at the target (got " + copy.logicalPose().position() + ")");
            helper.assertTrue(copy.boundingBox().minY() >= end.getMinBuildHeight()
                            && copy.boundingBox().maxY() <= end.getMaxBuildHeight(),
                    "the placed bounds lie inside the End layout");
            helper.assertTrue(SubLevelContainer.getContainer(level).getSubLevel(bodyId) == null,
                    "the source is no longer registered in the source level");
            XenoPixelsMod.LOGGER.info("PodTransfer target-bounds GameTest PASS: out-of-layout source placed in range");
            helper.succeed();
        } finally {
            if (copy != null) {
                PodTransferTransaction.releaseTicket(copy, podId);
                cleanup(end, copy);
            } else {
                cleanup(level, source);
            }
        }
    }

    /** Source below the End floor, target above its ceiling: REFUSED before teardown, source intact. */
    @GameTest(template = "empty", timeoutTicks = 100, batch = "pod_transfer_end_bounds")
    public static void podIsRefusedBeforeTeardownWhenItsTargetDoesNotFit(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerLevel end = level.getServer().getLevel(Level.END);
        helper.assertTrue(end != null, "The End is available to the GameTest server");
        UUID podId = UUID.randomUUID();
        ServerSubLevel source = stamp(buildBody(helper, level), podId);
        UUID bodyId = source.getUniqueId();
        try {
            helper.assertTrue(source.boundingBox().minY() < end.getMinBuildHeight(),
                    "precondition: the source capsule sits below the End floor (minY " + source.boundingBox().minY() + ")");
            Vector3d target = new Vector3d(60.5, end.getMaxBuildHeight() + 20.5, 60.5);
            PodTransferTransaction.Result result = PodTransferTransaction.transfer(level, end, source, null, target);
            XenoPixelsMod.LOGGER.info("PodTransfer target-bounds GameTest (out of range): status={} phase={} reason={}",
                    result.status(), result.phase(), result.reason());
            helper.assertTrue(result.status() == PodTransferTransaction.Status.REFUSED
                            && "validate-destination".equals(result.phase()),
                    "an out-of-range target is REFUSED at validate-destination (got " + result.status() + " at "
                            + result.phase() + ": " + result.reason() + ")");
            helper.assertTrue(result.destination() == source && !source.isRemoved()
                            && SubLevelContainer.getContainer(level).getSubLevel(bodyId) == source,
                    "the source is untouched and still registered");
            helper.assertTrue(SubLevelContainer.getContainer(end).getSubLevel(bodyId) == null,
                    "nothing was loaded into the End");
            XenoPixelsMod.LOGGER.info("PodTransfer target-bounds GameTest PASS: out-of-range target refused before teardown");
            helper.succeed();
        } finally {
            cleanup(level, source);
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

    private static void cleanup(ServerLevel level, ServerSubLevel body) {
        if (body == null || body.isRemoved()) return;
        var container = SubLevelContainer.getContainer(level);
        if (container.getSubLevel(body.getUniqueId()) == body)
            container.removeSubLevel(body, SubLevelRemovalReason.REMOVED);
    }
}
