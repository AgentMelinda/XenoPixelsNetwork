package net.bullettrain.xenopixelsmod.gametest;

import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelData;
import dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelSerializer;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.space.physics.PlotBodyBuilder;
import net.bullettrain.xenopixelsmod.space.pod.transfer.PodRiderBinding;
import net.bullettrain.xenopixelsmod.space.pod.transfer.PodTransferDestination;
import net.bullettrain.xenopixelsmod.space.pod.transfer.PodTransferSnapshot;
import net.bullettrain.xenopixelsmod.space.pod.transfer.PodTransferTransaction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Vector3d;

import java.util.List;
import java.util.UUID;

/**
 * Ordering and failure handling for {@link PodTransferTransaction}: one valid pod before, during and after a
 * transfer, and nothing taken apart when the destination cannot be validated. These are harness assertions on a
 * disposable GameTest world, not a live handoff. Production SpaceHandoffPass.podLeg uses the transaction for a
 * pod-only transfer and moves the player through a separate compensating protocol; these tests do not accept that
 * end-to-end path or the transaction's optional cross-dimension rider argument.
 */
@GameTestHolder(XenoPixelsMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SpacePodTransferTransactionGameTests {
    private static final String POD_ID = "xenopixelsmod:space_pod_id";
    private static final String POD_HULL = "xenopixelsmod:space_pod_hull";
    private static final String POD_SCHEMA = "xenopixelsmod:space_pod_schema";
    private static final Vector3d CAPSULE_CENTRE = new Vector3d(0.5, 64.5, 0.5);

    private SpacePodTransferTransactionGameTests() {}

    /** A pod that moves inside its own container keeps one registration, its motion, and one live body. */
    @GameTest(template = "empty", timeoutTicks = 240)
    public static void podTransferWithinOneContainerKeepsExactlyOnePod(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        UUID podId = UUID.randomUUID();
        ServerSubLevel source = stamp(buildBody(helper, level), podId);
        Vector3d injected = new Vector3d(0.40, 0.10, -0.25);
        RigidBodyHandle.of(source).addLinearAndAngularVelocity(injected, new Vector3d(0.02, -0.01, 0.005));
        Vector3d before = RigidBodyHandle.of(source).getLinearVelocity(new Vector3d());
        helper.assertTrue(before.distanceSquared(injected) < 1.0E-8,
                "the physics pipeline reports the velocity the test injected before the transfer (read " + before + ")");

        PodTransferTransaction.Result result = PodTransferTransaction.transfer(level, level, source, null);
        XenoPixelsMod.LOGGER.info("PodTransfer GameTest same-container: status={} phase={} reason={}",
                result.status(), result.phase(), result.reason());
        ServerSubLevel destination = result.destination();
        try {
            helper.assertTrue(result.status() == PodTransferTransaction.Status.TRANSFERRED,
                    "the same-container transfer commits (status=" + result.status() + ", phase=" + result.phase()
                            + ", reason=" + result.reason() + ")");
            helper.assertTrue(destination != source, "the committed body is the reloaded copy, not the old object");
            helper.assertTrue(destination != null && !destination.isRemoved(), "the destination body is live");
            helper.assertTrue(container.getSubLevel(source.getUniqueId()) == destination,
                    "exactly one body is registered under the transferred identity and it is the destination");
            helper.assertTrue(source.isRemoved(), "the source object is removed only after validation");
            helper.assertTrue(countPodBodies(level, podId) == 1,
                    "no duplicate pod identity exists in the container (found " + countPodBodies(level, podId) + ")");
            Vector3d after = RigidBodyHandle.of(destination).getLinearVelocity(new Vector3d());
            helper.assertTrue(PodTransferSnapshot.velocitiesMatch(before, after, 1.0E-4),
                    "the destination motion equals the captured motion (captured " + before + ", restored " + after + ")");
            helper.assertTrue(PodTransferDestination.holds(level, destination.getUniqueId(), podId.toString()),
                    "the transfer retains its force-load ticket for the caller");

            helper.runAfterDelay(2, () -> {
                try {
                    helper.assertTrue(!destination.isRemoved()
                                    && container.getSubLevel(destination.getUniqueId()) == destination,
                            "the transferred pod survives two ticks while the transaction ticket is held");
                } finally {
                    PodTransferTransaction.releaseTicket(destination, podId);
                    helper.assertTrue(!PodTransferDestination.holds(level, destination.getUniqueId(), podId.toString()),
                            "releasing the ticket removes exactly the ticket the transaction added");
                }
                helper.succeed();
            });
        } catch (RuntimeException | Error failure) {
            cleanup(level, destination);
            throw failure;
        }
    }

    /** Absolute section Y and pose Y are preserved across different dimension height layouts when the body fits. */
    @GameTest(template = "empty", timeoutTicks = 240)
    public static void podTransferIntoDifferentHeightLayoutPreservesWorldY(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerLevel end = level.getServer().getLevel(net.minecraft.world.level.Level.END);
        helper.assertTrue(end != null, "The End is available to the GameTest server");
        UUID podId = UUID.randomUUID();
        ServerSubLevel source = stamp(buildBody(helper, level), podId);
        double sourceY = source.logicalPose().position().y();

        helper.assertTrue(level.getMinBuildHeight() != end.getMinBuildHeight()
                        || level.getMaxBuildHeight() != end.getMaxBuildHeight(),
                "the pair really has different build-height layouts (" + level.getMinBuildHeight() + "/"
                        + level.getMaxBuildHeight() + " vs " + end.getMinBuildHeight() + "/"
                        + end.getMaxBuildHeight() + ")");

        SubLevelData data = SubLevelSerializer.toData(source, List.of());
        String gate = PodTransferDestination.refusal(level, end, source, data);
        XenoPixelsMod.LOGGER.info("PodTransfer GameTest height compatibility: {}", gate);
        helper.assertTrue(gate == null,
                "a body whose occupied sections and bounds fit is eligible across different height layouts (got: " + gate + ")");

        PodTransferTransaction.Result result = PodTransferTransaction.transfer(level, end, source, null);
        XenoPixelsMod.LOGGER.info("PodTransfer GameTest cross-height: status={} phase={} reason={}",
                result.status(), result.phase(), result.reason());
        ServerSubLevel destination = result.destination();
        try {
            helper.assertTrue(result.status() == PodTransferTransaction.Status.TRANSFERRED,
                    "the cross-layout transfer succeeds for overlapping world Y (status=" + result.status()
                            + ", phase=" + result.phase() + ", reason=" + result.reason() + ")");
            helper.assertTrue(destination != null && !destination.isRemoved()
                            && SubLevelContainer.getContainer(end).getSubLevel(source.getUniqueId()) == destination,
                    "the destination container owns the transferred pod");
            helper.assertTrue(Math.abs(destination.logicalPose().position().y() - sourceY) < 1.0E-6,
                    "Sable keeps the body's world Y unchanged across the dimension load");
            helper.assertTrue(source.isRemoved()
                            && SubLevelContainer.getContainer(level).getSubLevel(source.getUniqueId()) == null,
                    "the source is removed only after the cross-height destination is live");
            helper.assertTrue(countPodBodies(end, podId) == 1,
                    "the pod exists exactly once in the destination level");
            helper.runAfterDelay(2, () -> {
                try {
                    helper.assertTrue(!destination.isRemoved()
                                    && SubLevelContainer.getContainer(end).getSubLevel(destination.getUniqueId()) == destination,
                            "the cross-height pod remains registered after two ticks");
                } finally {
                    PodTransferTransaction.releaseTicket(destination, podId);
                    cleanup(end, destination);
                }
                helper.succeed();
            });
        } finally {
            if (destination == null || result.status() != PodTransferTransaction.Status.TRANSFERRED)
                cleanup(level, source);
        }
    }

    /** A narrower destination still refuses when occupied source sections fall outside its build range. */
    @GameTest(template = "empty", timeoutTicks = 240)
    public static void podTransferRefusesSectionsOutsideDestinationHeight(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerLevel end = level.getServer().getLevel(net.minecraft.world.level.Level.END);
        helper.assertTrue(end != null, "The End is available to the GameTest server");
        UUID podId = UUID.randomUUID();
        ServerSubLevel source = stamp(buildBodyAt(helper, level, new Vector3d(0.5, -128.5, 0.5)), podId);
        helper.assertTrue(source.logicalPose().position().y() < end.getMinBuildHeight(),
                "the test body starts below the destination range");

        PodTransferTransaction.Result result = PodTransferTransaction.transfer(level, end, source, null);
        XenoPixelsMod.LOGGER.info("PodTransfer GameTest out-of-range section: status={} phase={} reason={}",
                result.status(), result.phase(), result.reason());
        try {
            helper.assertTrue(result.status() == PodTransferTransaction.Status.REFUSED,
                    "an out-of-range body refuses before destination load (status=" + result.status()
                            + ", phase=" + result.phase() + ", reason=" + result.reason() + ")");
            helper.assertTrue(result.phase().equals("validate-destination")
                            && result.reason().contains("outside the destination build height"),
                    "the refusal comes from source-body bounds before Sable loads a copy (phase=" + result.phase()
                            + ", reason=" + result.reason() + ")");
            helper.assertTrue(!source.isRemoved()
                            && SubLevelContainer.getContainer(level).getSubLevel(source.getUniqueId()) == source,
                    "the out-of-range refusal leaves the registered source unchanged");
            helper.assertTrue(SubLevelContainer.getContainer(end).getSubLevel(source.getUniqueId()) == null,
                    "no out-of-range body was loaded in the destination");
        } finally {
            cleanup(level, source);
        }
        helper.succeed();
    }

    /**
     * The reservation gate has to notice a container that disagrees about who owns the serialized plot slot. The
     * harness aims the pod's own serialized tag at a slot a different body already holds, which is the same
     * disagreement the transaction has to survive; the pod's own unedited tag has to stay acceptable, or the gate
     * would be refusing everything.
     */
    @GameTest(template = "empty", timeoutTicks = 240)
    public static void podTransferRefusesADestinationSlotHeldByAnotherBody(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        UUID podId = UUID.randomUUID();
        ServerSubLevel source = stamp(buildBody(helper, level), podId);
        ServerSubLevel squatter = buildBody(helper, level);
        helper.assertTrue(squatter != source, "the container holds a second, unrelated Sable body");

        SubLevelData clean = SubLevelSerializer.toData(source, List.of());
        helper.assertTrue(PodTransferDestination.refusal(level, level, source, clean) == null,
                "a pod aimed at its own slot in the same container passes the reservation gate");

        CompoundTag tag = clean.fullTag();
        CompoundTag plot = tag.getCompound("plot");
        CompoundTag squatterPlot = SubLevelSerializer.toData(squatter, List.of()).fullTag().getCompound("plot");
        plot.putInt("plot_x", squatterPlot.getInt("plot_x"));
        plot.putInt("plot_z", squatterPlot.getInt("plot_z"));
        String gate = PodTransferDestination.refusal(level, level, source, SubLevelSerializer.fromData(tag));
        XenoPixelsMod.LOGGER.info("PodTransfer GameTest slot gate: {}", gate);
        helper.assertTrue(gate != null && gate.contains("held by another body"),
                "the reservation gate refuses a slot another body holds (got: " + gate + ")");
        helper.assertTrue(!source.isRemoved() && !squatter.isRemoved()
                        && SubLevelContainer.getContainer(level).getSubLevel(source.getUniqueId()) == source
                        && SubLevelContainer.getContainer(level).getSubLevel(squatter.getUniqueId()) == squatter,
                "the gate reads the container without taking anything apart");
        cleanup(level, squatter);
        cleanup(level, source);
        helper.succeed();
    }

    /**
     * The rider half is the player-to-sublevel tracking link plus the plot-local offset the stick code positions
     * from. A SableSubLevel cannot own a passenger, so this asserts that binding, never a vehicle seat.
     */
    @GameTest(template = "empty", timeoutTicks = 240)
    public static void podTransferRebindsTheRidersTrackingLinkToTheDestination(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        UUID podId = UUID.randomUUID();
        ServerSubLevel source = stamp(buildBody(helper, level), podId);
        // A logged-in mock player cannot be used here: the Simulated addon's datapack-sync listener throws
        // "Payload simulated:end_sea may not be sent to the client!" on login, which is what already fails the
        // V3 mock-player test. GameTestHelper$1 from makeMockPlayer is not a ServerPlayer, so the binding helpers
        // take Player - the type Sable's own tracking API is declared against. The per-tick stick pass itself is
        // therefore not exercised by this case.
        Player rider = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        rider.setPos(0.5, 65.0, 0.5);
        PodRiderBinding.restore(rider, source, PodRiderBinding.capture(rider, source));
        helper.assertTrue(PodRiderBinding.trackedBody(rider) == source,
                "the mock player starts out bound to the source pod body");
        Vector3d seatOffset = PodRiderBinding.capture(rider, source).plotLocalPosition();
        Vector3d targetBodyPosition = new Vector3d(20.5, 90.5, 20.5);

        PodTransferTransaction.Result result = PodTransferTransaction.transfer(level, level, source, rider,
                targetBodyPosition);
        XenoPixelsMod.LOGGER.info("PodTransfer GameTest rider: status={} phase={} reason={}",
                result.status(), result.phase(), result.reason());
        ServerSubLevel destination = result.destination();
        try {
            helper.assertTrue(result.status() == PodTransferTransaction.Status.TRANSFERRED,
                    "the rider transfer commits (status=" + result.status() + ", phase=" + result.phase()
                            + ", reason=" + result.reason() + ")");
            helper.assertTrue(PodRiderBinding.trackedBody(rider) == destination,
                    "the player's tracking link follows the transferred body instead of the removed source");
            helper.assertTrue(destination.logicalPose().position().distance(targetBodyPosition) < 1.0E-6,
                    "the transaction places the body at the requested destination position");
            helper.assertTrue(container.getSubLevel(source.getUniqueId()) == destination,
                    "the transferred body is the single registration for that identity");
            Vector3d restated = destination.logicalPose().transformPosition(seatOffset, new Vector3d());
            Vector3d actual = new Vector3d(rider.position().x, rider.position().y, rider.position().z);
            helper.assertTrue(restated.distanceSquared(actual) < 1.0E-6,
                    "the player sits at the same place inside the hull (expected " + restated + ", got " + actual + ")");
        } finally {
            if (destination != null) PodTransferTransaction.releaseTicket(destination, podId);
            cleanup(level, destination == null ? source : destination);
        }
        helper.succeed();
    }

    /** The one-voxel capsule keeps a run fast while still giving the transaction a real plot to move. */
    private static ServerSubLevel buildBody(GameTestHelper helper, ServerLevel level) {
        return buildBodyAt(helper, level, CAPSULE_CENTRE);
    }

    private static ServerSubLevel buildBodyAt(GameTestHelper helper, ServerLevel level, Vector3d centre) {
        PlotBodyBuilder.Result result = PlotBodyBuilder.build(level, centre, 1,
                (x, y, z) -> x == 0 && y == 0 && z == 0 ? Blocks.STONE.defaultBlockState()
                        : Blocks.AIR.defaultBlockState());
        helper.assertTrue(result != null && result.body() != null,
                "the one-voxel Sable capsule builds in " + level.dimension().location());
        return result.body();
    }

    /** The transaction refuses a body with no stable pod identity, so every test capsule carries the v1 stamp. */
    private static ServerSubLevel stamp(ServerSubLevel body, UUID podId) {
        CompoundTag data = body.getUserDataTag();
        if (data == null) data = new CompoundTag();
        data.putUUID(POD_ID, podId);
        data.putString(POD_HULL, "xenopixelsmod:pod/hull/classic_egg");
        data.putInt(POD_SCHEMA, 1);
        body.setUserDataTag(data);
        return body;
    }

    private static int countPodBodies(ServerLevel level, UUID podId) {
        int found = 0;
        for (var candidate : SubLevelContainer.getContainer(level).getAllSubLevels()) {
            if (!(candidate instanceof ServerSubLevel subLevel)) continue;
            CompoundTag data = subLevel.getUserDataTag();
            if (data != null && data.hasUUID(POD_ID) && data.getUUID(POD_ID).equals(podId)) found++;
        }
        return found;
    }

    private static void cleanup(ServerLevel level, ServerSubLevel body) {
        if (body == null || body.isRemoved()) return;
        var container = SubLevelContainer.getContainer(level);
        if (container.getSubLevel(body.getUniqueId()) == body)
            container.removeSubLevel(body, SubLevelRemovalReason.REMOVED);
    }
}
