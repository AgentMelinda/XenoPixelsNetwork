package net.bullettrain.xenopixelsmod.npc.inventory;

/**
 * Where an NPC's drops go.
 *
 * <p><b>Ordinals go on the wire</b> — the editor cycles by index and the save carries that index,
 * the same contract the store categories have. Append only; never insert.
 */
public enum NpcLootMode {
    /** Items fall on the ground where the NPC died, the way a mob's do. */
    NORMAL("Normal"),
    /** Items go straight into the killer's inventory, and fall only when it is full. */
    AUTO_PICKUP("Auto Pickup");

    private final String label;

    NpcLootMode(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** An index off the wire, clamped rather than trusted. */
    public static NpcLootMode byIndex(int index) {
        NpcLootMode[] all = values();
        return index >= 0 && index < all.length ? all[index] : NORMAL;
    }

    public static java.util.List<String> labels() {
        java.util.List<String> out = new java.util.ArrayList<>();
        for (NpcLootMode mode : values()) {
            out.add(mode.label);
        }
        return java.util.List.copyOf(out);
    }
}
