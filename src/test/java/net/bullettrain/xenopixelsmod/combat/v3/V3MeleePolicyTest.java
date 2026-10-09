package net.bullettrain.xenopixelsmod.combat.v3;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class V3MeleePolicyTest {
    private static final UUID APPROVED = UUID.randomUUID();
    private static final UUID OTHER = UUID.randomUUID();

    @Test void onlyTheApprovedUuidSurvivesAndOnlyOnce() {
        Map<Integer, UUID> world = Map.of(5, OTHER, 9, APPROVED, 12, OTHER);
        assertArrayEquals(new int[] {9}, V3Melee.approvedOnly(new int[] {5, 9, 12, 9}, world::get, APPROVED));
    }

    @Test void reusedEntityIdWithADifferentUuidIsDropped() {
        Map<Integer, UUID> world = Map.of(9, OTHER);
        assertArrayEquals(new int[0], V3Melee.approvedOnly(new int[] {9}, world::get, APPROVED));
    }

    @Test void missingLockMissingEntitiesAndNullInputHitNobody() {
        Map<Integer, UUID> world = Map.of(9, APPROVED);
        assertArrayEquals(new int[0], V3Melee.approvedOnly(new int[] {9}, world::get, null));
        assertArrayEquals(new int[0], V3Melee.approvedOnly(new int[] {1, 2}, world::get, APPROVED));
        assertArrayEquals(new int[0], V3Melee.approvedOnly(null, world::get, APPROVED));
    }
}
