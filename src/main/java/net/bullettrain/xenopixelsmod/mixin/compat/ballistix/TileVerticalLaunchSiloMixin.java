package net.bullettrain.xenopixelsmod.mixin.compat.ballistix;

import ballistix.api.silo.ILauncherControlPanel;
import ballistix.common.tile.TileVerticalLaunchSilo;
import net.bullettrain.xenopixelsmod.compat.ballistix.BallistixVs2Compat;
import net.bullettrain.xenopixelsmod.compat.ballistix.MissileChunkLoadManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Ballistix VLS ({@code TileVerticalLaunchSilo}) ↔ VS2 + chunkloader wake.
 * This is the real vertical launch silo (block {@code ballistix:vls}), separate from horizontal platforms.
 */
@Mixin(value = TileVerticalLaunchSilo.class, remap = false)
public abstract class TileVerticalLaunchSiloMixin {
    @Inject(method = "launch()V", at = @At("HEAD"), remap = false)
    private void xenopixels$vlsLaunch(CallbackInfo ci) {
        BlockEntity be = (BlockEntity) (Object) this;
        if (be.getLevel() != null) {
            BallistixVs2Compat.onLauncherFired(be.getLevel(), be.getBlockPos());
            if (be.getLevel() instanceof ServerLevel sl) {
                MissileChunkLoadManager.forceNear(sl, be.getBlockPos(), 2, 20 * 60);
            }
        }
    }

    @Inject(method = "launch", at = @At("HEAD"), remap = false)
    private void xenopixels$vlsLaunchIface(ILauncherControlPanel panel, boolean sam, int freq,
                                           CallbackInfoReturnable<Integer> cir) {
        BlockEntity be = (BlockEntity) (Object) this;
        if (be.getLevel() != null) {
            BallistixVs2Compat.onLauncherFired(be.getLevel(), be.getBlockPos());
            if (be.getLevel() instanceof ServerLevel sl) {
                MissileChunkLoadManager.forceNear(sl, be.getBlockPos(), 2, 20 * 60);
            }
        }
    }

    @Inject(method = "launchMissile", at = @At("HEAD"), remap = false)
    private void xenopixels$vlsLaunchMissile(BlockPos target, int range,
                                             CallbackInfoReturnable<Boolean> cir) {
        BlockEntity be = (BlockEntity) (Object) this;
        if (be.getLevel() != null) {
            BallistixVs2Compat.onLauncherFired(be.getLevel(), be.getBlockPos());
            if (be.getLevel() instanceof ServerLevel sl) {
                MissileChunkLoadManager.forceNear(sl, be.getBlockPos(), 2, 20 * 90);
                if (target != null) {
                    MissileChunkLoadManager.forceNear(sl, target, 2, 20 * 90);
                }
            }
        }
    }

    @ModifyVariable(method = "launchMissile", at = @At("HEAD"), argsOnly = true, ordinal = 0, remap = false)
    private BlockPos xenopixels$vlsWorldTarget(BlockPos target) {
        BlockEntity be = (BlockEntity) (Object) this;
        if (be.getLevel() == null || target == null) return target;
        return BallistixVs2Compat.toWorldBlockPos(be.getLevel(), target);
    }
}
