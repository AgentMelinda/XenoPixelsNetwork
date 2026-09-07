package net.bullettrain.xenopixelsmod.compat.npc;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NpcKiAttackDispatcherTest {
    @Test void supportedIdsAreCaseInsensitiveAndUnknownIdsRejectBeforeCasterAccess() {
        assertTrue(NpcKiAttackDispatcher.supportsPredefinedTechnique("kamehameha"));
        assertTrue(NpcKiAttackDispatcher.supportsPredefinedTechnique("KAMEHAMEHA"));
        assertTrue(NpcKiAttackDispatcher.supportsPredefinedTechnique("supernova_cooler"));
        assertFalse(NpcKiAttackDispatcher.supportsPredefinedTechnique(null));
        assertFalse(NpcKiAttackDispatcher.supportsPredefinedTechnique("custom:unsupported"));
        assertFalse(NpcKiAttackDispatcher.firePredefinedTechnique("custom:unsupported", null,
                new NpcCombatProfile(), 20, null, 0));
    }
}
