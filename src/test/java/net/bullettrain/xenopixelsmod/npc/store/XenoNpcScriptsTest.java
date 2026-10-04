package net.bullettrain.xenopixelsmod.npc.store;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The script store's own rules: what one entry looks like on disk, and what we refuse to write.
 *
 * <p>Scripts are the only category whose payload is source text somebody else will execute, so the
 * bounds here are not tidiness. They are also the reason the category can exist before an engine
 * does: storing and reviewing text is safe, running it is a separate decision.
 */
class XenoNpcScriptsTest {

    private static CompoundTag payload(String name, String language, boolean enabled, String script) {
        CompoundTag tag = new CompoundTag();
        tag.putString("Name", name);
        tag.putString("Language", language);
        tag.putByte("Enabled", (byte) (enabled ? 1 : 0));
        tag.putString("Script", script);
        return tag;
    }

    // ------------------------------------------------------------ the shape on disk

    @Test
    void anEntryRoundTripsThroughTheStoreFileItIsWrittenTo(@TempDir Path dir) throws IOException {
        XenoNpcWorldStore store = new XenoNpcWorldStore(dir);
        assertNull(store.put(XenoNpcStoreCategory.SCRIPTS, XenoNpcScripts.NO_GROUP, "greet",
                payload("Greet", "ecmascript", true, "log.line('hi');\nvar x = 1;")));
        assertTrue(Files.isRegularFile(dir.resolve("scripts/greet.json")),
                "one file per script, so a diff shows one script");

        XenoNpcWorldStore reopened = new XenoNpcWorldStore(dir);
        reopened.loadAll();
        CompoundTag read = reopened.get(XenoNpcStoreCategory.SCRIPTS, "", "greet");
        assertNotNull(read);
        XenoNpcScripts.Script script = XenoNpcScripts.Script.of("greet", read);
        assertEquals("Greet", script.name());
        assertEquals("ecmascript", script.language());
        assertTrue(script.enabled());
        assertEquals("log.line('hi');\nvar x = 1;", script.script());
        assertEquals(List.of("greet"), reopened.list(XenoNpcStoreCategory.SCRIPTS).stream()
                .map(XenoNpcWorldStore.Entry::id).toList());
        assertEquals(List.of(), reopened.loadErrors());
    }

    @Test
    void aScriptRecordWritesTheSameTagItReads() {
        XenoNpcScripts.Script script = new XenoNpcScripts.Script("greet", "Greet", "ecmascript",
                false, "1 + 1");
        assertEquals(script, XenoNpcScripts.Script.of("greet", script.toTag()));
    }

    @Test
    void aHandWrittenFileWithoutTheOptionalKeysStillRuns() {
        // Absent Enabled means enabled, absent Language means the default, absent Name falls back to
        // the id: an operator who edits the JSON by hand should not get a dead entry.
        CompoundTag bare = new CompoundTag();
        bare.putString("Script", "1 + 1");
        XenoNpcScripts.Script script = XenoNpcScripts.Script.of("greet", bare);
        assertEquals("greet", script.name());
        assertEquals(XenoNpcScripts.DEFAULT_LANGUAGE, script.language());
        assertTrue(script.enabled());
    }

    @Test
    void aLanguageIsNormalisedRatherThanStoredInWhateverCaseItArrived() {
        CompoundTag tag = payload("Greet", "  ECMAScript ", true, "1 + 1");
        assertEquals("ecmascript", XenoNpcScripts.Script.of("greet", tag).language());
        CompoundTag disabled = payload("Greet", "ecmascript", false, "1 + 1");
        assertFalse(XenoNpcScripts.Script.of("greet", disabled).enabled());
    }

    // ------------------------------------------------------------ what is refused

    @Test
    void aPayloadWithNothingToRunIsRefusedBeforeItIsStored() {
        assertNull(XenoNpcScripts.rejectPayload(payload("Greet", "ecmascript", true, "1 + 1")));
        assertNotNull(XenoNpcScripts.rejectPayload(null));
        assertNotNull(XenoNpcScripts.rejectPayload(new CompoundTag()), "no Script key at all");
        assertNotNull(XenoNpcScripts.rejectPayload(payload("   ", "ecmascript", true, "1 + 1")),
                "an unnamed script cannot be picked out of a list");
        assertNotNull(XenoNpcScripts.rejectPayload(
                payload("a".repeat(XenoNpcScripts.MAX_NAME_CHARS + 1), "ecmascript", true, "1")),
                "the index label would be truncated later without saying so");
        assertNotNull(XenoNpcScripts.rejectPayload(
                payload("Greet", "x".repeat(XenoNpcScripts.MAX_LANGUAGE_CHARS + 1), true, "1")),
                "a language field carrying a paragraph is a mistake worth catching");
        assertNotNull(XenoNpcScripts.rejectPayload(payload("Greet", "ecmascript", true, "  \n ")),
                "blank source");
        assertNotNull(XenoNpcScripts.rejectPayload(payload("Greet", "ecmascript", true,
                "x".repeat(XenoNpcScripts.MAX_SCRIPT_CHARS + 1))), "past the size cap");
    }

