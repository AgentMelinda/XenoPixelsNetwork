package net.bullettrain.xenopixelsmod.features.transformation.passive;

/**
 * What one DMZ form does beyond its stat multipliers (2026-09-29): the Ultra Ego aura, the
 * Hakaishin mantle, Ultra Instinct's dodge. Read from {@code form_passives.json}, so any race's
 * form gains one by adding a line there.
 *
 * @param weakerImmunity        damage from an attacker weaker by {@link #weakerRatio} is negated
 * @param weakerRatio           "weaker" means battle power below the holder's times this
 * @param defPenBonus           extra defense penetration (0..1) on the holder's ki and melee
 * @param deleteWeakProjectiles weaker projectiles and ki reaching the holder are deleted
 * @param punchBreaksWeakKi     punching a weaker ki blast destroys it instead of returning it
 * @param hakaiMantle           every incoming attack and ki blast is erased, whatever its strength
 * @param dodgeMin              auto-dodge chance at no mastery
 * @param dodgeMax              auto-dodge chance at full mastery
 * @param hudTint               the HUD's HP and ki bars take the form's aura colour, as Sparking
 *                              turns the ki bar gold (client only; not a combat passive)
 */
public record FormPassive(boolean weakerImmunity, float weakerRatio, float defPenBonus,
                          boolean deleteWeakProjectiles, boolean punchBreaksWeakKi, boolean hakaiMantle,
                          float dodgeMin, float dodgeMax, boolean hudTint) {

    public static final FormPassive NONE = new FormPassive(false, 0.8f, 0.0f, false, false, false, 0.0f, 0.0f, false);

    public boolean hasDodge() {
        return dodgeMax > 0.0f;
    }

    public boolean any() {
        return weakerImmunity || defPenBonus > 0.0f || deleteWeakProjectiles || punchBreaksWeakKi
                || hakaiMantle || hasDodge();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private boolean weakerImmunity;
        private float weakerRatio = 0.8f;
        private float defPenBonus;
        private boolean deleteWeakProjectiles;
        private boolean punchBreaksWeakKi;
        private boolean hakaiMantle;
        private float dodgeMin;
        private float dodgeMax;
        private boolean hudTint;

        public Builder weakerImmunity(boolean v) { weakerImmunity = v; return this; }
        public Builder weakerRatio(float v) { weakerRatio = v; return this; }
        public Builder defPenBonus(float v) { defPenBonus = v; return this; }
        public Builder deleteWeakProjectiles(boolean v) { deleteWeakProjectiles = v; return this; }
        public Builder punchBreaksWeakKi(boolean v) { punchBreaksWeakKi = v; return this; }
        public Builder hakaiMantle(boolean v) { hakaiMantle = v; return this; }
        public Builder dodge(float min, float max) { dodgeMin = min; dodgeMax = max; return this; }
        public Builder hudTint(boolean v) { hudTint = v; return this; }

        public FormPassive build() {
            return new FormPassive(weakerImmunity, clamp(weakerRatio, 0.0f, 1.0f), clamp(defPenBonus, 0.0f, 1.0f),
                    deleteWeakProjectiles, punchBreaksWeakKi, hakaiMantle,
                    clamp(dodgeMin, 0.0f, 0.95f), clamp(Math.max(dodgeMin, dodgeMax), 0.0f, 0.95f),
                    hudTint);
        }

        private static float clamp(float v, float lo, float hi) {
            return Float.isFinite(v) ? Math.max(lo, Math.min(hi, v)) : lo;
        }
    }
}
