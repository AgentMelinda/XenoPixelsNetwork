package net.bullettrain.xenopixelsmod.compat.npc;

/** Pure calculations used by {@link NpcVitalitySync}. */
final class NpcVitalityMath {
    static final int MAX_HEALTH = 1_048_576;

    private NpcVitalityMath() {}

    static int authoritativeMaxHealth(int vitality, double formMultiplier) {
        return clampMaxHealth(scaledVitality(vitality, formMultiplier));
    }

    static int hybridMaxHealth(int baseHealth, int vitality, double formMultiplier) {
        return clampMaxHealth(Math.max(1L, baseHealth) + scaledVitality(vitality, formMultiplier));
    }

    private static double scaledVitality(int vitality, double formMultiplier) {
        double multiplier = Double.isFinite(formMultiplier) && formMultiplier > 0.0
                ? formMultiplier
                : 1.0;
        return Math.max(0L, vitality) * multiplier;
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
