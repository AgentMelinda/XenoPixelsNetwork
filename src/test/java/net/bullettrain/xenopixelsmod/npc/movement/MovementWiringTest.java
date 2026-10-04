package net.bullettrain.xenopixelsmod.npc.movement;

import net.bullettrain.xenopixelsmod.RepoRoot;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * That every system which moves an NPC actually asks the arbiter.
 *
 * <p>Source-level, the way {@code PatherWiringTest} is, because steering needs a level, a
 * navigation and a loaded world. {@link NpcMovementLedgerTest} covers the rules; this covers the
 * thing a rule cannot catch - a mover that never asks. An arbiter one caller ignores is worse than
 * no arbiter, because it looks like the problem is solved.
 */
class MovementWiringTest {

    private static final String NPC = "src/main/java/net/bullettrain/xenopixelsmod/npc";
    private static final String COMPAT = "src/main/java/net/bullettrain/xenopixelsmod/compat/npc";

    private static String raw(String dir, String file) throws IOException {
        return Files.readString(RepoRoot.of(dir, file), StandardCharsets.UTF_8);
    }

    /** The file with comments and javadoc stripped, so a mention in prose proves nothing. */
    private static String code(String dir, String file) throws IOException {
        return raw(dir, file).replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("(?m)//.*$", "");
    }

    // ------------------------------------------------------------ the two home leashes

    @Test
    void thereIsOnlyOneHomeLeashLeft() throws IOException {
        // The cause of the shudder. XenoNpcBrainV5.returnHome pulled an NPC home against the radius
        // its ROLE implies; XenoNpcBehaviour.tickLeash pulled it home against the radius the EDITOR
        // sets. Two pulls to the same spot on different schedules, disagreeing about when to stop:
        // whenever the radii differed, one said "close enough" every check while the other said
        // "go home", and neither ever won.
        String brain = code(NPC + "/brain", "XenoNpcBrainV5.java");
        assertFalse(brain.contains("returnHome"),
                "XenoNpcBrainV5 must not pull an NPC home - tickLeash is the only home leash");
    }

    @Test
    void theRolesLeashStillDecidesTheDefaultRadius() throws IOException {
        // Retiring the second pull must not quietly unanchor every guard. The role's leash keeps
        // meaning what it meant - it is the radius for an NPC that has never had one set.
        String behaviour = code(NPC, "XenoNpcBehaviour.java");
        assertTrue(behaviour.contains("leashRadiusFor"), "the fallback must exist");
        assertTrue(behaviour.contains("XenoNpcRoleBehaviour.homeLeash"),
                "and it must be the role's own leash, not a new number");
    }

    @Test
    void theLeashUsesTheResolvedRadiusRatherThanTheRawField() throws IOException {
        // Reading npcData().leashRadius() directly here would skip the role fallback and leave
        // every NPC that has never been edited with no leash at all.
        String behaviour = code(NPC, "XenoNpcBehaviour.java");
        int start = behaviour.indexOf("public static void tickLeash");
        assertTrue(start > 0, "tickLeash must still exist");
        // Bounded by the next method's signature rather than by the first mention of the helper -
        // tickLeash calls it, so searching for the name is what ends the slice too early.
        int end = behaviour.indexOf("public static double leashRadiusFor", start);
        assertTrue(end > start, "leashRadiusFor must be declared after tickLeash");
        String body = behaviour.substring(start, end);
        assertTrue(body.contains("leashRadiusFor(npc)"),
                "tickLeash must resolve the radius through the fallback");
        assertFalse(body.contains("data.leashRadius()"),
                "reading the raw field here would skip the role fallback");
    }

    // ------------------------------------------------------------ everyone asks

    @Test
    void theLeashAsksBeforeSteering() throws IOException {
        String behaviour = code(NPC, "XenoNpcBehaviour.java");
        assertTrue(behaviour.contains("Claim.LEASH"), "the leash must take a claim");
    }

    @Test
    void thePatrolWalkerAsksBeforeSteering() throws IOException {
        String walker = code(NPC + "/path", "NpcPathWalker.java");
        assertTrue(walker.contains("Claim.PATROL"), "the walker must take a claim");
    }

    @Test
    void everyCombatRepositionAsksBeforeSteering() throws IOException {
        // teleportFacing is the single place chase, vanish, backstep and teleportAbove all land, so
        // the claim belongs there rather than at four call sites that could each forget.
        String moves = code(COMPAT, "NpcCombatMoves.java");
        assertTrue(moves.contains("Claim.COMBAT"), "combat repositioning must take a claim");
        int claim = moves.indexOf("Claim.COMBAT");
        int method = moves.lastIndexOf("teleportFacing", claim);
        assertTrue(method > 0 && claim - method < 600,
                "the claim belongs inside teleportFacing, where every reposition passes");
    }

