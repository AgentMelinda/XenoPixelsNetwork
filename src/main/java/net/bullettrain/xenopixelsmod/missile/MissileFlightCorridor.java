package net.bullettrain.xenopixelsmod.missile;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

import java.util.function.BiConsumer;

/**
 * XZ corridor a live missile needs force-loaded so it keeps ticking toward a far
 * (possibly unloaded) target. Does not stamp the entire path — a sliding lookahead
 * plus a few samples toward the target.
 */
public final class MissileFlightCorridor {
    public static final int MAX_LOOKAHEAD_CHUNKS = 12;
    public static final int MAX_PATH_SAMPLES = 8;
    public static final double PATH_SAMPLE_STEP = 64.0;
    public static final double TARGET_NEAR_BLOCKS = 128.0;

    private MissileFlightCorridor() {
    }

    public static int lookaheadChunks(double speedBlocksPerTick) {
        double speed = Math.abs(speedBlocksPerTick);
        return Math.max(4, Math.min(MAX_LOOKAHEAD_CHUNKS, (int) Math.ceil(speed / 8.0) + 4));
    }

    public static boolean targetIsNear(Vec3 position, Vec3 target) {
        if (position == null || target == null) return false;
        return position.distanceToSqr(target) <= TARGET_NEAR_BLOCKS * TARGET_NEAR_BLOCKS;
    }

    public static void visitChunks(Vec3 position, Vec3 velocity, Vec3 target,
                                  BiConsumer<Integer, Integer> chunk) {
        if (position == null || chunk == null) return;
        emit(position, chunk);
        Vec3 vel = velocity == null ? Vec3.ZERO : velocity;
        Vec3 travel = travelDirection(position, vel, target);
        double horizSpeed = new Vec3(vel.x, 0.0, vel.z).length();
        int ahead = lookaheadChunks(Math.max(horizSpeed, vel.length() * 0.5));
        for (int i = 1; i <= ahead; i++) {
            emit(position.add(travel.scale(i * 16.0)), chunk);
        }
        if (target == null) return;
        emit(target, chunk);
        Vec3 to = new Vec3(target.x - position.x, 0.0, target.z - position.z);
        double dist = to.length();
        if (dist <= 16.0) return;
        Vec3 along = to.scale(1.0 / dist);
        int samples = Math.min(MAX_PATH_SAMPLES, (int) (dist / PATH_SAMPLE_STEP) + 1);
        for (int i = 1; i <= samples; i++) {
            double step = Math.min(dist, i * PATH_SAMPLE_STEP);
            emit(position.add(along.scale(step)), chunk);
        }
    }

    public static long chunkKey(Vec3 pos) {
        ChunkPos chunk = new ChunkPos(BlockPos.containing(pos));
        return ChunkPos.asLong(chunk.x, chunk.z);
    }

    private static Vec3 travelDirection(Vec3 position, Vec3 velocity, Vec3 target) {
        Vec3 horiz = new Vec3(velocity.x, 0.0, velocity.z);
        if (horiz.lengthSqr() > 1.0e-8) return horiz.normalize();
        if (target != null) {
            Vec3 to = new Vec3(target.x - position.x, 0.0, target.z - position.z);
            if (to.lengthSqr() > 1.0e-8) return to.normalize();
        }
        Vec3 full = velocity.lengthSqr() > 1.0e-8 ? velocity.normalize() : new Vec3(0.0, 1.0, 0.0);
        Vec3 flat = new Vec3(full.x, 0.0, full.z);
        return flat.lengthSqr() > 1.0e-8 ? flat.normalize() : new Vec3(1.0, 0.0, 0.0);
    }

    private static void emit(Vec3 pos, BiConsumer<Integer, Integer> chunk) {
        ChunkPos c = new ChunkPos(BlockPos.containing(pos.x, pos.y, pos.z));
        chunk.accept(c.x, c.z);
    }
}
