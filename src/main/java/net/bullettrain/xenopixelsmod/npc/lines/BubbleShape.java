package net.bullettrain.xenopixelsmod.npc.lines;

import java.util.Locale;

/**
 * The outline a speech bubble is drawn with. Each shape is generated art in all four atlas
 * palettes (see {@code tools/atlas-panels/xeno_extra_specs.py}); {@link #INHERIT} on the wire means
 * "use the NPC's own default".
 */
public enum BubbleShape {
    INHERIT("inherit"),
    ROUNDED("rounded"),
    THOUGHT("thought"),
    SHOUT("shout"),
    BANNER("banner");

    private final String id;

    BubbleShape(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    /** Lenient by name ("shout", "Shout"), falling back to {@code fallback} for anything unknown. */
    public static BubbleShape byName(String name, BubbleShape fallback) {
        if (name == null || name.isBlank()) {
            return fallback;
        }
        String wanted = name.trim().toLowerCase(Locale.ROOT);
        for (BubbleShape shape : values()) {
            if (shape.id.equals(wanted)) {
                return shape;
            }
        }
        return fallback;
    }

    /** Bounds-checked: the ordinal comes off the wire. */
    public static BubbleShape byOrdinal(int ordinal) {
        BubbleShape[] all = values();
        return ordinal >= 0 && ordinal < all.length ? all[ordinal] : INHERIT;
    }

    /** The shapes an author can pick for an NPC's default, in cycle order. */
    public static final BubbleShape[] PICKABLE = {ROUNDED, THOUGHT, SHOUT, BANNER};
}
