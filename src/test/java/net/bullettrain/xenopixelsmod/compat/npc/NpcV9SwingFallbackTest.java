package net.bullettrain.xenopixelsmod.compat.npc;

import net.bullettrain.xenopixelsmod.RepoRoot;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 2026-09-30 owner: "no handwave animations? on both versions".
 *
 * <p>A new NPC starts with its DMZ appearance off, so {@link NpcDmzAnim#canAnimate} is false and no
 * Xeno clip can play on it. The older brain covers that with a vanilla arm swing
 * ({@code NpcCombatBrain.swingBeforeHit}); the V9 brain, now on by default, only ever called
 * {@code NpcDmzAnim.play}, which then returns false - so V9 NPCs hit with the arm never moving.
 * Source-shape checks, as in {@link NpcComboSwingRangeTest}: the behaviour needs a live server.
 */
class NpcV9SwingFallbackTest {

    private static String source(String relative) throws IOException {
        return Files.readString(RepoRoot.of("src/main/java/net/bullettrain/xenopixelsmod").resolve(relative),
                StandardCharsets.UTF_8);
    }

    @Test
    void comboBeatsSwingTheArmWhenNoClipCanPlay() throws IOException {
        String combos = source("compat/npc/brain/v2/NpcSagaCombos.java");
        assertTrue(combos.contains("if (!NpcDmzAnim.play(npc, intent)) swingWithoutClip(npc);"),
                "a V9 combo beat must fall back to the vanilla swing when its clip cannot play");
        assertTrue(combos.contains("npc.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);"),
                "the fallback is the broadcast vanilla swing");
        assertTrue(combos.contains("!NpcGeckoAnim.canAnimate(npc)"),
                "GeckoLib NPCs play their own attack clip in NpcMeleeDamage; no second swing for them");
    }

    @Test
    void plainV9MeleeSwingsTheArmWhenNoClipCanPlay() throws IOException {
        String brain = source("compat/npc/brain/v2/NpcSagaCombatBrain.java");
        int melee = brain.indexOf("private static void melee(LivingEntity npc, LivingEntity victim) {");
        assertTrue(melee >= 0, "melee() should exist");
        int swing = brain.indexOf("NpcSagaCombos.swingWithoutClip(npc);", melee);
        int hit = brain.indexOf("NpcMeleeDamage.hit(npc, victim, 1.0f);", melee);
        assertTrue(swing > melee && swing < hit, "melee() swings before it hits");
    }
}
