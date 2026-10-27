package net.bullettrain.xenopixelsmod.mixin.compat.sable;

import net.bullettrain.xenopixelsmod.space.smallplanet.SmallPlanetPhysicalizer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Commits an edited-small-planet reservation only after the host chunk changed a block state. */
@Mixin(LevelChunk.class)
public abstract class SmallPlanetBlockMutationCommitMixin {
    @Inject(method = "setBlockState(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Z)"
                    + "Lnet/minecraft/world/level/block/state/BlockState;",
            at = @At("RETURN"), require = 1)
    private void xenopixels$commitSmallPlanetMutation(BlockPos position, BlockState requested, boolean moved,
                                                       CallbackInfoReturnable<BlockState> cir) {
        BlockState previous = cir.getReturnValue();
        if (previous == null || previous.equals(requested)) return;
        Level level = ((LevelChunk) (Object) this).getLevel();
        if (!(level instanceof ServerLevel serverLevel)) return;
        if (level.restoringBlockSnapshots) return;
        if (!SmallPlanetPhysicalizer.hasPendingEditReservations(serverLevel)) return;
        if (level.captureBlockSnapshots) {
            SmallPlanetPhysicalizer.captureBlockMutation(level, position, previous, requested);
            return;
        }
        SmallPlanetPhysicalizer.blockMutationCommitted(level, position);
    }
}
