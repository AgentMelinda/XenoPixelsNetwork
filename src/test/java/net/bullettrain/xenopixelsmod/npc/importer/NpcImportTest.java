package net.bullettrain.xenopixelsmod.npc.importer;

import net.bullettrain.xenopixelsmod.npc.faction.XenoFaction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.IntTag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reading My NPCs / CustomNPCs content off disk.
 *
 * <p>Implements {@code docs/superpowers/specs/2026-09-22-npc-importer-design.md}. Everything here
 * is pure: no server, no foreign mod, and deliberately no foreign class. An operator migrating
 * away from My NPCs will not keep it installed to be migrated away from, so an importer that needs
 * it on the classpath is an importer that cannot run when it is wanted.
 */
class NpcImportTest {

    // ------------------------------------------------------------ their dialect

    @Test
    void anOrdinaryTagParses() {
        CompoundTag tag = ForeignSnbt.parse("{Name:\"Guards\",Color:16711680}");
        assertEquals("Guards", tag.getString("Name"));
        assertEquals(16711680, tag.getInt("Color"));
    }

    @Test
    void rawNewlinesSurviveTheParser() {
        // Their real content files carry unescaped newlines inside quoted strings. The schema doc
        // warned TagParser might reject them; tested against NeoForge 21.1.248 it does not, and
        // hands the newline back intact. Pinned so a future version that tightens this is caught
        // here rather than in somebody's migration.
        CompoundTag tag = ForeignSnbt.parse("{Text:\"first\nsecond\"}");
        assertEquals("first\nsecond", tag.getString("Text"),
                "the newline survives as a newline, not as a broken parse");
    }

    @Test
    void aCarriageReturnIsTreatedTheSameWay() {
        CompoundTag tag = ForeignSnbt.parse("{Text:\"first\r\nsecond\"}");
        assertTrue(tag.getString("Text").contains("\n"));
    }

    @Test
    void anEscapedQuoteStillEndsNothing() {
        // The escaping pass has to track quote state, and a \" must not be read as a close.
        CompoundTag tag = ForeignSnbt.parse("{Text:\"he said \\\"no\\\"\nthen left\"}");
        assertTrue(tag.getString("Text").startsWith("he said \"no\""));
        assertTrue(tag.getString("Text").endsWith("then left"));
    }

    @Test
    void aNewlineOutsideAStringIsLeftAlone() {
        // Pretty-printed SNBT is full of them and they are not the problem being solved.
        CompoundTag tag = ForeignSnbt.parse("{\n  Name: \"Guards\"\n}");
        assertEquals("Guards", tag.getString("Name"));
    }

    @Test
    void somethingThatIsNotSnbtAtAllFailsLoudly() {
        // Reported per file so one bad entry costs one entry, never the import.
        assertThrows(ForeignSnbt.ParseFailure.class, () -> ForeignSnbt.parse("this is not a tag"));
        assertThrows(ForeignSnbt.ParseFailure.class, () -> ForeignSnbt.parse(""));
        assertThrows(ForeignSnbt.ParseFailure.class, () -> ForeignSnbt.parse(null));
    }

    // ------------------------------------------------------------ slot to id

    @Test
    void aNameBecomesALowerCaseId() {
        SlotIndex index = new SlotIndex();
        assertEquals("guards", index.assign(0, "Guards"));
    }

    @Test
    void illegalCharactersCollapseIntoSingleSeparators() {
        SlotIndex index = new SlotIndex();
        assertEquals("the_kings_men", index.assign(0, "The King's  Men!"));
    }

    @Test
    void leadingAndTrailingSeparatorsAreTrimmed() {
        // The store rejects a leading dot or dash and a trailing dot, so producing one would make
        // an import that writes files the store then refuses to read.
        SlotIndex index = new SlotIndex();
        assertEquals("guards", index.assign(0, "...Guards..."));
        assertEquals("watch", index.assign(1, "--Watch--"));
    }

    @Test
    void twoNamesThatCollideGetDistinctIds() {
        // NTFS treats Guards and guards as one file and ext4 as two. Lower-casing both makes the
        // collision explicit here rather than leaving a world that behaves differently per
        // filesystem.
        SlotIndex index = new SlotIndex();
        assertEquals("guards", index.assign(0, "Guards"));
        assertEquals("guards_2", index.assign(1, "guards"));
        assertEquals("guards_3", index.assign(2, "GUARDS"));
    }

