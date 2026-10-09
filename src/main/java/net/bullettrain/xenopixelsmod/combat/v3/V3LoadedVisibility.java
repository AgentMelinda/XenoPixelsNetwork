package net.bullettrain.xenopixelsmod.combat.v3;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Collider clipping without Level.getBlockState's chunk-loading access, including shape neighbors. */
public final class V3LoadedVisibility implements BlockGetter {
    private final ServerLevel level;
    private V3LoadedVisibility(ServerLevel level) { this.level = level; }
    static boolean visible(ServerPlayer player, LivingEntity target) {
        Vec3 from = player.getEyePosition();
        Vec3 to = target.getEyePosition();
        if (player.level() != target.level() || !V3TargetingRules.finite(from) || !V3TargetingRules.finite(to)) return false;
        try {
            return new V3LoadedVisibility(player.serverLevel()).clip(new ClipContext(from, to,
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player)).getType() == HitResult.Type.MISS;
        } catch (RuntimeException | LinkageError unavailable) { return false; }
    }

    /** Loaded-only collider test for delayed projectiles and sustained beams. */
    public static boolean clear(ServerPlayer player, Vec3 from, Vec3 to) {
        if (player == null || !V3TargetingRules.finite(from) || !V3TargetingRules.finite(to)) return false;
        try {
            return new V3LoadedVisibility(player.serverLevel()).clip(new ClipContext(from, to,
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player)).getType() == HitResult.Type.MISS;
        } catch (RuntimeException | LinkageError unavailable) {
            return false;
        }
    }
    private LevelChunk loaded(BlockPos pos) {
        LevelChunk chunk = level.getChunkSource().getChunkNow(SectionPos.blockToSectionCoord(pos.getX()),
                SectionPos.blockToSectionCoord(pos.getZ()));
        if (chunk == null) throw new MissingChunk();
        return chunk;
    }
    @Override public BlockState getBlockState(BlockPos pos) {
        return isOutsideBuildHeight(pos) ? Blocks.VOID_AIR.defaultBlockState() : loaded(pos).getBlockState(pos);
    }
    @Override public FluidState getFluidState(BlockPos pos) { return getBlockState(pos).getFluidState(); }
    @Override public BlockEntity getBlockEntity(BlockPos pos) {
        return isOutsideBuildHeight(pos) ? null : loaded(pos).getBlockEntity(pos, LevelChunk.EntityCreationType.CHECK);
    }
    @Override public int getHeight() { return level.getHeight(); }
    @Override public int getMinBuildHeight() { return level.getMinBuildHeight(); }
    private static final class MissingChunk extends RuntimeException {
        private MissingChunk() { super(null, null, false, false); }
    }
}
