package net.bullettrain.xenopixelsmod.mixin.compat.ballistix;

import ballistix.api.silo.ILauncherControlPanel;
import ballistix.common.tile.silo.TileLauncherPlatformT1;
import net.bullettrain.xenopixelsmod.compat.ballistix.BallistixVs2Compat;
import net.bullettrain.xenopixelsmod.compat.ballistix.MissileChunkLoadManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Launcher platform T1 (T2/T3 extend): VS2 wake + world-space missile spawn/target.
 * Range check is fixed on the control panel ({@link TileLauncherControlPanelMixin}).
 */
@Mixin(value = TileLauncherPlatformT1.class, remap = false)
public abstract class TileLauncherPlatformMixin {

    @Inject(method = "launch", at = @At("HEAD"), remap = false, require = 0)
    private void xenopixels$onLaunch(ILauncherControlPanel panel, boolean sam, int freq,
                                     CallbackInfoReturnable<Integer> cir) {
        BlockEntity be = (BlockEntity) (Object) this;
        if (be.getLevel() != null) {
            BallistixVs2Compat.onLauncherFired(be.getLevel(), be.getBlockPos());
        }
    }

    @Inject(method = "launchMissile", at = @At("HEAD"), remap = false, require = 0)
    private void xenopixels$onLaunchMissile(BlockPos target, int range,
                                            CallbackInfoReturnable<Boolean> cir) {
        BlockEntity be = (BlockEntity) (Object) this;
        if (be.getLevel() == null) return;
        BallistixVs2Compat.onLauncherFired(be.getLevel(), be.getBlockPos());
        if (be.getLevel() instanceof ServerLevel sl) {
            BlockPos worldPad = BallistixVs2Compat.toWorldBlockPos(sl, be.getBlockPos());
            BlockPos worldTarget = BallistixVs2Compat.toWorldBlockPos(sl, target);
            MissileChunkLoadManager.forceNear(sl, worldPad, 1, 20 * 60, MissileChunkLoadManager.Role.PAD);
            if (worldTarget != null) {
                MissileChunkLoadManager.forceNear(sl, worldTarget, 1, 20 * 60, MissileChunkLoadManager.Role.TARGET);
            }
        }
    }

    @ModifyArg(
            method = "launchMissile",
            at = @At(
                    value = "INVOKE",
                    target = "Lballistix/api/missile/virtual/VirtualMissile;<init>(Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;FLballistix/api/missile/virtual/VirtualMissile$FlightPath;FFLnet/minecraft/core/BlockPos;ILballistix/api/blast/IBlast;IZ)V",
                    remap = false
            ),
            index = 0,
            remap = false,
            require = 0
    )
    private Vec3 xenopixels$platformSpawnWorld(Vec3 shipyardSpawn) {
        BlockEntity be = (BlockEntity) (Object) this;
        // Clear hull roof — plain world transform still detonates on the pad
        return BallistixVs2Compat.toWorldLaunchSpawn(be.getLevel(), shipyardSpawn);
    }

    @ModifyArg(
            method = "launchMissile",
            at = @At(
                    value = "INVOKE",
                    target = "Lballistix/api/missile/virtual/VirtualMissile;<init>(Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;FLballistix/api/missile/virtual/VirtualMissile$FlightPath;FFLnet/minecraft/core/BlockPos;ILballistix/api/blast/IBlast;IZ)V",
                    remap = false
            ),
            index = 6,
            remap = false,
            require = 0
    )
    private BlockPos xenopixels$platformTargetWorld(BlockPos target) {
        BlockEntity be = (BlockEntity) (Object) this;
        return BallistixVs2Compat.toWorldBlockPos(be.getLevel(), target);
    }
}
