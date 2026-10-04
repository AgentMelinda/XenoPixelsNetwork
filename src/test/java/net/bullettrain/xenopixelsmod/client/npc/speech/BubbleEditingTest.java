package net.bullettrain.xenopixelsmod.client.npc.speech;

import net.bullettrain.xenopixelsmod.RepoRoot;
import net.bullettrain.xenopixelsmod.compat.npc.NpcCombatProfile;
import net.bullettrain.xenopixelsmod.npc.lines.XenoNpcLines;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Per-NPC bubble geometry, timing, ordering and lines.
 *
 * <p>Implements {@code docs/superpowers/specs/2026-09-22-bubble-editing-design.md}. Rendering needs
 * a running client, so what is pinned here is every rule that is pure: the defaults that protect
 * NPCs already placed in a world, the clamps on values that arrive over the network, the ordering
 * that fixes the flicker, and the full-override rule for lines.
 */
class BubbleEditingTest {

    private static String code(String dir, String file) throws IOException {
        return Files.readString(RepoRoot.of(dir, file), StandardCharsets.UTF_8)
                .replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("(?m)//.*$", "");
    }

    // ------------------------------------------------------- 1. geometry and timing

    @Test
    void anNpcThatSetsNothingKeepsTheOldConstants() {
        // The compatibility invariant. These three constants were the hardcoded values before the
        // fields existed; every NPC already in a world must render exactly as it did.
        NpcCombatProfile profile = new NpcCombatProfile();
        assertEquals(0.7f, profile.bubbleHeight, "SpeechBubbleRenderer.HEIGHT_ABOVE_ENTITY");
        assertEquals(60, profile.bubbleDurationTicks, "SpeechBubbleQueue.DEFAULT_LIFETIME_TICKS");
        assertEquals(0, profile.bubbleMaxLines, "0 means defer to SpeechBubbleLayout.maxLines()");
    }

    @Test
    void aTagWrittenBeforeTheFieldsExistedReadsAsTheDefaults() {
        CompoundTag old = new NpcCombatProfile().toTag();
        old.remove("BubbleHeight");
        old.remove("BubbleDurationTicks");
        old.remove("BubbleMaxLines");
        NpcCombatProfile read = NpcCombatProfile.fromTag(old);
        assertEquals(0.7f, read.bubbleHeight);
        assertEquals(60, read.bubbleDurationTicks);
        assertEquals(0, read.bubbleMaxLines);
    }

    @Test
    void theThreeFieldsSurviveASaveAndLoad() {
        NpcCombatProfile profile = new NpcCombatProfile();
        profile.bubbleHeight = 2.5f;
        profile.bubbleDurationTicks = 200;
        profile.bubbleMaxLines = 4;
        NpcCombatProfile read = NpcCombatProfile.fromTag(profile.toTag());
        assertEquals(2.5f, read.bubbleHeight);
        assertEquals(200, read.bubbleDurationTicks);
        assertEquals(4, read.bubbleMaxLines);
    }

    @Test
    void outOfRangeValuesClampRatherThanRefuse() {
        // They arrive in a save packet. A crafted one must not put a bubble a kilometre up or pin
        // it on screen forever - but refusing the whole save over one bad number is worse than
        // clamping it.
        CompoundTag tag = new NpcCombatProfile().toTag();
        tag.putFloat("BubbleHeight", 900.0f);
        tag.putInt("BubbleDurationTicks", Integer.MAX_VALUE);
        tag.putInt("BubbleMaxLines", 400);
        NpcCombatProfile read = NpcCombatProfile.fromTag(tag);
        assertEquals(NpcCombatProfile.MAX_BUBBLE_HEIGHT, read.bubbleHeight);
        assertEquals(NpcCombatProfile.MAX_BUBBLE_DURATION_TICKS, read.bubbleDurationTicks);
        assertEquals(NpcCombatProfile.MAX_BUBBLE_LINES, read.bubbleMaxLines);
    }

    @Test
    void aNegativeOrNonFiniteHeightFallsBackToTheDefault() {
        CompoundTag tag = new NpcCombatProfile().toTag();
        tag.putFloat("BubbleHeight", Float.NaN);
        assertEquals(0.7f, NpcCombatProfile.fromTag(tag).bubbleHeight);

        tag.putFloat("BubbleHeight", -3.0f);
        assertEquals(0.0f, NpcCombatProfile.fromTag(tag).bubbleHeight,
                "zero is a legitimate height - a bubble at the NPC's own head");
    }

    // ------------------------------------------------------- 2. ordering

    @Test
    void bubblesSortFarToNearSoTheNearestPaintsOnTop() {
        // The flicker fix. HashMap iteration order is unspecified and free to change between
        // frames, so with the depth test off, which of two overlapping bubbles was on top was
        // arbitrary per frame.
        List<Integer> order = SpeechBubbleOrder.farToNear(List.of(1, 2, 3),
                id -> switch (id) {
                    case 1 -> 4.0;
                    case 2 -> 100.0;
                    default -> 25.0;
                });
        assertEquals(List.of(2, 3, 1), order, "farthest first, nearest last");
    }

    @Test
    void theOrderIsStableForEqualDistances() {
        // Two NPCs the same distance away must not swap places between frames, which is the very
        // thing this sort exists to stop.
        List<Integer> first = SpeechBubbleOrder.farToNear(List.of(7, 8, 9), id -> 10.0);
        List<Integer> second = SpeechBubbleOrder.farToNear(List.of(7, 8, 9), id -> 10.0);
        assertEquals(first, second);
        assertEquals(List.of(7, 8, 9), first, "input order is preserved on a tie");
    }

