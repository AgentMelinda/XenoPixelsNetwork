package net.bullettrain.xenopixelsmod.compat.ballistix;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.perf.Vs2ShipSleepManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.valkyrienskies.core.api.ships.LoadedShip;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

/**
 * Ballistix ↔ VS2 glue called from gated mixins.
 * Safe no-ops when VS2 APIs throw (version skew).
 */
public final class BallistixVs2Compat {
    private BallistixVs2Compat() {}

    /** Wake any ship under / near a live missile entity. */
    public static void onMissileEntityTick(Entity missile) {
        if (missile == null || missile.level().isClientSide()) return;
        try {
            wakeAt(missile.level(), missile.getX(), missile.getY(), missile.getZ(), 80);
            // If entity is somehow still in shipyard coords, re-seat into world
            if (VSGameUtilsKt.isBlockInShipyard(missile.level(), missile.blockPosition())) {
                Vec3 world = shipyardToWorld(missile.level(), missile.position());
                if (world != null) {
                    missile.setPos(world.x, world.y, world.z);
                }
            }
        } catch (Throwable t) {
            // never break Ballistix tick
        }
    }

    /** Virtual missile simulation position (world or shipyard). */
    public static void onVirtualMissileTick(ServerLevel level, Vec3 position) {
        if (level == null || position == null) return;
        try {
            wakeAt(level, position.x, position.y, position.z, 60);
        } catch (Throwable ignored) {
        }
    }

    /** Called when a launcher platform fires (tile on ship must wake). */
    public static void onLauncherFired(Level level, BlockPos platformPos) {
        if (level == null || platformPos == null || level.isClientSide()) return;
        try {
            wakeAt(level, platformPos.getX() + 0.5, platformPos.getY() + 0.5, platformPos.getZ() + 0.5, 200);
            Ship ship = VSGameUtilsKt.getShipManagingPos(level, platformPos);
            if (ship != null) {
                Vs2ShipSleepManager.forceHot(ship.getId(), 200);
                XenoPixelsMod.LOGGER.debug("Ballistix launch on ship {} — force Hot", ship.getId());
            }
        } catch (Throwable ignored) {
        }
    }

    /** Transform shipyard / ship-local block pos to world BlockPos for targeting. */
    public static BlockPos toWorldBlockPos(Level level, BlockPos pos) {
        if (level == null || pos == null) return pos;
        try {
            if (!VSGameUtilsKt.isBlockInShipyard(level, pos)
                    && VSGameUtilsKt.getShipManagingPos(level, pos) == null) {
                return pos;
            }
            Vec3 w = shipyardToWorld(level, Vec3.atCenterOf(pos));
            if (w == null) return pos;
            return BlockPos.containing(w.x, w.y, w.z);
        } catch (Throwable t) {
            return pos;
        }
    }

    public static Vec3 shipyardToWorld(Level level, Vec3 pos) {
        if (level == null || pos == null) return null;
        try {
            LoadedShip ship = VSGameUtilsKt.getShipObjectManagingPos(level, pos.x, pos.y, pos.z);
            if (ship == null) {
                // chunk-based lookup
                ship = VSGameUtilsKt.getShipObjectManagingPos(level, BlockPos.containing(pos));
            }
            if (ship == null || ship.getShipToWorld() == null) return null;
            Vector3d out = ship.getShipToWorld().transformPosition(new Vector3d(pos.x, pos.y, pos.z));
            return new Vec3(out.x, out.y, out.z);
        } catch (Throwable t) {
            return null;
        }
    }

    private static void wakeAt(Level level, double x, double y, double z, int ticks) {
        try {
            LoadedShip ship = VSGameUtilsKt.getShipObjectManagingPos(level, x, y, z);
            if (ship == null) {
                ship = VSGameUtilsKt.getShipObjectManagingPos(level, BlockPos.containing(x, y, z));
            }
            if (ship != null) {
                Vs2ShipSleepManager.forceHot(ship.getId(), ticks);
            }
            // Also wake nearest intersecting ship AABB via world query
            var worldShip = VSGameUtilsKt.getShipManagingPos(level, BlockPos.containing(x, y, z));
            if (worldShip != null) {
                Vs2ShipSleepManager.forceHot(worldShip.getId(), ticks);
            }
        } catch (Throwable ignored) {
        }
    }
}
