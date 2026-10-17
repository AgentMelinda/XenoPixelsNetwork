package net.bullettrain.xenopixelsmod.combat.v3;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.bullettrain.xenopixelsmod.combat.technique.DmzTechniqueCapacity;
import org.junit.jupiter.api.Test;

class DmzTechniqueCapacityTest {
    @Test
    void capacityLeavesRoomForV3CatalogAndPlayerTechniquesWithoutBeingUnbounded() {
        assertEquals(1_024, DmzTechniqueCapacity.MAX_UNLOCKED);
        assertEquals(722, DmzTechniqueCapacity.MAX_UNLOCKED - 302,
                "the verified V3 catalog leaves room for DMZ and player-created techniques");
    }
}
