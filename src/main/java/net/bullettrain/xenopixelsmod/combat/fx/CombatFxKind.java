package net.bullettrain.xenopixelsmod.combat.fx;

/**
 * The impact vocabulary, shared by the server emitter and the client renderer.
 *
 * <p>Each constant is one recognisable moment in a Xenoverse/Budokai-style exchange, and the
 * client maps it to a fixed shake, flash and vignette response. Keeping that mapping keyed on an
 * enum rather than on loose numbers in the packet is what makes the feedback <em>learnable</em>:
 * a player should be able to tell a blocked hit from a landed one, and a heavy from an ultimate,
 * without reading the HUD.
 *
 * <p><b>Wire order is the contract.</b> This is sent as an ordinal by
 * {@code CombatFxPacket}, so constants may only be appended — reordering or removing one
 * silently remaps every effect a client already knows.
 */
public enum CombatFxKind {
    /** Ordinary combo connect. Deliberately small: it fires several times a second. */
    IMPACT_LIGHT,
    /** Combo finisher, charged fist/kick, Z-Burst slam. Ring plus a short flash. */
    IMPACT_HEAVY,
    /** Ultimate connect. The loudest thing in the vocabulary; nothing else should reach it. */
    IMPACT_ULTIMATE,
    /** Damage absorbed by guard. Reads cyan and outward, so blocking looks like blocking. */
    GUARD_BLOCK,
    /** Super-counter window landed — the reversal moment, all flash and no ring. */
    COUNTER_FLASH,
    /** Vanish departure clap, paired with the existing {@code VanishShadeFx} silhouette. */
    VANISH_CLAP,
    /** Dash or chase launch. Motion cue only: a nudge and a cone of dust, no flash. */
    DASH_LAUNCH,
    /**
     * Charge-overcharge tier crossing. Appended — do not reorder earlier constants.
     * A longer, slightly gold kick so a 400% charge reads different from a combo jab.
     */
    CHARGE_PEAK,
    /**
     * Hakai channel completes and the target is erased. Appended — do not reorder earlier
     * constants. A slow, violet flash rather than a hard hit: the moment reads as absolute
     * and final, not as an impact.
     */
    HAKAI_ERASE
}
