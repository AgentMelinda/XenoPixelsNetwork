package net.bullettrain.xenopixelsmod.combat.v3;

import net.minecraft.world.phys.Vec3;
import java.util.UUID;

/** A server-validated target identity and motion snapshot; never client combat authority. */
public record V3TargetSnapshot(UUID target, int entityId, Vec3 position, Vec3 velocity, long revision) {
    public V3TargetSnapshot {
        if (target == null || entityId < 0 || revision < 0 || !finite(position) || !finite(velocity)) {
            throw new IllegalArgumentException("Invalid V3 target snapshot");
        }
    }
    private static boolean finite(Vec3 vector) {
        return vector != null && Double.isFinite(vector.x) && Double.isFinite(vector.y) && Double.isFinite(vector.z);
    }
}
