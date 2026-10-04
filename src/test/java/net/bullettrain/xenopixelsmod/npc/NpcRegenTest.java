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
 * Stats &gt; the two regeneration rates.
 *
 * <p>Healing itself needs a live entity, so what is checked here is the pair of things that go wrong
 * quietly: a rate that survives the round trip changed, and a default that starts healing NPCs that
 * were never meant to heal.
 */
class NpcRegenTest {

    private static String code(String dir, String file) throws IOException {
        return Files.readString(RepoRoot.of(dir, file), StandardCharsets.UTF_8)
                .replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("(?m)//.*$", "");
    }

    @Test
    void aFreshNpcDoesNotHeal() {
        // What every NPC did before these fields existed.
        NpcCombatProfile fresh = new NpcCombatProfile();
        assertEquals(0.0f, fresh.healthRegen, 0.0001f);
        assertEquals(0.0f, fresh.combatRegen, 0.0001f);
    }

    @Test
    void anNpcSavedBeforeTheseShippedStillDoesNotHeal() {
        // Zero is both the default and what an absent key reads as, so unlike the AI switches these
        // need no fallback - but that is worth holding, because a later change to the default would
        // silently start healing every NPC in every existing world.
        CompoundTag existing = new NpcCombatProfile().toTag();
        existing.remove("HealthRegen");
        existing.remove("CombatRegen");

        NpcCombatProfile old = NpcCombatProfile.fromTag(existing);
        assertEquals(0.0f, old.healthRegen, 0.0001f);
        assertEquals(0.0f, old.combatRegen, 0.0001f);
    }

    @Test
    void bothRatesSurviveASaveAndLoad() {
        NpcCombatProfile profile = new NpcCombatProfile();
        profile.healthRegen = 2.5f;
        profile.combatRegen = 0.5f;

        NpcCombatProfile read = NpcCombatProfile.fromTag(profile.toTag());
        assertEquals(2.5f, read.healthRegen, 0.0001f);
        assertEquals(0.5f, read.combatRegen, 0.0001f);
    }

    @Test
    void theyAreSeparateNumbers() {
        // One value covering both would make any boss unkillable or any guard permanently wounded.
        NpcCombatProfile profile = new NpcCombatProfile();
        profile.healthRegen = 10.0f;
        NpcCombatProfile read = NpcCombatProfile.fromTag(profile.toTag());
        assertEquals(10.0f, read.healthRegen, 0.0001f);
        assertEquals(0.0f, read.combatRegen, 0.0001f);
    }

    // ------------------------------------------------------------ bounds

    @Test
    void aRateOffTheWireIsClamped() {
        // A negative rate would damage the NPC through heal(), and an enormous one makes it
        // unkillable. Neither is a meaning anybody intended by typing it.
        assertEquals(0.0f, NpcCombatProfile.clampRegen(-5.0f), 0.0001f);
        assertEquals(0.0f, NpcCombatProfile.clampRegen(Float.NaN), 0.0001f);
        assertEquals(NpcCombatProfile.MAX_REGEN,
                NpcCombatProfile.clampRegen(1_000_000.0f), 0.0001f);
        assertEquals(2.0f, NpcCombatProfile.clampRegen(2.0f), 0.0001f);
    }

    @Test
    void aHostileRateIsClampedOnLoadRatherThanTrusted() {
        CompoundTag tag = new NpcCombatProfile().toTag();
        tag.putFloat("HealthRegen", -100.0f);
        tag.putFloat("CombatRegen", 1e9f);

        NpcCombatProfile read = NpcCombatProfile.fromTag(tag);
        assertEquals(0.0f, read.healthRegen, 0.0001f);
        assertEquals(NpcCombatProfile.MAX_REGEN, read.combatRegen, 0.0001f);
    }

    // ------------------------------------------------------------ the consumer

    @Test
    void theRatesAreActuallyRead() throws IOException {
        // The whole point of whitelisting a key only once it has a consumer.
        String behaviour = code("src/main/java/net/bullettrain/xenopixelsmod/npc",
                "XenoNpcBehaviour.java");
        assertTrue(behaviour.contains("healthRegen"), "the out-of-combat rate must be read");
        assertTrue(behaviour.contains("combatRegen"), "and the in-combat one");
        assertTrue(behaviour.contains("npc.heal("), "and something must actually heal");
    }

    @Test
    void anNpcWithNoRegenReturnsBeforeDoingAnyWork() throws IOException {
        // This runs every tick for every NPC, and almost every NPC has both rates at zero. The
        // early return is what keeps that from costing anything.
        String behaviour = code("src/main/java/net/bullettrain/xenopixelsmod/npc",
                "XenoNpcBehaviour.java");
        int start = behaviour.indexOf("public static void tickRegen");
        assertTrue(start > 0);
        int guard = behaviour.indexOf("profile.healthRegen <= 0.0f && profile.combatRegen <= 0.0f",
                start);
        int stagger = behaviour.indexOf("REGEN_INTERVAL_TICKS", start);
        assertTrue(guard > start, "the zero-rate guard must exist");
        assertTrue(guard < stagger, "and come before any other work");
    }

    @Test
    void beingHitCountsAsAFightEvenWithNothingTargeted() throws IOException {
        // An NPC shot at from a distance has no target of its own but is plainly in a fight, and
        // that is exactly when the two rates differ most.
        String behaviour = code("src/main/java/net/bullettrain/xenopixelsmod/npc",
                "XenoNpcBehaviour.java");
        assertTrue(behaviour.contains("getLastHurtByMob()"),
                "recently hurt must count as fighting");
        assertTrue(behaviour.contains("COMBAT_MEMORY_TICKS"), "for a bounded while");
    }

    @Test
    void healingIsSkippedAtFullHealth() throws IOException {
        String behaviour = code("src/main/java/net/bullettrain/xenopixelsmod/npc",
                "XenoNpcBehaviour.java");
        assertTrue(behaviour.contains("npc.getHealth() >= npc.getMaxHealth()"),
                "a full-health NPC must not be healed every second for nothing");
    }

    @Test
    void bothKeysAreWhitelisted() throws IOException {
        String policy = code("src/main/java/net/bullettrain/xenopixelsmod/network/packet",
                "XenoNpcSavePolicy.java");
        assertTrue(policy.contains("\"HealthRegen\""));
        assertTrue(policy.contains("\"CombatRegen\""));
    }

    @Test
    void theEditorRowsAreLiveRatherThanDisabled() throws IOException {
        String editor = code("src/main/java/net/bullettrain/xenopixelsmod/client/npc",
                "XenoNpcEditorScreen.java");
        assertFalse(editor.contains("disabledField(\"Health Regen\""),
                "Health Regen must no longer be a dead row");
        assertFalse(editor.contains("disabledField(\"Combat Regen\""),
                "nor Combat Regen");
    }
}