    @Test
    void aSceneHoldsTheNpcWhileItPlays() throws IOException {
        String entity = code(NPC, "XenoNpcEntity.java");
        assertTrue(entity.contains("Claim.SCENE"), "a running scene must hold the NPC");
    }

    // ------------------------------------------------------------ giving up

    @Test
    void theLeashStopsReIssuingAtSomethingItCannotReach() throws IOException {
        String behaviour = code(NPC, "XenoNpcBehaviour.java");
        assertTrue(behaviour.contains("progressing(npc)"),
                "the leash must stop re-issuing once it is plainly not working");
        assertTrue(behaviour.contains("NpcMovementOwner.stuck(npc)"),
                "and place the NPC once walking has demonstrably failed");
    }

    @Test
    void thePatrolWalkerStopsReIssuingAtSomethingItCannotReach() throws IOException {
        String walker = code(NPC + "/path", "NpcPathWalker.java");
        assertTrue(walker.contains("progressing(npc)"),
                "re-issuing forever at an unreachable point is the shudder");
    }

    @Test
    void movingTheGoalpostsForgetsThatItWasStuck() throws IOException {
        // An NPC wedged once must not refuse to walk for the rest of its life, so every place that
        // changes where it is going clears the record.
        assertTrue(code(NPC + "/path", "NpcPathWalker.java").contains("clearProgress"),
                "a new patrol point is a new destination");
        assertTrue(code(COMPAT, "NpcCombatMoves.java").contains("clearProgress"),
                "a combat teleport moves it a long way on purpose");
        assertTrue(code(NPC, "XenoNpcBehaviour.java").contains("clearProgress"),
                "being placed at home is an arrival, not a failure");
    }

    // ------------------------------------------------------------ the dead band

    @Test
    void theLeashHasADeadBandSoArrivalSettles() throws IOException {
        // It released at exactly the radius and re-acquired at exactly the radius, so an NPC
        // standing on that boundary - which is where being walked home leaves it - flipped between
        // "home" and "go home" on every check.
        String behaviour = code(NPC, "XenoNpcBehaviour.java");
        assertTrue(behaviour.contains("LEASH_HYSTERESIS"), "the dead band must exist");
        assertTrue(behaviour.contains("radius + LEASH_HYSTERESIS"),
                "and widen the grab distance, not the release distance");
    }

    // ------------------------------------------------------------ housekeeping and the switch

    @Test
    void aRemovedNpcIsForgotten() throws IOException {
        // The same housekeeping XenoNpcSpeech and NpcSocialBehaviour do, so a long-running server
        // does not accumulate an entry per NPC that ever existed.
        assertTrue(code(NPC, "XenoNpcEntity.java").contains("NpcMovementOwner.forget"),
                "removal must drop the claim and the progress record");
    }

    @Test
    void theOldBehaviourIsStillReachable() throws IOException {
        // Arbitration changes how NPCs move, and nothing here has been watched in a game. The
        // previous free-for-all stays one config flip away so the two can be compared in play.
        String behaviour = code(NPC, "XenoNpcBehaviour.java");
        assertTrue(behaviour.contains("XenoServerConfig.npcMovementArbitration"),
                "the arbiter must be switchable");
        String config = raw("src/main/java/net/bullettrain/xenopixelsmod/config",
                "XenoServerConfig.java");
        assertTrue(config.contains("npcMovementArbitration = true"),
                "and default to on, with the old route reachable rather than deleted");
    }

    @Test
    void claimMovementIsTheOnlyGate() throws IOException {
        // Every caller goes through XenoNpcBehaviour.claimMovement rather than NpcMovementOwner
        // directly, because that is where the config switch is honoured. One that called the owner
        // straight would keep arbitrating with the switch off.
        for (String[] target : new String[][] {
                {NPC + "/path", "NpcPathWalker.java"},
                {COMPAT, "NpcCombatMoves.java"},
        }) {
            String body = code(target[0], target[1]);
            assertFalse(body.contains("NpcMovementOwner.claim("),
                    target[1] + " must claim through XenoNpcBehaviour.claimMovement");
        }
    }

    @Test
    void theClaimOrderIsDocumentedWhereItIsDefined() throws IOException {
        // The enum order is the behaviour, and a future reorder is a silent behaviour change unless
        // that is said out loud next to the constants.
        String owner = raw(NPC + "/movement", "NpcMovementOwner.java");
        assertTrue(owner.contains("Ordinal <em>is</em> the priority"),
                "the ordering contract must be stated at the enum");
        assertEquals(6, NpcMovementOwner.Claim.values().length);
    }
}
