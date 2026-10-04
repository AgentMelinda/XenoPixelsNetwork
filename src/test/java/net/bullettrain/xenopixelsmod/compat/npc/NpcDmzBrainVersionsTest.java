package net.bullettrain.xenopixelsmod.compat.npc;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The two DragonMineZ-driven brains, V6 and V7.
 *
 * <p>Both run the same ported decision tree. The only difference is whether the Brain tab's
 * per-action toggles still apply: V6 honours them, V7 hands every choice to the DMZ logic. These
 * tests pin that difference at the one gate every brain action passes through, and pin that adding
 * them left V1 the default and the older versions untouched.
 *
 * <p>Neither brain calls DMZ's own {@code SagasCombatBrain.decide}. It cannot be called on a Xeno
 * NPC: {@code CombatContext.self} is a {@code final DBSagasEntity} and a Xeno NPC is a
 * {@code PathfinderMob}. They are ports running on DMZ's constants, and nothing here claims more.
 */
class NpcDmzBrainVersionsTest {

    @Test
    void bothNewVersionsRunTheSagaTree() {
        assertTrue(NpcCombatBrainVersion.V6.usesSagaTree());
        assertTrue(NpcCombatBrainVersion.V7.usesSagaTree());
        // And the ones that always did still do.
        assertTrue(NpcCombatBrainVersion.V2.usesSagaTree());
        assertTrue(NpcCombatBrainVersion.V3.usesSagaTree());
        // While the flag loop and the legacy engine do not.
        assertFalse(NpcCombatBrainVersion.V1.usesSagaTree());
        assertFalse(NpcCombatBrainVersion.V4.usesSagaTree());
    }

    @Test
    void onlyV7DropsTheToggles() {
        assertTrue(NpcCombatBrainVersion.V6.honoursToggles());
        assertFalse(NpcCombatBrainVersion.V7.honoursToggles());
        for (NpcCombatBrainVersion older : new NpcCombatBrainVersion[]{
                NpcCombatBrainVersion.V1, NpcCombatBrainVersion.V2, NpcCombatBrainVersion.V3,
                NpcCombatBrainVersion.V4, NpcCombatBrainVersion.V5}) {
            assertTrue(older.honoursToggles(), older + " must keep honouring its toggles");
        }
    }

    @Test
    void turningAnActionOffStillStopsItOnV6() {
        // The bug fixed last round was a toggle that did nothing. V6 must not reintroduce it.
        NpcCombatProfile profile = new NpcCombatProfile();
        profile.brainVersion = NpcCombatBrainVersion.V6;
        profile.brainVanish = false;
        assertFalse(profile.allowBrainAction("vanish", null));

        profile.brainVanish = true;
        assertTrue(profile.allowBrainAction("vanish", null));
    }

    @Test
    void v7IgnoresAnActionToggleEntirely() {
        // Not a bug, and not a dead control either: the editor hides these rows in V7, so nothing
        // on screen claims the switch still means something.
        NpcCombatProfile profile = new NpcCombatProfile();
        profile.brainVersion = NpcCombatBrainVersion.V7;
        profile.brainVanish = false;
        assertTrue(profile.allowBrainAction("vanish", null));
    }

    @Test
    void v7AlsoIgnoresThePerBandKiToggles() {
        // These are read straight off the profile by NpcBrainKiRotation rather than through
        // allowBrainAction, so they are the easy place for the bypass to be forgotten.
        NpcCombatProfile profile = new NpcCombatProfile();
        profile.brainVersion = NpcCombatBrainVersion.V7;
        profile.brainKiWave = false;
        profile.brainKiBlast = false;
        profile.brainKiDisk = false;
        profile.brainKiNamed = false;
        for (NpcBrainKiRotation.Band band : NpcBrainKiRotation.Band.values()) {
            assertTrue(NpcBrainKiRotation.allows(profile, band),
                    band + " should still be available to the fully-DMZ brain");
        }
    }

    @Test
    void v6StillRespectsThePerBandKiToggles() {
        NpcCombatProfile profile = new NpcCombatProfile();
        profile.brainVersion = NpcCombatBrainVersion.V6;
        profile.brainKiWave = false;
        assertFalse(NpcBrainKiRotation.allows(profile, NpcBrainKiRotation.Band.WAVE));
        assertTrue(NpcBrainKiRotation.allows(profile, NpcBrainKiRotation.Band.BLAST));
    }

    @Test
    void addingTheseDidNotChangeTheDefault() {
        // V1 stays the shipped default: no existing NPC changes behaviour because these exist.
        assertSame(NpcCombatBrainVersion.V1, new NpcCombatProfile().brainVersion);
        assertSame(NpcCombatBrainVersion.V1, NpcCombatBrainVersion.byName("whatever"));
    }

    @Test
    void bothAreReachableByCyclingTheButton() {
        // A brain nothing can select is a brain nobody has. Walk the cycler from V1 and require
        // both to appear before it wraps.
        boolean sawV6 = false;
        boolean sawV7 = false;
        NpcCombatBrainVersion at = NpcCombatBrainVersion.V1;
        for (int step = 0; step < NpcCombatBrainVersion.values().length + 2; step++) {
            at = at.nextNative();
            sawV6 |= at == NpcCombatBrainVersion.V6;
            sawV7 |= at == NpcCombatBrainVersion.V7;
        }
        assertTrue(sawV6, "V6 is unreachable from the native cycler");
        assertTrue(sawV7, "V7 is unreachable from the native cycler");
    }

    @Test
    void everyVersionRoundTripsThroughStorage() {
        for (NpcCombatBrainVersion version : NpcCombatBrainVersion.values()) {
            assertSame(version, NpcCombatBrainVersion.fromStored(version.stored()),
                    version + " does not survive a save and reload");
            assertSame(version, NpcCombatBrainVersion.byName(version.label()),
                    version + " does not survive its own label");
        }
    }
}
