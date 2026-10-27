package net.bullettrain.xenopixelsmod.space.pod;

import com.mojang.authlib.GameProfile;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsProvider;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.combat.v3.V3Resources;
import net.bullettrain.xenopixelsmod.config.XenoSpaceConfig;
import net.bullettrain.xenopixelsmod.space.physics.PlotBodyBuilder;
import net.bullettrain.xenopixelsmod.space.pod.transfer.PodRiderBinding;
import net.bullettrain.xenopixelsmod.space.pod.transfer.PodTransferTransaction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Vector3d;

import java.util.UUID;

/** Optional dedicated-server proof that the scheduled Sable force pass distinguishes cruise from Ctrl boost. */
@GameTestHolder(XenoPixelsMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SpacePodFlightGameTests {
    private static final String POD_ID = "xenopixelsmod:space_pod_id";
    private static final String TEST_TEMPLATE = "empty";

    private SpacePodFlightGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 80, required = false, manualOnly = true,
            batch = "space_pod_flight")
    public static void serverPhysicsAppliesCruiseAndBoostToTrackedPod(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        XenoSpaceConfig.Data previousConfig = XenoSpaceConfig.snapshot();
        XenoSpaceConfig.Data testConfig = XenoSpaceConfig.snapshot();
        testConfig.enabled = true;
        testConfig.spacePodsEnabled = true;
        testConfig.podCruiseSpeed = 16.0;
        testConfig.podCruiseAcceleration = 4.0;
        testConfig.podBoostSpeed = 48.0;
        testConfig.podBoostAcceleration = 12.0;
        testConfig.podFlightResponsePerSecond = 2.0;
        testConfig.podBoostKiPerSecond = 0.0; // This fake-player fixture has no DMZ KI capability; it isolates flight response.
        XenoSpaceConfig.apply(testConfig);

        UUID podId = UUID.randomUUID();
        Vec3 centre = helper.absoluteVec(new Vec3(2.5, 4.5, 2.5));
        PlotBodyBuilder.Result build = PlotBodyBuilder.build(level,
                new Vector3d(centre.x, centre.y, centre.z), 1,
                (x, y, z) -> x == 0 && y == 0 && z == 0
                        ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
        ServerSubLevel pod = build == null ? null : build.body();
        FakePlayer player = FakePlayerFactory.get(level,
                new GameProfile(UUID.randomUUID(), "SPF_" + UUID.randomUUID().toString().substring(0, 8)));
        boolean[] finished = {false};
        Runnable cleanup = () -> {
            if (finished[0]) return;
            finished[0] = true;
            PodRiderBinding.clear(player);
            SpacePodFlightPass.clearForTest();
            if (pod != null && !pod.isRemoved()) {
                var container = SubLevelContainer.getContainer(level);
                if (container.getSubLevel(pod.getUniqueId()) == pod)
                    container.removeSubLevel(pod, SubLevelRemovalReason.REMOVED);
            }
            XenoSpaceConfig.apply(previousConfig);
        };
        try {
            helper.assertTrue(pod != null, "the one-voxel Sable test pod builds");
            CompoundTag tag = pod.getUserDataTag();
            if (tag == null) tag = new CompoundTag();
            tag.putUUID(POD_ID, podId);
            pod.setUserDataTag(tag);
            player.setPos(centre);
            player.setYRot(0.0f);
            player.setXRot(0.0f);
            helper.assertTrue(PodRiderBinding.restore(player, pod, PodRiderBinding.capture(player, pod)),
                    "the test rider is bound to the pod");
            helper.assertTrue(PodTransferTransaction.podId(PodRiderBinding.trackedBody(player)) != null,
                    "the server recognizes the tracked Sable body as a pod");
            ServerPlayer serverPlayer = player;

            long started = level.getServer().getTickCount();
            int[] cruiseTicks = {0};
            double[] cruiseSpeed = {0.0};
            int[] boostTicks = {0};
            helper.onEachTick(() -> {
                if (finished[0]) return;
                long tick = level.getServer().getTickCount();
                SpacePodFlightPass.refreshPilot(serverPlayer, tick);
                Vector3d velocity = RigidBodyHandle.of(pod).getLinearVelocity(new Vector3d());
                if (cruiseTicks[0] == 0) {
                    if (velocity.length() > 0.05) {
                        cruiseSpeed[0] = velocity.length();
                        SpacePodFlightPass.setBoost(serverPlayer, true);
                        cruiseTicks[0] = 1;
                    } else if (tick - started > 12) {
                        cleanup.run();
                        helper.fail("scheduled pod cruise did not change Sable body velocity");
                    }
                    return;
                }

                if ((tick & 1L) == 0L) SpacePodFlightPass.setBoost(serverPlayer, true);
                if (++boostTicks[0] >= 12) {
                    double boostedSpeed = velocity.length();
                    helper.assertTrue(boostedSpeed > cruiseSpeed[0] + 0.25,
                            "Ctrl boost raises the pod's measured Sable velocity (cruise="
                                    + cruiseSpeed[0] + ", boost=" + boostedSpeed + ")");
                    XenoPixelsMod.LOGGER.info("P15 pod flight GameTest PASS cruiseSpeed={} boostSpeed={} dimension={}",
                            cruiseSpeed[0], boostedSpeed, level.dimension().location());
                    cleanup.run();
                    helper.succeed();
                }
            });
        } catch (RuntimeException | Error failure) {
            cleanup.run();
            throw failure;
        }
    }

    @GameTest(template = TEST_TEMPLATE, timeoutTicks = 100, required = false, manualOnly = true,
            batch = "space_pod_flight")
    public static void boostWithoutEnoughKiFallsBackToCruise(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        XenoSpaceConfig.Data previousConfig = XenoSpaceConfig.snapshot();
        XenoSpaceConfig.Data testConfig = XenoSpaceConfig.snapshot();
        testConfig.enabled = true;
        testConfig.spacePodsEnabled = true;
        testConfig.podCruiseSpeed = 16.0;
        testConfig.podCruiseAcceleration = 4.0;
        testConfig.podBoostSpeed = 48.0;
        testConfig.podBoostAcceleration = 12.0;
        testConfig.podFlightResponsePerSecond = 2.0;
        testConfig.podBoostKiPerSecond = 100_000.0;
        XenoSpaceConfig.apply(testConfig);
        helper.assertTrue(Math.abs(100_000.0 - XenoSpaceConfig.podBoostKiPerSecond()) < 1.0e-6,
                "the test applies its unaffordable KI charge");
        SpacePodFlightPass.clearForTest();
        float[] requestedCost = {0.0f};
        SpacePodFlightPass.setKiSpenderForTest((ignoredPlayer, cost) -> {
            requestedCost[0] = cost;
            return false;
        });

        UUID podId = UUID.randomUUID();
        Vec3 centre = helper.absoluteVec(new Vec3(2.5, 4.5, 2.5));
        PlotBodyBuilder.Result build = PlotBodyBuilder.build(level,
                new Vector3d(centre.x, centre.y, centre.z), 1,
                (x, y, z) -> x == 0 && y == 0 && z == 0
                        ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
        ServerSubLevel pod = build == null ? null : build.body();
        FakePlayer player = FakePlayerFactory.get(level,
                new GameProfile(UUID.randomUUID(), "SPN_" + UUID.randomUUID().toString().substring(0, 8)));
        player.setGameMode(GameType.SURVIVAL);
        player.getAbilities().instabuild = false;
        player.onUpdateAbilities();
        helper.assertFalse(player.isCreative(), "the KI-payment probe must use survival rules");
        StatsProvider.get(StatsCapability.INSTANCE, player).ifPresent(stats ->
                stats.getResources().setCurrentEnergy(0.0f));
        helper.assertFalse(V3Resources.spend(player, 1.0f, 0.0f),
                "the KI-payment probe must start with an unavailable or empty DragonMineZ KI pool");
        boolean[] finished = {false};
        Runnable cleanup = () -> {
            if (finished[0]) return;
            finished[0] = true;
            PodRiderBinding.clear(player);
            SpacePodFlightPass.clearForTest();
            if (pod != null && !pod.isRemoved()) {
                var container = SubLevelContainer.getContainer(level);
                if (container.getSubLevel(pod.getUniqueId()) == pod)
                    container.removeSubLevel(pod, SubLevelRemovalReason.REMOVED);
            }
            XenoSpaceConfig.apply(previousConfig);
        };
        try {
            helper.assertTrue(pod != null, "the one-voxel Sable test pod builds");
            CompoundTag tag = pod.getUserDataTag();
            if (tag == null) tag = new CompoundTag();
            tag.putUUID(POD_ID, podId);
            pod.setUserDataTag(tag);
            player.setPos(centre);
            player.setYRot(0.0f);
            player.setXRot(0.0f);
            helper.assertTrue(PodRiderBinding.restore(player, pod, PodRiderBinding.capture(player, pod)),
                    "the test rider is bound to the pod");
            ServerPlayer serverPlayer = player;
            long started = level.getServer().getTickCount();
            int[] pilotTicks = {0};
            double[] cruiseSpeed = {0.0};
            helper.onEachTick(() -> {
                if (finished[0]) return;
                long tick = level.getServer().getTickCount();
                // DMZ may regenerate a test player's pool between ticks; keep the integration falsifier empty.
                StatsProvider.get(StatsCapability.INSTANCE, player).ifPresent(stats ->
                        stats.getResources().setCurrentEnergy(0.0f));
                SpacePodFlightPass.refreshPilot(serverPlayer, tick);
                if (pilotTicks[0] == 0) {
                    double currentSpeed = RigidBodyHandle.of(pod).getLinearVelocity(new Vector3d()).length();
                    if (currentSpeed > 0.05) {
                        cruiseSpeed[0] = currentSpeed;
                        SpacePodFlightPass.setBoost(serverPlayer, true);
                        pilotTicks[0] = 1;
                    } else if (tick - started > 20) {
                        cleanup.run();
                        helper.fail("scheduled pod cruise did not begin for the unaffordable-boost check");
                    }
                    return;
                }
                if ((tick & 1L) == 0L) SpacePodFlightPass.setBoost(serverPlayer, true);
                if (++pilotTicks[0] >= 14) {
                    double currentSpeed = RigidBodyHandle.of(pod).getLinearVelocity(new Vector3d()).length();
                    helper.assertTrue(Math.abs(requestedCost[0] - 100_000.0f) < 0.1f,
                            "the server attempted the configured KI price before rejecting boost");
                    helper.assertFalse(SpacePodFlightPass.boostActiveForTest(player.getUUID()),
                            "unaffordable boost input must leave the tracked pilot in cruise mode");
                    helper.assertTrue(currentSpeed > 0.05,
                            "the unaffordable boost refusal leaves the pod's cruise physics active");
                    XenoPixelsMod.LOGGER.info("P15 pod KI GameTest PASS requestedKi={} cruiseStartSpeed={} currentSpeed={}",
                            requestedCost[0], cruiseSpeed[0], currentSpeed);
                    cleanup.run();
                    helper.succeed();
                }
            });
        } catch (RuntimeException | Error failure) {
            cleanup.run();
            throw failure;
        }
    }

    @GameTest(template = TEST_TEMPLATE, timeoutTicks = 100, required = false, manualOnly = true,
            batch = "space_pod_flight")
    public static void boostKiPaymentUsesOneSecondCadenceAndRechargesAfterRelease(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        XenoSpaceConfig.Data previousConfig = XenoSpaceConfig.snapshot();
        XenoSpaceConfig.Data testConfig = XenoSpaceConfig.snapshot();
        testConfig.enabled = true;
        testConfig.spacePodsEnabled = true;
        testConfig.podBoostKiPerSecond = 5.0;
        XenoSpaceConfig.apply(testConfig);
        SpacePodFlightPass.clearForTest();
        int[] charges = {0};
        long[] firstChargeTick = {Long.MIN_VALUE};
        long[] secondChargeTick = {Long.MIN_VALUE};
        long[] thirdChargeTick = {Long.MIN_VALUE};
        SpacePodFlightPass.setKiSpenderForTest((ignoredPlayer, cost) -> {
            helper.assertTrue(Math.abs(cost - 5.0f) < 1.0e-6,
                    "each accepted boost payment uses the configured KI amount");
            long tick = level.getServer().getTickCount();
            switch (++charges[0]) {
                case 1 -> firstChargeTick[0] = tick;
                case 2 -> secondChargeTick[0] = tick;
                case 3 -> thirdChargeTick[0] = tick;
                default -> helper.fail("boost charged more than once per second while held");
            }
            return true;
        });

        UUID podId = UUID.randomUUID();
        Vec3 centre = helper.absoluteVec(new Vec3(2.5, 4.5, 2.5));
        PlotBodyBuilder.Result build = PlotBodyBuilder.build(level,
                new Vector3d(centre.x, centre.y, centre.z), 1,
                (x, y, z) -> x == 0 && y == 0 && z == 0
                        ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
        ServerSubLevel pod = build == null ? null : build.body();
        FakePlayer player = FakePlayerFactory.get(level,
                new GameProfile(UUID.randomUUID(), "SPC_" + UUID.randomUUID().toString().substring(0, 8)));
        player.setGameMode(GameType.SURVIVAL);
        player.getAbilities().instabuild = false;
        player.onUpdateAbilities();
        boolean[] finished = {false};
        Runnable cleanup = () -> {
            if (finished[0]) return;
            finished[0] = true;
            PodRiderBinding.clear(player);
            SpacePodFlightPass.clearForTest();
            if (pod != null && !pod.isRemoved()) {
                var container = SubLevelContainer.getContainer(level);
                if (container.getSubLevel(pod.getUniqueId()) == pod)
                    container.removeSubLevel(pod, SubLevelRemovalReason.REMOVED);
            }
            XenoSpaceConfig.apply(previousConfig);
        };
        try {
            helper.assertTrue(pod != null, "the one-voxel Sable test pod builds");
            CompoundTag tag = pod.getUserDataTag();
            if (tag == null) tag = new CompoundTag();
            tag.putUUID(POD_ID, podId);
            pod.setUserDataTag(tag);
            player.setPos(centre);
            helper.assertTrue(PodRiderBinding.restore(player, pod, PodRiderBinding.capture(player, pod)),
                    "the test rider is bound to the pod");
            long started = level.getServer().getTickCount();
            boolean[] released = {false};
            helper.onEachTick(() -> {
                if (finished[0]) return;
                long tick = level.getServer().getTickCount();
                long elapsed = tick - started;
                // Four-tick keepalives model the client's held-key packet cadence.
                if (!released[0] || elapsed >= 30) {
                    if ((tick & 3L) == 0L) SpacePodFlightPass.setBoost(player, true);
                }
                if (elapsed == 1) SpacePodFlightPass.refreshPilot(player, tick);
                SpacePodFlightPass.refreshPilot(player, tick);
                if (!released[0] && elapsed >= 25) {
                    helper.assertTrue(charges[0] == 2,
                            "held boost is charged initially and once at the one-second renewal");
                    helper.assertTrue(secondChargeTick[0] - firstChargeTick[0] == 20L,
                            "the held renewal occurs exactly 20 server ticks after the initial payment");
                    SpacePodFlightPass.setBoost(player, false);
                    released[0] = true;
                }
                if (released[0] && elapsed >= 30 && charges[0] == 3) {
                    helper.assertTrue(charges[0] == 3,
                            "a new press requests its initial payment without waiting another second");
                    helper.assertTrue(thirdChargeTick[0] > secondChargeTick[0],
                            "the post-release payment follows the held renewal");
                    XenoPixelsMod.LOGGER.info("P15 pod KI cadence GameTest PASS firstTick={} secondTick={} repressTick={}",
                            firstChargeTick[0], secondChargeTick[0], thirdChargeTick[0]);
                    cleanup.run();
                    helper.succeed();
                }
                if (elapsed > 70) {
                    cleanup.run();
                    helper.fail("boost KI cadence did not reach both renewal and repress payment assertions");
                }
            });
        } catch (RuntimeException | Error failure) {
            cleanup.run();
            throw failure;
        }
    }
}
