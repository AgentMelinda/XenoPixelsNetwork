package net.bullettrain.xenopixelsmod.npc;

import net.bullettrain.xenopixelsmod.RepoRoot;
import net.bullettrain.xenopixelsmod.compat.npc.NpcCombatProfile;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The AI page's switches.
 *
 * <p>The goals themselves need a level and a navigation, so what is checked here is the part that
 * silently breaks a world: a default that changes what an existing NPC does. Two of these default
 * <em>on</em>, and {@code getBoolean} answers false for a key an older save never wrote - which
 * would have sunk every NPC standing in water and unanchored every one with a home.
 */
class NpcAiSettingsTest {

    private static final String NPC = "src/main/java/net/bullettrain/xenopixelsmod/npc";

    private static String code(String dir, String file) throws IOException {
        return Files.readString(RepoRoot.of(dir, file), StandardCharsets.UTF_8)
                .replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("(?m)//.*$", "");
    }

    // ------------------------------------------------------------ defaults

    @Test
    void aFreshNpcSwimsAndGoesHome() {
        NpcCombatProfile fresh = new NpcCombatProfile();
        assertTrue(fresh.aiCanSwim, "FloatGoal was unconditional before this switch existed");
        assertTrue(fresh.aiReturnToStart, "the leash has always run for an NPC with a home");
        assertFalse(fresh.aiAvoidsWater);
        assertFalse(fresh.aiLeapAtTarget);
        assertEquals(NpcDoorInteract.DISABLED, fresh.aiDoorInteract);
        assertEquals(NpcOnFoundEnemy.RETALIATE, fresh.aiOnFoundEnemy);
        assertEquals(NpcShelterFrom.DISABLED, fresh.aiShelterFrom);
        assertTrue(fresh.aiMustSeeTarget);
        assertFalse(fresh.aiAttackInvisible);
        assertFalse(fresh.aiMountControl);
    }

    @Test
    void anNpcSavedBeforeTheseShippedBehavesExactlyAsItDid() {
        // The asymmetry that matters, and the one SocialClipsTest documents for its own pair. The
        // tag must be non-empty: fromTag short-circuits an empty one to fresh defaults, because
        // "no profile at all" means a brand new NPC rather than an old one.
        CompoundTag existing = new NpcCombatProfile().toTag();
        existing.remove("AiCanSwim");
        existing.remove("AiAvoidsWater");
        existing.remove("AiDoorInteract");
        existing.remove("AiLeapAtTarget");
        existing.remove("AiReturnToStart");
        existing.remove("AiOnFoundEnemy");
        existing.remove("AiShelterFrom");
        existing.remove("AiMustSeeTarget");
        existing.remove("AiAttackInvisible");
        existing.remove("AiMountControl");

        NpcCombatProfile old = NpcCombatProfile.fromTag(existing);
        assertTrue(old.aiCanSwim, "an existing NPC must not suddenly sink");
        assertTrue(old.aiReturnToStart, "nor suddenly stop going home");
        assertFalse(old.aiAvoidsWater, "nor start routing around ponds");
        assertFalse(old.aiLeapAtTarget);
        assertEquals(NpcDoorInteract.DISABLED, old.aiDoorInteract, "nor start opening doors");
        assertEquals(NpcOnFoundEnemy.RETALIATE, old.aiOnFoundEnemy);
        assertEquals(NpcShelterFrom.DISABLED, old.aiShelterFrom);
        assertTrue(old.aiMustSeeTarget);
        assertFalse(old.aiAttackInvisible);
        assertFalse(old.aiMountControl);
    }

    // ------------------------------------------------------------ persistence

    @Test
    void everySwitchSurvivesASaveAndLoad() {
        NpcCombatProfile profile = new NpcCombatProfile();
        profile.aiCanSwim = false;
        profile.aiAvoidsWater = true;
        profile.aiDoorInteract = NpcDoorInteract.BREAK;
        profile.aiLeapAtTarget = true;
        profile.aiReturnToStart = false;
        profile.aiOnFoundEnemy = NpcOnFoundEnemy.RETREAT;
        profile.aiShelterFrom = NpcShelterFrom.SUNLIGHT;
        profile.aiMustSeeTarget = false;
        profile.aiAttackInvisible = true;
        profile.aiMountControl = true;

        NpcCombatProfile read = NpcCombatProfile.fromTag(profile.toTag());
        assertFalse(read.aiCanSwim);
        assertTrue(read.aiAvoidsWater);
        assertEquals(NpcDoorInteract.BREAK, read.aiDoorInteract);
        assertTrue(read.aiLeapAtTarget);
        assertFalse(read.aiReturnToStart);
        assertEquals(NpcOnFoundEnemy.RETREAT, read.aiOnFoundEnemy);
        assertEquals(NpcShelterFrom.SUNLIGHT, read.aiShelterFrom);
        assertFalse(read.aiMustSeeTarget);
        assertTrue(read.aiAttackInvisible);
        assertTrue(read.aiMountControl);
    }

    // ------------------------------------------------------------ the wire

    @Test
    void doorModeOrdinalsAreAppendOnly() {
        // The editor cycles by index and the save carries that index, so inserting a mode would
        // silently change what every already-saved NPC does about doors.
        assertEquals(0, NpcDoorInteract.DISABLED.ordinal());
        assertEquals(1, NpcDoorInteract.OPEN.ordinal());
        assertEquals(2, NpcDoorInteract.BREAK.ordinal());
        assertEquals(NpcDoorInteract.DISABLED, NpcDoorInteract.byIndex(-1));
        assertEquals(NpcDoorInteract.DISABLED, NpcDoorInteract.byIndex(99));
        assertEquals(3, NpcDoorInteract.labels().size());
    }

    // ------------------------------------------------------------ the gate shape

