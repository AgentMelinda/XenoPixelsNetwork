package net.bullettrain.xenopixelsmod.missile;

import dev.ryanhcode.sable.companion.SableCompanion;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Look-ray world target used by the guidance computer and the missile radar item.
 */
public final class MissileLookTarget {
    public static final double RANGE = 200.0;

    private MissileLookTarget() {
    }

    public static @Nullable BlockPos fromPlayer(@Nullable Player player) {
        if (player == null) return null;
        HitResult hit = player.pick(RANGE, 0f, false);
        if (hit.getType() == HitResult.Type.MISS) return null;
        return worldPos(player.level(), hit.getLocation());
    }

    /**
     * World-space block of a look hit. When the ray lands in a Sable sub-level, the point is
     * projected out so missile targeting uses overworld coordinates.
     */
    public static BlockPos worldPos(@Nullable Level level, Vec3 loc) {
        BlockPos raw = BlockPos.containing(loc);
        if (level == null) return raw;
        try {
            if (SableCompanion.INSTANCE.isInPlotGrid(level, raw)
                    || SableCompanion.INSTANCE.getContaining(level, raw) != null) {
                Vec3 world = SableCompanion.INSTANCE.projectOutOfSubLevel(level, loc);
                if (world != null) return BlockPos.containing(world);
            }
        } catch (Throwable ignored) {
            return raw;
        }
        return raw;
    }
}
