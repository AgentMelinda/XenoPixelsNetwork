package net.bullettrain.xenopixelsmod.fx.effek;

import java.util.Locale;

/**
 * Named Effekseer effects. Each lives at {@code assets/xenopixelsmod/effeks/<slot>/<slot>.efkefc};
 * dropping a new file with the same name replaces the effect with no code change.
 */
public enum EffectSlot {
    PUNCH_IMPACT(EffectGate.Category.PUNCH, 1.0f, false),
    PUNCH_HEAVY(EffectGate.Category.PUNCH, 1.6f, false),
    PUNCH_GUARD(EffectGate.Category.PUNCH, 1.0f, false),
    HAKAI_CHANNEL(EffectGate.Category.HAKAI, 1.0f, true),
    HAKAI_CRUMBLE(EffectGate.Category.HAKAI, 1.0f, true),
    HAKAI_ERASE(EffectGate.Category.HAKAI, 1.0f, true),
    HAKAI_PALM(EffectGate.Category.HAKAI, 1.0f, true),
    MISSILE_EXPLOSION(EffectGate.Category.MISSILE, 1.0f, true),
    MISSILE_THRUSTER(EffectGate.Category.MISSILE, 1.0f, false),
    SHIP_THRUSTER(EffectGate.Category.SHIP, 1.0f, false),
    SPARKING_AURA(EffectGate.Category.SPARKING, 1.0f, true),
    /** Optional steadier aura (effekseerSparkingSmooth): constant brightness, flatter look. */
    SPARKING_AURA_SMOOTH(EffectGate.Category.SPARKING, 1.0f, true),
    SPARKING_BURST(EffectGate.Category.SPARKING, 1.0f, true),
    /** The Sparking aura lying along a DMZ flyer's body; played bound to the look. */
    SPARKING_FLIGHT(EffectGate.Category.SPARKING, 1.0f, false),
    /**
     * A ki attack exploding (2026-09-29 owner): the punch impact instead of DragonMineZ's explosion
     * visual. Uses the punch_heavy effect file; its own switch and size.
     */
    KI_IMPACT(EffectGate.Category.KI, 1.0f, true, "punch_heavy");

    private final EffectGate.Category category;
    private final float defaultScale;
    private final boolean upright;
    /** The effect folder and file name, when it reuses another slot's effect (else its own name). */
    private final String asset;

    EffectSlot(EffectGate.Category category, float defaultScale, boolean upright) {
        this(category, defaultScale, upright, null);
    }

    EffectSlot(EffectGate.Category category, float defaultScale, boolean upright, String asset) {
        this.category = category;
        this.defaultScale = defaultScale;
        this.upright = upright;
        this.asset = asset;
    }

    /**
     * Authored standing up in world space and sent with no rotation. AAA's rotationFromForward
     * maps the effect's local +Z onto the forward vector, so "up" would lay an upright effect on
     * its side (2026-09-29, the Hakai veil). Directional effects (punches, plumes) keep their facing.
     */
    public boolean upright() {
        return upright;
    }

    /** Asset path under {@code effeks/}, without the extension: {@code punch_heavy/punch_heavy}. */
    public String path() {
        String name = asset != null ? asset : name().toLowerCase(Locale.ROOT);
        return name + "/" + name;
    }

    public EffectGate.Category category() {
        return category;
    }

    public float defaultScale() {
        return defaultScale;
    }
}
