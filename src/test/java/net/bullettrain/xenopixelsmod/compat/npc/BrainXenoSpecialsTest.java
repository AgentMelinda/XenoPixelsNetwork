package net.bullettrain.xenopixelsmod.compat.npc;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The v7 / v8 split, and the migration that keeps it from taking anything away.
 *
 * <p>v7 used to mean "fully DragonMineZ toggles-wise" while still firing this mod's own BT3 combo
 * poses and teleport moves. It now means the ported tree and nothing of ours, and v8 is that tree
 * with the Xeno layer switchable. Redefining v7 in place would have quietly changed what every NPC
 * already set to it did, so those land on v8 instead.
 */
class BrainXenoSpecialsTest {

    // ------------------------------------------------------------ what each version means

    @Test
    void v7IsTheOnlyVersionThatRefusesTheXenoLayer() {
        for (NpcCombatBrainVersion version : NpcCombatBrainVersion.values()) {
            assertEquals(version != NpcCombatBrainVersion.V7, version.usesXenoSpecials(),
                    version + " usesXenoSpecials");
        }
    }

    @Test
    void v7AndV8BothHandEveryChoiceToTheDmzTree() {
        assertFalse(NpcCombatBrainVersion.V7.honoursToggles());
        assertFalse(NpcCombatBrainVersion.V8.honoursToggles());
        assertTrue(NpcCombatBrainVersion.V6.honoursToggles(), "v6 keeps the per-action switches");
    }

    @Test
    void allThreeDmzBrainsRunTheSameTree() {
        assertTrue(NpcCombatBrainVersion.V6.isDmzPort());
        assertTrue(NpcCombatBrainVersion.V7.isDmzPort());
        assertTrue(NpcCombatBrainVersion.V8.isDmzPort());
        assertTrue(NpcCombatBrainVersion.V8.usesSagaTree());
    }

    @Test
    void v8IsReachableByCyclingTheEditorRow() {
        // A version nothing can select is a version nobody has.
        assertEquals(NpcCombatBrainVersion.V8, NpcCombatBrainVersion.V7.next());
        assertEquals(NpcCombatBrainVersion.V8, NpcCombatBrainVersion.V7.nextNative());
    }

    @Test
    void theWireValueForV8IsEightAndNothingElseMoved() {
        // Stored ordinals go on the wire and into save files. Appending 8 is safe; changing any of
        // the others would repoint every NPC already saved.
        assertEquals(8, NpcCombatBrainVersion.V8.stored());
        assertEquals(NpcCombatBrainVersion.V8, NpcCombatBrainVersion.fromStored(8));
        for (NpcCombatBrainVersion version : NpcCombatBrainVersion.values()) {
            assertEquals(version, NpcCombatBrainVersion.fromStored(version.stored()),
                    version + " must round-trip through its stored value");
        }
    }

    @Test
    void v8IsNameableTheWayTheOthersAre() {
        assertEquals("v8", NpcCombatBrainVersion.V8.label());
        assertEquals(NpcCombatBrainVersion.V8, NpcCombatBrainVersion.byName("v8"));
        assertEquals(NpcCombatBrainVersion.V8, NpcCombatBrainVersion.byName("8"));
    }

    // ------------------------------------------------------------ the gate

    @Test
    void v7RefusesTheXenoLayerEvenWithTheSwitchOn() {
        // The switch belongs to v8. On v7 the version wins, or "pure DragonMineZ" would be a lie
        // the moment somebody left the toggle on before switching versions.
        NpcCombatProfile profile = new NpcCombatProfile();
        profile.brainVersion = NpcCombatBrainVersion.V7;
        profile.xenoSpecials = true;
        assertFalse(profile.allowXenoSpecial());
    }

    @Test
    void v8WithTheSwitchOffMatchesV7() {
        NpcCombatProfile v8 = new NpcCombatProfile();
        v8.brainVersion = NpcCombatBrainVersion.V8;
        v8.xenoSpecials = false;

        NpcCombatProfile v7 = new NpcCombatProfile();
        v7.brainVersion = NpcCombatBrainVersion.V7;

        assertEquals(v7.allowXenoSpecial(), v8.allowXenoSpecial());
        assertFalse(v8.allowXenoSpecial());
    }

    @Test
    void v8WithTheSwitchOnUsesTheXenoLayer() {
        NpcCombatProfile profile = new NpcCombatProfile();
        profile.brainVersion = NpcCombatBrainVersion.V8;
        profile.xenoSpecials = true;
        assertTrue(profile.allowXenoSpecial());
    }

