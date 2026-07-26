package net.bullettrain.xenopixelsmod.mixin.compat.ballistix;

import ballistix.api.silo.ILauncherControlPanel;
import ballistix.common.tile.silo.TileLauncherPlatformT1;
import net.bullettrain.xenopixelsmod.compat.ballistix.BallistixVs2Compat;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Launcher platform (T1 base; T2/T3 extend T1) wake VS ships on fire
 * and rewrite target positions from shipyard → world when needed.
 * Mod methods → remap = false.
 */
@Mixin(value = TileLauncherPlatformT1.class, remap = false)
public abstract class TileLauncherPlatformMixin {
    @Inject(method = "launch", at = @At("HEAD"), remap = false)
    private void xenopixels$onLaunch(ILauncherControlPanel panel, boolean sam, int freq,
                                     CallbackInfoReturnable<Integer> cir) {
        BlockEntity be = (BlockEntity) (Object) this;
        if (be.getLevel() != null) {
            BallistixVs2Compat.onLauncherFired(be.getLevel(), be.getBlockPos());
        }
    }

    @Inject(method = "launchMissile", at = @At("HEAD"), remap = false)
    private void xenopixels$onLaunchMissile(BlockPos target, int range,
                                            CallbackInfoReturnable<Boolean> cir) {
        BlockEntity be = (BlockEntity) (Object) this;
        if (be.getLevel() != null) {
            BallistixVs2Compat.onLauncherFired(be.getLevel(), be.getBlockPos());
        }
    }

    @ModifyVariable(method = "launchMissile", at = @At("HEAD"), argsOnly = true, ordinal = 0, remap = false)
    private BlockPos xenopixels$worldTarget(BlockPos target) {
        BlockEntity be = (BlockEntity) (Object) this;
        if (be.getLevel() == null || target == null) return target;
        return BallistixVs2Compat.toWorldBlockPos(be.getLevel(), target);
    }
}
