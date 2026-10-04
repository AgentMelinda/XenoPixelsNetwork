package net.bullettrain.xenopixelsmod.npc.scene;

/**
 * What makes an NPC's scene play.
 *
 * <p>Scenes could only be started by {@code /xenoscene play} — an operator typing a command at the
 * moment they wanted it. That is fine for testing one and useless for authoring a world: a greeter
 * who bows when you walk up, a boss who taunts when it is hurt, a lamplighter who does its round on
 * a timer. Every one of those is the same scene, wanting a different question answered about
 * <em>when</em>.
 *
 * <p>This is the first half of what a script would otherwise be asked for — and it is the half that
 * needs no script. The other half is richer actions, which is the {@code Kind} list on
 * {@link XenoNpcScene}; both together cover most of what NPC scripts are used for, with no arbitrary
 * code execution, no new dependency and a save format that is already validated.
 *
 * <p><b>Stored by name, not ordinal</b> — as {@code XenoNpcScene.Kind} is. A profile carries
 * {@code SceneTrigger} as a string, so this list may be reordered freely and an unknown value from a
 * newer build reads back as {@link #MANUAL} rather than as whatever happens to sit at that index.
 * That is the opposite of the store categories, whose ordinals <em>are</em> the wire format.
 */
public enum XenoNpcSceneTrigger {

    /** Only {@code /xenoscene play}. The default, and what every scene did before this existed. */
    MANUAL("Manual"),

    /**
     * A player right-clicks the NPC.
     *
     * <p>After the tools and the wand, and after a trader's shop or a transporter's list — an NPC
     * with something to open should open it. A scene is what an NPC does when it has nothing more
     * urgent to do with the click.
     */
    INTERACT("On interact"),

    /** A player comes within speaking distance. Rate-limited, or it fires every tick. */
    APPROACH("On approach"),

    /** The NPC is hurt. The taunt-when-wounded case. */
    DAMAGED("On damaged"),

    /** The NPC dies. Plays before it is removed, so a last word is possible. */
    DEATH("On death"),

    /** Every so often, unprompted. The patrolling lamplighter. */
    TIMER("On a timer");

    private final String label;

    XenoNpcSceneTrigger(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /**
     * A stored name back to a trigger, defaulting to {@link #MANUAL}.
     *
     * <p>Unknown reads as MANUAL rather than throwing: a profile written by a newer build naming a
     * trigger this one has never heard of should leave the NPC quiet, not break its save.
     */
    public static XenoNpcSceneTrigger byName(String raw) {
        if (raw == null || raw.isBlank()) {
            return MANUAL;
        }
        String key = raw.trim().toUpperCase(java.util.Locale.ROOT);
        for (XenoNpcSceneTrigger trigger : values()) {
            if (trigger.name().equals(key)) {
                return trigger;
            }
        }
        return MANUAL;
    }

    /** An index off the editor's cycle, clamped rather than trusted. */
    public static XenoNpcSceneTrigger byIndex(int index) {
        XenoNpcSceneTrigger[] all = values();
        return index >= 0 && index < all.length ? all[index] : MANUAL;
    }

    public static java.util.List<String> labels() {
        java.util.List<String> out = new java.util.ArrayList<>();
        for (XenoNpcSceneTrigger trigger : values()) {
            out.add(trigger.label);
        }
        return java.util.List.copyOf(out);
    }
}
