package net.bullettrain.xenopixelsmod.compat.voltaic;

import net.bullettrain.xenopixelsmod.compat.ballistix.BallistixVs2Compat;
import net.bullettrain.xenopixelsmod.config.XenoPerfConfig;
import net.bullettrain.xenopixelsmod.perf.Vs2ShipSleepManager;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

/**
 * Voltaic tile hooks (from mixins). Keeps ship-mounted Ballistix / Voltaic tiles awake
 * when interacted with, without requiring Voltaic at runtime when absent.
 */
public final class VoltaicCompatHooks {
    private VoltaicCompatHooks() {}

    public static void onTileLoad(BlockEntity be) {
        if (be == null || be.getLevel() == null || be.getLevel().isClientSide()) return;
        if (!XenoPerfConfig.perfEnabled) return;
        try {
            Level level = be.getLevel();
            BlockPos pos = be.getBlockPos();
            Ship ship = VSGameUtilsKt.getShipManagingPos(level, pos);
            if (ship != null) {
                // Freshly loaded ship tile — do not force hot forever, just note
                Vs2ShipSleepManager.forceHot(ship.getId(), 40);
            }
        } catch (Throwable ignored) {
        }
    }

    public static void onTileUse(BlockEntity be) {
        if (be == null || be.getLevel() == null || be.getLevel().isClientSide()) return;
        try {
            BallistixVs2Compat.onLauncherFired(be.getLevel(), be.getBlockPos());
        } catch (Throwable ignored) {
        }
    }
}