    @Test
    void theSwitchStillAppliesOnTheOlderBrains() {
        // usesXenoSpecials is true for them, so the field decides - an operator who turns the
        // layer off on a v1 NPC means it.
        for (NpcCombatBrainVersion version : NpcCombatBrainVersion.values()) {
            if (version == NpcCombatBrainVersion.V7) {
                continue;
            }
            NpcCombatProfile profile = new NpcCombatProfile();
            profile.brainVersion = version;
            profile.xenoSpecials = false;
            assertFalse(profile.allowXenoSpecial(), version + " must honour the switch");
        }
    }

    @Test
    void theGateIsSeparateFromThePerActionSwitches() {
        // allowBrainAction returns true unconditionally on v7 so DMZ's tree can decide. Folding the
        // Xeno gate into it would make v7 bypass that too, which is the opposite of the point.
        NpcCombatProfile profile = new NpcCombatProfile();
        profile.brainVersion = NpcCombatBrainVersion.V7;
        profile.brainStrike = false;
        assertTrue(profile.allowBrainAction("strike", null),
                "v7 hands the DMZ actions to the tree");
        assertFalse(profile.allowXenoSpecial(), "but never uses the Xeno layer");
    }

    // ------------------------------------------------------------ defaults and persistence

    @Test
    void aFreshNpcHasTheXenoLayerOn() {
        // What every brain did before the switch existed.
        assertTrue(new NpcCombatProfile().xenoSpecials);
    }

    @Test
    void theSwitchSurvivesASaveAndLoad() {
        NpcCombatProfile profile = new NpcCombatProfile();
        profile.brainVersion = NpcCombatBrainVersion.V8;
        profile.xenoSpecials = false;
        NpcCombatProfile read = NpcCombatProfile.fromTag(profile.toTag());
        assertEquals(NpcCombatBrainVersion.V8, read.brainVersion);
        assertFalse(read.xenoSpecials);
    }

    @Test
    void anNpcSavedBeforeTheSwitchKeepsTheLayerOn() {
        // getBoolean answers false for a key that is not there, which would have silently disarmed
        // the BT3 poses and teleport moves on every NPC in every existing world.
        CompoundTag existing = new NpcCombatProfile().toTag();
        existing.remove("BrainXenoSpecials");
        assertTrue(NpcCombatProfile.fromTag(existing).xenoSpecials);
    }

    // ------------------------------------------------------------ the migration

    @Test
    void anNpcStoredAsV7BeforeThisShippedBecomesV8() {
        // The whole point. Those NPCs fired BT3 poses and teleport moves; the new v7 does not, so
        // leaving them on v7 would take that away without anybody asking for it on those NPCs.
        NpcCombatProfile profile = new NpcCombatProfile();
        profile.brainVersion = NpcCombatBrainVersion.V7;
        CompoundTag old = profile.toTag();
        old.remove("BrainXenoSpecials");

        NpcCombatProfile read = NpcCombatProfile.fromTag(old);
        assertEquals(NpcCombatBrainVersion.V8, read.brainVersion);
        assertTrue(read.xenoSpecials, "and with the layer on, which is what it did before");
        assertTrue(read.allowXenoSpecial());
    }

    @Test
    void anNpcSavedAsV7AfterThisShippedStaysOnV7() {
        // Once the key is present the tag has been written by this build, and v7 means what it now
        // means. Migrating again would make the new v7 unusable - it would never stick.
        NpcCombatProfile profile = new NpcCombatProfile();
        profile.brainVersion = NpcCombatBrainVersion.V7;

        NpcCombatProfile read = NpcCombatProfile.fromTag(profile.toTag());
        assertEquals(NpcCombatBrainVersion.V7, read.brainVersion);
        assertFalse(read.allowXenoSpecial());
    }

    @Test
    void chosenV7SurvivesBeingSavedAndLoadedRepeatedly() {
        NpcCombatProfile profile = new NpcCombatProfile();
        profile.brainVersion = NpcCombatBrainVersion.V7;
        for (int round = 0; round < 5; round++) {
            profile = NpcCombatProfile.fromTag(profile.toTag());
            assertEquals(NpcCombatBrainVersion.V7, profile.brainVersion, "round " + round);
        }
    }

    @Test
    void noOtherVersionIsMigrated() {
        // Only v7 changed meaning. Moving anything else would be a bug, not a migration.
        for (NpcCombatBrainVersion version : NpcCombatBrainVersion.values()) {
            if (version == NpcCombatBrainVersion.V7
                    || version != version.legacyCompatible()) {
                continue;
            }
            NpcCombatProfile profile = new NpcCombatProfile();
            profile.brainVersion = version;
            CompoundTag old = profile.toTag();
            old.remove("BrainXenoSpecials");
            assertEquals(version, NpcCombatProfile.fromTag(old).brainVersion,
                    version + " must not be migrated");
        }
    }
}
