package net.bullettrain.xenopixelsmod.combat.v2;

/** Pure charge timing, deterministic fourth-release cadence and flight geometry. */
public final class V2ChargeRules {
    public static final int MIN_TICKS = 8;
    public static final int FULL_TICKS = 20;
    public static final int MAX_TICKS = 200;
    public static final int ARC_TICKS = 24;
    public static final String PUNCH_HOLD = "combat.xeno_charge_punch_hold";
    public static final String PUNCH_FIRE = "combat.xeno_charge_punch_fire";
    public static final String KICK_HOLD = "combat.xeno_charge_kick_hold";
    public static final String KICK_FIRE = "combat.xeno_charge_kick_fire";
    private V2ChargeRules() {}

    public static float progress(int chargeTicks) {
        return Math.clamp(chargeTicks / (float) FULL_TICKS, 0f, 1f);
    }

    public static boolean eligible(float charge, double distance) {
        return charge >= 1f && Double.isFinite(distance) && distance >= 7 && distance <= 20;
    }

    public static int nextCount(int count) {
        return (Math.floorMod(count, 4) + 1) % 4;
    }

    /** Twenty horizontal blocks with a six-block apex; collision may end the flight early. */
    public static double[] arcOffset(int tick) {
        return arcOffset(tick, 20, 6);
    }

    public static double[] arcOffset(int tick, double distance, double apex) {
        double t = Math.clamp(tick / (double) ARC_TICKS, 0, 1);
        return new double[] {distance * t, 4 * apex * t * (1 - t)};
    }
}
