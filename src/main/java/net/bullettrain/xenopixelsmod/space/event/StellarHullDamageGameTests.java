package net.bullettrain.xenopixelsmod.space.event;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import dev.ryanhcode.sable.api.sublevel.ticket.SubLevelLoadingTicketType;
import net.bullettrain.xenopixelsmod.space.physics.PlotBodyBuilder;
import net.bullettrain.xenopixelsmod.space.pod.transfer.PodRiderBinding;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import com.mojang.authlib.GameProfile;
import org.joml.Vector3d;

import java.util.List;
import java.util.UUID;

/** Sable integration falsifier for §23.1's bounded supernova hull voxel damage. */
@GameTestHolder("xenopixels_space_config")
@PrefixGameTestTemplate(false)
public final class StellarHullDamageGameTests {
    private static final SubLevelLoadingTicketType<Unit> KEEP_ALIVE = SubLevelLoadingTicketType.COMMAND_FORCED;
    private StellarHullDamageGameTests() {}

    private static BlockPos findNearby(GameTestHelper helper,
                                       dev.ryanhcode.sable.sublevel.plot.EmbeddedPlotLevelAccessor accessor,
                                       BlockState state) {
        for (int radius = 0; radius <= 8; radius++) {
            for (int x = -radius; x <= radius; x++)
                for (int y = -radius; y <= radius; y++)
                    for (int z = -radius; z <= radius; z++) {
                        if (Math.max(Math.max(Math.abs(x), Math.abs(y)), Math.abs(z)) != radius) continue;
                        BlockPos pos = new BlockPos(x, y, z);
                        if (accessor.getBlockState(pos).is(state.getBlock())) return pos;
                    }
        }
        helper.fail("could not find " + state + " near the embedded plot origin");
        throw new AssertionError("unreachable");
    }

    private static BlockPos installDependentTorch(GameTestHelper helper,
                                                   dev.ryanhcode.sable.sublevel.plot.EmbeddedPlotLevelAccessor accessor) {
        BlockPos support = findNearby(helper, accessor, Blocks.STONE.defaultBlockState());
        BlockPos torch = support.above();
        helper.assertTrue(accessor.setBlock(torch, Blocks.TORCH.defaultBlockState(), 2, 0),
                "flag-2 fixture placement creates a torch above a known stone support");
        helper.assertTrue(accessor.getBlockState(torch).is(Blocks.TORCH), "dependent torch fixture exists");
        return support;
    }

    private static BlockPos findUntouchedStone(GameTestHelper helper,
                                               dev.ryanhcode.sable.sublevel.plot.EmbeddedPlotLevelAccessor accessor,
                                               BlockPos support, BlockPos torch) {
        BlockPos control = null;
        int farthest = -1;
        for (int x = -8; x <= 8; x++)
            for (int y = -8; y <= 8; y++)
                for (int z = -8; z <= 8; z++) {
                    BlockPos candidate = new BlockPos(x, y, z);
                    if (candidate.equals(support) || candidate.equals(torch)
                            || !accessor.getBlockState(candidate).is(Blocks.STONE)) continue;
                    int distance = x * x + y * y + z * z;
                    if (distance > farthest) {
                        control = candidate;
                        farthest = distance;
                    }
                }
        helper.assertTrue(control != null, "an untouched stone control voxel exists in the body");
        return control;
    }

    private static BlockState readHostBlock(ServerSubLevel body, BlockPos local) {
        var host = (net.minecraft.server.level.ServerLevel) body.getLevel();
        return host.getBlockState(body.getPlot().getCenterBlock().offset(local));
    }

    private static void assertNoOtherBodySharesPlotCenter(GameTestHelper helper, ServerSubLevel body) {
        var container = SubLevelContainer.getContainer((net.minecraft.server.level.ServerLevel) body.getLevel());
        var center = body.getPlot().getCenterBlock();
        long conflicts = container.getAllSubLevels().stream()
                .filter(other -> other != body && other.getPlot().getCenterBlock().equals(center))
                .count();
        helper.assertTrue(conflicts == 0, "no other live sub-level shares plot center " + center);
    }

