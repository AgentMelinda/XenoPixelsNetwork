package net.bullettrain.xenopixelsmod.client.aura;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HakaiEnergyAuraPolicyTest {
    @Test void onlyTheActiveDestroyerFormUsesTheExistingToggle() {
        assertTrue(HakaiEnergyAuraPolicy.active(true, true, "xenopixels_hakaishin"));
        assertFalse(HakaiEnergyAuraPolicy.active(false, true, "xenopixels_hakaishin"));
        assertFalse(HakaiEnergyAuraPolicy.active(true, false, "xenopixels_hakaishin"));
        assertFalse(HakaiEnergyAuraPolicy.active(true, true, "supersaiyan"));
        assertFalse(HakaiEnergyAuraPolicy.active(true, true, null));
    }

    @Test void chargeAuraSurvivesAFlickeringChargeFlag() {
        assertFalse(HakaiEnergyAuraPolicy.suppressAura(true, 0));
        assertFalse(HakaiEnergyAuraPolicy.suppressAura(false, 2));
        assertTrue(HakaiEnergyAuraPolicy.suppressAura(false, HakaiEnergyAuraPolicy.CHARGE_GRACE_TICKS + 1));
        assertTrue(HakaiEnergyAuraPolicy.suppressAura(false, -1));
    }
}
