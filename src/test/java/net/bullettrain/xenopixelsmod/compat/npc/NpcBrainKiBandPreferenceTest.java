package net.bullettrain.xenopixelsmod.compat.npc;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Range has to change what an NPC throws, not just whether it throws something.
 *
 * <p>The rotation used to be one flat list: the same cursor walked blasts, waves and disks alike, so
 * an NPC at 25 blocks was as likely to lob a short blast as a wave. These tests cover the band
 * preference that {@code NpcCombatBrain} applies - blasts and disks up close, waves and named
 * attacks out far - and, just as importantly, that it is a <em>preference</em> and never leaves an
 * NPC with nothing to fire.
 */
class NpcBrainKiBandPreferenceTest {

    private static NpcCombatProfile withAllBands() {
        NpcCombatProfile profile = new NpcCombatProfile();
        profile.techniques.add("kiblast");   // BLAST
        profile.techniques.add("kiwave");    // WAVE
        profile.techniques.add("kienzan");   // DISK
        return profile;
    }

    @Test
    void theFarPreferenceReachesPastBlastsForAWave() {
        NpcCombatProfile profile = withAllBands();
        // Cursor 0 would be "kiblast" in the flat rotation; preferring WAVE/NAMED must not answer a
        // blast just because it happens to come first in the list.
        assertEquals("kiwave", NpcBrainKiRotation.pick(profile, 0, id -> true,
                EnumSet.of(NpcBrainKiRotation.Band.WAVE, NpcBrainKiRotation.Band.NAMED)));
    }

    @Test
    void theClosePreferenceRotatesOnlyWithinItsOwnBands() {
        NpcCombatProfile profile = withAllBands();
        EnumSet<NpcBrainKiRotation.Band> close =
                EnumSet.of(NpcBrainKiRotation.Band.BLAST, NpcBrainKiRotation.Band.DISK);
        // Two qualifying techniques, so the cursor alternates between them and never reaches the
        // wave - that is the whole point of the preference.
        assertEquals("kiblast", NpcBrainKiRotation.pick(profile, 0, id -> true, close));
        assertEquals("kienzan", NpcBrainKiRotation.pick(profile, 1, id -> true, close));
        assertEquals("kiblast", NpcBrainKiRotation.pick(profile, 2, id -> true, close));
    }

    @Test
    void aPreferenceIsNotARestriction() {
        // An NPC that only knows waves still fires one at point-blank range rather than standing
        // there doing nothing, which is what a hard filter would have produced.
        NpcCombatProfile profile = new NpcCombatProfile();
        profile.techniques.add("kiwave");
        String picked = NpcBrainKiRotation.pick(profile, 0, id -> true,
                EnumSet.of(NpcBrainKiRotation.Band.BLAST, NpcBrainKiRotation.Band.DISK));
        assertEquals("kiwave", picked);
    }

    @Test
    void anUnreadyPreferredTechniqueFallsThroughRatherThanBlocking() {
        NpcCombatProfile profile = withAllBands();
        // The wave is on cooldown; the far preference must still produce something to fire.
        String picked = NpcBrainKiRotation.pick(profile, 0, id -> !"kiwave".equals(id),
                EnumSet.of(NpcBrainKiRotation.Band.WAVE, NpcBrainKiRotation.Band.NAMED));
        assertNotNull(picked);
        assertEquals("kiblast", picked);
    }

    @Test
    void noPreferenceBehavesExactlyAsBefore() {
        // The three-argument overload is still the plain rotation, so every existing caller and the
        // assertions in NpcBrainKiRotationTest keep their old meaning.
        NpcCombatProfile profile = withAllBands();
        for (int cursor = 0; cursor < 6; cursor++) {
            assertEquals(NpcBrainKiRotation.pick(profile, cursor, id -> true),
                    NpcBrainKiRotation.pick(profile, cursor, id -> true, null));
            assertEquals(NpcBrainKiRotation.pick(profile, cursor, id -> true),
                    NpcBrainKiRotation.pick(profile, cursor, id -> true, EnumSet.noneOf(
                            NpcBrainKiRotation.Band.class)));
        }
    }

    @Test
    void aDisallowedBandIsStillDisallowedEvenWhenPreferred() {
        // The Brain tab's per-band toggles outrank the distance preference: turning Ki Wave off must
        // mean off, at every range. A preferred-but-forbidden band is exactly where that could slip.
        NpcCombatProfile profile = withAllBands();
        profile.brainKiWave = false;
        String picked = NpcBrainKiRotation.pick(profile, 0, id -> true,
                EnumSet.of(NpcBrainKiRotation.Band.WAVE, NpcBrainKiRotation.Band.NAMED));
        assertEquals("kiblast", picked);
    }
}
