package net.bullettrain.xenopixelsmod.npc.importer;

import net.bullettrain.xenopixelsmod.npc.faction.XenoFaction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Fanning their single {@code factions.dat} out into our per-entry files.
 *
 * <p>The blob's internal key was never confirmed — every {@code .dat} sampled for the schema doc
 * held an empty list — so the reader finds the entry list by shape and fails loudly on anything
 * else. These pin both halves of that: the shapes it should accept, and the ones it must refuse
 * rather than silently import as nothing.
 */
class FactionsBlobImportTest {

    private static CompoundTag faction(int slot, String name) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Slot", slot);
        tag.putString("Name", name);
        tag.putInt("Color", 0xFFFFFF);
        return tag;
    }

    private static CompoundTag blob(String key, CompoundTag... factions) {
        ListTag list = new ListTag();
        for (CompoundTag faction : factions) {
            list.add(faction);
        }
        CompoundTag root = new CompoundTag();
        root.put(key, list);
        return root;
    }

    @Test
    void theEntryListIsFoundWhateverItIsCalled() {
        // The key was never confirmed against a real file, so the reader must not depend on it.
        for (String key : new String[]{"Data", "Factions", "List", "whatever"}) {
            List<XenoFaction> out = FactionsBlobImport.fanOut(
                    blob(key, faction(0, "Guards")), new NpcImportReport());
            assertEquals(1, out.size(), "key " + key);
            assertEquals("guards", out.get(0).id());
        }
    }

    @Test
    void anEmptyListImportsNothingAndIsNotAnError() {
        // Every .dat sampled for the schema doc was exactly this. A world with no factions is a
        // normal world, not a failure.
        assertTrue(FactionsBlobImport.fanOut(blob("Data"), new NpcImportReport()).isEmpty());
    }

    @Test
    void aBlobWithNoListAtAllIsRefusedAndNamesWhatItFound() {
        // Refusing loudly matters more than usual here: importing nothing looks exactly like a
        // world that had no factions, so a silent miss would never be noticed.
        CompoundTag odd = new CompoundTag();
        odd.putString("Version", "1.5.0");
        odd.putInt("Count", 3);

        FactionsBlobImport.UnknownShape failure = assertThrows(
                FactionsBlobImport.UnknownShape.class,
                () -> FactionsBlobImport.fanOut(odd, new NpcImportReport()));
        assertTrue(failure.getMessage().contains("Version"), "it should name the keys it saw");
        assertTrue(failure.getMessage().contains("Count"));
        assertTrue(failure.getMessage().contains("report"), "and ask for the real shape");
    }

    @Test
    void twoCandidateListsAreRefusedRatherThanGuessedBetween() {
        CompoundTag two = new CompoundTag();
        two.put("Data", new ListTag());
        ListTag other = new ListTag();
        other.add(new CompoundTag());
        two.put("Extra", other);

        assertThrows(FactionsBlobImport.UnknownShape.class,
                () -> FactionsBlobImport.fanOut(two, new NpcImportReport()));
    }

    @Test
    void aListOfSomethingOtherThanCompoundsIsNotMistakenForEntries() {
        CompoundTag odd = new CompoundTag();
        ListTag ints = new ListTag();
        ints.add(IntTag.valueOf(1));
        odd.put("Slots", ints);

        assertThrows(FactionsBlobImport.UnknownShape.class,
                () -> FactionsBlobImport.fanOut(odd, new NpcImportReport()));
    }

    @Test
    void anEntryWithoutASlotFallsBackToItsPosition() {
        CompoundTag first = new CompoundTag();
        first.putString("Name", "Guards");
        CompoundTag second = new CompoundTag();
        second.putString("Name", "Bandits");

        List<XenoFaction> out = FactionsBlobImport.fanOut(
                blob("Data", first, second), new NpcImportReport());
        assertEquals(List.of("guards", "bandits"), out.stream().map(XenoFaction::id).toList());
    }

    @Test
    void aForwardHostilityResolves() {
        // Two passes exist for exactly this: Guards is hostile to Bandits, which appears after it.
        // A single pass would drop it and report an unknown slot.
        CompoundTag guards = faction(0, "Guards");
        ListTag attacks = new ListTag();
        attacks.add(IntTag.valueOf(1));
        guards.put("AttackFactions", attacks);

        List<XenoFaction> out = FactionsBlobImport.fanOut(
                blob("Data", guards, faction(1, "Bandits")), new NpcImportReport());
        assertEquals(List.of("bandits"), out.get(0).hostileTo());
    }

    @Test
    void oneBadEntryCostsOneEntry() {
        // A blob is already a single point of failure; letting one bad faction take the other
        // forty would make that worse.
        CompoundTag good = faction(0, "Guards");
        CompoundTag broken = new CompoundTag();
        broken.putInt("Slot", 1);
        broken.putString("Name", "Bandits");
        broken.putString("Color", "not an int");

        NpcImportReport report = new NpcImportReport();
        List<XenoFaction> out = FactionsBlobImport.fanOut(
                blob("Data", good, broken), report);
        assertTrue(out.stream().anyMatch(f -> f.id().equals("guards")),
                "the good one still arrives");
    }

    @Test
    void renamesAreReportedOncePerFaction() {
        NpcImportReport report = new NpcImportReport();
        FactionsBlobImport.fanOut(blob("Data", faction(0, "The King's Men")), report);
        assertTrue(report.notes().stream().anyMatch(n -> n.contains("The King's Men")),
                "the operator is told what their faction is now called");
    }
}
