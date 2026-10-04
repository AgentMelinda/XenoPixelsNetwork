package net.bullettrain.xenopixelsmod.compat.npc;

import net.bullettrain.xenopixelsmod.RepoRoot;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Why an NPC still threw punches all the way in, after the melee damage gate was added.
 *
 * <p>Gating {@link NpcMeleeDamage#hit} stopped the <em>damage</em> landing from range, but both
 * combo loops animated first and hit second:
 *
 * <pre>
 *   play(JAB)                  // always
 *   NpcMeleeDamage.hit(...)    // refused when out of reach
 * </pre>
 *
 * <p>And a combo is deliberately kept alive further out than a punch reaches, so that a target
 * stepping back for a moment does not cancel the string. That difference is the bug: between the
 * swing range and the keep-alive range the NPC played a full punch into empty air, once per combo
 * gap, for the whole approach.
 *
 * <p>These are source-shape checks. The behaviour needs a live server and two entities, which a
 * unit test has neither of; what is pinned here is that the animation sits behind the reach check
 * in both brains, and that the two ranges really do differ - because if they ever stopped
 * differing, someone could "simplify" the gate away without noticing.
 */
class NpcComboSwingRangeTest {

    private static String source(String relative) throws IOException {
        Path path = RepoRoot.of("src/main/java/net/bullettrain/xenopixelsmod").resolve(relative);
        assertTrue(Files.exists(path), path + " should exist");
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    @Test
    void aComboOutlivesThePunchesItIsMadeOf() {
        // The keep-alive band is MELEE_BAND + 2.0 in both brains. If this ever became equal to the
        // swing range the gate below would look redundant - it is not, and this says why.
        double keepAlive = NpcCombatBrain.MELEE_BAND + 2.0;
        assertTrue(keepAlive > NpcCombatRanges.MELEE,
                "a combo is kept alive further out than a punch reaches; that gap is the bug");
        assertTrue(keepAlive - NpcCombatRanges.MELEE >= 1.0,
                "and the gap is wide enough to be several wasted swings while closing");
    }

    @Test
    void theDefaultBrainChecksReachBeforeItAnimates() throws IOException {
        String brain = source("compat/npc/NpcCombatBrain.java");
        int gate = brain.indexOf("NpcCombatRanges.withinMelee(npc, victim)");
        int animate = brain.indexOf("Bt3ComboChoreography.resolve(");
        assertTrue(gate >= 0, "advance() must consult the reach before swinging");
        assertTrue(animate >= 0, "the combo animation should still be there");
        assertTrue(gate < animate,
                "the reach check has to come first, or the punch is thrown before it is tested");
    }

    @Test
    void theSagaBrainChecksReachBeforeItAnimates() throws IOException {
        String combos = source("compat/npc/brain/v2/NpcSagaCombos.java");
        int gate = combos.indexOf("NpcCombatRanges.withinMelee(npc, victim)");
        int animate = combos.indexOf("Bt3AnimationIntent.JAB_LEFT");
        assertTrue(gate >= 0, "the pressure string must consult the reach before jabbing");
        assertTrue(animate >= 0, "the jab animation should still be there");
        assertTrue(gate < animate, "the reach check has to come first");
    }

    @Test
    void anOutOfReachStepYieldsBeforeAnimating() throws IOException {
        // The legacy brain reschedules; the saga brain releases its movement-owning combo.
        for (String file : new String[]{"compat/npc/NpcCombatBrain.java",
                "compat/npc/brain/v2/NpcSagaCombos.java"}) {
            String text = source(file);
            // The pressure-step gate; NpcSagaCombos also gates its finisher earlier in the file.
            int step = Math.max(0, text.indexOf("static void advance("));
            int gate = text.indexOf("NpcCombatRanges.withinMelee(npc, victim)", step);
            assertTrue(gate >= 0, file + " must have the reach gate");
            String branch = text.substring(gate, Math.min(text.length(), gate + 500));
            String expected = file.endsWith("NpcSagaCombos.java") ? "COMBOS.remove(" : "COMBOS.put(";
            assertTrue(branch.contains(expected), file + ": out-of-reach movement must yield");
            assertTrue(branch.contains("return;"),
                    file + ": and must return before the animation");
        }
    }

    @Test
    void aStrikeTechniqueNeedsMeleeReachBeforeItSwings() throws IOException {
        // 2026-09-30: strikes fired from up to 8 blocks, so an NPC on a ledge punched the air.
        String strike = source("compat/npc/NpcStrikeDispatcher.java");
        int fire = strike.indexOf("public static boolean fire(");
        int gate = strike.indexOf("NpcCombatRanges.withinMelee(caster, target)", fire);
        int swing = strike.indexOf("caster.swing(", fire);
        assertTrue(gate > fire && gate < swing, "fire() must refuse out of reach before it swings");
        int tick = strike.indexOf("public static void tick(");
        assertTrue(strike.indexOf("NpcCombatRanges.withinMelee(caster, target)", tick) > tick,
                "the queued impact re-checks reach");
    }

    @Test
    void theMeleeDamageGateIsStillThereUnderneath() throws IOException {
        // The animation gate is the second line of defence, not a replacement: every other caller
        // of hit() - scripts included - still relies on hit() refusing out of range.
        String melee = source("compat/npc/NpcMeleeDamage.java");
        int gate = melee.indexOf("NpcCombatRanges.withinMelee(attacker, target)");
        int hurt = melee.indexOf("target.hurt(attacker.damageSources().mobAttack(attacker)");
        assertTrue(gate >= 0, "hit() must still refuse out of range");
        assertTrue(hurt > gate, "and refuse before it deals damage");
    }
}
