package net.bullettrain.xenopixelsmod.space;

import com.mojang.authlib.GameProfile;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.space.physics.PlotBodyBuilder;
import net.bullettrain.xenopixelsmod.space.pod.transfer.PodRiderBinding;
import net.bullettrain.xenopixelsmod.space.pod.transfer.PodTransferTransaction;
import org.joml.Vector3d;

import java.util.UUID;

/**
 * Dedicated-server integration probe for the production pod handoff leg across two actual dimensions.
 * It is optional/manual because GameTestServer does not load the mod's Voidsea dimension; The End is
 * used to exercise the same Sable serialization, player dimension-change and rider-rebind sequence.
 */
@GameTestHolder(XenoPixelsMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SpacePodCrossDimensionGameTests {
    private static final String POD_ID = "xenopixelsmod:space_pod_id";
    private static final String POD_HULL = "xenopixelsmod:space_pod_hull";
    private static final String POD_SCHEMA = "xenopixelsmod:space_pod_schema";
    private static final Vec3 POD_CENTRE_OFFSET = new Vec3(1.5, 3.5, 1.5);

    private SpacePodCrossDimensionGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 160, required = false, manualOnly = true,
            batch = "space_pod_cross_dimension")
    public static void podLegReturnsCrossDimensionBodyWhenRiderMoveFails(GameTestHelper helper) {
        ServerLevel source = helper.getLevel();
        ServerLevel destination = source.getServer().getLevel(Level.END);
        if (destination == null) {
            helper.fail("Dedicated-server GameTest requires the vanilla End level");
            return;
        }

        Vec3 centre = helper.absoluteVec(POD_CENTRE_OFFSET);
        PlotBodyBuilder.Result built = PlotBodyBuilder.build(source,
                new Vector3d(centre.x, centre.y, centre.z), 1,
                (x, y, z) -> x == 0 && y == 0 && z == 0
                        ? Blocks.WHITE_CONCRETE.defaultBlockState() : Blocks.AIR.defaultBlockState());
        helper.assertTrue(built != null && built.body() != null, "the source pod fixture is a live Sable body");
        ServerSubLevel pod = built.body();
        UUID podId = UUID.randomUUID();
        var userData = pod.getUserDataTag();
        if (userData == null) userData = new net.minecraft.nbt.CompoundTag();
        userData.putUUID(POD_ID, podId);
        userData.putString(POD_HULL, "xenopixelsmod:pod/hull/classic_egg");
        userData.putInt(POD_SCHEMA, 1);
        pod.setUserDataTag(userData);

        UUID riderId = UUID.randomUUID();
        String riderName = "XPD_" + riderId.toString().replace("-", "").substring(0, 8);
        FakePlayer rider = FakePlayerFactory.get(source, new GameProfile(riderId, riderName));
        source.addNewPlayer(rider);
        ServerSubLevel[] destinationPod = new ServerSubLevel[1];
        try {
            rider.setPos(centre);
            helper.assertTrue(PodRiderBinding.restore(rider, pod, PodRiderBinding.capture(rider, pod)),
                    "the connected server-side fixture rider is bound to the pod before departure");

            Vec3 arrival = new Vec3(40.5, 100.5, 40.5);
            SpaceHandoffPass.PodLeg leg = SpaceHandoffPass.podLeg(rider, pod, source, destination, arrival,
                    null, null, target -> {
                        throw new IllegalStateException("injected before player dimension mutation");
                    });
            destinationPod[0] = leg.transfer() != null
                    && leg.transfer().status() == PodTransferTransaction.Status.TRANSFERRED
                    ? leg.transfer().destination() : null;

            helper.assertTrue(!leg.moved() && leg.riderBound(),
                    "a failed rider move before dimension mutation restores the rider aboard the pod ("
                            + leg.problem() + ")");
            helper.assertTrue(leg.transfer() != null
                            && leg.transfer().status() == PodTransferTransaction.Status.TRANSFERRED,
                    "the pod's forward cross-dimension transfer succeeded before the injected rider failure");
            ServerSubLevel recovered = SubLevelContainer.getContainer(source).getSubLevel(pod.getUniqueId())
                    instanceof ServerSubLevel live ? live : null;
            helper.assertTrue(recovered != null && recovered != pod && !recovered.isRemoved(),
                    "rollback restores the pod under its original Sable body UUID in the source dimension");
            helper.assertTrue(SubLevelContainer.getContainer(destination).getSubLevel(pod.getUniqueId()) == null,
                    "rollback leaves no duplicate pod body in the destination dimension");
            helper.assertTrue(PodRiderBinding.trackedBody(rider) == recovered
                            && rider.serverLevel() == source,
                    "the fixture rider remains in the source dimension and is rebound to the recovered pod");
            helper.assertTrue(!net.bullettrain.xenopixelsmod.space.pod.transfer.PodTransferDestination.holds(
                            destination, pod.getUniqueId(), podId.toString()),
                    "rollback releases the temporary destination transfer ticket");
            XenoPixelsMod.LOGGER.info("POD CROSS-DIMENSION ROLLBACK PASS rider={} pod={} source={} destination={} problem={}",
                    riderId, podId, source.dimension().location(), destination.dimension().location(), leg.problem());
            helper.succeed();
        } finally {
            source.getServer().getPlayerList().remove(rider);
            ServerSubLevel sourceBody = SubLevelContainer.getContainer(source).getSubLevel(pod.getUniqueId())
                    instanceof ServerSubLevel live ? live : null;
            ServerSubLevel destinationBody = SubLevelContainer.getContainer(destination).getSubLevel(pod.getUniqueId())
                    instanceof ServerSubLevel live ? live : null;
            cleanup(source, sourceBody);
            cleanup(destination, destinationBody == sourceBody ? null : destinationBody);
        }
    }

    private static void cleanup(ServerLevel level, ServerSubLevel body) {
        if (body == null || body.isRemoved()) return;
        var container = SubLevelContainer.getContainer(level);
        if (container != null && container.getSubLevel(body.getUniqueId()) == body)
            container.removeSubLevel(body, SubLevelRemovalReason.REMOVED);
    }
}
