package net.bullettrain.xenopixelsmod.gametest;

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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Vector3d;

import java.util.UUID;

/**
 * A rider cannot be carried into another level by the transaction: PodRiderBinding only acts inside the player's
 * current level. These cases prove the cross-level rider is refused before anything is taken apart, and that the
 * binding helper itself refuses a body in another level. The same-container rider path is covered by
 * SpacePodTransferTransactionGameTests.
 */
@GameTestHolder(XenoPixelsMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PodTransferRiderLevelGameTests {
    private static final String POD_ID = "xenopixelsmod:space_pod_id";
    private static final String POD_HULL = "xenopixelsmod:space_pod_hull";
    private static final String POD_SCHEMA = "xenopixelsmod:space_pod_schema";
    /** Relative to the test's own area in its level; the End has no test area, so it gets its own fixed point. */
    private static final Vec3 CAPSULE_OFFSET = new Vec3(1.5, 3.5, 1.5);
    private static final Vector3d END_CENTRE = new Vector3d(40.5, 100.5, 40.5);

    private PodTransferRiderLevelGameTests() {}

    /** A rider still in the source level is refused for a cross-level transfer; the source stays intact. */
    @GameTest(template = "empty", timeoutTicks = 100, batch = "pod_transfer_end_rider")
    public static void podTransferRefusesARiderLeftInAnotherLevel(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerLevel end = level.getServer().getLevel(Level.END);
        helper.assertTrue(end != null, "The End is available to the GameTest server");
        UUID podId = UUID.randomUUID();
        ServerSubLevel source = stamp(buildBody(helper, level), podId);
        try {
            // makeMockPlayer, not a logged-in mock ServerPlayer: see SpacePodTransferTransactionGameTests for why.
            Player rider = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
            rider.setPos(helper.absoluteVec(CAPSULE_OFFSET.add(0.0, 0.5, 0.0)));
            helper.assertTrue(PodRiderBinding.restore(rider, source, PodRiderBinding.capture(rider, source)),
                    "the mock player starts bound to the source pod in its own level");

            PodTransferTransaction.Result result = PodTransferTransaction.transfer(level, end, source, rider);
            XenoPixelsMod.LOGGER.info("PodTransfer rider-level GameTest: status={} phase={} reason={}",
                    result.status(), result.phase(), result.reason());
            helper.assertTrue(result.status() == PodTransferTransaction.Status.REFUSED,
                    "a rider outside the destination level is REFUSED, never TRANSFERRED (got " + result.status()
                            + ": " + result.reason() + ")");
            helper.assertTrue(result.destination() == source && !source.isRemoved()
                            && SubLevelContainer.getContainer(level).getSubLevel(source.getUniqueId()) == source,
                    "the source pod is untouched and still registered");
            helper.assertTrue(SubLevelContainer.getContainer(end).getSubLevel(source.getUniqueId()) == null,
                    "no copy was loaded into the destination level");
            helper.assertTrue(!PodTransferDestination.holds(end, source.getUniqueId(), podId.toString()),
                    "no transfer ticket was left in the destination level");
            helper.assertTrue(PodRiderBinding.trackedBody(rider) == source,
                    "the rider stays bound to the source pod");
            XenoPixelsMod.LOGGER.info("PodTransfer rider-level GameTest PASS: cross-level rider refused before teardown");
            helper.succeed();
        } finally {
            cleanup(level, source);
        }
    }

    /** The binding helper refuses a body in another level instead of moving the player within the wrong one. */
    @GameTest(template = "empty", timeoutTicks = 100, batch = "pod_transfer_end_rider")
    public static void riderBindingRefusesABodyInAnotherLevel(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerLevel end = level.getServer().getLevel(Level.END);
        helper.assertTrue(end != null, "The End is available to the GameTest server");
        ServerSubLevel local = stamp(buildBody(helper, level), UUID.randomUUID());
        ServerSubLevel foreign = null;
        try {
            Player rider = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
            rider.setPos(helper.absoluteVec(CAPSULE_OFFSET.add(0.0, 0.5, 0.0)));
            var captured = PodRiderBinding.capture(rider, local);
            foreign = stamp(buildBody(helper, end), UUID.randomUUID());
            var before = rider.position();
            helper.assertTrue(!PodRiderBinding.restore(rider, foreign, captured),
                    "restore refuses a body in another level");
            helper.assertTrue(PodRiderBinding.trackedBody(rider) != foreign,
                    "the rider is not bound to the foreign-level body");
            helper.assertTrue(rider.position().equals(before), "the rider was not moved");
            XenoPixelsMod.LOGGER.info("PodTransfer rider-binding GameTest PASS: foreign-level body refused");
            helper.succeed();
        } finally {
            cleanup(level, local);
            if (foreign != null) cleanup(end, foreign);
        }
    }

    private static ServerSubLevel buildBody(GameTestHelper helper, ServerLevel level) {
        Vector3d centre;
        if (level == helper.getLevel()) {
            Vec3 relative = helper.absoluteVec(CAPSULE_OFFSET);
            centre = new Vector3d(relative.x, relative.y, relative.z);
        } else {
            centre = END_CENTRE;
        }
        PlotBodyBuilder.Result result = PlotBodyBuilder.build(level, centre, 1,
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
