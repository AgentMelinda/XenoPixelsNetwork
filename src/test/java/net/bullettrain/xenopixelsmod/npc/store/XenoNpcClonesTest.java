package net.bullettrain.xenopixelsmod.npc.store;

import net.bullettrain.xenopixelsmod.RepoRoot;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The saved-clone library, and the reservation it retires.
 *
 * <p>Saving and placing need a world store and a live entity, so what is checked here is the tab
 * arithmetic — which is pure — and the wiring that decides whether the category is genuinely read
 * rather than merely written.
 */
class XenoNpcClonesTest {

    private static final String STORE = "src/main/java/net/bullettrain/xenopixelsmod/npc/store";
    private static final String COMMAND = "src/main/java/net/bullettrain/xenopixelsmod/command";
    private static final String CUSTOM = "src/main/java/net/bullettrain/xenopixelsmod/item/custom";

    private static String code(String dir, String file) throws IOException {
        return Files.readString(RepoRoot.of(dir, file), StandardCharsets.UTF_8)
                .replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("(?m)//.*$", "");
    }

    // ------------------------------------------------------------ tabs

    @Test
    void thereAreNineTabsAsTheirsHas() {
        assertEquals(1, XenoNpcClones.MIN_TAB);
        assertEquals(9, XenoNpcClones.MAX_TAB);
    }

    @Test
    void aTabOutsideTheRangeLandsOnAValidOneRatherThanWritingNowhere() {
        // The group folder is the tab number, so an unclamped value would write to a directory
        // nothing lists - a save that reports success and cannot be found.
        assertEquals(1, XenoNpcClones.clampTab(0));
        assertEquals(1, XenoNpcClones.clampTab(-7));
        assertEquals(9, XenoNpcClones.clampTab(99));
        assertEquals(5, XenoNpcClones.clampTab(5));
    }

    @Test
    void theGroupFolderIsThePlainTabNumber() {
        // It becomes a directory name - clones/<tab>/<id>.json - so anything fancier would have to
        // survive XenoNpcStorePaths' own validation for no gain.
        assertEquals("3", XenoNpcClones.group(3));
        assertEquals("1", XenoNpcClones.group(0));
        assertEquals("9", XenoNpcClones.group(1000));
    }

    @Test
    void aTabIsBoundedSoItStaysReadable() {
        assertTrue(XenoNpcClones.MAX_PER_TAB > 0);
        // Nine tabs at the cap is the most the library can hold; a number that made the whole
        // library unlistable in chat would defeat the command surface it is authored through.
        assertTrue(XenoNpcClones.MAX_PER_TAB <= 128);
    }

    // ------------------------------------------------------------ the reservation

    @Test
    void theClonesCategoryIsNoLongerReserved() throws IOException {
        // It carried "Reserved, no reader" from the day the store was written. Something can spend
        // a clone now, which is exactly what the note said would unblock it.
        String categories = Files.readString(
                RepoRoot.of(STORE, "XenoNpcStoreCategory.java"), StandardCharsets.UTF_8);
        int clones = categories.indexOf("CLONES(\"clones\"");
        assertTrue(clones > 0);
        String javadoc = categories.substring(Math.max(0, clones - 700), clones);
        assertFalse(javadoc.contains("Reserved, no reader"),
                "CLONES has a reader now; its reservation must be retired rather than left as a "
                        + "promise the code no longer keeps");
    }

    @Test
    void somethingCanActuallySpendAClone() throws IOException {
        // The half that made the reservation real. Writing templates nothing reads is what the
        // note was warning against.
        String commands = code(COMMAND, "XenoNpcCloneCommands.java");
        assertTrue(commands.contains("XenoNpcClones.load("), "the library must be read");
        assertTrue(commands.contains("addFreshEntity"), "and a clone turned into a live NPC");
    }

    @Test
    void theLibraryAndTheItemShareOnePayloadFormat() throws IOException {
        // Two ideas of "what a saved NPC is" would drift, and the drift shows up as a template
        // placing an NPC subtly unlike the one it was saved from.
        assertTrue(code(STORE, "XenoNpcClones.java").contains("XenoNpcData.editorPayload"),
                "the library saves the editor payload");
        assertTrue(code(COMMAND, "XenoNpcCloneCommands.java").contains("XenoNpcPayload.apply"),
                "and places it through the same restore the Cloner item uses");
        assertTrue(code(CUSTOM, "XenoNpcClonerItem.java").contains("XenoNpcPayload"),
                "which is the item's own path too");
    }

    @Test
    void aSavedCloneIsBoundedTheSameWayACarriedOneIs() throws IOException {
        // An unbounded template is an unbounded file on disk and an unbounded allocation on load.
        String library = code(STORE, "XenoNpcClones.java");
        assertTrue(library.contains("MAX_BYTES"), "a saved template must be size-bounded");
        assertTrue(library.contains("NpcSlotStack.tagBytes"),
                "measured the same way the item measures it");
    }

    @Test
    void aFullTabRefusesRatherThanGrowing() throws IOException {
        String library = code(STORE, "XenoNpcClones.java");
        assertTrue(library.contains("MAX_PER_TAB"), "the cap must be enforced, not just declared");
        // Replacing an existing id is an overwrite rather than growth, so it stays allowed even at
        // the cap - otherwise a full tab could never be corrected.
        assertTrue(library.contains("store.get(XenoNpcStoreCategory.CLONES, folder, id) == null"),
                "an overwrite must not be refused for being at the cap");
    }

    @Test
    void savingSyncsTheIndexSoTheClientCanSeeIt() throws IOException {
        // The editor's pickers read the synced index; a save nobody broadcasts is a template that
        // does not exist until the client reconnects.
        assertTrue(code(COMMAND, "XenoNpcCloneCommands.java")
                        .contains("SyncNpcStoreIndexPacket.current()"),
                "a store write must push the index");
    }

    @Test
    void placingFromTheLibraryMakesANewNpcRatherThanRestoringAnOldOne() throws IOException {
        // keepOwner false, as the Cloner does: a template is a pattern, and an NPC stamped from one
        // belongs to whoever stamped it rather than to whoever the template was saved from.
        assertTrue(code(COMMAND, "XenoNpcCloneCommands.java").contains("x, y, z, false"),
                "a library placement takes a new owner");
    }
}
