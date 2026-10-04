package net.bullettrain.xenopixelsmod.npc;

/**
 * What an NPC does when a door is in its way.
 *
 * <p>The reference editor's AI page offers exactly these three. <b>Ordinals go on the wire</b> — the
 * editor cycles by index and the save carries that index — so this is append only, never reordered.
 */
public enum NpcDoorInteract {
    /** Walks around. The default, and what every NPC did before this existed. */
    DISABLED("Disabled"),
    /** Opens it and walks through, the way a villager does. */
    OPEN("Open"),
    /** Smashes it, the way a zombie does. Only on Hard, which is vanilla's own rule. */
    BREAK("Break");

    private final String label;

    NpcDoorInteract(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** An index off the wire, clamped rather than trusted. */
    public static NpcDoorInteract byIndex(int index) {
        NpcDoorInteract[] all = values();
        return index >= 0 && index < all.length ? all[index] : DISABLED;
    }

    public static java.util.List<String> labels() {
        java.util.List<String> out = new java.util.ArrayList<>();
        for (NpcDoorInteract mode : values()) {
            out.add(mode.label);
        }
        return java.util.List.copyOf(out);
    }
}
