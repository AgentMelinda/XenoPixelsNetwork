package net.bullettrain.xenopixelsmod.vs;

import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.Vector3dc;

/** Sable-backed moving-sub-level lookup and transform helpers. */
public final class VsShipHelper {
    private VsShipHelper() {}

    /** Finds the Sable sub-level containing a plot-space block position. */
    public static SubLevelAccess getShipAt(Level level, BlockPos pos) {
        if (level == null || pos == null) return null;
        return SableCompanion.INSTANCE.getContaining(level, pos);
    }

    public static ServerSubLevel getLoadedShipById(ServerLevel level, long shipId) {
        if (level == null || shipId < 0) return null;
        for (var candidate : SubLevelContainer.getContainer(level).getAllSubLevels()) {
            if (!(candidate instanceof ServerSubLevel subLevel)) continue;
            if (getShipId(subLevel) == shipId) return subLevel;
        }
        return null;
    }

    public static ServerSubLevel getClosestLoadedShip(ServerLevel level, Vec3 worldPos, double maxDistance) {
        if (level == null || worldPos == null) return null;
        ServerSubLevel best = null;
        double bestDistanceSquared = maxDistance * maxDistance;
        for (var candidate : SubLevelContainer.getContainer(level).getAllSubLevels()) {
            if (!(candidate instanceof ServerSubLevel subLevel)) continue;
            Vector3dc position = subLevel.logicalPose().position();
            double distanceSquared = position.distanceSquared(worldPos.x, worldPos.y, worldPos.z);
            if (distanceSquared < bestDistanceSquared) {
                bestDistanceSquared = distanceSquared;
                best = subLevel;
            }
        }
        return best;
    }

    public static ServerSubLevel getLoadedShipAtFast(ServerLevel level, BlockPos pos) {
        SubLevelAccess containing = getShipAt(level, pos);
        return containing instanceof ServerSubLevel serverSubLevel ? serverSubLevel : null;
    }

    public static ServerSubLevel getLoadedShipAt(ServerLevel level, BlockPos pos) {
        return getLoadedShipAtFast(level, pos);
    }

    public static boolean isOnShip(Level level, BlockPos pos) {
        return level != null && pos != null && SableCompanion.INSTANCE.getContaining(level, pos) != null;
    }

    public static long getShipId(SubLevelAccess subLevel) {
        if (subLevel == null) return -1L;
        if (subLevel instanceof ServerSubLevel serverSubLevel) return serverSubLevel.getRuntimeId();
        return subLevel.getUniqueId().getMostSignificantBits() ^ subLevel.getUniqueId().getLeastSignificantBits();
    }

    public static Vector3dc worldPosition(SubLevelAccess subLevel) {
        return subLevel.logicalPose().position();
    }

    public static Vector3dc velocity(ServerLevel level, ServerSubLevel subLevel) {
        RigidBodyHandle handle = RigidBodyHandle.of(subLevel);
        return handle != null && handle.isValid() ? handle.getLinearVelocity() : new Vector3d();
    }

    public static boolean teleportPlayerShip(ServerPlayer player, double x, double y, double z) {
        if (player == null || player.level().isClientSide) return false;
        SubLevelAccess tracked = SableCompanion.INSTANCE.getTrackingOrVehicleSubLevel(player);
        ServerSubLevel subLevel = tracked instanceof ServerSubLevel serverSubLevel
                ? serverSubLevel
                : getLoadedShipAt(player.serverLevel(), player.blockPosition().below());
        if (subLevel == null) return false;

        try {
            teleportShip(player.serverLevel(), subLevel, x, y, z);
            return true;
        } catch (RuntimeException exception) {
            player.sendSystemMessage(Component.literal("[XenoPixels] Failed to teleport Sable sub-level: "
                    + exception.getMessage()));
            return false;
        }
    }

    public static void teleportShip(ServerLevel level, ServerSubLevel subLevel, double x, double y, double z) {
        if (level == null || subLevel == null) throw new IllegalArgumentException("level and sub-level are required");
        RigidBodyHandle handle = RigidBodyHandle.of(subLevel);
        if (handle == null || !handle.isValid()) throw new IllegalStateException("Sable physics body is not ready");
        Quaterniond rotation = new Quaterniond(subLevel.logicalPose().orientation());
        handle.teleport(new Vector3d(x, y, z), rotation);
    }
}
