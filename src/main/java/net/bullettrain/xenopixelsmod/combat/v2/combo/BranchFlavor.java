package net.bullettrain.xenopixelsmod.combat.v2.combo;

import net.bullettrain.xenopixelsmod.combat.v2.HitReaction;

/**
 * What kind of move a combo branch leads to, in a word a prompt can print.
 *
 * <p>The state packet tells a client which inputs are open with one bit each. That is enough to
 * draw "light" and "heavy" but not to say what they will do, and after three punches the light
 * attack is a kick. So the flavour of the light and heavy branches rides in the same byte, two
 * bits each above the four input bits, where {@link ComboInput#has} never looks. Minecraft-free.
 */
public enum BranchFlavor {
    PUNCH("Punch"),
    KICK("Kick"),
    LAUNCH("Launch"),
    SMASH("Smash");

    private static final BranchFlavor[] VALUES = values();
    private static final int LIGHT_SHIFT = 4;
    private static final int HEAVY_SHIFT = 6;

    private final String label;

    BranchFlavor(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** The flavour of a beat, from what it does with no direction held. Null reads as a punch. */
    public static BranchFlavor of(ComboNode node) {
        if (node == null) return PUNCH;
        HitReaction reaction = node.reaction();
        if (reaction.isKick()) return KICK;
        switch (reaction) {
            case LAUNCH_UP, LAUNCH_FORWARD, LAUNCH_DOWN:
                return LAUNCH;
            case KNOCKBACK_SHORT, KNOCKBACK_LONG, KNOCKDOWN:
                return SMASH;
            default:
                break;
        }
        String pose = node.intent().name();
        return pose.contains("KICK") || pose.contains("KNEE") || pose.contains("ROUNDHOUSE") ? KICK : PUNCH;
    }

    /** Adds {@code flavor} for {@code input} to a branch mask. Only light and heavy carry one. */
    public static int pack(int mask, ComboInput input, BranchFlavor flavor) {
        int shift = shift(input);
        if (shift < 0 || flavor == null) return mask;
        return (mask & ~(0b11 << shift)) | (flavor.ordinal() << shift);
    }

    /** The flavour packed for {@code input}; a punch when none was. */
    public static BranchFlavor unpack(int mask, ComboInput input) {
        int shift = shift(input);
        return shift < 0 ? PUNCH : VALUES[(mask >> shift) & 0b11];
    }

    private static int shift(ComboInput input) {
        if (input == ComboInput.LIGHT) return LIGHT_SHIFT;
        if (input == ComboInput.HEAVY) return HEAVY_SHIFT;
        return -1;
    }
}
