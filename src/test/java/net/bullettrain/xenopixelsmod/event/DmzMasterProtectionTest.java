package net.bullettrain.xenopixelsmod.event;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DmzMasterProtectionTest {
    @Test
    void onlyMasterRegistryIdsMatchCleanupClassification() {
        assertTrue(DmzMasterProtection.isDmzMasterTypeId("dragonminez:master_goku"));
        assertTrue(DmzMasterProtection.isDmzMasterTypeId("dragonminez:master_roshi"));
        assertFalse(DmzMasterProtection.isDmzMasterTypeId("dragonminez:saga_raditz"));
        assertFalse(DmzMasterProtection.isDmzMasterTypeId("dragonminez:saga_vegeta"));
        assertFalse(DmzMasterProtection.isDmzMasterTypeId("minecraft:villager"));
    }
}
