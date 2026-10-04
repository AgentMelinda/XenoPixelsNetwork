package net.bullettrain.xenopixelsmod.command;

import net.bullettrain.xenopixelsmod.RepoRoot;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The operator-facing half of the importer.
 *
 * <p>Registering a Brigadier command needs a server, so these are source-level. Each pins a rule
 * whose absence would not fail a build and would matter a great deal to whoever runs it on a world
 * they care about.
 */
class NpcImportCommandsTest {

    private static String code(String dir, String file) throws IOException {
        return Files.readString(RepoRoot.of(dir, file), StandardCharsets.UTF_8)
                .replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("(?m)//.*$", "");
    }

    @Test
    void dryRunIsTheDefaultAndWritingNeedsConfirm() throws IOException {
        // A migration that has overwritten the destination before the operator has seen its plan
        // is a migration with no undo.
        String command = code("src/main/java/net/bullettrain/xenopixelsmod/command",
                "NpcImportCommands.java");
        int bare = command.indexOf(".executes(context -> run(context.getSource(), true, false))");
        int confirm = command.indexOf("Commands.literal(\"confirm\")");
        assertTrue(bare >= 0, "the bare command must be the dry run");
        assertTrue(confirm > bare, "and writing must sit behind an explicit confirm");
        assertTrue(command.contains("run(context.getSource(), false, false)"),
                "only the confirm branch writes");
        assertTrue(command.contains("Commands.literal(\"all\")"),
                "one command covers shared data and placed NPCs");
    }

    @Test
    void itIsOperatorGated() throws IOException {
        assertTrue(code("src/main/java/net/bullettrain/xenopixelsmod/command",
                        "NpcImportCommands.java").contains("hasPermission(2)"),
                "reading another mod's files and writing the world store is an operator action");
    }

    @Test
    void bothRootsArePassed() throws IOException {
        // Clones live in the game directory rather than the world folder in the 1.5.0 install, so
        // a single-root call finds the authored content and silently misses every clone.
        String command = code("src/main/java/net/bullettrain/xenopixelsmod/command",
                "NpcImportCommands.java");
        assertTrue(command.contains("getWorldPath("));
        assertTrue(command.contains("getServerDirectory()"));
    }

    @Test
    void theWholeReportReachesTheLogEvenWhenChatIsTruncated() throws IOException {
        // Chat is capped so an import of forty factions does not flood it; the log is not, so
        // nothing is lost to a scroll.
        String command = code("src/main/java/net/bullettrain/xenopixelsmod/command",
                "NpcImportCommands.java");
        assertTrue(command.contains("MAX_CHAT_LINES"), "chat should be bounded");
        assertTrue(command.contains("LOGGER.info(\"NPC import note"), "and the log should not be");
        assertTrue(command.contains("LOGGER.warn(\"NPC import failure"));
    }

    @Test
    void aClosedStoreIsRefusedRatherThanCrashed() throws IOException {
        String command = code("src/main/java/net/bullettrain/xenopixelsmod/command",
                "NpcImportCommands.java");
        assertTrue(command.contains("store == null"),
                "the store is null before a world is open, and that is a refusal, not a crash");
    }

    @Test
    void theServiceImportsFactionsDialogsAndExecutableQuestShapes() throws IOException {
        String service = code("src/main/java/net/bullettrain/xenopixelsmod/npc/importer",
                "NpcImportService.java");
        assertTrue(service.contains("XenoNpcStoreCategory.FACTIONS"));
        assertTrue(service.contains("XenoNpcStoreCategory.DIALOGS"));
        assertTrue(service.contains("XenoNpcStoreCategory.QUESTS"));
        assertTrue(service.contains("NpcWorldDataConverter"),
                "CustomNPC structured content should reuse the existing conversion logic");
    }

    @Test
    void theBlobIsSizeCappedBeforeItIsRead() throws IOException {
        // It is another mod's file and may be anything at all; an uncapped read of it is an
        // uncapped allocation.
        String service = code("src/main/java/net/bullettrain/xenopixelsmod/npc/importer",
                "NpcImportService.java");
        assertTrue(service.contains("MAX_BLOB_BYTES"));
        assertTrue(service.contains("NbtAccounter.create("),
                "the accounter bounds the parse as well as the file size");
    }

    @Test
    void oneDamagedRootDoesNotHideAHealthyOne() throws IOException {
        String service = code("src/main/java/net/bullettrain/xenopixelsmod/npc/importer",
                "NpcImportService.java");
        int loop = service.indexOf("for (ForeignNpcRoots.Root root : roots)");
        int katch = service.indexOf("catch (IOException | RuntimeException e)", loop);
        assertTrue(loop >= 0 && katch > loop, "the failure is caught inside the per-root loop");
    }
}
