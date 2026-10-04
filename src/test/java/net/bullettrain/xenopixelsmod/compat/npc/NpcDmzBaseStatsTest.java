package net.bullettrain.xenopixelsmod.compat.npc;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 2026-09-30 owner: "he doens't damage me too on hits" and "they dont have battlepower". The NPC's
 * DragonMineZ blob (from /data get entity ... ForgeCaps) had STR/VIT/RES/SKP/PWR/ENE all 0 - on
 * XenoPixels full mode too - because a fresh profile never set its six stats. DMZ then computes zero
 * battle power, and melee damage from STR 0 with no RES stamina pool to spend. A new native NPC starts
 * with the base stats DMZ gives a new character of its race and class, and an existing NPC still at
 * all zeros is repaired once.
 */
class NpcDmzBaseStatsTest {
    private static final int[] HUMAN_WARRIOR = {15, 12, 10, 14, 8, 11};

    private static int[] stats(NpcCombatProfile p) {
        return new int[]{p.strength, p.strikePower, p.resistance, p.vitality, p.kiPower, p.energy};
    }

    @Test
    void anNpcAtAllZerosGetsDmzBaseStatsOnce() {
        NpcCombatProfile profile = new NpcCombatProfile();
        assertTrue(NpcCombatProfile.applyDmzBaseStats(true, profile, HUMAN_WARRIOR));
        assertArrayEquals(HUMAN_WARRIOR, stats(profile));

        profile.strength = 0; profile.strikePower = 0; profile.resistance = 0;
        profile.vitality = 0; profile.kiPower = 0; profile.energy = 0;
        assertFalse(NpcCombatProfile.applyDmzBaseStats(true, profile, HUMAN_WARRIOR),
                "an author who later sets every stat to zero keeps it");
        assertArrayEquals(new int[6], stats(profile));
    }

    @Test
    void authoredStatsAreNeverOverwritten() {
        NpcCombatProfile profile = new NpcCombatProfile();
        profile.strength = 40;
        assertFalse(NpcCombatProfile.applyDmzBaseStats(true, profile, HUMAN_WARRIOR));
        assertArrayEquals(new int[]{40, 0, 0, 0, 0, 0}, stats(profile));
    }

    @Test
    void withoutDmzConfigItWaitsAndTriesAgainLater() {
        NpcCombatProfile profile = new NpcCombatProfile();
        assertFalse(NpcCombatProfile.applyDmzBaseStats(true, profile, null));
        assertTrue(NpcCombatProfile.applyDmzBaseStats(true, profile, HUMAN_WARRIOR),
                "no config yet is not a decision; the next load applies it");
    }

    @Test
    void customNpcsAndMyNpcsKeepTheirOwnStats() {
        NpcCombatProfile profile = new NpcCombatProfile();
        assertFalse(NpcCombatProfile.applyDmzBaseStats(false, profile, HUMAN_WARRIOR));
        assertArrayEquals(new int[6], stats(profile));
    }
}
