package net.bullettrain.xenopixelsmod.features.transformation.passive;

import net.bullettrain.xenopixelsmod.config.XenoServerConfig;

/** The switches and small arithmetic behind the form passives (pure, for tests). */
public final class FormPassiveRules {
    private FormPassiveRules() {
    }

    public enum Kind { IMMUNITY, PENETRATION, PROJECTILE_AURA, PUNCH_BREAK, MANTLE, DODGE }

    /** The master switch and the passive's own switch ({@code /xenoset}). */
    public static boolean enabled(Kind kind) {
        if (!XenoServerConfig.formPassives) return false;
        return switch (kind) {
            case IMMUNITY -> XenoServerConfig.ueImmunity;
            case PENETRATION -> XenoServerConfig.uePenetration;
            case PROJECTILE_AURA -> XenoServerConfig.ueProjectileAura;
            case PUNCH_BREAK -> XenoServerConfig.uePunchBreak;
            case MANTLE -> XenoServerConfig.hakaiMantle;
            case DODGE -> XenoServerConfig.uiDodge;
        };
    }

    /**
     * The Hakaishin mantle erases every attack, however strong - except the ones the game uses to
     * end a life no matter what ({@code /kill}, the void) and anything marked as bypassing
     * invulnerability, so a creative-mode or command kill still works.
     */
    public static boolean mantleErases(boolean killOrVoid, boolean bypassesInvulnerability) {
        return !killOrVoid && !bypassesInvulnerability;
    }

    /** DMZ's own penetration fraction plus Ultra Ego's bonus, never a full 100%. */
    public static double withPenetration(double dmzPenetration, float bonus) {
        return Math.min(0.95, dmzPenetration + Math.max(0.0, Double.parseDouble(Float.toString(bonus))));
    }
}
