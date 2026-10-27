package net.bullettrain.xenopixelsmod.mixin.common;

import net.bullettrain.xenopixelsmod.space.event.StellarHullMutationContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Target: {@link Level#destroyBlock(BlockPos, boolean, Entity, int)}.
 * Sable's EmbeddedPlotLevelAccessor forwards translated writes to the host Level; vanilla dependent-block
 * destruction therefore runs at plot-storage coordinates. During an explicitly scoped stellar hull edit,
 * remove only recursion-zero, null-source dependents directly, avoiding loot and effects at storage coords.
 * Ordinary block destruction and destruction initiated by an entity retain vanilla behavior.
 */
@Mixin(Level.class)
public abstract class StellarHullDependentBlockDestroyMixin {
    @Shadow public abstract BlockState getBlockState(BlockPos pos);
    @Shadow public abstract net.minecraft.world.level.material.FluidState getFluidState(BlockPos pos);
    @Shadow public abstract boolean setBlock(BlockPos pos, BlockState state, int flags, int recursionLeft);

    @Inject(method = "destroyBlock(Lnet/minecraft/core/BlockPos;ZLnet/minecraft/world/entity/Entity;I)Z",
            at = @At("HEAD"), cancellable = true)
    private void xenopixels$suppressStorageSideEffectsForHullDependents(BlockPos pos, boolean drop, Entity breaker,
            int recursionLeft, CallbackInfoReturnable<Boolean> cir) {
        if (!StellarHullMutationContext.active() || breaker != null || recursionLeft > 0) return;
        BlockState current = getBlockState(pos);
        if (current.isAir()) {
            cir.setReturnValue(false);
            return;
        }
        net.minecraft.world.level.material.FluidState fluid = getFluidState(pos);
        cir.setReturnValue(setBlock(pos, fluid.createLegacyBlock(), 3, recursionLeft));
    }
}
