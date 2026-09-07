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
