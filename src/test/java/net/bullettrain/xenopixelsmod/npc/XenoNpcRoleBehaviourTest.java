package net.bullettrain.xenopixelsmod.npc;

import net.bullettrain.xenopixelsmod.RepoRoot;
import net.bullettrain.xenopixelsmod.compat.npc.NpcCombatRanges;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What each role does, rather than what it is called.
 *
 * <p>Role picked an NPC's entity type and its starting appearance from the day the six were added,
 * and a Companion followed its owner - but Trader, Guard and Quest behaved exactly like a Humanoid.
 * A quest giver would chase an attacker across the map and never come back, which makes it a poor
 * quest giver.
 */
class XenoNpcRoleBehaviourTest {

    private static String source(String relative) throws IOException {
        Path path = RepoRoot.of("src/main/java/net/bullettrain/xenopixelsmod").resolve(relative);
        assertTrue(Files.exists(path), path + " should exist");
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    @Test
    void tradersAndQuestGiversDoNotFight() {
        assertFalse(XenoNpcRoleBehaviour.fights(XenoNpcRole.TRADER));
        assertFalse(XenoNpcRoleBehaviour.fights(XenoNpcRole.QUEST));
    }

    @Test
    void everyOtherRoleStillFights() {
        for (XenoNpcRole role : new XenoNpcRole[]{XenoNpcRole.HUMANOID, XenoNpcRole.CREATURE,
                XenoNpcRole.GUARD, XenoNpcRole.COMPANION}) {
            assertTrue(XenoNpcRoleBehaviour.fights(role), role + " should still fight");
        }
    }

    @Test
    void onlyAGuardGuards() {
        assertTrue(XenoNpcRoleBehaviour.guardsHome(XenoNpcRole.GUARD));
        for (XenoNpcRole role : XenoNpcRole.values()) {
            if (role != XenoNpcRole.GUARD) {
                assertFalse(XenoNpcRoleBehaviour.guardsHome(role), role + " should not guard");
            }
        }
    }

    @Test
    void onlyACompanionFollows() {
        assertTrue(XenoNpcRoleBehaviour.followsOwner(XenoNpcRole.COMPANION));
        for (XenoNpcRole role : XenoNpcRole.values()) {
            if (role != XenoNpcRole.COMPANION) {
                assertFalse(XenoNpcRoleBehaviour.followsOwner(role), role + " should not follow");
            }
        }
    }

    @Test
    void aCompanionHasNoHomeLeashBecauseItsAnchorIsItsOwner() {
        // Pulling it back to a spawn point would fight the following.
        assertEquals(0.0, XenoNpcRoleBehaviour.homeLeash(XenoNpcRole.COMPANION));
    }

    @Test
    void nonCombatantsAreHeldCloserThanGuards() {
        double passive = XenoNpcRoleBehaviour.homeLeash(XenoNpcRole.TRADER);
        double guard = XenoNpcRoleBehaviour.homeLeash(XenoNpcRole.GUARD);
        assertTrue(passive > 0.0, "a trader should not drift");
        assertEquals(passive, XenoNpcRoleBehaviour.homeLeash(XenoNpcRole.QUEST));
        assertTrue(guard > passive, "a guard needs room to finish a fight");
    }

    @Test
    void aGuardsLeashIsChosenAgainstTheCombatBands() {
        // Far enough to finish a fight it started, short enough that it is still guarding
        // something. Tied to the real band rather than picked as a round number.
        assertTrue(XenoNpcRoleBehaviour.GUARD_LEASH < NpcCombatRanges.OUT,
                "a guard should give up before the outer ki band");
        assertTrue(XenoNpcRoleBehaviour.GUARD_LEASH > NpcCombatRanges.MID,
                "but not abandon a fight it is winning at mid range");
    }

    @Test
    void fightersRoamFreely() {
        assertEquals(0.0, XenoNpcRoleBehaviour.homeLeash(XenoNpcRole.HUMANOID));
        assertEquals(0.0, XenoNpcRoleBehaviour.homeLeash(XenoNpcRole.CREATURE));
    }

    @Test
    void everyRoleSaysWhatItWillDo() {
        // The editor shows this under the role cycler. A role with no description is a label over
        // nothing, which is what all of these were.
        for (XenoNpcRole role : XenoNpcRole.values()) {
            String text = XenoNpcRoleBehaviour.describe(role);
            assertFalse(text.isBlank(), role + " should describe itself");
        }
        assertEquals("", XenoNpcRoleBehaviour.describe(null));
    }

    @Test
    void theQuestRoleExplainsItsPassiveDialogueAndHandInFlow() {
        assertFalse(XenoNpcRoleBehaviour.fights(XenoNpcRole.QUEST));
        String description = XenoNpcRoleBehaviour.describe(XenoNpcRole.QUEST);
        assertTrue(description.contains("dialogue"));
        assertTrue(description.contains("QUEST"));
        assertTrue(description.contains("completer"));
    }

    @Test
    void theQuestRoleCanAuthorAndAssignItsPassiveDialogue() throws IOException {
        String editor = source("client/npc/XenoNpcEditorScreen.java");
        assertTrue(editor.contains("Create or edit shared dialogs"));
        assertTrue(editor.contains("profile.dialogSlots.set(dialogSlotBeingPicked"));
        assertTrue(editor.contains("QUEST answers offer quests."));
        assertTrue(editor.contains("NPC hand-in uses the quest's configured completer"));
        assertTrue(editor.contains("Job Enabled"));
        assertTrue(editor.contains("v -> profile.jobEnabled = v, \"JobEnabled\""));
        assertTrue(editor.contains("Category.NPC)"),
                "NPC Interact Lines now opens the NPC line category XenoNpcSpeech says");
    }

    @Test
    void theBrainAndTheTargetingAskTheSameQuestions() throws IOException {
        // Two ideas of what a Guard is would disagree the first time either was edited - the brain
        // would chase while the targeting refused to pick anything up, or the reverse.
        assertTrue(source("npc/brain/XenoNpcBrainV5.java").contains("XenoNpcRoleBehaviour"),
                "the brain should consult the shared predicates");
        assertTrue(source("npc/XenoNpcEntity.java").contains("XenoNpcRoleBehaviour"),
                "and so should registerGoals");
    }

    @Test
    void retaliationGoalUsesTheCurrentSavedRole() throws IOException {
        // Role can be edited after creation and is loaded from NBT, so the goal checks the
        // data-backed role when it runs instead of capturing the entity type's original role.
        String entity = source("npc/XenoNpcEntity.java");
        int at = entity.indexOf("HurtByTargetGoal(this)");
        assertTrue(at >= 0, "the retaliation goal should be registered");
        String body = entity.substring(at, Math.min(entity.length(), at + 500));
        assertTrue(body.contains("XenoNpcRoleBehaviour.fights(XenoNpcEntity.this.role())"),
                "only fighting roles should retaliate, based on the live role");
    }

    @Test
    void aGuardPicksUpHostilesRatherThanAnything() throws IOException {
        // Monster, not LivingEntity: a guard should not start on the village cow.
        String entity = source("npc/XenoNpcEntity.java");
        // The guard's own goal. There are two NearestAttackableTargetGoals now - the other is
        // faction hostility over LivingEntity - so search for the guard branch, not the first hit.
        int at = entity.indexOf("guardsHome(XenoNpcEntity.this.role())");
        assertTrue(at >= 0, "a guard needs a target goal of its own");
        int goal = entity.lastIndexOf("new NearestAttackableTargetGoal<>", at);
        String body = entity.substring(Math.max(0, goal), Math.min(entity.length(), at + 250));
        assertTrue(body.contains("NearestAttackableTargetGoal"), "a guard needs its own goal");
        assertTrue(body.contains("Monster.class"), "hostiles only");
    }
}
