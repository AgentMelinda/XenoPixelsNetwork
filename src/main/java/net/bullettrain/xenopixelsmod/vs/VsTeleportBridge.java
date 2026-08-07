package net.bullettrain.xenopixelsmod.vs;

import dev.ryanhcode.sable.companion.SubLevelAccess;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/** Backwards-compatible facade retained for callers while the implementation uses Sable. */
public final class VsTeleportBridge {
    private VsTeleportBridge() {}

    public static SubLevelAccess getShipAt(Level level, BlockPos pos) {
        return VsShipHelper.getShipAt(level, pos);
    }

    public static boolean teleportPlayerShip(ServerPlayer player, double x, double y, double z) {
        return VsShipHelper.teleportPlayerShip(player, x, y, z);
    }

    public static void teleportShip(ServerLevel level, ServerSubLevel subLevel, double x, double y, double z) {
        VsShipHelper.teleportShip(level, subLevel, x, y, z);
    }

    public static boolean isOnShip(Level level, BlockPos pos) {
        return VsShipHelper.isOnShip(level, pos);
    }
}
