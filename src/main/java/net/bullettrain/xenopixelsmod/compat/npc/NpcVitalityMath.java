package net.bullettrain.xenopixelsmod.compat.npc;

/** Pure calculations used by {@link NpcVitalitySync}. */
final class NpcVitalityMath {
    static final int MAX_HEALTH = 1_048_576;
    /**
     * Vanilla {@code Attributes.MAX_HEALTH} base. DragonMineZ adds {@code getHealthBonus()} on
     * top as an {@code ADD_VALUE} modifier, so a player's displayed HP is {@code 20 + bonus}.
     */
    static final int VANILLA_BASE = 20;

    private NpcVitalityMath() {}

    /**
     * DMZ {@code StatsData.getHealthBonus}: {@code vit * vitScaling * formVitMult}, then the
     * vanilla 20 is added the same way {@code StatsEvents.applyHealthBonus} does for players.
     */
    static int authoritativeMaxHealth(int vitality, double formMultiplier, double vitScaling) {
        return clampMaxHealth(VANILLA_BASE + scaledVitality(vitality, formMultiplier, vitScaling));
    }

    static int hybridMaxHealth(int baseHealth, int vitality, double formMultiplier,
                               double vitScaling) {
        return clampMaxHealth(Math.max(1L, baseHealth)
                + scaledVitality(vitality, formMultiplier, vitScaling));
    }

    private static double scaledVitality(int vitality, double formMultiplier, double vitScaling) {
        double form = Double.isFinite(formMultiplier) && formMultiplier > 0.0 ? formMultiplier : 1.0;
        double scale = Double.isFinite(vitScaling) && vitScaling > 0.0 ? vitScaling : 1.0;
        return Math.max(0L, vitality) * form * scale;
    }

    private static int clampMaxHealth(double calculated) {
        if (!Double.isFinite(calculated) || calculated >= MAX_HEALTH) {
            return MAX_HEALTH;
        }
        return Math.max(1, (int) Math.round(calculated));
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
