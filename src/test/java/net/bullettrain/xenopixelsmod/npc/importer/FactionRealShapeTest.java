package net.bullettrain.xenopixelsmod.npc.importer;

import net.bullettrain.xenopixelsmod.npc.faction.XenoFaction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The shape My NPCs 1.5.0 actually writes, and what it converts to.
 *
 * <p>Everything asserted here was read out of a real {@code factions.dat} and confirmed against the
 * jar bytecode rather than assumed. The reader had been written against a shape nobody had seen a
 * populated example of, and both halves of it were wrong in a way that still produced
 * plausible-looking output — which is the kind of failure a test has to exist to catch.
 *
 * <p><b>Hostilities.</b> {@code Faction.readNBT} calls
 * {@code NBTTags.getIntegerSet(tag.getList("AttackFactions", 10))}, and that helper is
 * {@code getCompound(i).getInt("Integer")}. The element type is 10 — compound — not int, and
 * {@code ListTag.getInt} answers zero for a compound element, so the previous reader turned every
 * hostility in the file into slot 0.
 *
 * <p><b>Standing.</b> {@code Faction.playerStatus} is {@code points >= friendlyPoints} friendly,
 * {@code points < neutralPoints} hostile, neutral between. Their axis starts at zero with
 * per-faction thresholds; ours is -1000..1000 with fixed ones. Clamping one onto the other changed
 * the attitude of three of the five factions a default install ships.
 */
class FactionRealShapeTest {

