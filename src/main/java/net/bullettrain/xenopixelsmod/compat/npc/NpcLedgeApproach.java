package net.bullettrain.xenopixelsmod.compat.npc;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

/**
 * How an NPC out of melee reach gets onto its target when the chase is not an option.
 *
 * <p>2026-09-30 owner, with the NPC standing on a ledge above the player swinging at the air: "the
 * npc check if it can continue foward or foward and deacend down needs to let it be on the player".
 * The chase teleport refuses under 2.5 blocks and the approach did nothing inside the melee band, so
 * a target a floor below was simply never reached.
 *
 * <p>In order: walk a path when the navigator finds one (it plans drops, see
 * {@code XenoNpcEntity.getMaxFallDistance}); otherwise, when the target is below and the ground ahead
 * falls away, step forward off the edge; otherwise nothing new.
 */
public final class NpcLedgeApproach {
    public enum Move { NONE, NAVIGATE, STEP_OFF }

    /** How far below its feet the target must be before stepping off is worth it. */
    static final double MIN_DROP = 0.5;
    /** A path that only re-centres the NPC cannot close a vertical gap in this column. */
    static final double SAME_COLUMN_HORIZONTAL = 0.5;
    /** Past this, stepping off is a leap at something far away, not getting onto it. */
    static final double MAX_STEP_HORIZONTAL = 6.0;
    /** Horizontal speed of the step, blocks per tick: a walk, not a launch. */
    static final double STEP_SPEED = 0.25;
    /** How far ahead the edge is probed. */
    static final double PROBE = 0.8;

    private NpcLedgeApproach() {}

    /**
     * @param dy target feet minus NPC feet (negative when the target is lower)
     * @param horizontal ground distance between them
     * @param dropAhead whether the block the NPC would step onto has no floor
     */
    public static Move decide(boolean inReach, boolean hasPath, double dy, double horizontal, boolean dropAhead) {
        if (inReach) return Move.NONE;
        if (hasPath && !sameColumnHeightGap(dy, horizontal)) return Move.NAVIGATE;
        if (dy <= -MIN_DROP && dropAhead && horizontal <= MAX_STEP_HORIZONTAL) return Move.STEP_OFF;
        return Move.NONE;
    }

    private static boolean sameColumnHeightGap(double dy, double horizontal) {
        return horizontal <= SAME_COLUMN_HORIZONTAL && Math.abs(dy) >= MIN_DROP;
    }

    /** Applies {@link #decide} to a live NPC; returns what it did. */
    public static Move apply(LivingEntity npc, LivingEntity target, double speed) {
        if (!(npc instanceof Mob mob) || target == null || npc.level().isClientSide()) return Move.NONE;
        boolean inReach = NpcCombatRanges.withinMelee(npc, target);
        if (inReach) return Move.NONE;
        double dx = target.getX() - npc.getX();
        double dz = target.getZ() - npc.getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        double dy = target.getY() - npc.getY();
        boolean sameColumn = sameColumnHeightGap(dy, horizontal);
        Path path = sameColumn ? null : mob.getNavigation().createPath(target, 0);
        boolean hasPath = path != null && path.canReach();
        Vec3 dir = horizontal > 0.3 ? new Vec3(dx / horizontal, 0.0, dz / horizontal)
                : horizontalFacing(npc);
        boolean dropAhead = !hasPath && npc.onGround() && dropAhead(npc.level(), npc.position(), dir);
        Move move = decide(false, hasPath, dy, horizontal, dropAhead);
        switch (move) {
            case NAVIGATE -> mob.getNavigation().moveTo(path, speed);
            case STEP_OFF -> {
                mob.getNavigation().stop();
                Vec3 motion = npc.getDeltaMovement();
                npc.setDeltaMovement(dir.x * STEP_SPEED, motion.y, dir.z * STEP_SPEED);
                npc.hurtMarked = true;
            }
            default -> {
                if (sameColumn) mob.getNavigation().stop();
            }
        }
        return move;
    }

    private static Vec3 horizontalFacing(LivingEntity npc) {
        Vec3 look = npc.getLookAngle();
        double length = Math.sqrt(look.x * look.x + look.z * look.z);
        return length < 1.0e-4 ? new Vec3(1.0, 0.0, 0.0) : new Vec3(look.x / length, 0.0, look.z / length);
    }

    /** The block ahead at foot level is passable and has no floor under it. */
    private static boolean dropAhead(Level level, Vec3 feet, Vec3 dir) {
        BlockPos ahead = BlockPos.containing(feet.x + dir.x * PROBE, feet.y + 0.1, feet.z + dir.z * PROBE);
        BlockPos floor = ahead.below();
        return level.getBlockState(ahead).getCollisionShape(level, ahead).isEmpty()
                && level.getBlockState(floor).getCollisionShape(level, floor).isEmpty();
    }
}
