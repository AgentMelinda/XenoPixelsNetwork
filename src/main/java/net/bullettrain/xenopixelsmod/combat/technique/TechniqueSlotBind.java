package net.bullettrain.xenopixelsmod.combat.technique;

/**
 * Place/clear rules for DMZ technique slots.
 *
 * <p>DragonMineZ {@code Techniques.equipOrSwapTechnique} swaps the occupant into the incoming
 * technique's previous slot, and treats {@code ""} as “already equipped” (the first hole). Binding
 * then looks like the attack deleted itself. This is place-or-move: the clicked slot gets the
 * technique, its previous slot is cleared, and the occupant is unequipped rather than teleported.
 *
 * <p>Minecraft-free so the rules can be unit-tested.
 */
public final class TechniqueSlotBind {

    private TechniqueSlotBind() {
    }

    /**
     * Apply a bind (or empty-id unequip) to {@code slots} in place.
     *
     * @param slots     live equipped-id array; empty string means vacant
     * @param slotIndex target slot
     * @param techniqueId technique to place, or null/empty to clear only that slot
     */
    public static void place(String[] slots, int slotIndex, String techniqueId) {
        if (slots == null || slotIndex < 0 || slotIndex >= slots.length) return;

        if (techniqueId == null || techniqueId.isEmpty()) {
            slots[slotIndex] = "";
            return;
        }

        int existing = -1;
        for (int i = 0; i < slots.length; i++) {
            String id = slots[i];
            if (id != null && !id.isEmpty() && id.equals(techniqueId)) {
                existing = i;
                break;
            }
        }
        if (existing != -1 && existing != slotIndex) {
            slots[existing] = "";
        }
        slots[slotIndex] = techniqueId;
    }
}
