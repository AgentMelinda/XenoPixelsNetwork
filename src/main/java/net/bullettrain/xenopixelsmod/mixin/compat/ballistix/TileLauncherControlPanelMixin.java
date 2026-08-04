package net.bullettrain.xenopixelsmod.mixin.compat.ballistix;

import ballistix.common.tile.silo.TileLauncherControlPanelT1;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.compat.ballistix.BallistixVs2Compat;
import net.bullettrain.xenopixelsmod.vs.VsShipHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

/**
 * Fix Ballistix control-panel range checks on VS2 ships.
 * <p>
 * Radar gun stores world XYZ; silo {@code BlockPos} is shipyard → vanilla
 * {@code calculateDistance} is huge and launch aborts silently.
 * <p>
 * Two layers: redirect in {@code tickServer}, plus HEAD inject on static
 * {@code calculateDistance} (catches VLS + any other callers).
 */
@Mixin(value = TileLauncherControlPanelT1.class, remap = false)
public abstract class TileLauncherControlPanelMixin {

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
    private double xenopixels$worldSpaceDistance(BlockPos from, BlockPos to) {
        BlockEntity be = (BlockEntity) (Object) this;
        Level level = be.getLevel();
        double d = BallistixVs2Compat.worldDistance(level, from, to);
        XenoPixelsMod.LOGGER.debug("Ballistix range (panel): {} → {} = {}", from, to, d);
        return d;
    }

    /**
     * Global override for every {@code calculateDistance} call when either pos
     * is shipyard / on a ship. Does not need a BE instance.
     */
    @Inject(method = "calculateDistance", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void xenopixels$staticWorldDistance(BlockPos a, BlockPos b,
                                                       CallbackInfoReturnable<Double> cir) {
        if (a == null || b == null) return;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        double best = Double.MAX_VALUE;
        boolean usedShip = false;
        for (ServerLevel level : server.getAllLevels()) {
            boolean aShip = isShipRelated(level, a);
            boolean bShip = isShipRelated(level, b);
            if (!aShip && !bShip) continue;
            usedShip = true;
            double d = BallistixVs2Compat.worldDistance(level, a, b);
            if (d < best) best = d;
        }
        if (usedShip && best < Double.MAX_VALUE / 2) {
            XenoPixelsMod.LOGGER.info("Ballistix calculateDistance VS2-fixed: {} → {} = {}", a, b, best);
            cir.setReturnValue(best);
        }
    }

    private static boolean isShipRelated(ServerLevel level, BlockPos pos) {
        try {
            if (VSGameUtilsKt.isBlockInShipyard(level, pos)) return true;
            if (VsShipHelper.getLoadedShipAt(level, pos) != null) return true;
            if (VsShipHelper.getShipAt(level, pos) != null) return true;
        } catch (Throwable ignored) {
        }
        return false;
    }
}
