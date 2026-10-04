package net.bullettrain.xenopixelsmod.npc.lines;

import net.bullettrain.xenopixelsmod.RepoRoot;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every line category an NPC can be given must have something that says it.
 *
 * <p>All six categories have been loadable from role definitions since lines existed, and
 * {@code XenoNpcSpeech.speak} has always handled any of them - but only three were ever triggered.
 * A pack author writing {@code kill}, {@code random} or {@code world} lines got silence, with no
 * error to explain it. That is the same shape of bug as a stored profile field nothing reads.
 *
 * <p>Source-shape assertions: the triggers are an entity tick and a death event, neither of which
 * runs without a server.
 */
class XenoNpcSpeechTriggersTest {

    private static String source(String relative) throws IOException {
        Path path = RepoRoot.of("src/main/java/net/bullettrain/xenopixelsmod").resolve(relative);
        assertTrue(Files.exists(path), path + " should exist");
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    /** Everywhere a category could be spoken from. */
    private static String allTriggerSources() throws IOException {
        return source("npc/XenoNpcEntity.java")
                + source("npc/lines/XenoNpcSpeech.java")
                + source("compat/npc/NpcProfileLifecycle.java");
    }

    @Test
    void everyCategoryHasATrigger() throws IOException {
        String triggers = allTriggerSources();
        for (XenoNpcLines.Category category : XenoNpcLines.Category.values()) {
            assertTrue(triggers.contains("Category." + category.name()),
                    category + " lines can be authored but nothing ever says them");
        }
    }

    @Test
    void theThreeThatWereSilentAreNamedExplicitly() throws IOException {
        // Guarding the specific regression rather than only the general rule, so a future refactor
        // that drops one of these fails with a useful name.
        String triggers = allTriggerSources();
        assertTrue(triggers.contains("Category.KILL"), "an NPC should speak when it kills");
        assertTrue(triggers.contains("Category.RANDOM"), "idle chatter with a player nearby");
        assertTrue(triggers.contains("Category.WORLD"), "idle chatter with nobody nearby");
    }

    @Test
    void ambientChatterIsRateLimitedRatherThanSaidEveryTick() throws IOException {
        String speech = source("npc/lines/XenoNpcSpeech.java");
        assertTrue(speech.contains("AMBIENT_COOLDOWN"),
                "an NPC repeating itself every tick would be worse than saying nothing");
        assertTrue(speech.contains("AMBIENT_READY.put"),
                "the cooldown must be set whether or not a line came out, or an NPC with no "
                        + "ambient lines is re-checked forever");
    }

    @Test
    void theAmbientCooldownSurvivesNeitherRemovalNorAServerStop() throws IOException {
        // Per-NPC maps keyed by UUID leak if nothing clears them.
        String speech = source("npc/lines/XenoNpcSpeech.java");
        int forget = speech.indexOf("public static void forget(");
        int clearAll = speech.indexOf("public static void clearAll(");
        assertTrue(forget >= 0 && clearAll >= 0, "both cleanup hooks should exist");
        assertTrue(speech.indexOf("AMBIENT_READY.remove", forget) > forget,
                "forget must drop the cooldown");
        assertTrue(speech.indexOf("AMBIENT_READY.clear", clearAll) > clearAll,
                "clearAll must drop the cooldowns");
    }

    @Test
    void theKillLineComesFromTheKillerNotTheVictim() throws IOException {
        // The existing onDeath handler is about the entity that died and bails for anything that
        // is not a CustomNPC. Reusing it would have meant a Xeno NPC never speaking on a kill.
        String lifecycle = source("compat/npc/NpcProfileLifecycle.java");
        int handler = lifecycle.indexOf("onKillSpeech");
        assertTrue(handler >= 0, "the kill line needs its own handler");
        String body = lifecycle.substring(handler, Math.min(lifecycle.length(), handler + 900));
        assertTrue(body.contains("getSource().getEntity()"),
                "it must look at who did the killing");
        assertTrue(body.contains("XenoNpcEntity killer"), "and only speak for our own NPCs");
    }

    @Test
    void anNpcDoesNotCongratulateItselfOnItsOwnDeath() throws IOException {
        String lifecycle = source("compat/npc/NpcProfileLifecycle.java");
        int handler = lifecycle.indexOf("onKillSpeech");
        String body = lifecycle.substring(handler, Math.min(lifecycle.length(), handler + 900));
        assertTrue(body.contains("killer != event.getEntity()"),
                "a self-inflicted death must not count as a kill");
    }
}
