package net.bullettrain.xenopixelsmod.compat.npc;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NpcMeleeAnimCycleTest {
    @Test
    void allOffReturnsBlankAndKeepsCursor() {
        NpcCombatProfile profile = new NpcCombatProfile();
        profile.setMeleeSlot(0, "jab", false);
        NpcMeleeAnimCycle.Pick pick = NpcMeleeAnimCycle.next(profile, 4);
        assertEquals("", pick.clip());
        assertEquals(4, pick.nextCursor());
    }

    @Test
    void blankOnSlotsAreSkipped() {
        NpcCombatProfile profile = new NpcCombatProfile();
        profile.setMeleeSlot(0, "", true);
        profile.setMeleeSlot(1, "second", true);
        NpcMeleeAnimCycle.Pick pick = NpcMeleeAnimCycle.next(profile, 0);
        assertEquals("second", pick.clip());
        assertEquals(2, pick.nextCursor());
    }

    @Test
    void wrapsPastTheLastEnabledSlot() {
        NpcCombatProfile profile = new NpcCombatProfile();
        profile.setMeleeSlot(1, "a", true);
        profile.setMeleeSlot(18, "b", true);
        NpcMeleeAnimCycle.Pick first = NpcMeleeAnimCycle.next(profile, 0);
        assertEquals("a", first.clip());
        assertEquals(2, first.nextCursor());
        NpcMeleeAnimCycle.Pick second = NpcMeleeAnimCycle.next(profile, first.nextCursor());
        assertEquals("b", second.clip());
        assertEquals(19, second.nextCursor());
        NpcMeleeAnimCycle.Pick wrap = NpcMeleeAnimCycle.next(profile, second.nextCursor());
        assertEquals("a", wrap.clip());
        assertEquals(2, wrap.nextCursor());
    }

    @Test
    void nullProfileIsBlank() {
        NpcMeleeAnimCycle.Pick pick = NpcMeleeAnimCycle.next(null, 0);
        assertEquals("", pick.clip());
        assertEquals(0, pick.nextCursor());
    }

    @Test
    void onlyNonBlankEnabledSlotsCountAsOrderedAnimations() {
        NpcCombatProfile profile = new NpcCombatProfile();
        profile.setMeleeSlot(0, "jab", false);
        profile.setMeleeSlot(1, "", true);
        assertEquals(false, NpcMeleeAnimCycle.hasEnabledClip(profile));

        profile.setMeleeSlot(2, "uppercut", true);
        assertEquals(true, NpcMeleeAnimCycle.hasEnabledClip(profile));
    }
}
