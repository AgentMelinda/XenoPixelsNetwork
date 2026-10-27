package net.bullettrain.xenopixelsmod.gametest;

import com.dragonminez.common.init.MainGameRules;
import com.dragonminez.common.init.MainEntities;
import com.dragonminez.common.init.entities.ki.KiBlastEntity;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.compat.sable.SableBlastCenter;
import net.bullettrain.xenopixelsmod.compat.sable.SableKiClip;
import net.bullettrain.xenopixelsmod.mixin.common.KiProjectileGriefAccess;
import net.bullettrain.xenopixelsmod.space.physics.PlotBodyBuilder;
import net.bullettrain.xenopixelsmod.space.smallplanet.SmallPlanetPhysicalizer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Vector3d;

import java.util.UUID;

/** Falsifiers for DMZ Ki permission admission against real Sable plot coordinates. */
@GameTestHolder(XenoPixelsMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SmallPlanetKiAdmissionGameTests {
    private SmallPlanetKiAdmissionGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void unmanagedKiPermissionRemainsAllowed(GameTestHelper helper) {
        BlockPos position = helper.absolutePos(new BlockPos(1, 2, 1));
        helper.assertTrue(MainGameRules.canKiGrief(helper.getLevel(), position, null),
                "the Xeno hook leaves unrelated DMZ-permitted blocks unchanged");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 120)
    public static void hostLevelBlockWriteReachesSablePlot(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        PlotBodyBuilder.Result built = PlotBodyBuilder.build(level, new Vector3d(64.5, 64.5, 64.5), 1,
                (x, y, z) -> x == 0 && y == 0 && z == 0 ? Blocks.STONE.defaultBlockState()
                        : Blocks.AIR.defaultBlockState());
        helper.assertTrue(built != null && built.body() != null, "the Sable plot fixture is built");
        ServerSubLevel body = built.body();
        UUID id = body.getUniqueId();
        BlockPos position = body.getPlot().getCenterBlock();
        try {
            helper.assertTrue(container.getPlot(new ChunkPos(position)) != null,
                    "the host mutation position resolves to the Sable plot");
            helper.assertTrue(body.getPlot().getEmbeddedLevelAccessor().getBlockState(BlockPos.ZERO).is(Blocks.STONE),
                    "the plot begins with the fixture stone voxel");
            level.setBlock(position, Blocks.GOLD_BLOCK.defaultBlockState(), 3, 0);
            helper.assertTrue(body.getPlot().getEmbeddedLevelAccessor().getBlockState(BlockPos.ZERO).is(Blocks.GOLD_BLOCK),
                    "a host ServerLevel block write at the plot coordinate updates its embedded block");
            helper.succeed();
        } finally {
            if (container.getSubLevel(id) instanceof ServerSubLevel live && !live.isRemoved())
                container.removeSubLevel(live, SubLevelRemovalReason.REMOVED);
        }
    }

    @GameTest(template = "empty", timeoutTicks = 120)
    public static void dmzKiBlastAtVisiblePlanetPoseBreaksTheHitPlotVoxel(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        PlotBodyBuilder.Result built = PlotBodyBuilder.build(level, new Vector3d(64.5, 64.5, 64.5), 1,
                (x, y, z) -> x == 0 && y == 0 && z == 0 ? Blocks.STONE.defaultBlockState()
                        : Blocks.AIR.defaultBlockState());
        helper.assertTrue(built != null && built.body() != null, "the Sable planet fixture is built");
        ServerSubLevel body = built.body();
        UUID id = body.getUniqueId();
        KiBlastEntity blast = new KiBlastEntity(MainEntities.KI_BLAST.get(), level);
        try {
            // The projectile travels through the body's visible world pose. SableKiClip must
            // inverse-transform that ray and return the voxel's actual plot-storage position.
            blast.setPos(68.5, 64.5, 64.5);
            blast.setDeltaMovement(-8.0, 0.0, 0.0);
            BlockHitResult hit = (BlockHitResult) SableKiClip.preferCloser(blast,
                    BlockHitResult.miss(new Vec3(60.5, 64.5, 64.5), net.minecraft.core.Direction.UP,
                            BlockPos.containing(60.5, 64.5, 64.5)));
            helper.assertTrue(hit.getType() == HitResult.Type.BLOCK,
                    "DMZ's Sable-aware sweep sees the planet from its visible world pose");
            BlockPos impact = SableBlastCenter.resolve(blast, hit);
            helper.assertTrue(impact != null && container.getPlot(new ChunkPos(impact)) == body.getPlot(),
                    "DMZ destruction is centered at the Sable plot voxel, not the Voidsea pose");
            helper.assertTrue(body.getPlot().getEmbeddedLevelAccessor().getBlockState(BlockPos.ZERO).is(Blocks.STONE),
                    "the fixture voxel exists before DMZ destruction");
            boolean broken = ((KiProjectileGriefAccess) blast).xenopixels$destroyKiBlock(impact, false);
            helper.assertTrue(broken, "DMZ's own permission and destruction helper breaks the impacted plot voxel");
            helper.assertTrue(body.getPlot().getEmbeddedLevelAccessor().getBlockState(BlockPos.ZERO).isAir(),
                    "the physical Sable planet plot reflects the DMZ block break");
            helper.succeed();
        } finally {
            blast.discard();
            if (container.getSubLevel(id) instanceof ServerSubLevel live && !live.isRemoved())
                container.removeSubLevel(live, SubLevelRemovalReason.REMOVED);
        }
    }

    @GameTest(template = "empty", timeoutTicks = 120)
    public static void dmzKiPermissionDeniesTaggedUntrackedPlanet(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        PlotBodyBuilder.Result built = PlotBodyBuilder.build(level, new Vector3d(64.5, 64.5, 64.5), 1,
                (x, y, z) -> x == 0 && y == 0 && z == 0 ? Blocks.STONE.defaultBlockState()
                        : Blocks.AIR.defaultBlockState());
        helper.assertTrue(built != null && built.body() != null, "the tagged Sable fixture is built");
        ServerSubLevel body = built.body();
        UUID id = body.getUniqueId();
        CompoundTag userData = new CompoundTag();
        userData.put(SmallPlanetPhysicalizer.TAG, new CompoundTag());
        body.setUserDataTag(userData);
        BlockPos position = body.getPlot().getCenterBlock();
        try {
            helper.assertTrue(container.getPlot(new ChunkPos(position)) != null,
                    "the permission position resolves to the tagged Sable plot");
            helper.assertFalse(MainGameRules.canKiGrief(level, position, null),
                    "the shared DMZ Ki-grief check denies a tagged body without an active edit reservation");
            helper.succeed();
        } finally {
            if (container.getSubLevel(id) instanceof ServerSubLevel live && !live.isRemoved())
                container.removeSubLevel(live, SubLevelRemovalReason.REMOVED);
        }
    }
}
