package net.bullettrain.xenopixelsmod.compat.sable;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.bullettrain.xenopixelsmod.combat.HakaiAreaRules;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;

/**
 * The Sable ship (plane) blocks inside an area Hakai's sphere.
 *
 * <p>A ship's blocks live in its plot chunks, in the same {@link Level} as the world, at plot-local
 * positions; DMZ ki already griefs them there ({@link SableKiClip}). So the sphere's centre is taken
 * into each touching ship's plot space with the same pose calls SableKiClip uses, and the plot
 * positions within the radius are returned for the ordinary block erasure. Only the part of the
 * ship inside the sphere goes; what happens to the rest (it falls, it splits) is Sable's physics.
 *
 * <p>Assumes a ship pose of scale 1 (not verified in game).
 */
public final class SableHakaiBlocks {
    private SableHakaiBlocks() {
    }

    /** One Sable ship the sphere reaches: the sphere's centre in its plot space, and its plot's chunks. */
    public record Region(Vec3 localCentre, net.bullettrain.xenopixelsmod.combat.HakaiBlockScan.Bounds bounds) {
    }

    /**
     * The ships the sphere reaches, for the lazy scan: positions are not listed here, so a large
     * radius costs nothing until the scan walks the plot (bounded by its own chunks).
     */
    public static List<Region> regions(Level level, Vec3 centre, double radius) {
        List<Region> out = new ArrayList<>();
        if (level == null || centre == null) return out;
        AABB box = new AABB(centre, centre).inflate(radius);
        try {
            for (SubLevel ship : Sable.HELPER.getAllIntersecting(level, new BoundingBox3d(box))) {
                if (ship == null || ship.isRemoved() || ship.getPlot() == null) continue;
                Pose3dc pose = ship.logicalPose();
                Vector3d local = pose.transformPositionInverse(
                        new Vector3d(centre.x, centre.y, centre.z), new Vector3d());
                var min = ship.getPlot().getChunkMin();
                var max = ship.getPlot().getChunkMax();
                out.add(new Region(new Vec3(local.x, local.y, local.z),
                        new net.bullettrain.xenopixelsmod.combat.HakaiBlockScan.Bounds(min.x, min.z, max.x, max.z)));
            }
        } catch (RuntimeException | LinkageError ignored) {
            // Sable changed shape: the world blocks still go, the ships are left alone.
        }
        return out;
    }

    public static List<BlockPos> collect(Level level, Vec3 centre, double radius, int limit) {
        List<BlockPos> out = new ArrayList<>();
        if (level == null || centre == null || limit <= 0) return out;
        AABB box = new AABB(centre, centre).inflate(radius);
        try {
            for (SubLevel ship : Sable.HELPER.getAllIntersecting(level, new BoundingBox3d(box))) {
                if (ship == null || ship.isRemoved()) continue;
                Pose3dc pose = ship.logicalPose();
                Vector3d local = pose.transformPositionInverse(
                        new Vector3d(centre.x, centre.y, centre.z), new Vector3d());
                out.addAll(HakaiAreaRules.sphereTopDown(new Vec3(local.x, local.y, local.z), radius,
                        limit - out.size()));
                if (out.size() >= limit) break;
            }
        } catch (RuntimeException | LinkageError ignored) {
            // Sable changed shape: the world blocks still go, the ships are left alone.
        }
        return out;
    }

    /** Where a (possibly plot-local) block position is in the world, for its particles. */
    public static Vec3 worldCentre(Level level, BlockPos pos) {
        Vec3 c = Vec3.atCenterOf(pos);
        try {
            return Sable.HELPER.projectOutOfSubLevel(level, c);
        } catch (RuntimeException | LinkageError ignored) {
            return c;
        }
    }
}
