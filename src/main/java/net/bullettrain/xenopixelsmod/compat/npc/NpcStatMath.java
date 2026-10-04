package net.bullettrain.xenopixelsmod.compat.npc;

/** Pure DMZ-style calculations for NPC profiles, kept separate for focused tests. */
final class NpcStatMath {
    static final double BASE_RESOURCE = 20.0;

    private NpcStatMath() {}

    static double scaled(int stat, double multiplier) {
        return Math.max(0, stat) * safeMultiplier(multiplier);
    }

    /**
     * The scaling arguments carry the race/class factor DragonMineZ applies to every stat.
     *
     * <p>They used to be absent, so an NPC's damage ignored its race and class while its health did
     * not. Passed in rather than looked up here so this class stays free of DragonMineZ config and
     * testable on plain numbers.
     */
    static double meleeDamage(int strength, double strMultiplier, double strScaling, double release) {
        return 1.0 + scaled(strength, strMultiplier) * safeMultiplier(strScaling)
                * safeRelease(release);
    }

    static double strikeDamage(int strikePower, int strength, double skpMultiplier,
                               double strMultiplier, double skpScaling, double strScaling,
                               double release) {
        double offense = scaled(strikePower, skpMultiplier) * safeMultiplier(skpScaling)
                + scaled(strength, strMultiplier) * safeMultiplier(strScaling) * 0.25;
        return 1.0 + offense * safeRelease(release);
    }

    static double kiDamage(int kiPower, double pwrMultiplier, double pwrScaling, double release) {
        return scaled(kiPower, pwrMultiplier) * safeMultiplier(pwrScaling) * safeRelease(release);
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

    /**
     * The share of a melee hit an NPC can afford right now.
     *
     * <p>A punch costs stamina in proportion to its own damage, while the pool is set by RES and
     * refilled from VIT. Any NPC built to hit hard rather than to be balanced outruns that: the cost
     * passes what the pool can ever hold, the all-or-nothing spend refuses, and the hit used to fall
     * back to a flat {@code 1.0} — which is how a character with millions of strength ended up
     * scratching. The stat that produces the damage stopped mattering entirely.
     *
     * <p>So an underfunded punch is weaker rather than inert: it spends what is there and keeps that
     * fraction of its damage, never less than {@link #MIN_TIRED_FRACTION}. Stamina still does its
     * job — a fight drains an NPC, and a drained NPC hits softer — without deciding whether strength
     * counts at all.
     *
     * @param available stamina in the pool
     * @param cost      stamina this swing asks for
     * @return 0..1, the multiplier to apply to the swing's damage
     */
    static double affordableFraction(double available, double cost) {
        if (!Double.isFinite(cost) || cost <= 0.0) {
            return 1.0;
        }
        if (!Double.isFinite(available) || available <= 0.0) {
            return MIN_TIRED_FRACTION;
        }
        if (available >= cost) {
            return 1.0;
        }
        return Math.max(MIN_TIRED_FRACTION, available / cost);
    }

    /**
     * Floor for a punch thrown with an empty pool.
     *
     * <p>Not zero: an NPC that cannot land anything reads as broken rather than tired, and the combat
     * brains treat a hit that does nothing as a whiff and eventually give up on the target.
     */
    static final double MIN_TIRED_FRACTION = 0.15;

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