    @Test
    void theGoalsAreGatedRatherThanConditionallyRegistered() throws IOException {
        // registerGoals runs once at construction, so a goal left out there could never come back
        // when its switch is turned on. Every one is added always and refuses to start - the shape
        // StayHomeStrollGoal established.
        String goals = code(NPC, "NpcAiGoals.java");
        for (String gate : new String[] {"aiCanSwim", "aiDoorInteract", "aiLeapAtTarget"}) {
            assertTrue(goals.contains(gate), gate + " must gate a goal");
        }
        assertTrue(goals.contains("canUse()"), "the gate belongs in canUse, not in registration");

        String entity = code(NPC, "XenoNpcEntity.java");
        assertTrue(entity.contains("NpcAiGoals.register"), "the goals must be registered");
        assertFalse(entity.contains("new FloatGoal(this)"),
                "the plain FloatGoal must be gone - the gated SwimGoal replaces it");
    }

    @Test
    void theSettingsThatAreNotGoalsAreAppliedOnSave() throws IOException {
        // The water malus and the navigation's door permission are entity state, read when a path
        // is built rather than checked per tick, so they are pushed on write.
        String goals = code(NPC, "NpcAiGoals.java");
        assertTrue(goals.contains("setPathfindingMalus"), "Avoids Water needs a malus");
        assertTrue(goals.contains("setCanOpenDoors"), "Door Interact needs the navigation told");
        assertTrue(code("src/main/java/net/bullettrain/xenopixelsmod/compat/npc",
                "NpcCombatProfile.java").contains("NpcAiGoals.apply"),
                "and a save must apply them at once");
    }

    @Test
    void theMalusIsWrittenBackToTheDefaultWhenTheSwitchIsOff() throws IOException {
        // Written every time rather than only when true, so turning the switch off undoes it
        // instead of leaving the NPC with whatever it was last given.
        String goals = code(NPC, "NpcAiGoals.java");
        assertTrue(goals.contains("profile.aiAvoidsWater ? WATER_AVOID_MALUS : 0.0f"),
                "both branches must be written");
    }

    @Test
    void theLeashHonoursReturnToStart() throws IOException {
        String behaviour = code(NPC, "XenoNpcBehaviour.java");
        assertTrue(behaviour.contains("aiReturnToStart"), "the leash must read the switch");
        int gate = behaviour.indexOf("aiReturnToStart");
        int radius = behaviour.indexOf("leashRadiusFor(npc)");
        assertTrue(gate > 0 && gate < radius, "and ask before it decides to pull");
    }

    @Test
    void turningTheLeashOffFreesTheNpcForSomethingElseToSteer() throws IOException {
        // Otherwise an NPC that had been walking home when the switch was flipped would keep the
        // movement claim until it timed out, and nothing else could take over meanwhile.
        String behaviour = code(NPC, "XenoNpcBehaviour.java");
        int gate = behaviour.indexOf("aiReturnToStart");
        assertTrue(behaviour.indexOf("release", gate) - gate < 400,
                "the claim must be released when the leash stands down");
    }

    // ------------------------------------------------------------ no dead controls

    @Test
    void everyPreviouslyDisabledRowHasRuntimeBehavior() throws IOException {
        String goals = code(NPC, "NpcAiGoals.java");
        for (String behavior : new String[] {
                "aiShelterFrom", "aiOnFoundEnemy", "aiMustSeeTarget", "aiAttackInvisible",
                "aiMountControl"}) {
            assertTrue(goals.contains(behavior) || code(NPC, "XenoNpcEntity.java").contains(behavior),
                    behavior + " must have a runtime consumer");
        }

        String policy = code("src/main/java/net/bullettrain/xenopixelsmod/network/packet",
                "XenoNpcSavePolicy.java");
        for (String key : new String[] {"AiOnFoundEnemy", "AiShelterFrom", "AiMustSeeTarget",
                "AiAttackInvisible", "AiMountControl"}) {
            assertTrue(policy.contains('"' + key + '"'), key + " must be editor-owned");
        }
    }

    @Test
    void everyLiveSwitchIsWhitelisted() throws IOException {
        // The whitelist is always the last step, and a key missing from it is a save that silently
        // does nothing.
        String policy = code("src/main/java/net/bullettrain/xenopixelsmod/network/packet",
                "XenoNpcSavePolicy.java");
        for (String key : new String[] {
                "AiCanSwim", "AiAvoidsWater", "AiDoorInteract", "AiLeapAtTarget",
                "AiReturnToStart", "AiOnFoundEnemy", "AiShelterFrom", "AiMustSeeTarget",
                "AiAttackInvisible", "AiMountControl"}) {
            assertTrue(policy.contains('"' + key + '"'), key + " is not editor-owned");
        }
    }

    @Test
    void aiModeOrdersMatchMyNpcsAndRejectUnknownOrdinals() {
        assertEquals(0, NpcOnFoundEnemy.RETALIATE.ordinal());
        assertEquals(1, NpcOnFoundEnemy.PANIC.ordinal());
        assertEquals(2, NpcOnFoundEnemy.RETREAT.ordinal());
        assertEquals(3, NpcOnFoundEnemy.NOTHING.ordinal());
        assertEquals(NpcOnFoundEnemy.RETALIATE, NpcOnFoundEnemy.byIndex(-1));
        assertEquals(NpcShelterFrom.DARKNESS, NpcShelterFrom.byIndex(0));
        assertEquals(NpcShelterFrom.SUNLIGHT, NpcShelterFrom.byIndex(1));
        assertEquals(NpcShelterFrom.DISABLED, NpcShelterFrom.byIndex(2));
        assertEquals(NpcShelterFrom.DISABLED, NpcShelterFrom.byIndex(9));
    }
}