    /** One faction, written the way the 1.5.0 jar writes it. */
    private static CompoundTag faction(int slot, String name, int defaultPoints, int... hostileTo) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Slot", slot);
        tag.putString("Name", name);
        tag.putInt("Color", 0x00DD00);
        tag.putInt("DefaultPoints", defaultPoints);
        tag.putInt("NeutralPoints", 500);
        tag.putInt("FriendlyPoints", 1500);
        tag.putBoolean("HideFaction", false);
        tag.putBoolean("GetsAttacked", false);
        ListTag attack = new ListTag();
        for (int other : hostileTo) {
            CompoundTag entry = new CompoundTag();
            entry.putInt("Integer", other);
            attack.add(entry);
        }
        tag.put("AttackFactions", attack);
        return tag;
    }

    private static CompoundTag wrap(CompoundTag... factions) {
        ListTag list = new ListTag();
        for (CompoundTag faction : factions) {
            list.add(faction);
        }
        CompoundTag root = new CompoundTag();
        root.put("NPCFactions", list);
        return root;
    }

    /** The five factions a default My NPCs install ships, in their file order. */
    private static CompoundTag defaultInstall() {
        CompoundTag root = wrap(
                faction(0, "Friendly", 2000, 2),
                faction(1, "Neutral", 1000, 0, 2),
                faction(2, "Aggressive", 0, 1),
                faction(3, "Npc", 1000, 2),
                faction(4, "Grindables", 1000, 2));
        root.putInt("lastID", 4);
        return root;
    }

    private static XenoFaction find(List<XenoFaction> out, String id) {
        return out.stream().filter(f -> f.id().equals(id)).findFirst().orElseThrow();
    }

    // ------------------------------------------------------------ hostilities

    @Test
    void aHostilityIsReadOutOfTheCompoundRatherThanAsZero() {
        // The bug in one line. Aggressive is hostile to slot 1 (Neutral) and to nothing else;
        // reading the list as ints answered 0, making it hostile to Friendly instead.
        List<XenoFaction> out = FactionsBlobImport.fanOut(defaultInstall(), new NpcImportReport());
        assertEquals(List.of("neutral"), find(out, "aggressive").hostileTo());
    }

    @Test
    void everyHostilityInTheFileSurvives() {
        List<XenoFaction> out = FactionsBlobImport.fanOut(defaultInstall(), new NpcImportReport());
        assertEquals(List.of("friendly", "aggressive"), find(out, "neutral").hostileTo());
    }

    @Test
    void anEmptyAttackListIsStillNoHostilities() {
        // A populated file writes element type 10; an empty one writes element type 0. Both have
        // to read as "hostile to nobody" rather than throwing.
        List<XenoFaction> out = FactionsBlobImport.fanOut(
                wrap(faction(0, "Friendly", 2000)), new NpcImportReport());
        assertTrue(out.get(0).hostileTo().isEmpty());
    }

    // ------------------------------------------------------------ standing

    @Test
    void everyDefaultFactionKeepsTheAttitudeItHadOverThere() {
        // The whole point of the conversion. Before it, Neutral came out FRIENDLY and Aggressive
        // came out NEUTRAL, because 1000 and 0 were simply clamped onto our axis.
        List<XenoFaction> out = FactionsBlobImport.fanOut(defaultInstall(), new NpcImportReport());
        assertEquals(XenoFaction.Attitude.FRIENDLY, attitude(out, "friendly"));
        assertEquals(XenoFaction.Attitude.NEUTRAL, attitude(out, "neutral"));
        assertEquals(XenoFaction.Attitude.HOSTILE, attitude(out, "aggressive"));
        assertEquals(XenoFaction.Attitude.NEUTRAL, attitude(out, "npc"));
        assertEquals(XenoFaction.Attitude.NEUTRAL, attitude(out, "grindables"));
    }

    private static XenoFaction.Attitude attitude(List<XenoFaction> out, String id) {
        return XenoFaction.attitudeAt(find(out, id).defaultStanding());
    }

    @Test
    void theirBoundaryValuesLandOnOurs() {
        // Their hostile test is strict (<) and ours is not (<=), so their lowest *neutral* value
        // must not land on HOSTILE_BELOW itself.
        assertEquals(XenoFaction.HOSTILE_BELOW + 1, FactionImport.convertStanding(500, 500, 1500));
        assertEquals(XenoFaction.FRIENDLY_AT, FactionImport.convertStanding(1500, 500, 1500));
        assertEquals(XenoFaction.MIN_STANDING, FactionImport.convertStanding(0, 500, 1500));
    }

    @Test
    void theBandsAreOrderPreserving() {
        // Whatever the mapping does inside a band, a faction their side liked more must not come
        // out liked less over here.
        int previous = Integer.MIN_VALUE;
        for (int points = 0; points <= 3000; points += 25) {
            int standing = FactionImport.convertStanding(points, 500, 1500);
            assertTrue(standing >= previous, "went backwards at " + points);
            previous = standing;
        }
    }

    @Test
    void aFactionWithNoNeutralBandStillConverts() {
        // Misconfigured thresholds must not divide by zero or invert.
        assertEquals(XenoFaction.FRIENDLY_AT, FactionImport.convertStanding(900, 800, 800));
        assertEquals(XenoFaction.HOSTILE_BELOW, FactionImport.convertStanding(700, 800, 800));
    }

    @Test
    void aFactionMissingItsThresholdsFallsBackToTheirDefaults() {
        // An older faction predating the fields should still convert the way the install does.
        CompoundTag tag = faction(0, "Neutral", 1000);
        tag.remove("NeutralPoints");
        tag.remove("FriendlyPoints");
        XenoFaction out = FactionsBlobImport.fanOut(wrap(tag), new NpcImportReport()).get(0);
        assertEquals(XenoFaction.Attitude.NEUTRAL, XenoFaction.attitudeAt(out.defaultStanding()));
    }

    // ------------------------------------------------------------ the report

    @Test
    void theThresholdsAreNoLongerReportedAsDropped() {
        // They are read now. Telling an operator their thresholds were discarded, while quietly
        // using them, is worse than either.
        NpcImportReport report = new NpcImportReport();
        FactionsBlobImport.fanOut(defaultInstall(), report);
        String text = String.join(" | ", report.notes());
        assertTrue(!text.contains("FriendlyPoints") && !text.contains("NeutralPoints"),
                "thresholds are used, not dropped: " + text);
    }
}
