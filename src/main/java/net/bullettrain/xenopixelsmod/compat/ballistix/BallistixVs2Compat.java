package net.bullettrain.xenopixelsmod.compat.ballistix;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.perf.Vs2ShipSleepManager;
import net.bullettrain.xenopixelsmod.vs.VsShipHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.valkyrienskies.core.api.ships.LoadedServerShip;
import org.valkyrienskies.core.api.ships.LoadedShip;
import org.valkyrienskies.core.api.ships.ServerShip;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

/**
 * Ballistix ↔ VS2 glue called from gated mixins.
 * <p>
 * Core problem: Ballistix range checks and VirtualMissile spawns use raw
 * {@link BlockPos} / shipyard coordinates when silos sit on a VS ship, while
 * radar-gun targets are world coords — so range fails and missiles never leave
 * the shipyard. All geometry here is converted to <b>world space</b> first.
 */
public final class BallistixVs2Compat {
    private BallistixVs2Compat() {}

    /** Wake any ship under / near a live missile entity. */
    public static void onMissileEntityTick(Entity missile) {
        if (missile == null || missile.level().isClientSide()) return;
        try {
            wakeAt(missile.level(), missile.getX(), missile.getY(), missile.getZ(), 80);
            // If entity is somehow still in shipyard coords, re-seat into world
            if (isShipyardPos(missile.level(), missile.position())) {
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

    /**
     * Called when a launcher platform / VLS fires.
     * Wakes the hull, unfreezes static ships, chunkloads <b>world</b> corridor.
     */
    public static void onLauncherFired(Level level, BlockPos platformPos) {
        if (level == null || platformPos == null || level.isClientSide()) return;
        try {
            BlockPos worldPos = toWorldBlockPos(level, platformPos);
            wakeAt(level, worldPos.getX() + 0.5, worldPos.getY() + 0.5, worldPos.getZ() + 0.5, 200);

            Ship ship = VSGameUtilsKt.getShipManagingPos(level, platformPos);
            if (ship != null) {
                try {
                    if (ship instanceof ServerShip ss && ss.isStatic()) {
                        ss.setStatic(false);
                    }
                    if (ship instanceof LoadedServerShip loaded && loaded.isStatic()) {
                        loaded.setStatic(false);
                    }
                } catch (Throwable ignored) {
                }
                Vs2ShipSleepManager.forceHot(ship.getId(), 20 * 30);
                XenoPixelsMod.LOGGER.info("Ballistix launch on VS ship {} — world pos {} (shipyard {})",
                        ship.getId(), worldPos, platformPos);
            }

            if (level instanceof ServerLevel sl) {
                // World-space tickets (shipyard tickets do nothing useful for flight)
                MissileChunkLoadManager.forceNear(sl, worldPos, 1, 20 * 60, MissileChunkLoadManager.Role.PAD);
            }
        } catch (Throwable t) {
            XenoPixelsMod.LOGGER.debug("onLauncherFired failed: {}", t.toString());
        }
    }

    /**
     * World-space Euclidean distance between two positions that may be shipyard or world.
     * Used to replace {@code TileLauncherControlPanelT1.calculateDistance} so ship silos
     * pass range checks against radar-gun world targets.
     */
    public static double worldDistance(Level level, BlockPos a, BlockPos b) {
        if (a == null || b == null) return Double.MAX_VALUE;
        if (level == null) {
            return plainDistance(a, b);
        }
        try {
            Vec3 wa = toWorldVecRobust(level, a);
            Vec3 wb = toWorldVecRobust(level, b);
            double d = wa.distanceTo(wb);
            // Sanity: if still absurdly large but both converted, keep; if one failed, try again
            if (d > 1.0e6) {
                XenoPixelsMod.LOGGER.debug(
                        "Ballistix worldDistance huge {} (a={}→{} b={}→{})",
                        d, a, wa, b, wb);
            }
            return d;
        } catch (Throwable t) {
            return plainDistance(a, b);
        }
    }

    /** Prefer VsShipHelper loaded-ship transform (more reliable than shipyard flag alone). */
    private static Vec3 toWorldVecRobust(Level level, BlockPos pos) {
        try {
            if (level instanceof ServerLevel sl) {
                var loaded = net.bullettrain.xenopixelsmod.vs.VsShipHelper.getLoadedShipAt(sl, pos);
                if (loaded != null && loaded.getShipToWorld() != null) {
                    Vector3d out = loaded.getShipToWorld().transformPosition(
                            new Vector3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5));
                    return new Vec3(out.x, out.y, out.z);
                }
            }
        } catch (Throwable ignored) {
        }
        return toWorldVec(level, Vec3.atCenterOf(pos));
    }

    public static double plainDistance(BlockPos a, BlockPos b) {
        double dx = a.getX() - b.getX();
        double dy = a.getY() - b.getY();
        double dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /** Transform shipyard / ship-local block pos to world BlockPos for targeting. */
    public static BlockPos toWorldBlockPos(Level level, BlockPos pos) {
        if (level == null || pos == null) return pos;
        try {
            if (!isOnShipOrShipyard(level, pos)) {
                return pos;
            }
            Vec3 w = shipyardToWorld(level, Vec3.atCenterOf(pos));
            if (w == null) return pos;
            return BlockPos.containing(w.x, w.y, w.z);
        } catch (Throwable t) {
            return pos;
        }
    }

    /** Ensure a Vec3 (spawn / missile pos) is in world space. */
    public static Vec3 toWorldVec(Level level, Vec3 pos) {
        if (level == null || pos == null) return pos;
        try {
            if (!isShipyardPos(level, pos) && !isOnShipOrShipyard(level, BlockPos.containing(pos))) {
                return pos;
            }
            Vec3 world = shipyardToWorld(level, pos);
            return world != null ? world : pos;
        } catch (Throwable t) {
            return pos;
        }
    }

    /**
     * World-space launch spawn that clears the VS hull.
     * Ballistix spawns at platform center; on a ship that is <b>inside</b> the
     * voxel body so the first collision tick detonates the warhead on the launcher.
     * We lift the missile above the ship world AABB (+ clearance).
     *
     * @return world spawn, or original if not on a ship
     */
    public static Vec3 toWorldLaunchSpawn(Level level, Vec3 spawn) {
        if (level == null || spawn == null) return spawn;
        Vec3 world = toWorldVec(level, spawn);
        if (world == null) world = spawn;

        try {
            LoadedServerShip ship = null;
            if (level instanceof ServerLevel sl) {
                ship = VsShipHelper.getLoadedShipAt(sl, BlockPos.containing(spawn));
                if (ship == null) {
                    ship = VsShipHelper.getLoadedShipAt(sl, BlockPos.containing(world));
                }
            }
            if (ship == null) {
                // Not ship-mounted — still nudge up so silo doesn't eat the rocket
                return world.add(0, 2.0, 0);
            }

            double clear = 8.0;
            try {
                // Reflect world AABB so we don't hard-depend on JOML primitives types
                Object aabb = ship.getClass().getMethod("getWorldAABB").invoke(ship);
                if (aabb != null) {
                    double top = ((Number) aabb.getClass().getMethod("maxY").invoke(aabb)).doubleValue();
                    double y = Math.max(world.y + clear, top + clear);
                    world = new Vec3(world.x, y, world.z);
                    XenoPixelsMod.LOGGER.info(
                            "Ballistix launch spawn cleared hull ship={} spawn=({}, {}, {})",
                            ship.getId(),
                            String.format("%.1f", world.x),
                            String.format("%.1f", world.y),
                            String.format("%.1f", world.z));
                    return world;
                }
            } catch (Throwable ignored) {
            }
            // Fallback: COM Y + clearance
            try {
                var com = ship.getTransform().getPositionInWorld();
                double y = Math.max(world.y + clear, com.y() + clear + 4.0);
                world = new Vec3(world.x, y, world.z);
            } catch (Throwable t) {
                world = world.add(0, clear, 0);
            }
            return world;
        } catch (Throwable t) {
            return world.add(0, 8.0, 0);
        }
    }

    /** True if this world/shipyard block is part of the given ship. */
    public static boolean isBlockOnShip(Level level, BlockPos pos, long shipId) {
        if (level == null || pos == null || shipId < 0) return false;
        try {
            Ship s = VSGameUtilsKt.getShipManagingPos(level, pos);
            if (s != null && s.getId() == shipId) return true;
            if (level instanceof ServerLevel sl) {
                LoadedServerShip loaded = VsShipHelper.getLoadedShipAt(sl, pos);
                if (loaded != null && loaded.getId() == shipId) return true;
            }
            // World-space sample may hit ship AABB without shipyard chunk
            Ship worldShip = VSGameUtilsKt.getShipManagingPos(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
            return worldShip != null && worldShip.getId() == shipId;
        } catch (Throwable t) {
            return false;
        }
    }

    public static long findShipIdAt(Level level, Vec3 pos) {
        if (level == null || pos == null) return -1L;
        try {
            if (level instanceof ServerLevel sl) {
                LoadedServerShip loaded = VsShipHelper.getLoadedShipAt(sl, BlockPos.containing(pos));
                if (loaded != null) return loaded.getId();
            }
            Ship s = VSGameUtilsKt.getShipManagingPos(level, pos.x, pos.y, pos.z);
            return s != null ? s.getId() : -1L;
        } catch (Throwable t) {
            return -1L;
        }
    }

    public static boolean isOnShipOrShipyard(Level level, BlockPos pos) {
        if (level == null || pos == null) return false;
        try {
            if (VSGameUtilsKt.isBlockInShipyard(level, pos)) return true;
            if (level instanceof ServerLevel sl && VsShipHelper.getLoadedShipAt(sl, pos) != null) return true;
            return VSGameUtilsKt.getShipManagingPos(level, pos) != null
                    || VsShipHelper.getShipAt(level, pos) != null;
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean isShipyardPos(Level level, Vec3 pos) {
        if (level == null || pos == null) return false;
        try {
            return VSGameUtilsKt.isBlockInShipyard(level, BlockPos.containing(pos));
        } catch (Throwable t) {
            return false;
        }
    }

    public static Vec3 shipyardToWorld(Level level, Vec3 pos) {
        if (level == null || pos == null) return null;
        try {
            LoadedShip ship = VSGameUtilsKt.getShipObjectManagingPos(level, pos.x, pos.y, pos.z);
            if (ship == null) {
                ship = VSGameUtilsKt.getShipObjectManagingPos(level, BlockPos.containing(pos));
            }
            if (ship == null) {
                // Fallback: ship managing block pos via chunk claim
                Ship s = VSGameUtilsKt.getShipManagingPos(level, BlockPos.containing(pos));
                if (s instanceof LoadedShip ls) ship = ls;
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
            var worldShip = VSGameUtilsKt.getShipManagingPos(level, BlockPos.containing(x, y, z));
            if (worldShip != null) {
                Vs2ShipSleepManager.forceHot(worldShip.getId(), ticks);
            }
        } catch (Throwable ignored) {
        }
    }
}
