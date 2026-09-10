package net.bullettrain.xenopixelsmod.combat.clone;

/** Original, deterministic decisions; no third-party AI implementation is used. */
public final class CloneCombatPolicy {
    public enum Action { FORMATION, APPROACH, MELEE, RANGED, RECOVER }
    public static final double LEASH = 32;
    public static final double MELEE_RANGE = 2.8;
    public static final double RANGED_RANGE = 24;

    private CloneCombatPolicy() {}

    public static Action decide(boolean active, boolean validTarget, double ownerDistance,
                                double targetDistance, boolean visible, int cooldown) {
        if (!active || !validTarget || !Double.isFinite(ownerDistance)
                || !Double.isFinite(targetDistance) || ownerDistance > LEASH) return Action.FORMATION;
        if (visible && targetDistance <= MELEE_RANGE) {
            return cooldown > 0 ? Action.RECOVER : Action.MELEE;
        }
        if (visible && targetDistance <= RANGED_RANGE && cooldown <= 0) return Action.RANGED;
        return Action.APPROACH;
    }

    /**
     * How many melee attempts in a row may land nothing before a clone gives up on its target.
     *
     * <p>Small on purpose. A clone that cannot hurt what it is swinging at will never be able to,
     * and the failure looks identical to the clone being broken: it stands there punching air.
     */
    public static final int MAX_WHIFFS = 3;

    /**
     * Whether a clone should disengage after a run of melee attempts that connected with nothing.
     *
     * <p>{@code LivingEntity.hurt} returning false is the honest signal that a swing did nothing —
     * the target is invulnerable, already dying, protected by another mod, or the damage was
     * cancelled outright. None of that is visible to {@link #decide}, whose inputs are all
     * positional, so a clone would otherwise keep choosing MELEE forever against something it can
     * never actually hit.
     */
    public static boolean shouldAbandon(int whiffStreak) {
        return whiffStreak >= MAX_WHIFFS;
    }

    public static boolean canSpend(double energy, double stamina, double energyCost, double staminaCost) {
        return Double.isFinite(energy) && Double.isFinite(stamina)
                && Double.isFinite(energyCost) && Double.isFinite(staminaCost)
                && energyCost >= 0 && staminaCost >= 0 && energy >= energyCost && stamina >= staminaCost;
    }

    public static boolean earnsMastery(long now, long previous, float damage, int mastery) {
        return Float.isFinite(damage) && damage > 0 && mastery < CloneFormation.PERFECT_MASTERY
                && (previous == Long.MIN_VALUE || now < previous || now - previous >= 20);
    }
}