    @Test
    void anOverLongNameIsTruncatedAndStillUnique() {
        SlotIndex index = new SlotIndex();
        String long1 = index.assign(0, "x".repeat(200));
        String long2 = index.assign(1, "x".repeat(200));
        assertTrue(long1.length() <= 64);
        assertTrue(long2.length() <= 64);
        assertNotEquals(long1, long2);
    }

    @Test
    void aNameThatReducesToNothingFallsBackToItsSlot() {
        // Stable and traceable, rather than random: the operator can find it by slot.
        SlotIndex index = new SlotIndex();
        assertEquals("faction_7", index.assign(7, "!!!"));
        assertEquals("faction_8", index.assign(8, ""));
        assertEquals("faction_9", index.assign(9, null));
    }

    @Test
    void aReservedWindowsDeviceNameIsAvoided() {
        // con.json cannot be created on Windows at all; an import that produced one would fail
        // there and succeed on Linux.
        SlotIndex index = new SlotIndex();
        String id = index.assign(0, "CON");
        assertNotEquals("con", id);
        assertTrue(id.startsWith("con"));
    }

    @Test
    void aSlotResolvesBackToItsId() {
        SlotIndex index = new SlotIndex();
        index.assign(3, "Guards");
        assertEquals("guards", index.idFor(3));
        assertEquals(null, index.idFor(99), "an unknown slot resolves to nothing, not to a guess");
    }

    // ------------------------------------------------------------ factions

    private static CompoundTag theirFaction(String name, int color, int points) {
        CompoundTag tag = new CompoundTag();
        tag.putString("Name", name);
        tag.putInt("Color", color);
        tag.putInt("DefaultPoints", points);
        tag.putBoolean("GetsAttacked", true);
        return tag;
    }

    @Test
    void aFactionCarriesTheFourFieldsWeKeep() {
        SlotIndex index = new SlotIndex();
        index.assign(0, "Guards");
        NpcImportReport report = new NpcImportReport();

        XenoFaction faction = FactionImport.convert(0, theirFaction("Guards", 0xFF0000, 250),
                index, report);
        assertEquals("guards", faction.id());
        assertEquals("Guards", faction.name());
        assertEquals(0xFF0000, faction.color());
        // Standing is converted rather than copied. 250 of their points sits below their default
        // neutral threshold of 500, so it is a faction that attacks on sight over there and has to
        // be one here too - which a straight copy of the number did not produce. The exact
        // arithmetic is pinned in FactionRealShapeTest; what matters here is that the field
        // arrived at all, and arrived hostile.
        assertEquals(FactionImport.convertStanding(250, 500, 1500), faction.defaultStanding());
        assertEquals(XenoFaction.Attitude.HOSTILE,
                XenoFaction.attitudeAt(faction.defaultStanding()));
    }

    @Test
    void aStandingPastTheLimitClamps() {
        SlotIndex index = new SlotIndex();
        index.assign(0, "Guards");
        XenoFaction faction = FactionImport.convert(0,
                theirFaction("Guards", 0, 999_999), index, new NpcImportReport());
        assertEquals(XenoFaction.MAX_STANDING, faction.defaultStanding());
    }

    @Test
    void hostilitySlotsResolveThroughTheIndex() {
        SlotIndex index = new SlotIndex();
        index.assign(0, "Guards");
        index.assign(1, "Bandits");

        CompoundTag tag = theirFaction("Guards", 0, 0);
        ListTag attacks = new ListTag();
        attacks.add(IntTag.valueOf(1));
        tag.put("AttackFactions", attacks);

        XenoFaction faction = FactionImport.convert(0, tag, index, new NpcImportReport());
        assertEquals(List.of("bandits"), faction.hostileTo(),
                "a slot becomes our id, not a number");
    }

    @Test
    void hostilityTowardsAnUnknownSlotIsDroppedAndReported() {
        // It names a faction that was not in the file. Inventing an id for it would produce a
        // hostility toward something that does not exist.
        SlotIndex index = new SlotIndex();
        index.assign(0, "Guards");

        CompoundTag tag = theirFaction("Guards", 0, 0);
        ListTag attacks = new ListTag();
        attacks.add(IntTag.valueOf(42));
        tag.put("AttackFactions", attacks);

        NpcImportReport report = new NpcImportReport();
        XenoFaction faction = FactionImport.convert(0, tag, index, report);
        assertTrue(faction.hostileTo().isEmpty());
        assertTrue(report.notes().stream().anyMatch(n -> n.contains("42")),
                "the dropped slot is named, so an operator can go and look");
    }

