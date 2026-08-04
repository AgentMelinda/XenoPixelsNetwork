package net.bullettrain.xenopixelsmod.mixin.compat.ballistix;

import ballistix.api.missile.virtual.VirtualMissile;
import ballistix.api.silo.ILauncherControlPanel;
import ballistix.common.tile.TileVerticalLaunchSilo;
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
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Ballistix VLS ({@code ballistix:vls}) ↔ VS2:
 * <ul>
 *   <li>World-space range check (shipyard silo vs world target)</li>
 *   <li>World-space missile spawn position</li>
 *   <li>Wake hull + chunkload corridor on fire</li>
 * </ul>
 */
@Mixin(value = TileVerticalLaunchSilo.class, remap = false)
public abstract class TileVerticalLaunchSiloMixin {

    // ---- range: tickServer uses TileLauncherControlPanelT1.calculateDistance ----
    // (static calculateDistance inject on panel mixin is the primary fix; this is backup)
    @Redirect(
            method = "tickServer",
            at = @At(
                    value = "INVOKE",
                    target = "Lballistix/common/tile/silo/TileLauncherControlPanelT1;calculateDistance(Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/BlockPos;)D",
                    remap = false
            ),
            remap = false,
            require = 0
    )
    private double xenopixels$vlsWorldDistance(BlockPos from, BlockPos to) {
        BlockEntity be = (BlockEntity) (Object) this;
        return BallistixVs2Compat.worldDistance(be.getLevel(), from, to);
    }

    // ---- wake + chunkload ----
    @Inject(method = "launch()V", at = @At("HEAD"), remap = false, require = 0)
    private void xenopixels$vlsLaunch(CallbackInfo ci) {
        fireHooks();
    }

    @Inject(method = "launch", at = @At("HEAD"), remap = false, require = 0)
    private void xenopixels$vlsLaunchIface(ILauncherControlPanel panel, boolean sam, int freq,
                                           CallbackInfoReturnable<Integer> cir) {
        fireHooks();
    }

    @Inject(method = "launchMissile", at = @At("HEAD"), remap = false, require = 0)
    private void xenopixels$vlsLaunchMissile(BlockPos target, int range,
                                             CallbackInfoReturnable<Boolean> cir) {
        BlockEntity be = (BlockEntity) (Object) this;
        if (be.getLevel() == null) return;
        BallistixVs2Compat.onLauncherFired(be.getLevel(), be.getBlockPos());
        if (be.getLevel() instanceof ServerLevel sl) {
            BlockPos worldTarget = BallistixVs2Compat.toWorldBlockPos(sl, target);
            BlockPos worldPad = BallistixVs2Compat.toWorldBlockPos(sl, be.getBlockPos());
            MissileChunkLoadManager.forceNear(sl, worldPad, 1, 20 * 60, MissileChunkLoadManager.Role.PAD);
            if (worldTarget != null) {
                MissileChunkLoadManager.forceNear(sl, worldTarget, 1, 20 * 60, MissileChunkLoadManager.Role.TARGET);
            }
        }
    }

    /** Ensure target used for flight is world-space (if it was shipyard). */
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
    private Vec3 xenopixels$vlsSpawnWorld(Vec3 shipyardSpawn) {
        BlockEntity be = (BlockEntity) (Object) this;
        return BallistixVs2Compat.toWorldLaunchSpawn(be.getLevel(), shipyardSpawn);
    }

    /** Target BlockPos arg to VirtualMissile ctor (index 6). */
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
    private BlockPos xenopixels$vlsTargetWorld(BlockPos target) {
        BlockEntity be = (BlockEntity) (Object) this;
        return BallistixVs2Compat.toWorldBlockPos(be.getLevel(), target);
    }

    private void fireHooks() {
        BlockEntity be = (BlockEntity) (Object) this;
        if (be.getLevel() != null) {
            BallistixVs2Compat.onLauncherFired(be.getLevel(), be.getBlockPos());
        }
    }
}