    private static void logAccessorFrame(String phase, ServerSubLevel body,
                                         dev.ryanhcode.sable.sublevel.plot.EmbeddedPlotLevelAccessor frozen,
                                         dev.ryanhcode.sable.sublevel.plot.EmbeddedPlotLevelAccessor fresh,
                                         BlockPos support, BlockPos torch, BlockPos control) {
        var pose = body.logicalPose().position();
        BlockState hostSupport = readHostBlock(body, support);
        BlockState hostTorch = readHostBlock(body, torch);
        BlockState hostControl = readHostBlock(body, control);
        net.bullettrain.xenopixelsmod.XenoPixelsMod.LOGGER.info(
                "HULL_ACCESSOR_PROBE {} body={} center={} pose=({}, {}, {}) "
                        + "frozen[support={}, torch={}, control={}] fresh[support={}, torch={}, control={}] "
                        + "host[support={}, torch={}, control={}]",
                phase, body.getUniqueId(), body.getPlot().getCenterBlock(), pose.x(), pose.y(), pose.z(),
                frozen.getBlockState(support), frozen.getBlockState(torch), frozen.getBlockState(control),
                fresh.getBlockState(support), fresh.getBlockState(torch), fresh.getBlockState(control),
                hostSupport, hostTorch, hostControl);
    }

    @GameTest(template = "empty", timeoutTicks = 120, batch = "stellar_hull_damage")
    public static void waveRemovesOneFacingHullVoxelAndUpdatesSableMass(GameTestHelper helper) {
        var level = helper.getLevel();
        var container = SubLevelContainer.getContainer(level);
        PlotBodyBuilder.Result built = PlotBodyBuilder.build(level, new Vector3d(64.5, 64.5, 64.5), 2,
                (x, y, z) -> Blocks.STONE.defaultBlockState());
        helper.assertTrue(built != null && built.body() != null, "Sable hull fixture is assembled");
        ServerSubLevel body = built.body();
        UUID id = body.getUniqueId();
        try {
            var center = body.logicalPose().position();
            long now = level.getGameTime();
            var wave = new StellarBlastWaveMath.Wave(center.x() + 100.0, center.y(), center.z(), now, 1.0, 20);
            var active = new StellarBlastWaveService.ActiveWave("test-star", wave, 1.0, List.of());
            double massBefore = body.getMassTracker().getMass();
            BlockPos damaged = StellarBlastWaveService.damageOneHullVoxel(body, active, now);
            helper.assertTrue(damaged != null, "a bounded face probe finds a breakable hull block");
            helper.assertTrue(body.getPlot().getEmbeddedLevelAccessor().getBlockState(damaged).isAir(),
                    "the selected local hull voxel is removed through Sable's embedded accessor");
            helper.assertTrue(body.getMassTracker().getMass() < massBefore,
                    "Sable's block-change callback updates the body's mass after the hull hit");
            helper.succeed();
        } finally {
            if (container.getSubLevel(id) instanceof ServerSubLevel live && !live.isRemoved())
                container.removeSubLevel(live, SubLevelRemovalReason.REMOVED);
        }
    }