    @Test
    void anIntArrayOfHostilitiesIsAlsoAccepted() {
        // Their list shows up as both a ListTag of ints and an IntArrayTag across versions.
        SlotIndex index = new SlotIndex();
        index.assign(0, "Guards");
        index.assign(1, "Bandits");

        CompoundTag tag = theirFaction("Guards", 0, 0);
        tag.put("AttackFactions", new IntArrayTag(new int[]{1}));

        XenoFaction faction = FactionImport.convert(0, tag, index, new NpcImportReport());
        assertEquals(List.of("bandits"), faction.hostileTo());
    }

    @Test
    void theDocumentedKeysAreDroppedAndEachIsReported() {
        SlotIndex index = new SlotIndex();
        index.assign(0, "Guards");

        CompoundTag tag = theirFaction("Guards", 0, 0);
        tag.putInt("FriendlyPoints", 1500);
        tag.putInt("NeutralPoints", 500);
        tag.putBoolean("HideFaction", true);
        tag.putInt("Slot", 0);

        NpcImportReport report = new NpcImportReport();
        FactionImport.convert(0, tag, index, report);
        for (String dropped : new String[]{"HideFaction", "Slot"}) {
            assertTrue(report.notes().stream().anyMatch(n -> n.contains(dropped)),
                    dropped + " should be reported, not silently discarded");
        }
        // FriendlyPoints and NeutralPoints used to be on that list. They are the two thresholds
        // their own playerStatus compares against, so they are what makes a standing convertible
        // at all - reporting them as discarded while reading them would be the worse of both.
        for (String kept : new String[]{"FriendlyPoints", "NeutralPoints"}) {
            assertTrue(report.notes().stream().noneMatch(n -> n.contains(kept)),
                    kept + " is read now and must not be reported as dropped");
        }
    }

    @Test
    void aRenameIsReportedNextToTheOriginalName() {
        // A silent rename is how an operator's next lookup misses.
        SlotIndex index = new SlotIndex();
        index.assign(0, "The King's Men");
        assertTrue(index.renames().stream()
                        .anyMatch(n -> n.contains("The King's Men") && n.contains("the_kings_men")),
                "both the name they typed and the id we derived");
    }

    @Test
    void theReportCountsWhatHappened() {
        NpcImportReport report = new NpcImportReport();
        assertEquals(0, report.imported());
        report.imported("factions", "guards");
        report.imported("factions", "bandits");
        report.failed("factions", "broken", "not a tag");
        assertEquals(2, report.imported());
        assertEquals(1, report.failures().size());
        assertFalse(report.summary().isBlank());
    }

    // ------------------------------------------------------------ the constraint

    @Test
    void theImporterNamesNoForeignClass() throws Exception {
        // The whole point: an operator migrating away from My NPCs will not keep it installed to
        // be migrated away from. NpcWorldMigrator reflects espi.mynpcs.controllers.
        // ServerCloneController, which is correct for a converter that runs alongside it and wrong
        // for this.
        for (String file : new String[]{"ForeignSnbt.java", "SlotIndex.java", "FactionImport.java",
                "ForeignNpcRoots.java", "NpcImportReport.java"}) {
            // Comments are stripped: naming the class in a javadoc to explain why it is NOT used
            // is exactly the documentation this rule deserves, and must not trip its own test.
            String source = java.nio.file.Files.readString(
                    net.bullettrain.xenopixelsmod.RepoRoot.of(
                            "src/main/java/net/bullettrain/xenopixelsmod/npc/importer", file),
                    java.nio.charset.StandardCharsets.UTF_8)
                    .replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("(?m)//.*$", "");
            assertFalse(source.contains("espi.mynpcs"), file + " must not name a My NPCs class");
            assertFalse(source.contains("noppes"), file + " must not name a CustomNPCs class");
            assertFalse(source.contains("Class.forName"), file + " must not reflect one either");
        }
    }
}
