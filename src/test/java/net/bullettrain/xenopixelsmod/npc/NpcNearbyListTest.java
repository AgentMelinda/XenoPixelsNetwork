package net.bullettrain.xenopixelsmod.npc;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class NpcNearbyListTest {
    private static NpcNearbyList.Entry e(int id, String name, double distance) {
        return new NpcNearbyList.Entry(id, name, "humanoid", distance, 1, false);
    }

    @Test
    void entriesAreSortedByDistanceAndFilteredByNameIgnoringCase() {
        List<NpcNearbyList.Entry> all = List.of(e(1, "Flavius Rupilius", 4.8), e(2, "Camilio Azucedona", 3.3),
                e(3, "Goku", 20.0));
        assertEquals(List.of(2, 1, 3), NpcNearbyList.filterSort(all, "").stream().map(NpcNearbyList.Entry::entityId).toList());
        assertEquals(List.of(1), NpcNearbyList.filterSort(all, "  flav ").stream().map(NpcNearbyList.Entry::entityId).toList());
        assertTrue(NpcNearbyList.filterSort(all, "zzz").isEmpty());
    }

    @Test
    void theRowLabelMatchesTheMyNpcsLayout() {
        assertEquals("03.3 : Camilio", NpcNearbyList.label(e(2, "Camilio", 3.3)));
        assertEquals("12.0 : A", NpcNearbyList.label(e(2, "A", 12.0)));
    }

    @Test
    void longNamesAreShortenedForTheRow() {
        String label = NpcNearbyList.label(e(1, "Flavius Rupilius Carbatopusinicus The Great", 4.8));
        assertTrue(label.endsWith("..."));
        assertTrue(label.length() <= NpcNearbyList.MAX_LABEL);
    }
}
