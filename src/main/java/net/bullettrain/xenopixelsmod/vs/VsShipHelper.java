package net.bullettrain.xenopixelsmod.vs;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.valkyrienskies.core.api.ships.LoadedServerShip;
import org.valkyrienskies.core.api.ships.ServerShip;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.core.api.ships.ShipTeleportData;
import org.valkyrienskies.core.api.world.ServerShipWorld;
import org.valkyrienskies.core.internal.VsiCore;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

/**
 * Thin helper around the Valkyrien Skies 2 API (Minecraft 1.20.1 / VS 2.4.x).
 *
 * <p>All VS2 interaction for XenoPixels should go through this class so
 * API changes only need to be fixed in one place.
 *
 * <p>Ship blocks live in <b>shipyard</b> coordinates. Prefer
 * {@link #getLoadedShipAt(ServerLevel, BlockPos)} which tries every VS lookup
 * overload and upgrades a bare {@link ServerShip} to {@link LoadedServerShip}.
 */
public final class VsShipHelper {

    private VsShipHelper() {
    }

    /**
     * Finds the {@link ServerShip} managing the given block position.
     *
     * @return the ship, or {@code null} if none / client-side
     */
    public static ServerShip getShipAt(Level level, BlockPos pos) {
        if (level == null || pos == null || level.isClientSide) {
            return null;
        }
        try {
            if (level instanceof ServerLevel sl) {
                ServerShip ss = VSGameUtilsKt.getShipManagingPos(sl, pos);
                if (ss != null) return ss;
            }
            Ship ship = VSGameUtilsKt.getShipManagingPos(level, pos);
            return ship instanceof ServerShip serverShip ? serverShip : null;
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * O(1) loaded-ship resolve by id — use this on hot tick paths after first discovery.
     * Avoids the multi-API / all-ships scan in {@link #getLoadedShipAt}.
     */
    public static LoadedServerShip getLoadedShipById(ServerLevel level, long shipId) {
        if (level == null || shipId < 0) return null;
        return resolveLoaded(level, shipId);
    }

    /** Finds the nearest loaded ship COM without relying on shipyard coordinates. */
    public static LoadedServerShip getClosestLoadedShip(ServerLevel level, Vec3 worldPos, double maxDistance) {
        if (level == null || worldPos == null) return null;
        LoadedServerShip best = null;
        double bestDistance = maxDistance * maxDistance;
        try {
            ServerShipWorld world = VSGameUtilsKt.getShipObjectWorld(level);
            if (world == null) return null;
            for (LoadedServerShip ship : world.getLoadedShips()) {
                var p = ship.getTransform().getPositionInWorld();
                double dx = p.x() - worldPos.x, dy = p.y() - worldPos.y, dz = p.z() - worldPos.z;
                double distance = dx * dx + dy * dy + dz * dz;
                if (distance <= bestDistance) { bestDistance = distance; best = ship; }
            }
        } catch (Throwable ignored) { }
        return best;
    }

    /**
     * Fast pos lookup for thrusters / BE ticks: first 1–2 VS APIs only.
     * Prefer {@link #getLoadedShipById} once the ship id is known.
     */
    public static LoadedServerShip getLoadedShipAtFast(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null) return null;
        try {
            LoadedServerShip loaded = VSGameUtilsKt.getLoadedShipManagingPos(level, pos);
            if (loaded != null) return loaded;
        } catch (Throwable ignored) {
        }
        try {
            LoadedServerShip loaded = VSGameUtilsKt.getShipObjectManagingPos(level, pos);
            if (loaded != null) return loaded;
        } catch (Throwable ignored) {
        }
        try {
            ServerShip any = getShipAt(level, pos);
            if (any instanceof LoadedServerShip ls) return ls;
            if (any != null) return resolveLoaded(level, any.getId());
        } catch (Throwable ignored) {
        }
        return null;
    }

    /**
     * Robust shipyard/world lookup for loaded ships (attachments, thrusters, ballistic).
     * Tries multiple VS2 APIs because one path often returns null on ships.
     * <p><b>Hot paths:</b> do not call this every tick — use {@link #getLoadedShipById}
     * or {@link #getLoadedShipAtFast} instead. Full scan is for launch / one-shots.
     */
    public static LoadedServerShip getLoadedShipAt(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null) return null;

        LoadedServerShip fast = getLoadedShipAtFast(level, pos);
        if (fast != null) return fast;

        // Chunk coords explicitly
        try {
            ChunkPos cp = new ChunkPos(pos);
            LoadedServerShip loaded = VSGameUtilsKt.getLoadedShipManagingPos(level, cp);
            if (loaded != null) return loaded;
            loaded = VSGameUtilsKt.getShipObjectManagingPos(level, cp);
            if (loaded != null) return loaded;
            loaded = VSGameUtilsKt.getLoadedShipManagingPos(level, cp.x, cp.z);
            if (loaded != null) return loaded;
        } catch (Throwable ignored) {
        }

        // Center of block as doubles
        try {
            double x = pos.getX() + 0.5;
            double y = pos.getY() + 0.5;
            double z = pos.getZ() + 0.5;
            LoadedServerShip loaded = VSGameUtilsKt.getLoadedShipManagingPos(level, x, y, z);
            if (loaded != null) return loaded;
            loaded = VSGameUtilsKt.getShipObjectManagingPos(level, x, y, z);
            if (loaded != null) return loaded;
        } catch (Throwable ignored) {
        }

        // Shipyard flag + all loaded ships chunk-claim scan (expensive — last resort)
        try {
            if (VSGameUtilsKt.isBlockInShipyard(level, pos)) {
                ServerShipWorld world = VSGameUtilsKt.getShipObjectWorld(level);
                if (world != null) {
                    for (LoadedServerShip s : world.getLoadedShips()) {
                        if (shipClaimsBlock(s, pos)) return s;
                    }
                }
            }
        } catch (Throwable ignored) {
        }

        return null;
    }

    /** Whether this block is on any VS ship (loaded or not). */
    public static boolean isOnShip(Level level, BlockPos pos) {
        if (level == null || pos == null) return false;
        try {
            if (VSGameUtilsKt.isBlockInShipyard(level, pos)) return true;
            if (level instanceof ServerLevel sl) {
                return getLoadedShipAt(sl, pos) != null || getShipAt(sl, pos) != null;
            }
            return getShipAt(level, pos) != null
                    || VSGameUtilsKt.getShipManagingPos(level, pos) != null;
        } catch (Throwable t) {
            return false;
        }
    }

    private static LoadedServerShip resolveLoaded(ServerLevel level, long shipId) {
        try {
            ServerShipWorld world = VSGameUtilsKt.getShipObjectWorld(level);
            if (world == null) return null;
            Ship byId = world.getLoadedShips().getById(shipId);
            if (byId instanceof LoadedServerShip ls) return ls;
            // Also try all ships (unloaded map) then loaded again
            Ship any = world.getAllShips().getById(shipId);
            if (any instanceof LoadedServerShip ls) return ls;
            if (any != null) {
                Ship loaded = world.getLoadedShips().getById(any.getId());
                if (loaded instanceof LoadedServerShip ls) return ls;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static boolean shipClaimsBlock(Ship ship, BlockPos pos) {
        try {
            var claim = ship.getChunkClaim();
            if (claim == null) return false;
            int cx = pos.getX() >> 4;
            int cz = pos.getZ() >> 4;
            // ChunkClaim typically has contains(x, z) or similar
            try {
                return claim.contains(cx, cz);
            } catch (Throwable t) {
                // fallback: active chunks set
                var active = ship.getActiveChunksSet();
                if (active != null) {
                    return active.contains(cx, cz);
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    /**
     * Teleports the ship the player is standing on to world coordinates {@code (x, y, z)}.
     * Keeps the ship's current rotation / velocity (VS defaults).
     *
     * @return {@code true} if a ship was found and teleport was requested
     */
    public static boolean teleportPlayerShip(ServerPlayer player, double x, double y, double z) {
        if (player == null || player.level().isClientSide) {
            return false;
        }

        ServerLevel level = player.serverLevel();
        ServerShip ship = findPlayerShip(player);
        if (ship == null) {
            return false;
        }

        try {
            teleportShip(level, ship, x, y, z);
            return true;
        } catch (Throwable t) {
            player.sendSystemMessage(Component.literal(
                    "[XenoPixels] Failed to teleport VS2 ship: " + usefulMessage(t)));
            return false;
        }
    }

    /**
     * Teleports a specific ship using VS2's {@link VsiCore#newShipTeleportData} /
     * {@link org.valkyrienskies.core.internal.VsiCoreCommands#teleportShip}.
     */
    public static void teleportShip(ServerLevel level, ServerShip ship, double x, double y, double z) {
        if (level == null) {
            throw new IllegalArgumentException("level cannot be null");
        }
        if (ship == null) {
            throw new IllegalArgumentException("ship cannot be null");
        }

        VsiCore vsCore = VSGameUtilsKt.getVsCore();
        String dimensionId = VSGameUtilsKt.getDimensionId(level);

        // Keep current orientation; clear linear/angular velocity so the ship lands still.
        Quaterniond rotation = new Quaterniond(ship.getTransform().getShipToWorldRotation());
        ShipTeleportData teleportData = vsCore.newShipTeleportData(
                new Vector3d(x, y, z),
                rotation,
                new Vector3d(),
                new Vector3d(),
                dimensionId,
                null,
                null);

        ServerShipWorld shipWorld = VSGameUtilsKt.getShipObjectWorld(level);
        vsCore.teleportShip(shipWorld, ship, teleportData);
    }

    /**
     * Searches several positions because the player's feet can sit slightly above a ship deck.
     */
    private static ServerShip findPlayerShip(ServerPlayer player) {
        ServerLevel level = player.serverLevel();

        ServerShip ship = getShipAt(level, player.blockPosition());
        if (ship != null) {
            return ship;
        }

        ship = getShipAt(level, BlockPos.containing(player.getX(), player.getY() - 0.25D, player.getZ()));
        if (ship != null) {
            return ship;
        }

        ship = getShipAt(level, BlockPos.containing(player.getX(), player.getY() - 1.0D, player.getZ()));
        if (ship != null) {
            return ship;
        }

        return getShipAt(level, BlockPos.containing(player.getEyePosition()));
    }

    private static String usefulMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null || message.isBlank()
                ? current.getClass().getSimpleName()
                : message;
    }
}
