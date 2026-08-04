package net.bullettrain.xenopixelsmod.vs;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.valkyrienskies.core.api.ships.ServerShip;

/**
 * Compatibility facade for ship teleport helpers.
 *
 * <p>Prefer {@link VsShipHelper} for new code. This class exists so older call sites
 * / docs that mention a "teleport bridge" keep working without duplicating VS API logic.
 */
public final class VsTeleportBridge {

    private VsTeleportBridge() {
    }

    public static ServerShip getShipAt(Level level, BlockPos pos) {
        return VsShipHelper.getShipAt(level, pos);
    }

    public static boolean teleportPlayerShip(ServerPlayer player, double x, double y, double z) {
        return VsShipHelper.teleportPlayerShip(player, x, y, z);
    }

    public static void teleportShip(ServerLevel level, ServerShip ship, double x, double y, double z) {
        VsShipHelper.teleportShip(level, ship, x, y, z);
    }

    public static boolean isOnShip(Level level, BlockPos pos) {
        return VsShipHelper.isOnShip(level, pos);
    }
}
