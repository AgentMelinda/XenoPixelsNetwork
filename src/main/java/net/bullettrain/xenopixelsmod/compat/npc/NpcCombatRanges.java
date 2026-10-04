package net.bullettrain.xenopixelsmod.compat.npc;

import net.bullettrain.xenopixelsmod.config.XenoServerConfig;
import net.minecraft.world.entity.LivingEntity;

/**
 * The one definition of how far away an NPC's target is, and what that distance means.
 *
 * <p>The numbers are DragonMineZ's own, read from the decompiled
 * {@code com.dragonminez.common.init.entities.sagas.ai.SagasCombatBrain}: {@code MELEE_RANGE = 4.5},
 * {@code MID_RANGE = 12.0}, {@code OUT_RANGE = 28.0}, and {@code CombatContext.APPROACH_THRESHOLD =
 * 0.05}. They are not invented, and they are not tuned here - a saga mob and a Xeno NPC should read
 * the same distance the same way.
 *
 * <p>Before this existed, {@link NpcCombatBrain} used bands of its own ({@code MELEE_BAND = 4.0},
 * {@code KI_BAND = 32.0}) while {@code brain.v2.NpcSagaCombatContext} used DMZ's, so the two brains
 * disagreed about where melee ended. Both now read these constants.
 *
 * <p>The band constants are DMZ's; the actual melee {@link #meleeReach} is not a band. Its global
 * default is the {@code /xenoset npcAttackStartRadius} key (1.0 blocks, so an NPC starts attacking
 * only when it is close), and a per-NPC "Melee Range" still overrides it per attacker.
 */
public final class NpcCombatRanges {
    /** Inside this, melee strikes and small ki blasts. DMZ {@code SagasCombatBrain.MELEE_RANGE}. */
    public static final double MELEE = 4.5;
    /** Out to this, ki blasts and close deflection. DMZ {@code SagasCombatBrain.MID_RANGE}. */
    public static final double MID = 12.0;
    /** Out to this, ki waves and big blasts. Beyond it, close the distance. DMZ {@code OUT_RANGE}. */
    public static final double OUT = 28.0;
    /** Closing speed above which a target counts as approaching. DMZ {@code APPROACH_THRESHOLD}. */
    public static final double APPROACH_THRESHOLD = 0.05;

    private NpcCombatRanges() {
    }

    /** Which distance band a target is in. */
    public enum Band {
        /** Melee strikes and small ki blasts. */
        MELEE,
        /** Ki blasts and close deflection. */
        MID,
        /** Ki waves, big blasts and deflection. */
        OUT,
        /** Too far to attack at all - close the distance first. */
        BEYOND
    }

    /** The band {@code distance} falls in. */
    public static Band bandOf(double distance) {
        if (distance <= MELEE) {
            return Band.MELEE;
        }
        if (distance <= MID) {
            return Band.MID;
        }
        if (distance <= OUT) {
            return Band.OUT;
        }
        return Band.BEYOND;
    }

    /**
     * How far this attacker can actually reach, in blocks, centre to centre.
     *
     * <p>The global answer is {@link XenoServerConfig#clampedNpcAttackStartRadius()} - the
     * {@code /xenoset npcAttackStartRadius} key, default 1.0 so an NPC only starts attacking when
     * it is essentially on top of its target. A per-NPC "Melee Range" above zero still wins, so an
     * editor can give one NPC a longer reach without moving anyone else.
     *
     * <p>{@link #MELEE} is no longer the default reach; it stays as DMZ's band boundary and as the
     * fallback when there is no attacker to read a config for. The result is floored at the two
     * bounding boxes because a Xeno NPC's size is editable: an NPC scaled up several times over
     * has a bounding box wider than its reach on its own, and gating it on a flat number would
     * leave it unable to touch something it is standing on top of. Both widths count, since a
     * large target is reachable from further out than a small one.
     */
    public static double meleeReach(LivingEntity attacker, LivingEntity target) {
        if (attacker == null) {
            return MELEE;
        }
        double widths = attacker.getBbWidth() + (target == null ? 0.0 : target.getBbWidth());
        double configured = NpcCombatProfile.readCached(attacker).npcMeleeRange;
        return resolveReach(configured, XenoServerConfig.clampedNpcAttackStartRadius(), widths);
    }

    /**
     * The arithmetic behind {@link #meleeReach}, split out so it can be tested without entities:
     * the per-NPC value wins when set, otherwise the configured global default applies, and the
     * two bounding boxes form the floor either way.
     */
    static double resolveReach(double configuredNpcRange, double globalDefault, double widths) {
        return Math.max(configuredNpcRange > 0.0 ? configuredNpcRange : globalDefault, widths);
    }

    /**
     * Whether {@code attacker} can land a melee hit on {@code target} right now.
     *
     * <p>Both halves matter. Without the distance test an NPC deals melee damage from across the
     * arena - the "attacking the air" behaviour this was written for. Without the line-of-sight test
     * it punches through walls, and DMZ's own context carries a {@code hasLineOfSight} signal for
     * exactly this reason.
     */
    public static boolean withinMelee(LivingEntity attacker, LivingEntity target) {
        if (attacker == null || target == null) {
            return false;
        }
        double dx = target.getX() - attacker.getX();
        double dz = target.getZ() - attacker.getZ();
        double gap = verticalGap(attacker.getBoundingBox().minY, attacker.getBoundingBox().maxY,
                target.getBoundingBox().minY, target.getBoundingBox().maxY);
        return reachable(XenoServerConfig.npcMeleeHeightRule, Math.sqrt(dx * dx + dz * dz),
                attacker.distanceTo(target), gap, meleeReach(attacker, target),
                XenoServerConfig.clampNpcMeleeHeightReach(XenoServerConfig.npcMeleeHeightReach))
                && attacker.hasLineOfSight(target);
    }

    /**
     * Whether a melee swing reaches (2026-09-29 owner: "ai brain v9 cant hit me with a diffrance in
     * y level").
     *
     * <p>The old rule ({@code heightRule} false, {@code /xenoset npcMeleeHeightRule false}) is one
     * straight line from foot to foot against {@code reach}. With a ~1.2-block reach a target just a
     * block up or down - a step, a slab, mid-jump, on the NPC's head - fell outside it while the
     * saga tree, which reads DMZ's 4.5-block band, still chose melee, so the NPC stood there
     * swinging at nothing. The height rule measures {@code reach} across the ground and allows up to
     * {@code heightReach} blocks of air between the two hitboxes.
     */
    public static boolean reachable(boolean heightRule, double horizontal, double straight,
                                    double verticalGap, double reach, double heightReach) {
        if (!heightRule) {
            return straight <= reach;
        }
        return horizontal <= reach && verticalGap <= heightReach;
    }

    /** Blocks of air between two hitboxes stacked vertically; 0 when they overlap in height. */
    public static double verticalGap(double aMinY, double aMaxY, double bMinY, double bMaxY) {
        return Math.max(0.0, Math.max(aMinY, bMinY) - Math.min(aMaxY, bMaxY));
    }
}
