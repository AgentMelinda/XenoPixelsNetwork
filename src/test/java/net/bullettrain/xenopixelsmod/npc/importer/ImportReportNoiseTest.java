package net.bullettrain.xenopixelsmod.npc.importer;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Keeping the import report worth reading.
 *
 * <p>Written after the first real run. Five factions produced <b>twenty-five notes</b>: one rename
 * each that said only that a capital letter had become lower case, and four identical drop notes
 * repeated per faction. Every line was true and almost none of it was information — and a report
 * nobody reads is the same as no report, which for a migration is the failure that matters.
 *
 * <p>Evidence: {@code [22Sep2026 22:25:56] Imported: 5 imported, 25 note(s), 0 failed}.
 */
class ImportReportNoiseTest {

    private static CompoundTag faction(int slot, String name) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Slot", slot);
        tag.putString("Name", name);
        tag.putInt("FriendlyPoints", 500);
        tag.putInt("NeutralPoints", -500);
        tag.putBoolean("HideFaction", false);
        return tag;
    }

    private static CompoundTag realWorldBlob() {
        // The exact five from the run that prompted this.
        ListTag list = new ListTag();
        String[] names = {"Friendly", "Neutral", "Aggressive", "Npc", "Grindables"};
        for (int i = 0; i < names.length; i++) {
            list.add(faction(i, names[i]));
        }
        CompoundTag root = new CompoundTag();
        root.put("Data", list);
        return root;
    }

    @Test
    void pureLowerCasingIsNotWorthReporting() {
        // "Friendly" -> friendly is not a rename an operator can act on. One that dropped
        // characters or took a collision suffix is.
        SlotIndex index = new SlotIndex();
        index.assign(0, "Friendly");
        assertTrue(index.renames().isEmpty(), "case folding alone should be silent");
    }

    @Test
    void aRenameThatChangesMoreThanCaseIsStillReported() {
        SlotIndex index = new SlotIndex();
        index.assign(0, "The King's Men");
        assertEquals(1, index.renames().size(), "characters were dropped; say so");
        assertTrue(index.renames().get(0).contains("the_kings_men"));
    }

    @Test
    void aCollisionSuffixIsAlwaysReportedEvenIfOnlyCaseDiffered() {
        // Two factions cannot share a file. That one of them is now guards_2 is exactly the kind
        // of thing an operator must know, whatever the original capitalisation was.
        SlotIndex index = new SlotIndex();
        index.assign(0, "Guards");
        index.assign(1, "guards");
        assertEquals(1, index.renames().size());
        assertTrue(index.renames().get(0).contains("guards_2"));
    }

    @Test
    void theFiveFactionRunNowProducesAHandfulOfNotesRatherThanTwentyFive() {
        NpcImportReport report = new NpcImportReport();
        FactionsBlobImport.fanOut(realWorldBlob(), report);

        List<String> notes = report.notes();
        assertTrue(notes.size() <= 5,
                "five factions with identical drops should not produce 25 lines, got: " + notes);
        assertFalse(notes.isEmpty(), "and must not go silent either - the drops are real");
    }

    @Test
    void anAggregatedDropNamesTheFieldAndHowManyItAffected() {
        NpcImportReport report = new NpcImportReport();
        FactionsBlobImport.fanOut(realWorldBlob(), report);

        // HideFaction rather than FriendlyPoints: the thresholds stopped being dropped once the
        // standing conversion started reading them. What is under test is the aggregation, not
        // which field happens to be aggregated.
        assertTrue(report.notes().stream().anyMatch(
                        n -> n.contains("HideFaction") && n.contains("5")),
                "one line per dropped field, counting the entries it touched: " + report.notes());
    }

    @Test
    void anAggregatedNoteStillNamesTheDocThatExplainsIt() {
        NpcImportReport report = new NpcImportReport();
        FactionsBlobImport.fanOut(realWorldBlob(), report);
        assertTrue(report.notes().stream().anyMatch(n -> n.contains("xeno-npc-schema")),
                "the operator should still be able to go and read why");
    }

    @Test
    void aOneOffNoteIsNotHiddenByAggregation() {
        // An unresolved hostility affects one faction and is the single most important thing a
        // faction import can report. It must never be folded away with the boilerplate.
        NpcImportReport report = new NpcImportReport();
        report.noteGrouped("dropped FriendlyPoints", "friendly");
        report.noteGrouped("dropped FriendlyPoints", "neutral");
        report.note("faction guards: dropped hostility toward unknown slot 42");

        assertTrue(report.notes().stream()
                        .anyMatch(n -> n.contains("42")),
                "a singular problem stays visible: " + report.notes());
    }

    @Test
    void groupedNotesCountAndListWhatTheyTouched() {
        NpcImportReport report = new NpcImportReport();
        for (String id : new String[]{"a", "b", "c"}) {
            report.noteGrouped("dropped Slot", id);
        }
        String line = report.notes().stream()
                .filter(n -> n.contains("dropped Slot")).findFirst().orElse("");
        assertTrue(line.contains("3"), "how many: " + line);
        assertTrue(line.contains("a") && line.contains("c"), "and which: " + line);
    }

    @Test
    void aLongGroupIsTruncatedRatherThanListingHundreds() {
        NpcImportReport report = new NpcImportReport();
        for (int i = 0; i < 200; i++) {
            report.noteGrouped("dropped Slot", "faction_" + i);
        }
        String line = report.notes().stream()
                .filter(n -> n.contains("dropped Slot")).findFirst().orElse("");
        assertTrue(line.contains("200"), "the count is the point and must survive: " + line);
        assertTrue(line.length() < 400, "but the list must not be the whole world: " + line);
    }
}
