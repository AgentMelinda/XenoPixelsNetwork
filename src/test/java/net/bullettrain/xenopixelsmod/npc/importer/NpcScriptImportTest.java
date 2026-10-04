package net.bullettrain.xenopixelsmod.npc.importer;

import net.bullettrain.xenopixelsmod.npc.store.XenoNpcScripts;
import net.bullettrain.xenopixelsmod.npc.store.XenoNpcStoreCategory;
import net.bullettrain.xenopixelsmod.npc.store.XenoNpcWorldStore;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NpcScriptImportTest {

    @Test
    void oneScriptBecomesOneStoreEntryNamedAfterTheNpc() {
        NpcImportReport report = new NpcImportReport();
        CompoundTag source = scriptNpc("Dodoria", "forgerite", "log(\"hi\")");

        var drafts = NpcScriptImport.drafts(source, "mynpcs", "abc", report);
        assertEquals(1, drafts.size());
        assertEquals("mynpcs_abc_s1", drafts.get(0).id());
        assertEquals("Dodoria script", drafts.get(0).name());
        assertEquals("forgerite", drafts.get(0).payload().getString("Language"));
        assertEquals("log(\"hi\")", drafts.get(0).payload().getString("Script"));
        assertTrue(drafts.get(0).enabled());
        // The language has no engine in this build, which is a fact to state, not a reason to drop
        // the text or to pretend it is ECMAScript.
        assertTrue(report.notes().stream().anyMatch(n -> n.contains("forgerite")
                && n.contains("no engine")), report.notes().toString());
    }

    @Test
    void severalScriptsBecomeTabsInSourceOrder(@TempDir Path dir) {
        NpcImportReport report = new NpcImportReport();
        CompoundTag source = new CompoundTag();
        source.putString("Name", "Guard");
        ListTag scripts = new ListTag();
        scripts.add(scriptEntry("first"));
        scripts.add(scriptEntry("second"));
        source.put("Scripts", scripts);

        var drafts = NpcScriptImport.drafts(source, "mynpcs", "npc1", report);
        assertEquals(2, drafts.size());
        assertEquals("mynpcs_npc1_s1", drafts.get(0).id());
        assertEquals("mynpcs_npc1_s2", drafts.get(1).id());
        var container = NpcScriptImport.importContainer(source, "mynpcs", "npc1",
                new XenoNpcWorldStore(dir), new NpcImportReport());
        assertEquals(2, container.tabs().size(), "every CNPC script tab must survive the import");
        assertEquals("mynpcs_npc1_s1", container.tabs().get(0).scriptId());
        assertEquals("mynpcs_npc1_s2", container.tabs().get(1).scriptId());
        assertEquals("mynpcs_npc1_s1", container.firstScriptId());
    }

    @Test
    void emptyAndOversizedScriptsAreReportedInsteadOfVanishing() {
        NpcImportReport report = new NpcImportReport();
        CompoundTag source = new CompoundTag();
        ListTag scripts = new ListTag();
        scripts.add(scriptEntry("   "));
        scripts.add(scriptEntry("x".repeat(XenoNpcScripts.MAX_SCRIPT_CHARS + 1)));
        source.put("Scripts", scripts);

        assertTrue(NpcScriptImport.drafts(source, "mynpcs", "npc1", report).isEmpty());
        assertEquals(1, report.failures().size(), report.failures().toString());
        assertTrue(report.failures().get(0).reason().contains("characters"),
                report.failures().toString());
        assertTrue(report.notes().stream().anyMatch(n -> n.contains("empty")),
                report.notes().toString());
    }

    @Test
    void aDisabledNpcStoresItsScriptsUnbound(@TempDir Path dir) {
        NpcImportReport report = new NpcImportReport();
        CompoundTag source = scriptNpc("Dodoria", "ecmascript", "log(1)");
        source.putByte("ScriptEnabled", (byte) 0);

        var container = NpcScriptImport.importContainer(source, "mynpcs", "npc1",
                new XenoNpcWorldStore(dir), report);
        // CustomNPCs' switch is one flag for the whole NPC; the tab is kept, the container is off.
        assertEquals(1, container.tabs().size());
        assertFalse(container.enabled(), "a disabled source NPC must not start running its script");
        assertTrue(report.failures().isEmpty(), report.failures().toString());
    }

    @Test
    void missingStoreIsAFailureNotASilentSkip(@TempDir Path dir) {
        NpcImportReport report = new NpcImportReport();
        CompoundTag source = scriptNpc("Dodoria", "ecmascript", "log(1)");

        assertEquals("", NpcScriptImport.importScripts(source, "mynpcs", "npc1", null, report));
        assertEquals(1, report.failures().size(), report.failures().toString());
        assertTrue(report.failures().get(0).reason().contains("world store"),
                report.failures().toString());
    }

    @Test
    void scriptsRoundTripThroughTheWorldStoreAndSurviveReopening(@TempDir Path dir) {
        NpcImportReport report = new NpcImportReport();
        CompoundTag source = scriptNpc("Dodoria", "ecmascript", "api.setStat('STR', 9001);");
        XenoNpcWorldStore store = new XenoNpcWorldStore(dir);

        String bound = NpcScriptImport.importScripts(source, "mynpcs", "npc1", store, report);

        assertEquals("mynpcs_npc1_s1", bound);
        CompoundTag stored = store.get(XenoNpcStoreCategory.SCRIPTS, XenoNpcScripts.NO_GROUP, bound);
        assertNotNull(stored);
        assertEquals("api.setStat('STR', 9001);", stored.getString("Script"));
        assertEquals("ecmascript", stored.getString("Language"));
        assertEquals(1, report.imported());

        XenoNpcWorldStore reopened = new XenoNpcWorldStore(dir);
        reopened.loadAll();
        assertEquals("api.setStat('STR', 9001);",
                reopened.get(XenoNpcStoreCategory.SCRIPTS, XenoNpcScripts.NO_GROUP, bound)
                        .getString("Script"));
    }

    @Test
    void anExistingXenoEntryIsNeverOverwritten(@TempDir Path dir) {
        XenoNpcWorldStore store = new XenoNpcWorldStore(dir);
        CompoundTag mine = new CompoundTag();
        mine.putString("Name", "Mine");
        mine.putString("Language", "ecmascript");
        mine.putString("Script", "operator text");
        assertNull(store.put(XenoNpcStoreCategory.SCRIPTS, XenoNpcScripts.NO_GROUP,
                "mynpcs_npc1_s1", mine));

        NpcImportReport report = new NpcImportReport();
        String bound = NpcScriptImport.importScripts(scriptNpc("Dodoria", "ecmascript", "imported"),
                "mynpcs", "npc1", store, report);

        assertEquals("", bound);
        assertEquals(1, report.failures().size(), report.failures().toString());
        assertTrue(report.failures().get(0).reason().contains("already exists"),
                report.failures().toString());
        assertEquals("operator text",
                store.get(XenoNpcStoreCategory.SCRIPTS, XenoNpcScripts.NO_GROUP, "mynpcs_npc1_s1")
                        .getString("Script"));
    }

    @Test
    void consumedKeysLeaveTheNativeTagWithoutASecondDeadCopyOfTheScript() {
        CompoundTag tag = scriptNpc("Dodoria", "ecmascript", "log(1)");
        NpcScriptImport.consume(tag);
        assertTrue(!tag.contains("Scripts"));
        assertTrue(!tag.contains("ScriptLanguage"));
        assertTrue(!tag.contains("ScriptEnabled"));
        // Anything else the NPC carried is untouched: the consume is surgical, not a rewrite.
        assertEquals("Dodoria", tag.getString("Name"));
    }

    private static CompoundTag scriptNpc(String name, String language, String text) {
        CompoundTag source = new CompoundTag();
        source.putString("Name", name);
        source.putString("ScriptLanguage", language);
        ListTag scripts = new ListTag();
        scripts.add(scriptEntry(text));
        source.put("Scripts", scripts);
        return source;
    }

    private static CompoundTag scriptEntry(String text) {
        CompoundTag entry = new CompoundTag();
        entry.putString("Script", text);
        entry.putBoolean("Console", false);
        return entry;
    }
}
