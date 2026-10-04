package net.bullettrain.xenopixelsmod.compat.npc;

/** Pure calculations used by {@link NpcVitalitySync}. */
final class NpcVitalityMath {
    /**
     * Living-entity HP is a {@code float}. Match DragonMineZ {@code getHealthBonus()}, which
     * saturates at this rather than at the old 1,048,576 engine ceiling.
     */
    static final float LIVING_MAX_HEALTH = Float.MAX_VALUE;
    /**
     * CustomNPC / MyNPCs {@code setMaxHealth(int)} cannot store more than this. Combat HP still
     * uses the living attribute.
     */
    static final int NPC_STATS_MAX_HEALTH = Integer.MAX_VALUE;
    /**
     * Vanilla {@code Attributes.MAX_HEALTH} base. DragonMineZ adds {@code getHealthBonus()} on
     * top as an {@code ADD_VALUE} modifier, so a player's displayed HP is {@code 20 + bonus}.
     */
    static final int VANILLA_BASE = 20;

    private NpcVitalityMath() {}

    /**
     * DMZ {@code StatsData.getHealthBonus}, reduced for an entity with no {@code StatsData}.
     *
     * <p>The full formula in {@code dragonminez-2.1.3.jar} is
     * {@code (vit + bonusMult) * scaling * getTotalMultiplier("VIT") + bonusFlat * scaling},
     * where the two {@code bonus} terms come from {@code StatsData.bonusStats} and
     * {@code getTotalMultiplier} folds in {@code getFormMultiplier}, {@code getStackFormMultiplier},
     * {@code getEffectsMultiplier} and {@code secondaryStatEffects.getMultiplier}.
     *
     * <p>What is computed here is {@code vit * formAndStackMult * vitScaling}. That is not an
     * approximation: {@code bonusStats}, {@code effects} and {@code secondaryStatEffects} all hang
     * off a player's {@code StatsData}, and an NPC has none, so every term left out is structurally
     * identity - 1.0 multiplicative, 0.0 additive. Form and stack multipliers are the only ones an
     * NPC can actually carry, and {@code NpcFormLookup.multiplier} supplies both, honouring the
     * same {@code getMultiplicationInsteadOfAdditionForMultipliers} branch {@code getTotalMultiplier}
     * does.
     *
     * <p>Stated at this length because an earlier, shorter version of this comment read as a claim
     * of exact parity and sent a reader looking for a bug that was not there.
     */
    static float healthBonus(int vitality, double formMultiplier, double vitScaling) {
        double raw = scaledVitality(vitality, formMultiplier, vitScaling);
        if (Double.isNaN(raw) || raw <= 0.0) {
            return 0.0f;
        }
        if (!Double.isFinite(raw) || raw >= LIVING_MAX_HEALTH) {
            return LIVING_MAX_HEALTH;
        }
        return (float) raw;
    }

    /**
     * Player-parity max HP: vanilla 20 plus the health bonus, clamped to
     * {@link #LIVING_MAX_HEALTH}.
     *
     * <p><b>Summed in {@code float}, not {@code double}, on purpose.</b> DragonMineZ builds a
     * player's max health the same way — vanilla 20 as the attribute base, {@code getHealthBonus()}
     * on top as an {@code ADD_VALUE} modifier — but every DMZ readout of it goes through
     * {@code StatsData.getMaxHealth()}, which casts the attribute's double down to a float. Past
     * about 16 million a float step is larger than 20, so the vanilla base rounds away there and the
     * player's own screens never show it.
     *
     * <p>Computing this in double gave an NPC a number 20 higher than a player with identical
     * vitality — {@code 3,865,470,484} against {@code 3,865,470,464} at max VIT — which reads as the
     * NPC being handed 20 free health. It was not: both have the 20, and only the player's display
     * was rounding it off. Matching DMZ's precision here makes the two agree at every vitality,
     * including the small values where the 20 is real and must stay.
     */
    static double authoritativeMaxHealth(int vitality, double formMultiplier, double vitScaling) {
        return clampLiving((float) (VANILLA_BASE + healthBonus(vitality, formMultiplier, vitScaling)));
    }

    static double hybridMaxHealth(int baseHealth, int vitality, double formMultiplier,
                                  double vitScaling) {
        return clampLiving(Math.max(1.0, baseHealth)
                + (double) healthBonus(vitality, formMultiplier, vitScaling));
    }

    static int npcStatsMaxHealth(double livingMax) {
        if (!Double.isFinite(livingMax) || livingMax >= NPC_STATS_MAX_HEALTH) {
            return NPC_STATS_MAX_HEALTH;
        }
        return Math.max(1, (int) Math.round(livingMax));
    }

    static String displayedMaxHealth(double livingMax) {
        if (!Double.isFinite(livingMax) || livingMax <= 0.0) {
            return Integer.toString(VANILLA_BASE);
        }
        double clamped = Math.min(livingMax, LIVING_MAX_HEALTH);
        return Long.toString(Math.round(clamped));
    }

    private static double scaledVitality(int vitality, double formMultiplier, double vitScaling) {
        double form = Double.isFinite(formMultiplier) && formMultiplier > 0.0 ? formMultiplier : 1.0;
        double scale = Double.isFinite(vitScaling) && vitScaling > 0.0 ? vitScaling : 1.0;
        return Math.max(0L, vitality) * form * scale;
    }

    private static double clampLiving(double calculated) {
        if (!Double.isFinite(calculated) || calculated >= LIVING_MAX_HEALTH) {
            return LIVING_MAX_HEALTH;
        }
        return Math.max(1.0, calculated);
    }

    static float preserveHealthPercent(float currentHealth, float oldMaxHealth, float newMaxHealth) {
        if (!Float.isFinite(newMaxHealth) || newMaxHealth <= 0.0f || currentHealth <= 0.0f) {
            return 0.0f;
        }
        if (!Float.isFinite(currentHealth)) {
            return newMaxHealth;
        }
        if (!Float.isFinite(oldMaxHealth) || oldMaxHealth <= 0.0f) {
            return Math.min(currentHealth, newMaxHealth);
        }
        float ratio = Math.max(0.0f, Math.min(1.0f, currentHealth / oldMaxHealth));
        return Math.max(0.0f, Math.min(newMaxHealth, newMaxHealth * ratio));
    }
}
