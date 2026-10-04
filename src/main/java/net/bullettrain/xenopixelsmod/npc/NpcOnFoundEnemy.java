package net.bullettrain.xenopixelsmod.npc;

/** How a native NPC responds when something attacks it. */
public enum NpcOnFoundEnemy {
    RETALIATE("Retaliate"),
    PANIC("Panic"),
    RETREAT("Retreat"),
    NOTHING("Nothing");

    private final String label;

    NpcOnFoundEnemy(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static NpcOnFoundEnemy byIndex(int index) {
        NpcOnFoundEnemy[] all = values();
        return index >= 0 && index < all.length ? all[index] : RETALIATE;
    }

    public static java.util.List<String> labels() {
        return java.util.Arrays.stream(values()).map(NpcOnFoundEnemy::label).toList();
    }
}
