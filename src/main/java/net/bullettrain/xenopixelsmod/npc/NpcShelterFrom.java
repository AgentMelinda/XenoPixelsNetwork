package net.bullettrain.xenopixelsmod.npc;

/** When a native NPC should seek a roofed position. */
public enum NpcShelterFrom {
    DARKNESS("Darkness"),
    SUNLIGHT("Sunlight"),
    DISABLED("Disabled");

    private final String label;

    NpcShelterFrom(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static NpcShelterFrom byIndex(int index) {
        NpcShelterFrom[] all = values();
        return index >= 0 && index < all.length ? all[index] : DISABLED;
    }

    public static java.util.List<String> labels() {
        return java.util.Arrays.stream(values()).map(NpcShelterFrom::label).toList();
    }
}
