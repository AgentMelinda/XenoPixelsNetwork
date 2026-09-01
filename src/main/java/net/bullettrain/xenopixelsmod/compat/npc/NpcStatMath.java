package net.bullettrain.xenopixelsmod.compat.npc;

/** Pure DMZ-style calculations for NPC profiles, kept separate for focused tests. */
final class NpcStatMath {
    static final double BASE_RESOURCE = 20.0;

    private NpcStatMath() {}

    static double scaled(int stat, double multiplier) {
        return Math.max(0, stat) * safeMultiplier(multiplier);
    }

    static double meleeDamage(int strength, double strMultiplier, double release) {
        return 1.0 + scaled(strength, strMultiplier) * safeRelease(release);
    }

    static double strikeDamage(int strikePower, int strength, double skpMultiplier,
                               double strMultiplier, double release) {
        double offense = scaled(strikePower, skpMultiplier)
                + scaled(strength, strMultiplier) * 0.25;
        return 1.0 + offense * safeRelease(release);
    }

    static double kiDamage(int kiPower, double pwrMultiplier, double release) {
        return scaled(kiPower, pwrMultiplier) * safeRelease(release);
    }

    static double maxEnergy(int energy, double multiplier) {
        return BASE_RESOURCE + scaled(energy, multiplier);
    }

    static double maxStamina(int resistance, double multiplier) {
        return BASE_RESOURCE + scaled(resistance, multiplier);
    }

    static double resourceRecoveryPerTick(int governingStat, double multiplier) {
        return (BASE_RESOURCE + scaled(governingStat, multiplier)) / 100.0;
    }

    static double defense(int resistance, double multiplier, double release) {
        return scaled(resistance, multiplier) * safeRelease(release);
    }

    /** DMZ's bounded hyperbolic reduction core. */
    static double mitigate(double incoming, double defense, double scale, double cap) {
        if (!Double.isFinite(incoming) || incoming <= 0.0) return 0.0;
        double safeDefense = Math.max(0.0, defense);
        double denominatorScale = Math.max(12.0, scale);
        double reduction = safeDefense / (denominatorScale + safeDefense);
        reduction = Math.min(Math.max(0.0, cap), reduction);
        return Math.max(0.0, incoming * (1.0 - reduction));
    }

    static double preservePercent(double current, double oldMax, double newMax) {
        if (newMax <= 0.0 || !Double.isFinite(newMax)) return 0.0;
        if (oldMax <= 0.0 || !Double.isFinite(oldMax)) return newMax;
        double ratio = Math.max(0.0, Math.min(1.0, current / oldMax));
        return newMax * ratio;
    }

    private static double safeMultiplier(double value) {
        return Double.isFinite(value) && value > 0.0 ? value : 1.0;
    }

    private static double safeRelease(double value) {
        return Double.isFinite(value) ? Math.max(0.0, value) : 1.0;
    }
}
