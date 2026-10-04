package net.bullettrain.xenopixelsmod.npc.transport;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Where a transporter NPC can send a player, and who is allowed to see each destination. */
class NpcTransportTest {

    private static TransportDestination dest(String id, TransportDestination.Unlock unlock) {
        return new TransportDestination(id, id, "minecraft:overworld", 10, 64, -10, 90f, unlock);
    }

    @Test
    void aDimensionIsHeldAsANormalisedId() {
        // The same rule trade items follow: a destination in a dimension this server does not
        // have must fail to travel, never land the player somewhere else.
        assertEquals("othermod:moon", new TransportDestination("moon", "Moon", "OtherMod:Moon",
                0, 64, 0, 0, TransportDestination.Unlock.ALWAYS).dimension());
    }

    @Test
    void coordinatesClampToTheWorldBorder() {
        TransportDestination far = new TransportDestination("x", "X", "minecraft:overworld",
                1.0e12, 64, -1.0e12, 0, TransportDestination.Unlock.ALWAYS);
        assertEquals(TransportDestination.MAX_COORDINATE, far.x());
        assertEquals(-TransportDestination.MAX_COORDINATE, far.z());
    }

    @Test
    void aNonFiniteCoordinateBecomesZeroRatherThanPassingEveryCheck() {
        // A NaN would satisfy every range comparison and land the player nowhere at all.
        TransportDestination bad = new TransportDestination("x", "X", "minecraft:overworld",
                Double.NaN, 64, 0, Float.NaN, TransportDestination.Unlock.ALWAYS);
        assertEquals(0.0, bad.x());
        assertEquals(0.0f, bad.yaw());
    }

    @Test
    void aRowMissingAnIdOrDimensionIsNotOffered() {
        assertFalse(new TransportDestination("", "X", "minecraft:overworld", 0, 64, 0, 0,
                TransportDestination.Unlock.ALWAYS).usable());
        assertFalse(new TransportDestination("x", "X", "", 0, 64, 0, 0,
                TransportDestination.Unlock.ALWAYS).usable());
        assertFalse(TransportDestination.empty().usable());
    }

    @Test
    void anUnknownUnlockRuleReadsAsAlwaysRatherThanHidingTheDestination() {
        // A typo in a hand-edited file should cost the rule, not the destination.
        assertEquals(TransportDestination.Unlock.ALWAYS,
                TransportDestination.Unlock.parse("nonsense"));
        assertEquals(TransportDestination.Unlock.VISITED,
                TransportDestination.Unlock.parse("  Visited  "));
    }

    @Test
    void aDestinationSurvivesASaveAndLoad() {
        TransportDestination original = dest("capital", TransportDestination.Unlock.VISITED);
        assertEquals(original, TransportDestination.load(original.save()));
    }

    @Test
    void everybodySeesAnAlwaysDestination() {
        NpcTransportList list = new NpcTransportList();
        list.add(dest("capital", TransportDestination.Unlock.ALWAYS));
        assertEquals(1, list.visibleTo(Set.of()).size());
    }

    @Test
    void onlyAPlayerWhoUnlockedItSeesAVisitedDestination() {
        NpcTransportList list = new NpcTransportList();
        list.add(dest("secret", TransportDestination.Unlock.VISITED));
        assertTrue(list.visibleTo(Set.of()).isEmpty());
        assertEquals(1, list.visibleTo(Set.of("secret")).size());
    }

    @Test
    void anUnfinishedRowIsNeverVisibleHoweverItIsUnlocked() {
        NpcTransportList list = new NpcTransportList();
        list.add(TransportDestination.empty());
        assertTrue(list.visibleTo(Set.of("")).isEmpty());
    }

    @Test
    void orderIsTheOrderAnAuthorTyped() {
        // The list becomes buttons above the NPC; a menu that reshuffled could not be learned.
        NpcTransportList list = new NpcTransportList();
        list.add(dest("c", TransportDestination.Unlock.ALWAYS));
        list.add(dest("a", TransportDestination.Unlock.ALWAYS));
        assertEquals(List.of("c", "a"),
                list.visibleTo(Set.of()).stream().map(TransportDestination::id).toList());
    }

    @Test
    void theListStopsAtWhatCanBeReadAboveAnNpcsHead() {
        NpcTransportList list = new NpcTransportList();
        for (int i = 0; i < NpcTransportList.MAX_DESTINATIONS + 5; i++) {
            list.add(dest("d" + i, TransportDestination.Unlock.ALWAYS));
        }
        assertEquals(NpcTransportList.MAX_DESTINATIONS, list.size());
    }

    @Test
    void aTagClaimingMoreThanTheCapIsTruncatedOnRead() {
        CompoundTag tag = new CompoundTag();
        ListTag oversized = new ListTag();
        for (int i = 0; i < 100; i++) {
            oversized.add(dest("d" + i, TransportDestination.Unlock.ALWAYS).save());
        }
        tag.put("Transports", oversized);

        NpcTransportList read = new NpcTransportList();
        read.loadFrom(tag);
        assertEquals(NpcTransportList.MAX_DESTINATIONS, read.size());
    }

    @Test
    void lookupByIdIsCaseInsensitiveAndMissesSafely() {
        NpcTransportList list = new NpcTransportList();
        list.add(dest("capital", TransportDestination.Unlock.ALWAYS));
        assertEquals("capital", list.byId("CAPITAL").id());
        assertNull(list.byId("nowhere"), "an id this NPC does not offer resolves to nothing");
        assertNull(list.byId(""));
    }

    @Test
    void anOrdinaryNpcWritesNoTransportTag() {
        CompoundTag tag = new CompoundTag();
        new NpcTransportList().saveTo(tag);
        assertTrue(tag.isEmpty());
    }
}
