package net.bullettrain.xenopixelsmod.npc.scene;

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
 * What makes a scene play.
 *
 * <p>Firing one needs a level, a player and a live entity, so what is checked here is the part that
 * breaks a world quietly: a default that changes what an existing NPC does, and a stored value that
 * means something different after a reorder.
 */
class SceneTriggersTest {

    private static final String NPC = "src/main/java/net/bullettrain/xenopixelsmod/npc";

    private static String code(String dir, String file) throws IOException {
        return Files.readString(RepoRoot.of(dir, file), StandardCharsets.UTF_8)
                .replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("(?m)//.*$", "");
    }

    // ------------------------------------------------------------ defaults

    @Test
    void aFreshNpcPlaysItsSceneOnlyOnCommand() {
        // What every scene did before triggers existed. Anything else would make assigning a scene
        // silently change an NPC's behaviour.
        assertEquals(XenoNpcSceneTrigger.MANUAL, new NpcCombatProfile().sceneTrigger);
    }

    @Test
    void anNpcSavedBeforeTriggersExistedStaysManual() {
        CompoundTag existing = new NpcCombatProfile().toTag();
        existing.remove("SceneTrigger");
        assertEquals(XenoNpcSceneTrigger.MANUAL,
                NpcCombatProfile.fromTag(existing).sceneTrigger);
    }

    // ------------------------------------------------------------ storage

    @Test
    void theTriggerSurvivesASaveAndLoad() {
        NpcCombatProfile profile = new NpcCombatProfile();
        profile.sceneId = "greeting";
        profile.sceneTrigger = XenoNpcSceneTrigger.APPROACH;

        NpcCombatProfile read = NpcCombatProfile.fromTag(profile.toTag());
        assertEquals("greeting", read.sceneId);
        assertEquals(XenoNpcSceneTrigger.APPROACH, read.sceneTrigger);
    }

    @Test
    void aTriggerIsWrittenOnlyAlongsideAScene() {
        // A trigger on an NPC with no scene cannot do anything, and writing it would grow the key
        // on every untouched NPC in a world for nothing.
        NpcCombatProfile profile = new NpcCombatProfile();
        profile.sceneTrigger = XenoNpcSceneTrigger.DEATH;
        assertFalse(profile.toTag().contains("SceneTrigger"));

        profile.sceneId = "last_words";
        assertTrue(profile.toTag().contains("SceneTrigger"));
    }

    @Test
    void theTriggerIsStoredByNameRatherThanOrdinal() {
        // Which is what lets the list be reordered later. Storing the ordinal would silently
        // repoint every already-saved NPC the first time somebody inserted a trigger.
        NpcCombatProfile profile = new NpcCombatProfile();
        profile.sceneId = "x";
        profile.sceneTrigger = XenoNpcSceneTrigger.TIMER;
        assertEquals("TIMER", profile.toTag().getString("SceneTrigger"));
    }

    @Test
    void anUnknownTriggerReadsAsManualRatherThanThrowing() {
        // A profile written by a newer build naming a trigger this one has never heard of should
        // leave the NPC quiet, not break its save.
        CompoundTag tag = new NpcCombatProfile().toTag();
        tag.putString("SceneId", "x");
        tag.putString("SceneTrigger", "ON_FULL_MOON");
        assertEquals(XenoNpcSceneTrigger.MANUAL, NpcCombatProfile.fromTag(tag).sceneTrigger);
    }

    @Test
    void byNameIsForgivingAboutCaseAndBlanks() {
        assertEquals(XenoNpcSceneTrigger.INTERACT, XenoNpcSceneTrigger.byName("interact"));
        assertEquals(XenoNpcSceneTrigger.INTERACT, XenoNpcSceneTrigger.byName("  INTERACT "));
        assertEquals(XenoNpcSceneTrigger.MANUAL, XenoNpcSceneTrigger.byName(""));
        assertEquals(XenoNpcSceneTrigger.MANUAL, XenoNpcSceneTrigger.byName(null));
    }

    @Test
    void theEditorCycleIsClampedRatherThanTrusted() {
        assertEquals(XenoNpcSceneTrigger.MANUAL, XenoNpcSceneTrigger.byIndex(-1));
        assertEquals(XenoNpcSceneTrigger.MANUAL, XenoNpcSceneTrigger.byIndex(99));
        assertEquals(XenoNpcSceneTrigger.values().length, XenoNpcSceneTrigger.labels().size());
    }

    // ------------------------------------------------------------ the call sites

    @Test
    void everyTriggerHasSomethingThatFiresIt() throws IOException {
        // A trigger the editor offers and nothing raises is the dead-control problem wearing a
        // different hat: the setting saves, and the NPC never performs.
        String entity = code(NPC, "XenoNpcEntity.java");
        String triggers = code(NPC + "/scene", "XenoNpcSceneTriggers.java");
        assertTrue(entity.contains("XenoNpcSceneTrigger.INTERACT"), "interact must fire");
        assertTrue(entity.contains("XenoNpcSceneTrigger.DAMAGED"), "damaged must fire");
        assertTrue(entity.contains("XenoNpcSceneTrigger.DEATH"), "death must fire");
        assertTrue(triggers.contains("XenoNpcSceneTrigger.APPROACH"), "approach must fire");
        assertTrue(triggers.contains("XenoNpcSceneTrigger.TIMER"), "timer must fire");
    }