    @GameTest(template = "empty", timeoutTicks = 120, batch = "stellar_hull_damage")
    public static void destroyingLastHullVoxelReleasesBoundPlayer(GameTestHelper helper) {
        var level = helper.getLevel();
        var container = SubLevelContainer.getContainer(level);
        PlotBodyBuilder.Result built = PlotBodyBuilder.build(level, new Vector3d(96.5, 64.5, 64.5), 1,
                (x, y, z) -> x == 0 && y == 0 && z == 0
                        ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
        helper.assertTrue(built != null && built.body() != null, "one-voxel Sable hull fixture is assembled");
        ServerSubLevel body = built.body();
        UUID id = body.getUniqueId();
        UUID riderId = UUID.randomUUID();
        String riderName = "SNR_" + riderId.toString().replace("-", "").substring(0, 8);
        ServerPlayer rider = FakePlayerFactory.get(level, new GameProfile(riderId, riderName));
        level.addNewPlayer(rider);
        try {
            rider.setPos(96.5, 64.5, 64.5);
            helper.assertTrue(PodRiderBinding.restore(rider, body, PodRiderBinding.capture(rider, body)),
                    "fixture player is bound to the hull body");
            var center = body.logicalPose().position();
            long now = level.getGameTime();
            var wave = new StellarBlastWaveMath.Wave(center.x() + 100.0, center.y(), center.z(), now, 1.0, 20);
            var active = new StellarBlastWaveService.ActiveWave("test-star", wave, 1.0, List.of());
            BlockPos damaged = StellarBlastWaveService.damageOneHullVoxel(body, active, now);
            helper.assertTrue(damaged != null, "the wave removes the only hull voxel");
            helper.assertTrue(body.isRemoved() || body.getMassTracker().isInvalid(),
                    "Sable marks the zero-mass hull for removal");
            helper.assertTrue(PodRiderBinding.trackedBody(rider) == null,
                    "the player's server-side Sable tracking binding is cleared before deferred removal");
            helper.succeed();
        } finally {
            rider.discard();
            if (container.getSubLevel(id) instanceof ServerSubLevel live && !live.isRemoved())
                container.removeSubLevel(live, SubLevelRemovalReason.REMOVED);
        }
    }

    @GameTest(template = "empty", timeoutTicks = 120, batch = "stellar_hull_damage_rider_survives")
    public static void nonterminalHullDamageKeepsBoundPlayerAttached(GameTestHelper helper) {
        var level = helper.getLevel();
        var container = SubLevelContainer.getContainer(level);
        PlotBodyBuilder.Result built = PlotBodyBuilder.build(level, new Vector3d(256.5, 64.5, 64.5), 2,
                (x, y, z) -> Blocks.STONE.defaultBlockState());
        helper.assertTrue(built != null && built.body() != null, "multi-voxel Sable hull fixture is assembled");
        ServerSubLevel body = built.body();
        UUID bodyId = body.getUniqueId();
        container.addForceLoadTicket(body, KEEP_ALIVE, Unit.INSTANCE);
        UUID riderId = UUID.randomUUID();
        String riderName = "SNR_" + riderId.toString().replace("-", "").substring(0, 8);
        ServerPlayer rider = FakePlayerFactory.get(level, new GameProfile(riderId, riderName));
        level.addNewPlayer(rider);
        try {
            assertNoOtherBodySharesPlotCenter(helper, body);
            rider.setPos(256.5, 64.5, 64.5);
            helper.assertTrue(PodRiderBinding.restore(rider, body, PodRiderBinding.capture(rider, body)),
                    "fixture player is bound to the multi-voxel hull body");
            double massBefore = body.getMassTracker().getMass();
            var center = body.logicalPose().position();
            long now = level.getGameTime();
            var wave = new StellarBlastWaveMath.Wave(center.x() + 100.0, center.y(), center.z(), now, 1.0, 20);
            var active = new StellarBlastWaveService.ActiveWave("test-star", wave, 1.0, List.of());
            BlockPos damaged = StellarBlastWaveService.damageOneHullVoxel(body, active, now);
            helper.assertTrue(damaged != null, "supernova wave removes one exposed hull voxel");
            helper.assertTrue(body.getMassTracker().getMass() < massBefore,
                    "Sable mass decreases after nonterminal hull damage");
            helper.assertTrue(!body.isRemoved() && !body.getMassTracker().isInvalid(),
                    "multi-voxel ship body remains active after one hull hit");
            helper.assertTrue(PodRiderBinding.trackedBody(rider) == body,
                    "nonterminal hull damage leaves the player bound to the same body");
            helper.runAfterDelay(2, () -> {
                try {
                    helper.assertTrue(!body.isRemoved() && !body.getMassTracker().isInvalid(),
                            "body remains active after two server ticks following nonterminal hull damage");
                    helper.assertTrue(PodRiderBinding.trackedBody(rider) == body,
                            "rider remains bound to the same body after two server ticks");
                } finally {
                    level.getServer().getPlayerList().remove(rider);
                    if (container.getSubLevel(bodyId) instanceof ServerSubLevel live && !live.isRemoved()) {
                        container.removeForceLoadTicket(live, KEEP_ALIVE, Unit.INSTANCE);
                        container.removeSubLevel(live, SubLevelRemovalReason.REMOVED);
                    }
                    helper.assertTrue(level.players().stream().noneMatch(player -> player.getUUID().equals(riderId)),
                            "FakePlayer is removed from the GameTest level after cleanup");
                }
                helper.succeed();
            });
        } catch (RuntimeException | AssertionError failure) {
            level.getServer().getPlayerList().remove(rider);
            if (container.getSubLevel(bodyId) instanceof ServerSubLevel live && !live.isRemoved()) {
                container.removeForceLoadTicket(live, KEEP_ALIVE, Unit.INSTANCE);
                container.removeSubLevel(live, SubLevelRemovalReason.REMOVED);
            }
            throw failure;
        }
    }

    @GameTest(template = "empty", timeoutTicks = 120, batch = "stellar_hull_damage_flags3_probe")
    public static void flagThreeHullRemovalRecordsStableAccessorOutcome(GameTestHelper helper) {
        var level = helper.getLevel();
        var container = SubLevelContainer.getContainer(level);
        BlockPos vanillaSupport = helper.absolutePos(new BlockPos(1, 1, 1));
        BlockPos vanillaTorch = vanillaSupport.above();
        level.setBlockAndUpdate(vanillaSupport, Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(vanillaTorch, Blocks.TORCH.defaultBlockState());
        helper.assertTrue(level.getBlockState(vanillaTorch).is(Blocks.TORCH), "vanilla-world torch control is placed");
        level.setBlockAndUpdate(vanillaSupport, Blocks.AIR.defaultBlockState());
        helper.assertTrue(level.getBlockState(vanillaTorch).isAir(),
                "vanilla-world control confirms a support removal updates its dependent torch");
        PlotBodyBuilder.Result built = PlotBodyBuilder.build(level, new Vector3d(128.5, 64.5, 64.5), 2,
                (x, y, z) -> Blocks.STONE.defaultBlockState());
        helper.assertTrue(built != null && built.body() != null, "neighbor-update Sable hull fixture is assembled");
        ServerSubLevel body = built.body();
        UUID id = body.getUniqueId();
        container.addForceLoadTicket(body, KEEP_ALIVE, Unit.INSTANCE);
        assertNoOtherBodySharesPlotCenter(helper, body);
        var accessor = body.getPlot().getEmbeddedLevelAccessor();
        try {
            BlockPos support = installDependentTorch(helper, accessor);
            BlockPos torch = support.above();
            BlockPos control = findUntouchedStone(helper, accessor, support, torch);
            double massBefore = body.getMassTracker().getMass();
            logAccessorFrame("flags-3-t0", body, accessor, body.getPlot().getEmbeddedLevelAccessor(),
                    support, torch, control);
            helper.assertTrue(accessor.setBlock(support, Blocks.AIR.defaultBlockState(), 3, 0),
                    "flag-3 hull edit removes the support voxel");
            helper.assertTrue(accessor.getBlockState(support).isAir(), "removed support stays air");
            helper.runAfterDelay(2, () -> {
                try {
                    var fresh = body.getPlot().getEmbeddedLevelAccessor();
                    logAccessorFrame("flags-3-t+2", body, accessor, fresh, support, torch, control);
                    helper.assertTrue(fresh.getBlockState(support).isAir(), "removed support stays air");
                    helper.assertTrue(fresh.getBlockState(control).is(Blocks.STONE),
                            "untouched control stone remains loaded and unchanged");
                    helper.assertTrue(readHostBlock(body, control).is(Blocks.STONE),
                            "direct host-level control read matches the embedded plot");
                    helper.assertTrue(accessor.getBlockState(torch).equals(fresh.getBlockState(torch)),
                            "held and fresh accessors agree on the dependent torch");
                    helper.assertTrue(fresh.getBlockState(torch).is(Blocks.TORCH),
                            "dependent torch survives this ticketed Sable plot's flag-3 write");
                    helper.assertTrue(readHostBlock(body, torch).equals(fresh.getBlockState(torch)),
                            "direct host-level torch read matches the embedded plot");
                    helper.assertTrue(body.getMassTracker().getMass() < massBefore,
                            "Sable merged mass reflects the flag-3 edit");
                    helper.succeed();
                } finally {
                    if (container.getSubLevel(id) instanceof ServerSubLevel live && !live.isRemoved()) {
                        container.removeForceLoadTicket(live, KEEP_ALIVE, Unit.INSTANCE);
                        container.removeSubLevel(live, SubLevelRemovalReason.REMOVED);
                    }
                }
            });
        } catch (RuntimeException | AssertionError failure) {
            if (container.getSubLevel(id) instanceof ServerSubLevel live && !live.isRemoved()) {
                container.removeForceLoadTicket(live, KEEP_ALIVE, Unit.INSTANCE);
                container.removeSubLevel(live, SubLevelRemovalReason.REMOVED);
            }
            throw failure;
        }
    }

    @GameTest(template = "empty", timeoutTicks = 120, batch = "stellar_hull_damage_recursion_probe")
    public static void flagThreeHullRemovalWithDefaultRecursionUpdatesTorch(GameTestHelper helper) {
        var level = helper.getLevel();
        var container = SubLevelContainer.getContainer(level);
        PlotBodyBuilder.Result built = PlotBodyBuilder.build(level, new Vector3d(160.5, 64.5, 64.5), 2,
                (x, y, z) -> Blocks.STONE.defaultBlockState());
        helper.assertTrue(built != null && built.body() != null, "recursion Sable hull fixture is assembled");
        ServerSubLevel body = built.body();
        UUID id = body.getUniqueId();
        container.addForceLoadTicket(body, KEEP_ALIVE, Unit.INSTANCE);
        assertNoOtherBodySharesPlotCenter(helper, body);
        var accessor = body.getPlot().getEmbeddedLevelAccessor();
        try {
            BlockPos support = installDependentTorch(helper, accessor);
            BlockPos torch = support.above();
            BlockPos control = findUntouchedStone(helper, accessor, support, torch);
            double massBefore = body.getMassTracker().getMass();
            logAccessorFrame("flags-3-recursion-512-t0", body, accessor,
                    body.getPlot().getEmbeddedLevelAccessor(), support, torch, control);
            helper.assertTrue(accessor.setBlock(support, Blocks.AIR.defaultBlockState(), 3),
                    "flag-3 three-argument hull edit removes its support voxel");
            helper.runAfterDelay(2, () -> {
                try {
                    var fresh = body.getPlot().getEmbeddedLevelAccessor();
                    logAccessorFrame("flags-3-recursion-512-t+2", body, accessor, fresh, support, torch, control);
                    helper.assertTrue(fresh.getBlockState(support).isAir(), "recursion-512 support remains removed");
                    helper.assertTrue(fresh.getBlockState(control).is(Blocks.STONE), "control stone remains intact");
                    helper.assertTrue(readHostBlock(body, support).isAir()
                                    && readHostBlock(body, control).is(Blocks.STONE),
                            "direct host-level support/control reads match the embedded plot");
                    helper.assertTrue(fresh.getBlockState(torch).isAir(),
                            "flags 3 with vanilla recursion drops the unsupported torch");
                    helper.assertTrue(readHostBlock(body, torch).isAir(),
                            "direct host-level read confirms the dependent torch was removed");
                    helper.assertTrue(body.getMassTracker().getMass() < massBefore,
                            "Sable merged mass updates with vanilla neighbor recursion");
                    helper.succeed();
                } finally {
                    if (container.getSubLevel(id) instanceof ServerSubLevel live && !live.isRemoved()) {
                        container.removeForceLoadTicket(live, KEEP_ALIVE, Unit.INSTANCE);
                        container.removeSubLevel(live, SubLevelRemovalReason.REMOVED);
                    }
                }
            });
        } catch (RuntimeException | AssertionError failure) {
            if (container.getSubLevel(id) instanceof ServerSubLevel live && !live.isRemoved()) {
                container.removeForceLoadTicket(live, KEEP_ALIVE, Unit.INSTANCE);
                container.removeSubLevel(live, SubLevelRemovalReason.REMOVED);
            }
            throw failure;
        }
    }

    @GameTest(template = "empty", timeoutTicks = 120, batch = "stellar_hull_damage_recursion_one")
    public static void oneHopHullRemovalUpdatesDirectTorchWithoutDefaultRecursion(GameTestHelper helper) {
        var level = helper.getLevel();
        var container = SubLevelContainer.getContainer(level);
        BlockPos vanillaSupport = helper.absolutePos(new BlockPos(1, 1, 1));
        BlockPos vanillaTorch = vanillaSupport.above();
        level.setBlockAndUpdate(vanillaSupport, Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(vanillaTorch, Blocks.TORCH.defaultBlockState());
        level.setBlockAndUpdate(vanillaSupport, Blocks.AIR.defaultBlockState());
        helper.assertTrue(!level.getEntitiesOfClass(ItemEntity.class, new AABB(vanillaTorch).inflate(2.0)).isEmpty(),
                "ordinary host-world support removal still drops its unsupported torch");
        PlotBodyBuilder.Result built = PlotBodyBuilder.build(level, new Vector3d(288.5, 64.5, 64.5), 2,
                (x, y, z) -> Blocks.STONE.defaultBlockState());
        helper.assertTrue(built != null && built.body() != null, "one-hop Sable hull fixture is assembled");
        ServerSubLevel body = built.body();
        UUID id = body.getUniqueId();
        container.addForceLoadTicket(body, KEEP_ALIVE, Unit.INSTANCE);
        assertNoOtherBodySharesPlotCenter(helper, body);
        var accessor = body.getPlot().getEmbeddedLevelAccessor();
        try {
            BlockPos support = installDependentTorch(helper, accessor);
            BlockPos torch = support.above();
            BlockPos control = findUntouchedStone(helper, accessor, support, torch);
            BlockPos storageTorch = body.getPlot().getCenterBlock().offset(torch);
            AABB storageDropArea = new AABB(storageTorch).inflate(2.0);
            double massBefore = body.getMassTracker().getMass();
            helper.assertTrue(accessor.setBlock(support, Blocks.AIR.defaultBlockState(), 3, 1),
                    "one-recursion-step hull edit removes the support voxel");
            helper.runAfterDelay(2, () -> {
                try {
                    var fresh = body.getPlot().getEmbeddedLevelAccessor();
                    helper.assertTrue(fresh.getBlockState(support).isAir(), "one-hop support remains removed");
                    helper.assertTrue(fresh.getBlockState(torch).isAir(),
                            "one-hop neighbor-shape update removes the directly unsupported torch");
                    helper.assertTrue(fresh.getBlockState(control).is(Blocks.STONE),
                            "one-hop update leaves the untouched control voxel intact");
                    helper.assertTrue(readHostBlock(body, torch).isAir()
                                    && readHostBlock(body, control).is(Blocks.STONE),
                            "host reads match the one-hop embedded plot result");
                    helper.assertTrue(level.getEntitiesOfClass(ItemEntity.class, storageDropArea).isEmpty(),
                            "dependent hull removal does not spawn loot at Sable plot-storage coordinates");
                    helper.assertTrue(body.getMassTracker().getMass() < massBefore,
                            "Sable mass updates after the bounded one-hop hull edit");
                } finally {
                    if (container.getSubLevel(id) instanceof ServerSubLevel live && !live.isRemoved()) {
                        container.removeForceLoadTicket(live, KEEP_ALIVE, Unit.INSTANCE);
                        container.removeSubLevel(live, SubLevelRemovalReason.REMOVED);
                    }
                }
                helper.succeed();
            });
        } catch (RuntimeException | AssertionError failure) {
            if (container.getSubLevel(id) instanceof ServerSubLevel live && !live.isRemoved()) {
                container.removeForceLoadTicket(live, KEEP_ALIVE, Unit.INSTANCE);
                container.removeSubLevel(live, SubLevelRemovalReason.REMOVED);
            }
            throw failure;
        }
    }

    @GameTest(template = "empty", timeoutTicks = 120, batch = "stellar_hull_damage_accessor_probe")
    public static void flagTwoHullRemovalRecordsStableAccessorOutcomeAndMass(GameTestHelper helper) {
        var level = helper.getLevel();
        var container = SubLevelContainer.getContainer(level);
        PlotBodyBuilder.Result built = PlotBodyBuilder.build(level, new Vector3d(192.5, 64.5, 64.5), 2,
                (x, y, z) -> Blocks.STONE.defaultBlockState());
        helper.assertTrue(built != null && built.body() != null, "flag-2 Sable hull fixture is assembled");
        ServerSubLevel body = built.body();
        UUID id = body.getUniqueId();
        container.addForceLoadTicket(body, KEEP_ALIVE, Unit.INSTANCE);
        assertNoOtherBodySharesPlotCenter(helper, body);
        var accessor = body.getPlot().getEmbeddedLevelAccessor();
        try {
            BlockPos support = installDependentTorch(helper, accessor);
            BlockPos torch = support.above();
            BlockPos control = findUntouchedStone(helper, accessor, support, torch);
            double massBefore = body.getMassTracker().getMass();
            double selfMassBefore = body.getSelfMassTracker().getMass();
            helper.assertTrue(accessor.getBlockState(torch).is(Blocks.TORCH), "torch starts above the support");
            helper.assertTrue(accessor.getBlockState(control).is(Blocks.STONE), "control stone starts intact");
            logAccessorFrame("t0-before-edit", body, accessor, body.getPlot().getEmbeddedLevelAccessor(),
                    support, torch, control);
            helper.assertTrue(readHostBlock(body, support).is(Blocks.STONE)
                            && readHostBlock(body, torch).is(Blocks.TORCH)
                            && readHostBlock(body, control).is(Blocks.STONE),
                    "direct host-level reads match all three initial embedded-plot states");
            helper.assertTrue(accessor.setBlock(support, Blocks.AIR.defaultBlockState(), 2, 0),
                    "flag-2 hull edit removes the support voxel");
            helper.assertTrue(accessor.getBlockState(support).isAir(), "flag-2 support removal is visible immediately");
            helper.runAfterDelay(2, () -> {
                try {
                    var fresh = body.getPlot().getEmbeddedLevelAccessor();
                    logAccessorFrame("t+2-after-edit", body, accessor, fresh, support, torch, control);
                    var frozenSupport = accessor.getBlockState(support);
                    var freshSupport = fresh.getBlockState(support);
                    var frozenTorch = accessor.getBlockState(torch);
                    var freshTorch = fresh.getBlockState(torch);
                    var frozenControl = accessor.getBlockState(control);
                    var freshControl = fresh.getBlockState(control);
                    helper.assertTrue(frozenSupport.equals(freshSupport), "frozen and fresh accessors agree on support");
                    helper.assertTrue(frozenTorch.equals(freshTorch), "frozen and fresh accessors agree on torch");
                    helper.assertTrue(frozenControl.equals(freshControl), "frozen and fresh accessors agree on control");
                    helper.assertTrue(freshControl.is(Blocks.STONE), "never-written control stone remains intact");
                    helper.assertTrue(readHostBlock(body, support).isAir(),
                            "direct host-level read confirms the support removal");
                    helper.assertTrue(readHostBlock(body, control).is(Blocks.STONE),
                            "direct host-level read confirms the untouched control remains stone");
                    helper.assertTrue(freshSupport.isAir(), "flag-2 support removal stays visible through fresh accessor");
                    helper.assertTrue(freshTorch.is(Blocks.TORCH),
                            "dependent torch survives this ticketed Sable plot's flag-2 write");
                    helper.assertTrue(readHostBlock(body, torch).is(Blocks.TORCH),
                            "direct host-level read confirms the dependent torch remains present");
                    helper.assertTrue(body.getMassTracker().getMass() < massBefore,
                            "Sable mass updates even though neighbor notification is disabled");
                    helper.assertTrue(body.getSelfMassTracker().getMass() < selfMassBefore,
                            "Sable self mass updates after the flag-2 hull edit");
                    helper.succeed();
                } finally {
                    if (container.getSubLevel(id) instanceof ServerSubLevel live && !live.isRemoved()) {
                        container.removeForceLoadTicket(live, KEEP_ALIVE, Unit.INSTANCE);
                        container.removeSubLevel(live, SubLevelRemovalReason.REMOVED);
                    }
                }
            });
        } catch (RuntimeException | AssertionError failure) {
            if (container.getSubLevel(id) instanceof ServerSubLevel live && !live.isRemoved()) {
                container.removeForceLoadTicket(live, KEEP_ALIVE, Unit.INSTANCE);
                container.removeSubLevel(live, SubLevelRemovalReason.REMOVED);
            }
            throw failure;
        }
    }

    @GameTest(template = "empty", timeoutTicks = 120, batch = "stellar_hull_damage_flags18_probe")
    public static void flagEighteenHullRemovalRecordsStableAccessorOutcomeAndMass(GameTestHelper helper) {
        var level = helper.getLevel();
        var container = SubLevelContainer.getContainer(level);
        PlotBodyBuilder.Result built = PlotBodyBuilder.build(level, new Vector3d(224.5, 64.5, 64.5), 2,
                (x, y, z) -> Blocks.STONE.defaultBlockState());
        helper.assertTrue(built != null && built.body() != null, "flag-18 Sable hull fixture is assembled");
        ServerSubLevel body = built.body();
        UUID id = body.getUniqueId();
        container.addForceLoadTicket(body, KEEP_ALIVE, Unit.INSTANCE);
        assertNoOtherBodySharesPlotCenter(helper, body);
        var accessor = body.getPlot().getEmbeddedLevelAccessor();
        try {
            BlockPos support = installDependentTorch(helper, accessor);
            BlockPos torch = support.above();
            BlockPos control = findUntouchedStone(helper, accessor, support, torch);
            double massBefore = body.getMassTracker().getMass();
            logAccessorFrame("flags-18-t0", body, accessor, body.getPlot().getEmbeddedLevelAccessor(),
                    support, torch, control);
            helper.assertTrue(accessor.setBlock(support, Blocks.AIR.defaultBlockState(), 18, 0),
                    "flag-18 hull edit removes its support voxel");
            helper.runAfterDelay(2, () -> {
                try {
                    var fresh = body.getPlot().getEmbeddedLevelAccessor();
                    logAccessorFrame("flags-18-t+2", body, accessor, fresh, support, torch, control);
                    helper.assertTrue(fresh.getBlockState(support).isAir(), "flag-18 support removal stays visible");
                    helper.assertTrue(fresh.getBlockState(control).is(Blocks.STONE),
                            "untouched control stone remains loaded and unchanged");
                    helper.assertTrue(readHostBlock(body, control).is(Blocks.STONE),
                            "direct host-level control read matches the embedded plot");
                    helper.assertTrue(accessor.getBlockState(torch).equals(fresh.getBlockState(torch)),
                            "held and fresh accessors agree on the dependent torch");
                    helper.assertTrue(fresh.getBlockState(torch).is(Blocks.TORCH),
                            "dependent torch survives this ticketed Sable plot's flag-18 write");
                    helper.assertTrue(readHostBlock(body, torch).equals(fresh.getBlockState(torch)),
                            "direct host-level torch read matches the embedded plot");
                    helper.assertTrue(body.getMassTracker().getMass() < massBefore,
                            "Sable mass still updates with flag-18 neighbor-shape suppression");
                    helper.succeed();
                } finally {
                    if (container.getSubLevel(id) instanceof ServerSubLevel live && !live.isRemoved()) {
                        container.removeForceLoadTicket(live, KEEP_ALIVE, Unit.INSTANCE);
                        container.removeSubLevel(live, SubLevelRemovalReason.REMOVED);
                    }
                }
            });
        } catch (RuntimeException | AssertionError failure) {
            if (container.getSubLevel(id) instanceof ServerSubLevel live && !live.isRemoved()) {
                container.removeForceLoadTicket(live, KEEP_ALIVE, Unit.INSTANCE);
                container.removeSubLevel(live, SubLevelRemovalReason.REMOVED);
            }
            throw failure;
        }
    }
}