    @Test
    void sourceTextMustBeTextAndNotSomeOtherNbtType() {
        // A list or a number under Script would come back as an empty string from getString() and the
        // operator would watch their script vanish.
        CompoundTag tag = new CompoundTag();
        tag.putString("Name", "Greet");
        tag.putInt("Script", 42);
        assertNotNull(XenoNpcScripts.rejectPayload(tag));
        net.minecraft.nbt.ListTag list = new net.minecraft.nbt.ListTag();
        list.add(IntTag.valueOf(1));
        tag.put("Script", list);
        assertNotNull(XenoNpcScripts.rejectPayload(tag));
    }

    @Test
    void aScriptRightAtTheCapIsAccepted() {
        assertNull(XenoNpcScripts.rejectPayload(payload("Greet", "ecmascript", true,
                "x".repeat(XenoNpcScripts.MAX_SCRIPT_CHARS))), "the cap is inclusive");
    }

    // ------------------------------------------------------------ the category's place

    @Test
    void scriptsAreAnUngroupedCategoryAppendedToTheWireOrder() {
        // Ordinals travel on the wire, so a new category has to go last rather than where it reads
        // best; the existing tests pin the earlier ordinals for the same reason.
        assertEquals("scripts", XenoNpcStoreCategory.SCRIPTS.folder());
        assertFalse(XenoNpcStoreCategory.SCRIPTS.grouped(),
                "an NPC points at one id, so ids must be globally unique");
        // Appended after them since: PLAYER_SCRIPTS, then FORGE_SCRIPTS (2026-09-28).
        assertEquals(XenoNpcStoreCategory.values().length - 3,
                XenoNpcStoreCategory.SCRIPTS.ordinal());
        assertEquals(XenoNpcStoreCategory.values().length - 2,
                XenoNpcStoreCategory.PLAYER_SCRIPTS.ordinal());
        assertEquals(XenoNpcStoreCategory.SCRIPTS,
                XenoNpcStoreCategory.byOrdinal(XenoNpcStoreCategory.SCRIPTS.ordinal()));
        assertTrue(XenoNpcStoreCategory.SCRIPTS.hasRuntimeConsumer(),
                "XenoScriptRunner resolves the id, so the category is not a dead folder");
    }

    @Test
    void theReaderRefusesToInventAnEntryWhenNoStoreIsOpen() {
        // Headless: no world, no store. Every accessor has to say "nothing" instead of throwing,
        // because the server calls these while a world is still loading.
        assertNull(XenoNpcScripts.load("greet"));
        assertNull(XenoNpcScripts.load(null));
        assertNull(XenoNpcScripts.load("  "));
        assertNull(XenoNpcScripts.scriptText("greet"));
        assertEquals(0, XenoNpcScripts.total());
        assertEquals(List.of(), XenoNpcScripts.ids());
        assertEquals("the NPC store is not open",
                XenoNpcScripts.putUnchecked("greet", payload("Greet", "ecmascript", true, "1")));
    }

    @Test
    void anIdThatWouldEscapeTheFolderIsRefusedOnTheWayIn() {
        // Same boundary as every other category: the id becomes a filename.
        assertEquals("the NPC store is not open",
                XenoNpcScripts.putUnchecked("../evil", payload("x", "ecmascript", true, "1")));
    }

    @Test
    void retiringAScriptKeepsTheEntryStorableAndReadable() {
        // Enabled is a switch, not a deletion: a retired script must still save and still load, or
        // "turn it off" would silently mean "lose it". Deciding not to run it is scriptText()'s job.
        XenoNpcScripts.Script off = new XenoNpcScripts.Script("greet", "Greet", "ecmascript",
                false, "1 + 1");
        CompoundTag tag = off.toTag();
        assertNull(XenoNpcScripts.rejectPayload(tag));
        assertFalse(XenoNpcScripts.Script.of("greet", tag).enabled());
    }
}
