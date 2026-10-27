package net.bullettrain.xenopixelsmod.space.pod.transfer;

import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.mixinterface.entity.entities_stick_sublevels.EntityStickExtension;
import dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.EntityMovementExtension;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.UUID;

/**
 * The rider binding pinned Sable 2.0.3 actually supports for a pod body: the player's per-tick tracking link
 * to a sub-level, plus the plot-local offset the stick code positions them from. A {@code ServerSubLevel}
 * cannot own a passenger, so nothing here is a vehicle seat and it must not be described as one.
 *
 * <p>The offset is stored plot-local on purpose: it stays valid when the body's pose, plot slot, or
 * dimension change, as long as the hull profile is identical.
 */
public final class PodRiderBinding {
    /** Player-relative state. World position and motion are captured for the restore, not for the binding. */
    public record Captured(UUID playerId, Vec3 worldPosition, Vector3d plotLocalPosition,
                           Vec3 motion, float yRot, float xRot) {
        public Captured {
            plotLocalPosition = new Vector3d(plotLocalPosition);
        }

        /** Copy-on-read: the generated accessor would hand out the stored mutable joml vector. */
        @Override public Vector3d plotLocalPosition() { return new Vector3d(plotLocalPosition); }
    }

    private PodRiderBinding() {}

    /** The body this player is currently glued to, or null when they are standing in plain level space. */
    public static ServerSubLevel trackedBody(Player player) {
        return SableCompanion.INSTANCE.getTrackingSubLevel(player) instanceof ServerSubLevel body ? body : null;
    }

    /**
     * Records where inside {@code body} the player is standing. Computed from the body pose rather than read
     * from Sable's own plot-position field, so the value is defined even when that field was never populated.
     */
    public static Captured capture(Player player, ServerSubLevel body) {
        Vec3 world = player.position();
        Vector3d plotLocal = body.logicalPose().transformPositionInverse(
                new Vector3d(world.x, world.y, world.z), new Vector3d());
        return new Captured(player.getUUID(), world, plotLocal, player.getDeltaMovement(),
                player.getYRot(), player.getXRot());
    }

    /**
     * Points the player at {@code body} and puts them back at the same place inside it.
     *
     * @return true only when Sable reads the tracking binding back as the intended body
     */
    public static boolean restore(Player player, ServerSubLevel body, Captured captured) {
        return restore(player, body, captured, new Vector3d());
    }

    /** Restores a captured seat after the body's serialized storage frame has moved by {@code storageShift}. */
    static boolean restore(Player player, ServerSubLevel body, Captured captured, Vector3dc storageShift) {
        if (!player.getUUID().equals(captured.playerId())) return false;
        // Tracking and setPos act in the player's own level; binding to a body in another level would leave the
        // player in the wrong dimension while reporting success.
        if (player.level() != body.getLevel()) return false;
        Vector3d plotLocal = PodTransferSeatFrame.afterStorageShift(captured.plotLocalPosition(), storageShift,
                new Vector3d());
        if (plotLocal == null) return false;
        ((EntityMovementExtension) player).sable$setTrackingSubLevel(body);
        ((EntityStickExtension) player).sable$setPlotPosition(new Vec3(plotLocal.x, plotLocal.y, plotLocal.z));
        Vector3dc world = body.logicalPose().transformPosition(plotLocal, new Vector3d());
        player.setPos(world.x(), world.y(), world.z());
        player.setDeltaMovement(captured.motion());
        player.setYRot(captured.yRot());
        player.setXRot(captured.xRot());
        return trackedBody(player) == body;
    }

    /**
     * Drops the binding, leaving the player where they physically are. Used when a transfer is refused after
     * the player was already pointed at a destination body.
     */
    public static void clear(Player player) {
        ((EntityMovementExtension) player).sable$setTrackingSubLevel(null);
        ((EntityStickExtension) player).sable$setPlotPosition(Vec3.ZERO);
    }
}
