package net.bullettrain.xenopixelsmod.compat.npc;

/**
 * Picks the next On + named attack-page slot. Cursor is the next index to try; wrap uses the
 * profile's slot count.
 * All-off or all-blank returns an empty clip and leaves the cursor unchanged.
 */
public final class NpcMeleeAnimCycle {
    public record Pick(String clip, int nextCursor) {
        public static Pick none(int cursor) {
            int safe = Math.floorMod(cursor, NpcCombatProfile.MELEE_SLOT_COUNT);
            return new Pick("", safe);
        }
    }

    private NpcMeleeAnimCycle() {}

    public static Pick next(NpcCombatProfile profile, int cursor) {
        if (profile == null) {
            return Pick.none(0);
        }
        int start = Math.floorMod(cursor, NpcCombatProfile.MELEE_SLOT_COUNT);
        for (int n = 0; n < NpcCombatProfile.MELEE_SLOT_COUNT; n++) {
            int i = (start + n) % NpcCombatProfile.MELEE_SLOT_COUNT;
            if (!profile.meleeSlotOn(i)) {
                continue;
            }
            String clip = profile.meleeSlotClip(i);
            if (clip.isEmpty()) {
                continue;
            }
            return new Pick(clip, (i + 1) % NpcCombatProfile.MELEE_SLOT_COUNT);
        }
        return Pick.none(start);
    }

    /** True only when the ordered page can actually select a named clip. */
    public static boolean hasEnabledClip(NpcCombatProfile profile) {
        return !next(profile, 0).clip().isBlank();
    }
}
