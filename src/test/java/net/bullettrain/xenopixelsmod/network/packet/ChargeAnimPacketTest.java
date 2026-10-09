package net.bullettrain.xenopixelsmod.network.packet;

import net.bullettrain.xenopixelsmod.combat.controller.CombatControllerMode;
import net.bullettrain.xenopixelsmod.network.ChargeAnimPacket;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ChargeAnimPacketTest {
    @Test void queuedLegacyChargePoseStartsRefuseV3ButPreserveEveryOlderMode() {
        for (var mode : new CombatControllerMode[]{CombatControllerMode.LEGACY,
                CombatControllerMode.BT3_MANUAL, CombatControllerMode.V2}) {
            assertTrue(ChargeAnimPacket.allowed(ChargeAnimPacket.Phase.START, mode), mode.id());
        }
        assertTrue(ChargeAnimPacket.allowed(ChargeAnimPacket.Phase.START, null));
        assertFalse(ChargeAnimPacket.allowed(ChargeAnimPacket.Phase.START, CombatControllerMode.V3));
    }
    @Test void legacyCancelRemainsAvailableForCleanupInEveryMode() {
        for (var mode : CombatControllerMode.values()) {
            assertTrue(ChargeAnimPacket.allowed(ChargeAnimPacket.Phase.CANCEL, mode), mode.id());
        }
    }
}
