package net.bullettrain.xenopixelsmod.npc.importer;

import net.bullettrain.xenopixelsmod.npc.store.XenoNpcStoreCategory;
import net.bullettrain.xenopixelsmod.npc.store.XenoNpcWorldStore;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NpcImportServiceTest {
    @TempDir Path temp;

    @Test
    void allCommandImportPreviewsThenWritesFactionsDialogsAndExecutableQuests() throws Exception {
        Path world = temp.resolve("world");
        Path source = world.resolve("mynpcs");
        Path dialogs = source.resolve("dialogs/town");
        Path quests = source.resolve("quests/town");
        Files.createDirectories(dialogs);
        Files.createDirectories(quests);
        Files.writeString(source.resolve("factions.dat"), "");
        CompoundTag factions = new CompoundTag();
        CompoundTag faction = new CompoundTag();
        faction.putString("Name", "Guards");
        faction.putInt("Color", 0x336699);
        faction.putInt("DefaultPoints", 0);
        faction.putBoolean("GetsAttacked", true);
        ListTag indexed = new ListTag();
        faction.putInt("Slot", 0);
        indexed.add(faction);
        factions.put("Factions", indexed);
        NbtIo.writeCompressed(factions, source.resolve("factions.dat"));

        Files.writeString(dialogs.resolve("1.json"),
                "{DialogText:\"Welcome\",Options:[{Option:{OptionType:1,Title:\"Next\",Dialog:2}}]}");
        Files.writeString(dialogs.resolve("2.json"),
                "{DialogText:\"Goodbye\",Options:[{Option:{OptionType:0,Title:\"Leave\"}}]}");
        Files.writeString(quests.resolve("4.json"), "{Title:\"Defeat Raditz\",Text:\"Help\",Type:2,"
                + "QuestDialogs:[{Slot:\"Raditz\",Value:3}],QuestCompletion:1,RewardExp:40}");

        XenoNpcWorldStore store = new XenoNpcWorldStore(world.resolve("XenoNpcs"));
        store.loadAll();
        NpcImportReport preview = NpcImportService.importAll(world, temp, store, true);
        assertEquals(4, preview.imported());
        assertTrue(store.list(XenoNpcStoreCategory.FACTIONS).isEmpty());
        assertTrue(store.list(XenoNpcStoreCategory.DIALOGS).isEmpty());
        assertTrue(store.list(XenoNpcStoreCategory.QUESTS).isEmpty());

        NpcImportReport imported = NpcImportService.importAll(world, temp, store, false);
        assertEquals(4, imported.imported());
        assertEquals(1, store.list(XenoNpcStoreCategory.FACTIONS).size());
        assertEquals(2, store.list(XenoNpcStoreCategory.DIALOGS).size());
        assertEquals(1, store.list(XenoNpcStoreCategory.QUESTS).size());

        CompoundTag storedQuest = store.list(XenoNpcStoreCategory.QUESTS).get(0).tag();
        assertTrue(storedQuest.getString("DefinitionJson").contains("kill_npc"));
        assertTrue(Files.isRegularFile(store.root().resolve("dialogs/mynpcs_town/dialog_1.json")));
        assertNotNull(store.get(XenoNpcStoreCategory.DIALOGS, "mynpcs_town", "dialog_2"));

        NpcImportReport secondRun = NpcImportService.importAll(world, temp, store, false);
        assertEquals(0, secondRun.imported());
        assertFalse(secondRun.failures().isEmpty());
    }

    @Test
    void malformedFileDoesNotPreventOtherEntriesFromBeingImported() throws Exception {
        Path world = temp.resolve("world");
        Path dialogs = world.resolve("customnpcs/dialogs/town");
        Files.createDirectories(dialogs);
        Files.writeString(dialogs.resolve("1.json"), "not a tag");
        Files.writeString(dialogs.resolve("2.json"), "{DialogText:\"Valid\",Options:[]}");

        XenoNpcWorldStore store = new XenoNpcWorldStore(world.resolve("XenoNpcs"));
        store.loadAll();
        NpcImportReport report = NpcImportService.importAll(world, temp, store, false);

        assertEquals(1, report.imported());
        assertEquals(1, report.failures().size());
        assertNotNull(store.get(XenoNpcStoreCategory.DIALOGS, "customnpcs_town", "dialog_2"));
    }

    @Test
    void mismatchedDialogFileSlotIsRejectedAndReported() throws Exception {
        Path world = temp.resolve("world");
        Path dialogs = world.resolve("mynpcs/dialogs/town");
        Files.createDirectories(dialogs);
        Files.writeString(dialogs.resolve("1.json"), "{DialogId:2,DialogText:\"Wrong slot\",Options:[]}");

        XenoNpcWorldStore store = new XenoNpcWorldStore(world.resolve("XenoNpcs"));
        store.loadAll();
        NpcImportReport report = NpcImportService.importAll(world, temp, store, false);

        assertEquals(0, report.imported());
        assertTrue(report.failures().stream().anyMatch(failure -> failure.reason().contains("does not match filename slot")));
    }
}
