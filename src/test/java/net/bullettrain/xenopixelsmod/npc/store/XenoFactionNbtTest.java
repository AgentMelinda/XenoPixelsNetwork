package net.bullettrain.xenopixelsmod.npc.store;

import net.bullettrain.xenopixelsmod.npc.faction.XenoFaction;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A faction as a store file.
 *
 * <p>Field-for-field with My NPCs' faction entry under our names, so a later import renames rather
 * than restructures. Four of their fields are deliberately not carried — see the class doc — and
 * the schema doc records that so the importer drops them with a note instead of silently.
 */
class XenoFactionNbtTest {

    private static XenoFaction sample() {
        return new XenoFaction("guards", "Guards", 0x3366FF, List.of("bandits", "raiders"),
                -25, true);
    }

    @Test
    void everyCarriedFieldSurvivesTheRoundTrip() {
        XenoFaction got = XenoFactionNbt.read("guards", XenoFactionNbt.write(sample()));
        assertEquals("guards", got.id());
        assertEquals("Guards", got.name());
        assertEquals(0x3366FF, got.color());
        assertEquals(List.of("bandits", "raiders"), got.hostileTo());
        assertEquals(-25, got.defaultStanding());
        assertTrue(got.attackedByMobs());
    }

    @Test
    void mobAggressionRequiresOptInAndKeepsTheExactEntityIds() {
        XenoFaction passive = XenoFactionNbt.read("legacy", XenoFactionNbt.write(sample()));
        assertFalse(passive.aggressiveToMobs());
        assertTrue(passive.attackableMobs().isEmpty());

        XenoFaction configured = new XenoFaction("guards", "Guards", 0x22CCFF,
                List.of(), 0, false, true,
                List.of("minecraft:zombie", "minecraft:skeleton"));
        XenoFaction loaded = XenoFactionNbt.read("guards", XenoFactionNbt.write(configured));
        assertTrue(loaded.aggressiveToMobs());
        assertEquals(List.of("minecraft:zombie", "minecraft:skeleton"),
                loaded.attackableMobs());
    }

    @Test
    void theIdComesFromTheFilenameAndIsNotRepeatedInside() {
        // One fact, one place. A file called guards.json that said Id: bandits inside would have
        // two answers and no rule for which wins.
        CompoundTag tag = XenoFactionNbt.write(sample());
        assertFalse(tag.contains("Id"));
        assertEquals("renamed", XenoFactionNbt.read("renamed", tag).id());
    }

    @Test
    void aHandEditedStandingIsClampedRatherThanTrusted() {
        // Every comparison in XenoFaction assumes the range. A file is editable by hand, so the
        // clamp has to happen on the way in, not only where the value is set.
        CompoundTag tag = XenoFactionNbt.write(sample());
        tag.putInt("DefaultStanding", 999_999);
        assertEquals(XenoFaction.MAX_STANDING,
                XenoFactionNbt.read("guards", tag).defaultStanding());

        tag.putInt("DefaultStanding", -999_999);
        assertEquals(XenoFaction.MIN_STANDING,
                XenoFactionNbt.read("guards", tag).defaultStanding());
    }

    @Test
    void anEmptyTagIsNoFactionRatherThanABlankOne() {
        // Callers fall through to the datapack on null. A blank-but-present faction would shadow
        // the pack's and leave an NPC belonging to nothing.
        assertNull(XenoFactionNbt.read("guards", null));
        assertNull(XenoFactionNbt.read("", new CompoundTag()));
        assertNull(XenoFactionNbt.read(null, new CompoundTag()));
    }

    @Test
    void aTagWithNoNameFallsBackToItsId() {
        assertEquals("guards", XenoFactionNbt.read("guards", new CompoundTag()).name());
    }

    @Test
    void blankHostileEntriesAreDropped() {
        XenoFaction faction = new XenoFaction("guards", "Guards", 0, List.of("", "bandits", "  "),
                0, false);
        assertEquals(List.of("bandits"),
                XenoFactionNbt.read("guards", XenoFactionNbt.write(faction)).hostileTo());
    }

    @Test
    void theFieldsWeDoNotCarryAreAbsentOnPurpose() {
        // FriendlyPoints and NeutralPoints are per-faction thresholds in My NPCs and compile-time
        // constants here. Writing them would be a stored field nothing reads, which is the shape
        // this codebase keeps out — so they belong in the schema doc, not the file.
        CompoundTag tag = XenoFactionNbt.write(sample());
        assertFalse(tag.contains("FriendlyPoints"));
        assertFalse(tag.contains("NeutralPoints"));
        assertFalse(tag.contains("HideFaction"));
        assertFalse(tag.contains("Slot"));
    }
}
