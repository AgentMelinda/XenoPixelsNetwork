package net.bullettrain.xenopixelsmod.space.asteroid;

import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.ticket.SubLevelLoadingTicketType;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.plot.heat.SubLevelHeatMapManager;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.config.XenoSpaceConfig;
import net.bullettrain.xenopixelsmod.space.physics.PhysicalEarth;
import net.bullettrain.xenopixelsmod.space.physics.PlotBodyBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Unit;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Manual runtime acceptance for real Sable asteroid splitting, child tagging, and tiny-fragment expiry. */
@GameTestHolder(XenoPixelsMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AsteroidFragmentLifecycleGameTests {
    private static final SubLevelLoadingTicketType<Unit> KEEP_ALIVE = SubLevelLoadingTicketType.COMMAND_FORCED;

    private AsteroidFragmentLifecycleGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 180, required = false, manualOnly = true,
            batch = "asteroid_fragment_lifecycle")
    public static void sableSplitTagsAndExpiresTinyAsteroidFragment(GameTestHelper helper) {
        XenoPixelsMod.LOGGER.info("ASTEROID_FRAGMENT_TEST start helperDimension={}", helper.getLevel().dimension().location());
        ServerLevel level = helper.getLevel().getServer().getLevel(
                ResourceKey.create(Registries.DIMENSION, PhysicalEarth.HOST));
        helper.assertTrue(level != null, "the production Voidsea level is loaded for the lifecycle fixture");
        if (level == null) return;
        XenoPixelsMod.LOGGER.info("ASTEROID_FRAGMENT_TEST hostLevel={} tick={}", level.dimension().location(), level.getGameTime());
        helper.assertTrue(XenoSpaceConfig.asteroidFracture(),
                "asteroidFracture is enabled in the isolated test-server config");

        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        var center = new Vec3(48.5, 64.5, 48.5);
        PlotBodyBuilder.Result built = PlotBodyBuilder.build(level, new Vector3d(center.x, center.y, center.z), 2,
                (x, y, z) -> y == 0 && z == 0 && Math.abs(x) <= 1
                        ? Blocks.STONE.defaultBlockState() : null);
        helper.assertTrue(built != null && built.body() != null, "the three-block asteroid fixture was built");
        ServerSubLevel root = built.body();
        UUID rootUuid = root.getUniqueId();
        AsteroidField.ObjectId rootId = new AsteroidField.ObjectId(903, -2, 71, 0);
        var rootTag = new CompoundTag();
        rootTag.putLongArray("id", new long[]{rootId.cellX(), rootId.cellY(), rootId.cellZ(), rootId.slot()});
        rootTag.putDouble("mass", root.getMassTracker().getMass());
        var userData = new CompoundTag();
        userData.put(AsteroidPhysicalizer.TAG, rootTag);
        root.setUserDataTag(userData);
        container.addForceLoadTicket(root, KEEP_ALIVE, Unit.INSTANCE);

        try {
            SubLevelHeatMapManager heatMap = root.getHeatMapManager();
            helper.assertTrue(heatMap instanceof SableHeatMapFractureAccess,
                    "the exact Sable heat-map manager has Xeno's fracture access mixin");
            SableHeatMapFractureAccess fractureAccess = (SableHeatMapFractureAccess) heatMap;
            fractureAccess.xenopixels$beginFractureHeatMapRebuild();
            for (int x = -1; x <= 1; x++) fractureAccess.xenopixels$addFractureHeatMapBlock(hostBlock(root, x, 0, 0));
            fractureAccess.xenopixels$finishFractureHeatMapRebuild();
            awaitReadyThenDisconnect(helper, level, container, root, rootId, rootUuid, 0);
        } catch (RuntimeException | Error failure) {
            cleanup(container, root, rootUuid);
            throw failure;
        }
    }

    private static void awaitReadyThenDisconnect(GameTestHelper helper, ServerLevel level,
                                                 ServerSubLevelContainer container, ServerSubLevel root,
                                                 AsteroidField.ObjectId rootId, UUID rootUuid, int waited) {
        if (waited == 0 || waited % 20 == 0) {
            XenoPixelsMod.LOGGER.info("ASTEROID_FRAGMENT_TEST heatmap waited={} present={} ready={}", waited,
                    container.getSubLevel(rootUuid) == root,
                    root.getHeatMapManager() instanceof SableHeatMapFractureAccess access
                            && access.xenopixels$fractureHeatMapReady(3));
        }
        if (root.isRemoved() || container.getSubLevel(rootUuid) != root) {
            cleanup(container, root, rootUuid);
            helper.fail("the asteroid root disappeared before Sable's connectivity split");
            return;
        }
        if (root.getHeatMapManager() instanceof SableHeatMapFractureAccess access
                && access.xenopixels$fractureHeatMapReady(3)) {
            BlockPos bridge = hostBlock(root, 0, 0, 0);
            boolean removed = level.setBlock(bridge, Blocks.AIR.defaultBlockState(), 3);
            helper.assertTrue(removed, "the center bridge voxel was removed through the live host level");
            awaitTaggedChild(helper, level, container, root, rootId, rootUuid, 0);
            return;
        }
        if (waited >= 60) {
            cleanup(container, root, rootUuid);
            helper.fail("Sable's fracture heat map did not reach its ready state within 60 ticks");
            return;
        }
        helper.runAfterDelay(1, () -> awaitReadyThenDisconnect(helper, level, container, root, rootId, rootUuid, waited + 1));
    }

    private static void awaitTaggedChild(GameTestHelper helper, ServerLevel level,
                                         ServerSubLevelContainer container, ServerSubLevel root,
                                         AsteroidField.ObjectId rootId, UUID rootUuid, int waited) {
        ServerSubLevel child = null;
        for (SubLevel candidate : container.getAllSubLevels()) {
            if (!(candidate instanceof ServerSubLevel body) || body.isRemoved()) continue;
            CompoundTag data = body.getUserDataTag();
            if (data != null && data.contains(AsteroidPhysicalizer.FRAGMENT_TAG)
                    && rootUuid.equals(data.getCompound(AsteroidPhysicalizer.FRAGMENT_TAG).getUUID("parent"))) {
                child = body;
                break;
            }
        }
        if (child == null) {
            if (waited == 0 || waited % 20 == 0) {
                int tagged = 0;
                for (SubLevel candidate : container.getAllSubLevels()) {
                    if (candidate instanceof ServerSubLevel body && body.getUserDataTag() != null
                            && body.getUserDataTag().contains(AsteroidPhysicalizer.FRAGMENT_TAG)) tagged++;
                }
                XenoPixelsMod.LOGGER.info("ASTEROID_FRAGMENT_TEST child waited={} tagged={} levelTick={}", waited, tagged,
                        level.getGameTime());
            }
            if (waited >= 60) {
                cleanup(container, root, rootUuid);
                helper.fail("Sable did not create a detached child after the middle voxel was removed");
                return;
            }
            helper.runAfterDelay(1, () -> awaitTaggedChild(helper, level, container, root, rootId, rootUuid, waited + 1));
            return;
        }

        CompoundTag childData = child.getUserDataTag();
        helper.assertTrue(childData != null && childData.contains(AsteroidPhysicalizer.FRAGMENT_TAG),
                "the Sable split callback tagged the child as an asteroid fragment");
        CompoundTag fragment = childData.getCompound(AsteroidPhysicalizer.FRAGMENT_TAG);
        long[] encoded = fragment.getLongArray("rootId");
        helper.assertTrue(encoded.length == 4 && encoded[0] == rootId.cellX() && encoded[1] == rootId.cellY()
                        && encoded[2] == rootId.cellZ() && encoded[3] == rootId.slot(),
                "the fragment retains the root's deterministic asteroid identity");
        helper.assertTrue(rootUuid.equals(fragment.getUUID("parent")), "the child stores its direct Sable parent UUID");

        long now = level.getServer().overworld().getGameTime();
        long lifetime = XenoSpaceConfig.asteroidFragmentLifetimeTicks();
        fragment.putLong("createdTick", now - lifetime - 1);
        fragment.putLong("cleanupCheckAfter", 0);
        XenoPixelsMod.LOGGER.info("ASTEROID_FRAGMENT_TEST aging child={} now={} lifetime={} created={} levelTick={}",
                child.getUniqueId(), now, lifetime, fragment.getLong("createdTick"), level.getGameTime());
        awaitExpiredTinyChild(helper, level, container, root, rootId, rootUuid, child.getUniqueId(), 0);
    }

    private static void awaitExpiredTinyChild(GameTestHelper helper, ServerLevel level,
                                              ServerSubLevelContainer container, ServerSubLevel root,
                                              AsteroidField.ObjectId rootId, UUID rootUuid, UUID childUuid, int waited) {
        if (container.getSubLevel(childUuid) == null) {
            XenoPixelsMod.LOGGER.info("ASTEROID_FRAGMENT_TEST child removed after waited={} hostTick={}", waited, level.getGameTime());
            cleanup(container, root, rootUuid);
            helper.succeed();
            XenoPixelsMod.LOGGER.info("ASTEROID_FRAGMENT_TEST PASS sable split, root identity, and tiny-fragment expiry");
            return;
        }
        if (waited >= 100) {
            XenoPixelsMod.LOGGER.warn("ASTEROID_FRAGMENT_TEST expiry timeout waited={} hostTick={} childPresent={}",
                    waited, level.getGameTime(), container.getSubLevel(childUuid) != null);
            cleanup(container, root, rootUuid);
            helper.fail("the aged one-block fragment was not removed by the production bounded expiry scan");
            return;
        }
        helper.runAfterDelay(1, () -> awaitExpiredTinyChild(helper, level, container, root, rootId, rootUuid,
                childUuid, waited + 1));
    }

    private static BlockPos hostBlock(ServerSubLevel body, int x, int y, int z) {
        var plot = body.getPlot();
        BlockPos center = plot.getCenterBlock();
        ChunkPos localCenterChunk = plot.toLocal(new ChunkPos(center));
        int localX = localCenterChunk.x * 16 + (center.getX() & 15) + x;
        int localY = center.getY() + y;
        int localZ = localCenterChunk.z * 16 + (center.getZ() & 15) + z;
        ChunkPos globalChunk = plot.toGlobal(new ChunkPos(Math.floorDiv(localX, 16), Math.floorDiv(localZ, 16)));
        return new BlockPos(globalChunk.getMinBlockX() + Math.floorMod(localX, 16), localY,
                globalChunk.getMinBlockZ() + Math.floorMod(localZ, 16));
    }

    private static void cleanup(ServerSubLevelContainer container, ServerSubLevel root, UUID rootUuid) {
        Set<UUID> family = new HashSet<>();
        family.add(rootUuid);
        boolean changed;
        do {
            changed = false;
            for (SubLevel sub : new ArrayList<>(container.getAllSubLevels())) {
                if (!(sub instanceof ServerSubLevel body) || body.isRemoved()) continue;
                UUID parent = body.getSplitFromSubLevel();
                if (!family.contains(parent)) continue;
                if (family.add(body.getUniqueId())) changed = true;
            }
        } while (changed);
        for (UUID id : family) {
            SubLevel body = container.getSubLevel(id);
            if (body instanceof ServerSubLevel live && !live.isRemoved()) {
                container.removeForceLoadTicket(live, KEEP_ALIVE, Unit.INSTANCE);
                container.removeSubLevel(live, SubLevelRemovalReason.REMOVED);
            }
        }
    }
}
