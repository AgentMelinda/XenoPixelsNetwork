package net.bullettrain.xenopixelsmod.features.transformation.passive;

import net.bullettrain.xenopixelsmod.config.XenoServerConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Every god-form passive has its own switch; off means DMZ's behaviour as before. */
class FormPassiveConfigTest {
    private final XenoServerConfig.Data saved = XenoServerConfig.snapshot();

    @AfterEach
    void restore() {
        XenoServerConfig.apply(saved);
    }

    @Test
    void everyPassiveIsOnByDefault() {
        XenoServerConfig.Data d = new XenoServerConfig.Data();
        assertTrue(d.formPassives);
        assertTrue(d.ueImmunity);
        assertTrue(d.uePenetration);
        assertTrue(d.ueProjectileAura);
        assertTrue(d.uePunchBreak);
        assertTrue(d.hakaiMantle);
        assertTrue(d.uiDodge);
        assertEquals(1.0f, d.uiDodgeScale, 1e-6);
        assertFalse(d.hakaishinNeedsHakai, "given by /dmzform only; the Hakai check is optional");
    }

    @Test
    void theSwitchesRoundTripAndTheScaleIsClamped() {
        XenoServerConfig.Data d = new XenoServerConfig.Data();
        d.hakaiMantle = false;
        d.uiDodgeScale = 9.0f;
        XenoServerConfig.apply(d);
        assertFalse(XenoServerConfig.snapshot().hakaiMantle);
        assertEquals(2.0f, XenoServerConfig.uiDodgeScale, 1e-6);
        assertFalse(FormPassiveRules.enabled(FormPassiveRules.Kind.MANTLE));
        XenoServerConfig.formPassives = false;
        assertFalse(FormPassiveRules.enabled(FormPassiveRules.Kind.DODGE), "the master switch covers all");
    }

    @Test
    void theMantleSparesOnlyWhatMustAlwaysKill() {
        assertTrue(FormPassiveRules.mantleErases(false, false));
        assertFalse(FormPassiveRules.mantleErases(true, false), "/kill and the void");
        assertFalse(FormPassiveRules.mantleErases(false, true), "bypasses invulnerability");
    }

    @Test
    void theUltraEgoPenetrationAddsOnTopOfDmzs() {
        assertEquals(0.85, FormPassiveRules.withPenetration(0.5, 0.35f), 1e-9);
        assertEquals(0.95, FormPassiveRules.withPenetration(0.8, 0.35f), 1e-9, "never a full 100%");
        assertEquals(0.3, FormPassiveRules.withPenetration(0.3, 0.0f), 1e-9);
    }
}
