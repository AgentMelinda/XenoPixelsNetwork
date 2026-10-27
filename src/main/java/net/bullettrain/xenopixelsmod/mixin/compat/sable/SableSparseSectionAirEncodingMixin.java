package net.bullettrain.xenopixelsmod.mixin.compat.sable;

import dev.ryanhcode.sable.physics.chunk.VoxelNeighborhoodState;
import dev.ryanhcode.sable.util.LevelAccelerator;
import net.bullettrain.xenopixelsmod.config.XenoSpaceConfig;
import net.bullettrain.xenopixelsmod.space.physics.SableSparseAirCollisionEncoding;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Optional Sable 2.0.3 optimization: vanilla air encodes to the zero-filled entry already present in Rapier's array,
 * so generated-body finalization can skip redundant neighborhood lookups for those cells.
 * Non-air and modded air-like states continue through Sable unchanged.
 */
@Mixin(targets = "dev.ryanhcode.sable.physics.impl.rapier.RapierPhysicsPipeline", remap = false)
public abstract class SableSparseSectionAirEncodingMixin {
    private static final String METHOD = "handleChunkSectionAddition(Lnet/minecraft/world/level/chunk/LevelChunkSection;IIIZ)V";

    @Redirect(method = METHOD,
            at = @At(value = "INVOKE", target = "Ldev/ryanhcode/sable/physics/chunk/VoxelNeighborhoodState;getState(Ldev/ryanhcode/sable/util/LevelAccelerator;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/chunk/LevelChunk;)Ldev/ryanhcode/sable/physics/chunk/VoxelNeighborhoodState;"),
            require = 1)
    private VoxelNeighborhoodState xenopixels$skipAirNeighborhood(LevelAccelerator accelerator, BlockPos pos,
                                                                   LevelChunk chunk) {
        if (fastPathEnabled()) {
            BlockState state = chunk == null ? accelerator.getBlockState(pos) : accelerator.getBlockState(chunk, pos);
            if (SableSparseAirCollisionEncoding.isVanillaAir(state)) return VoxelNeighborhoodState.EMPTY;
        }
        return VoxelNeighborhoodState.getState(accelerator, pos, chunk);
    }

    private static boolean fastPathEnabled() {
        return XenoSpaceConfig.planetBuildSparseAirCollisionEncoding() && SableSparseAirCollisionEncoding.active();
    }
}
