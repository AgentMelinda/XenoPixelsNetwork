package net.bullettrain.xenopixelsmod.compat.sable;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.mixinterface.clip_overwrite.ClipContextExtension;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.joml.Vector3d;

/**
 * Raytraces KI against Sable / Aeronautics ship plots.
 *
 * <p>Sable's {@code BlockGetter.clip} inverse-transforms the ray into plot space and then
 * <em>skips</em> the ship unless {@code getContaining(plotLocalStart) == ship}. A blast or
 * beam that starts in the world fails that test, so the hull is invisible. This clips the
 * parent world and every intersecting plot without that start-inside-plot gate, and keeps
 * the winning {@link BlockHitResult} in plot-local coordinates so DMZ griefs the real
 * ship block (those chunks live in the same {@link Level}).
 */
public final class SableKiClip {

    private SableKiClip() {
    }

    public static BlockHitResult clip(Level level, ClipContext context) {
        if (level == null || context == null) {
            return miss(context);
        }
        return clip(level, context.getFrom(), context.getTo(), context);
    }

    public static BlockHitResult clip(Level level, Vec3 from, Vec3 to, Entity context) {
        if (level == null || from == null || to == null) {
            return BlockHitResult.miss(to != null ? to : Vec3.ZERO,
                    net.minecraft.core.Direction.UP, BlockPos.ZERO);
        }
        ClipContext ctx = context != null
                ? new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, context)
                : new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty());
        return clip(level, from, to, ctx);
    }

    public static HitResult preferCloser(Entity projectile, HitResult current) {
        if (projectile == null || projectile.level() == null) return current;
        Vec3 from = projectile.position();
        Vec3 to = from.add(projectile.getDeltaMovement());
        BlockHitResult ship = clip(projectile.level(), from, to, projectile);
        if (ship.getType() == HitResult.Type.MISS) return current;
        if (current == null || current.getType() == HitResult.Type.MISS) return ship;
        double shipDist = worldHit(projectile.level(), ship).distanceToSqr(from);
        double curDist = worldHit(projectile.level(), current).distanceToSqr(from);
        return shipDist < curDist ? ship : current;
    }

    public static BlockPos destructionCenter(Level level, BlockHitResult hit) {
        if (level == null || hit == null || hit.getType() != HitResult.Type.BLOCK) return null;
        if (Sable.HELPER.getContaining(level, hit.getBlockPos()) == null) return null;
        return hit.getBlockPos().immutable();
    }

    public static Vec3 worldHit(Level level, HitResult hit) {
        if (hit == null) return Vec3.ZERO;
        Vec3 local = hit.getLocation();
        if (level == null) return local;
        return Sable.HELPER.projectOutOfSubLevel(level, local);
    }

    private static BlockHitResult clip(Level level, Vec3 from, Vec3 to, ClipContext template) {
        ClipContext worldCtx = copyUnprojected(from, to, template);
        BlockHitResult best = level.clip(worldCtx);
        double bestDist = best.getType() == HitResult.Type.MISS
                ? Double.POSITIVE_INFINITY
                : best.getLocation().distanceToSqr(from);

        AABB segment = new AABB(from, to).inflate(0.25);
        for (SubLevel ship : Sable.HELPER.getAllIntersecting(level, new BoundingBox3d(segment))) {
            if (ship == null) continue;
            Pose3dc pose = ship.logicalPose();
            Vector3d localFrom = pose.transformPositionInverse(new Vector3d(from.x, from.y, from.z), new Vector3d());
            Vector3d localTo = pose.transformPositionInverse(new Vector3d(to.x, to.y, to.z), new Vector3d());
            ClipContext plotCtx = copyUnprojected(
                    new Vec3(localFrom.x, localFrom.y, localFrom.z),
                    new Vec3(localTo.x, localTo.y, localTo.z),
                    template);
            BlockHitResult plotHit = ship.getLevel().clip(plotCtx);
            if (plotHit.getType() == HitResult.Type.MISS) continue;
            Vector3d world = pose.transformPosition(
                    new Vector3d(plotHit.getLocation().x, plotHit.getLocation().y, plotHit.getLocation().z),
                    new Vector3d());
            double dist = from.distanceToSqr(world.x, world.y, world.z);
            if (dist < bestDist) {
                best = plotHit;
                bestDist = dist;
            }
        }
        return best;
    }

    private static ClipContext copyUnprojected(Vec3 from, Vec3 to, ClipContext template) {
        ClipContext copy = new ClipContext(from, to, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, CollisionContext.empty());
        if (copy instanceof ClipContextExtension ext) {
            ext.sable$setDoNotProject(true);
        }
        return copy;
    }

    private static BlockHitResult miss(ClipContext context) {
        Vec3 to = context != null ? context.getTo() : Vec3.ZERO;
        return BlockHitResult.miss(to, net.minecraft.core.Direction.UP, BlockPos.containing(to));
    }
}
