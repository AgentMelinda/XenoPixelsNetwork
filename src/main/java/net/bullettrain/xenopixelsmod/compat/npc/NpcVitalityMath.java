package net.bullettrain.xenopixelsmod.compat.npc;

/** Pure calculations used by {@link NpcVitalitySync}. */
final class NpcVitalityMath {
    private NpcVitalityMath() {}

    static int maxHealth(int baseHealth, int vitality, double formMultiplier) {
        long base = Math.max(1L, baseHealth);
        long vit = Math.max(0L, vitality);
        double multiplier = Double.isFinite(formMultiplier) && formMultiplier > 0.0
                ? formMultiplier
                : 1.0;
        double calculated = base + vit * multiplier;
        if (!Double.isFinite(calculated) || calculated >= Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
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
