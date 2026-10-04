package net.bullettrain.xenopixelsmod.npc.importer;

import net.bullettrain.xenopixelsmod.RepoRoot;
import net.bullettrain.xenopixelsmod.compat.npc.NpcCombatProfile;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Two fixes that only a running game could have found.
 *
 * <p>The first import wrote five factions and the editor went on saying "No factions yet."
 * {@code SyncFactionsPacket} goes out on join and after {@code /reload} and nowhere else, so an
 * operator who imported mid-session had to relog to see their own import — which reads exactly
 * like an import that silently did nothing. Every test in the suite passed while that was true.
 *
 * <p>The second is the ambient-line toggle: an NPC that will not stop talking to itself.
 */
class ImportSyncAndAmbientTest {

    private static String code(String dir, String file) throws IOException {
        return Files.readString(RepoRoot.of(dir, file), StandardCharsets.UTF_8)
                .replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("(?m)//.*$", "");
    }

    // ------------------------------------------------------------ the sync

    @Test
    void aConfirmedCommandImportTellsTheClients() throws IOException {
        String command = code("src/main/java/net/bullettrain/xenopixelsmod/command",
                "NpcImportCommands.java");
        assertTrue(command.contains("SyncFactionsPacket.current()"),
                "otherwise the editor shows an empty list over a store that has entries");
        assertTrue(command.contains("ModNetwork.sendToAll("),
                "factions are world state, so every client needs them, not just the importer");
    }

    @Test
    void aDryRunTellsNobody() throws IOException {
        // It wrote nothing, so a sync would push the unchanged list and make the dry run look
        // like it had taken effect.
        String command = code("src/main/java/net/bullettrain/xenopixelsmod/command",
                "NpcImportCommands.java");
        int guard = command.indexOf("if (!dryRun && report.imported() > 0)");
        int send = command.indexOf("ModNetwork.sendToAll(");
        assertTrue(guard >= 0, "the broadcast must be guarded on having actually written");
        assertTrue(send > guard, "and sit inside that guard");
    }

    // ------------------------------------------------------------ the toggle

    @Test
    void anNpcSpeaksUnpromptedByDefault() {
        // Every NPC did before the field existed; defaulting to off would silence every NPC in
        // every existing world.
        assertTrue(new NpcCombatProfile().ambientLinesEnabled);
        CompoundTag old = new NpcCombatProfile().toTag();
        old.remove("AmbientLines");
        assertTrue(NpcCombatProfile.fromTag(old).ambientLinesEnabled);
    }

    @Test
    void theToggleSurvivesASaveAndLoad() {
        NpcCombatProfile profile = new NpcCombatProfile();
        profile.ambientLinesEnabled = false;
        assertFalse(NpcCombatProfile.fromTag(profile.toTag()).ambientLinesEnabled);
    }

    @Test
    void itGatesOnlyTheUnpromptedPath() throws IOException {
        // An NPC that went silent when hit would read as broken rather than as quiet, so the
        // toggle must sit in ambient() and nowhere near speak().
        String speech = code("src/main/java/net/bullettrain/xenopixelsmod/npc/lines",
                "XenoNpcSpeech.java");
        int ambient = speech.indexOf("public static boolean ambient(");
        int check = speech.indexOf("ambientLinesEnabled");
        int speak = speech.indexOf("public static boolean speak(");
        assertTrue(ambient >= 0 && check > ambient, "the check belongs inside ambient()");
        assertTrue(check < speech.indexOf("AMBIENT_READY.put("),
                "and before the cooldown, so a silenced NPC costs nothing");
        assertTrue(speak < ambient || speech.indexOf("ambientLinesEnabled", speak) > ambient,
                "speak() must not consult it");
    }

    @Test
    void itIsEditableAndWhitelisted() throws IOException {
        assertTrue(code("src/main/java/net/bullettrain/xenopixelsmod/network/packet",
                "XenoNpcSavePolicy.java").contains("\"AmbientLines\""));
        assertTrue(code("src/main/java/net/bullettrain/xenopixelsmod/client/npc",
                "XenoNpcEditorScreen.java").contains("profile.ambientLinesEnabled"));
    }
}