    @Test
    void anEmptySetOrdersToNothing() {
        assertTrue(SpeechBubbleOrder.farToNear(List.of(), id -> 0.0).isEmpty());
    }

    @Test
    void theRendererWalksTheSortedListRatherThanTheMap() throws IOException {
        String renderer = code("src/main/java/net/bullettrain/xenopixelsmod/client/npc/speech",
                "SpeechBubbleRenderer.java");
        assertTrue(renderer.contains("SpeechBubbleOrder.farToNear("),
                "the render loop should take an ordered list");
        assertFalse(renderer.contains("active.entrySet()"),
                "unordered iteration is the bug this replaced");
    }

    // ------------------------------------------------------- 3. lines, full override

    @Test
    void anNpcWithNoLinesOfItsOwnFallsBackToTheRoleSet() {
        assertTrue(new NpcCombatProfile().lines.isEmpty());
        assertFalse(new NpcCombatProfile().hasOwnLines());
    }

    @Test
    void anyOwnLineOverridesTheRoleSetEntirely() {
        // Full override, chosen over per-category fallback: one rule is easier to reason about and
        // to show honestly in an editor than six independent ones. The cost is real and is the
        // point of this test - a custom greeting costs the role's combat lines too.
        NpcCombatProfile profile = new NpcCombatProfile();
        profile.setLines(XenoNpcLines.Category.INTERACT, List.of("Hello."));
        assertTrue(profile.hasOwnLines());
        assertEquals(List.of("Hello."), profile.linesFor(XenoNpcLines.Category.INTERACT));
        assertTrue(profile.linesFor(XenoNpcLines.Category.ATTACK).isEmpty(),
                "the role's attack lines are not merged in");
    }

    @Test
    void clearingEveryLineReturnsTheNpcToItsRoleSet() {
        NpcCombatProfile profile = new NpcCombatProfile();
        profile.setLines(XenoNpcLines.Category.INTERACT, List.of("Hello."));
        profile.setLines(XenoNpcLines.Category.INTERACT, List.of());
        assertFalse(profile.hasOwnLines(), "an empty category is not an override");
    }

    @Test
    void linesSurviveASaveAndLoadInOrder() {
        NpcCombatProfile profile = new NpcCombatProfile();
        profile.setLines(XenoNpcLines.Category.RANDOM, List.of("one", "two", "three"));
        NpcCombatProfile read = NpcCombatProfile.fromTag(profile.toTag());
        assertEquals(List.of("one", "two", "three"),
                read.linesFor(XenoNpcLines.Category.RANDOM),
                "authors write these in a deliberate order and a cycler walks them in it");
    }

    @Test
    void tooManyLinesAreTruncatedAndLongOnesCut() {
        // These become entity NBT and a save payload; both need a ceiling.
        NpcCombatProfile profile = new NpcCombatProfile();
        java.util.List<String> many = new java.util.ArrayList<>();
        for (int i = 0; i < NpcCombatProfile.MAX_LINES_PER_CATEGORY + 5; i++) {
            many.add("line " + i);
        }
        profile.setLines(XenoNpcLines.Category.WORLD, many);
        assertEquals(NpcCombatProfile.MAX_LINES_PER_CATEGORY,
                profile.linesFor(XenoNpcLines.Category.WORLD).size());

        profile.setLines(XenoNpcLines.Category.KILL, List.of("x".repeat(9999)));
        assertEquals(NpcCombatProfile.MAX_LINE_LENGTH,
                profile.linesFor(XenoNpcLines.Category.KILL).get(0).length());
    }

    @Test
    void blankLinesAreDropped() {
        // A blank line would show as an empty bubble, which reads as the NPC glitching.
        NpcCombatProfile profile = new NpcCombatProfile();
        profile.setLines(XenoNpcLines.Category.KILLED, java.util.Arrays.asList("real", "  ", null));
        assertEquals(List.of("real"), profile.linesFor(XenoNpcLines.Category.KILLED));
    }

    @Test
    void theSpeechPathChecksTheProfileBeforeTheRole() throws IOException {
        String speech = code("src/main/java/net/bullettrain/xenopixelsmod/npc/lines",
                "XenoNpcSpeech.java");
        assertTrue(speech.contains("hasOwnLines()"),
                "the NPC's own lines must be consulted first");
        int own = speech.indexOf("hasOwnLines()");
        int role = speech.indexOf("XenoNpcLineSets.get(");
        assertTrue(own >= 0 && role > own, "the role set is the fallback, not the first choice");
    }

    // ------------------------------------------------------- 4. per-node palette

    @Test
    void aNodeWithNoPaletteInheritsTheNpcs() throws IOException {
        String dialogue = code("src/main/java/net/bullettrain/xenopixelsmod/npc/dialog",
                "XenoDialogue.java");
        assertTrue(dialogue.contains("palette"), "Node should carry an optional palette");

        String renderer = code("src/main/java/net/bullettrain/xenopixelsmod/client/npc/dialog",
                "DialogueBubbleRenderer.java");
        assertTrue(renderer.contains("isBlank()") || renderer.contains("isEmpty()"),
                "a blank node palette must fall through to the profile's");
    }

    @Test
    void theWhitelistCarriesEveryNewProfileKey() throws IOException {
        // The standing rule: a key goes in only once something reads it. All four are read - three
        // by the renderers, one by XenoNpcSpeech.
        String policy = code("src/main/java/net/bullettrain/xenopixelsmod/network/packet",
                "XenoNpcSavePolicy.java");
        for (String key : new String[]{"BubbleHeight", "BubbleDurationTicks", "BubbleMaxLines",
                "NpcLines"}) {
            assertTrue(policy.contains('"' + key + '"'), key + " should be editable");
        }
    }
}