    @Test
    void manualNeverFiresByItself() throws IOException {
        // Otherwise a scene left on the default would play at every opportunity, and testing one by
        // command would be impossible to tell apart from it firing on its own.
        assertTrue(code(NPC + "/scene", "XenoNpcSceneTriggers.java")
                        .contains("cause == XenoNpcSceneTrigger.MANUAL"),
                "MANUAL must be refused inside fire()");
    }

    @Test
    void aSceneAlreadyPlayingIsNotRestarted() throws IOException {
        // The failure the cooldown exists for, and the cheaper half of it: a combo would otherwise
        // restart the scene from step one several times a second.
        String triggers = code(NPC + "/scene", "XenoNpcSceneTriggers.java");
        assertTrue(triggers.contains("XenoNpcScenePlayback.playing(npc)"),
                "a scene mid-play must not be restarted");
        assertTrue(triggers.contains("COOLDOWN_TICKS"), "and there must be a cooldown besides");
    }

    @Test
    void damageThatDidNotLandDoesNotFire() throws IOException {
        // super.hurt answering false means invulnerable, already dead, or the wrong damage type -
        // a taunt for a hit that did nothing is a scene firing at a phantom.
        String entity = code(NPC, "XenoNpcEntity.java");
        int hurt = entity.indexOf("public boolean hurt(");
        assertTrue(hurt > 0, "the NPC must override hurt to know it was damaged");
        String body = entity.substring(hurt, Math.min(entity.length(), hurt + 700));
        assertTrue(body.contains("boolean landed = super.hurt("), "the result must be kept");
        assertTrue(body.contains("if (landed"), "and gate the trigger");
    }

    @Test
    void deathFiresBeforeTheEntityLeavesItsTrackers() throws IOException {
        // Same edge the KILLED line sits on, and for the same reason: after super.die() a broadcast
        // reaches nobody, so a last word would be a last word nobody hears.
        String entity = code(NPC, "XenoNpcEntity.java");
        int death = entity.indexOf("XenoNpcSceneTrigger.DEATH");
        int superDie = entity.indexOf("super.die(", entity.indexOf("public void die("));
        assertTrue(death > 0 && superDie > 0 && death < superDie,
                "the death scene must fire before super.die()");
    }

    @Test
    void theSelfStartingTriggersAreStaggeredAcrossNpcs() throws IOException {
        // Approach and timer run off the tick. A row of NPCs all checking on the same tick is the
        // pattern the bard, the path walker and the social gestures all avoid.
        String triggers = code(NPC + "/scene", "XenoNpcSceneTriggers.java");
        assertTrue(triggers.contains("npc.tickCount + npc.getId()"),
                "the tick check must be staggered by entity id");
    }

    @Test
    void anNpcWithNoSceneReturnsBeforeReadingAnythingElse() throws IOException {
        // This runs every tick for every NPC, and almost none has a scene.
        String triggers = code(NPC + "/scene", "XenoNpcSceneTriggers.java");
        int tick = triggers.indexOf("public static void tick(");
        assertTrue(tick > 0);
        int guard = triggers.indexOf("profile.sceneId.isEmpty()", tick);
        int stride = triggers.indexOf("CHECK_STRIDE", tick);
        assertTrue(guard > tick && guard < stride,
                "the no-scene guard must come before any other work");
    }

    @Test
    void aRemovedNpcIsForgotten() throws IOException {
        assertTrue(code(NPC, "XenoNpcEntity.java").contains("XenoNpcSceneTriggers.forget"),
                "removal must drop the cooldown, as speech and gestures do");
    }

    // ------------------------------------------------------------ the wire

    @Test
    void theKeyIsWhitelisted() throws IOException {
        assertTrue(code("src/main/java/net/bullettrain/xenopixelsmod/network/packet",
                        "XenoNpcSavePolicy.java").contains("\"SceneTrigger\""),
                "the whitelist is always the last step, and a key missing from it saves nothing");
    }

    @Test
    void theShapeSampleCarriesBothHalves() throws IOException {
        // SceneTrigger is written only alongside a scene id, so a sample with one and not the other
        // ships a whitelisted key the type check then rejects - the trap that bit NpcLines, Trades
        // and BardSound in turn.
        String policy = code("src/main/java/net/bullettrain/xenopixelsmod/network/packet",
                "XenoNpcSavePolicy.java");
        assertTrue(policy.contains("sample.sceneId"), "the sample must set a scene");
        assertTrue(policy.contains("sample.sceneTrigger"), "and a trigger");
    }
}
