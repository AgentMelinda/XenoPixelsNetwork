package net.bullettrain.xenopixelsmod.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class XenoComboKnockTravelConfigTest {

    private final XenoServerConfig.Data saved = XenoServerConfig.snapshot();

    @AfterEach
    void restore() {
        XenoServerConfig.apply(saved);
        XenoServerConfig.rushComboKnockTravel = saved.rushComboKnockTravel != null
                ? saved.rushComboKnockTravel : 1.65;
        XenoServerConfig.rushComboKnockUp = saved.rushComboKnockUp != null
                ? saved.rushComboKnockUp : 1.85;
        XenoServerConfig.rushComboKnockDown = saved.rushComboKnockDown != null
                ? saved.rushComboKnockDown : 0.8;
        XenoServerConfig.liftComboKnockTravel = saved.liftComboKnockTravel != null
                ? saved.liftComboKnockTravel : 0.4;
        XenoServerConfig.liftComboKnockUp = saved.liftComboKnockUp != null
                ? saved.liftComboKnockUp : 0.9;
        XenoServerConfig.liftComboKnockDown = saved.liftComboKnockDown != null
                ? saved.liftComboKnockDown : 0.8;
        XenoServerConfig.comboRouteKnockbackDistance = XenoServerConfig.rushComboKnockTravel;
        XenoServerConfig.comboRouteKnockbackUp = XenoServerConfig.rushComboKnockUp;
        XenoServerConfig.comboRouteKnockbackDown = XenoServerConfig.rushComboKnockDown;
    }

    @Test
    void oldKnockNameWritesRushTrioAndMirrors() {
        XenoServerConfigKeys.Result result = XenoServerConfigKeys.set("comboRouteKnockbackDistance", "2.5");
        assertTrue(result.ok, result.message);
        assertEquals(2.5, XenoServerConfig.rushComboKnockTravel, 1.0e-6);
        assertEquals(2.5, XenoServerConfig.comboRouteKnockbackDistance, 1.0e-6);
        XenoServerConfig.Data snap = XenoServerConfig.snapshot();
        assertEquals(2.5, snap.comboRouteKnockbackDistance, 1.0e-6);
        assertEquals(2.5, snap.rushComboKnockTravel, 1.0e-6);
        assertEquals(XenoServerConfig.liftComboKnockTravel, snap.liftComboKnockTravel, 1.0e-6);
    }

    @Test
    void liftTravelDoesNotChangeRush() {
        XenoServerConfigKeys.set("rushComboKnockTravel", "2.5");
        XenoServerConfigKeys.set("liftComboKnockTravel", "9.0");
        assertEquals(2.5, XenoServerConfig.rushComboKnockTravel, 1.0e-6);
        assertEquals(2.5, XenoServerConfig.comboRouteKnockbackDistance, 1.0e-6);
        assertEquals(9.0, XenoServerConfig.liftComboKnockTravel, 1.0e-6);
    }

    @Test
    void v21OldTrioMigratesToLiveRushDefaults() {
        XenoServerConfig.Data d = new XenoServerConfig.Data();
        d.configVersion = 21;
        d.comboRouteKnockbackDistance = 1.2;
        d.comboRouteKnockbackUp = 0.35;
        d.comboRouteKnockbackDown = 0.8;
        d.rushComboKnockTravel = null;
        d.rushComboKnockUp = null;
        d.rushComboKnockDown = null;
        d.liftComboKnockTravel = null;
        d.liftComboKnockUp = null;
        d.liftComboKnockDown = null;
        XenoServerConfig.apply(d);
        assertEquals(1.65, XenoServerConfig.rushComboKnockTravel, 1.0e-6);
        assertEquals(1.85, XenoServerConfig.rushComboKnockUp, 1.0e-6);
        assertEquals(0.8, XenoServerConfig.rushComboKnockDown, 1.0e-6);
        assertEquals(1.65, XenoServerConfig.comboRouteKnockbackDistance, 1.0e-6);
        assertEquals(0.4, XenoServerConfig.liftComboKnockTravel, 1.0e-6);
        assertEquals(0.9, XenoServerConfig.liftComboKnockUp, 1.0e-6);
        assertEquals(0.8, XenoServerConfig.liftComboKnockDown, 1.0e-6);
    }

    @Test
    void v22OperatorRushTrioIsKept() {
        XenoServerConfig.Data d = new XenoServerConfig.Data();
        d.configVersion = 22;
        d.comboRouteKnockbackDistance = 3.0;
        d.comboRouteKnockbackUp = 2.2;
        d.comboRouteKnockbackDown = 1.1;
        d.rushComboKnockTravel = null;
        d.rushComboKnockUp = null;
        d.rushComboKnockDown = null;
        XenoServerConfig.apply(d);
        assertEquals(3.0, XenoServerConfig.rushComboKnockTravel, 1.0e-6);
        assertEquals(2.2, XenoServerConfig.rushComboKnockUp, 1.0e-6);
        assertEquals(1.1, XenoServerConfig.rushComboKnockDown, 1.0e-6);
        assertEquals(3.0, XenoServerConfig.comboRouteKnockbackDistance, 1.0e-6);
    }

    @Test
    void explicitZeroIsPreserved() {
        XenoServerConfig.Data d = new XenoServerConfig.Data();
        d.configVersion = 23;
        d.rushComboKnockTravel = 0.0;
        d.rushComboKnockUp = 0.0;
        d.rushComboKnockDown = 0.0;
        d.liftComboKnockTravel = 0.0;
        d.liftComboKnockUp = 1.2;
        d.liftComboKnockDown = null;
        XenoServerConfig.apply(d);
        assertEquals(0.0, XenoServerConfig.rushComboKnockTravel, 1.0e-9);
        assertEquals(0.0, XenoServerConfig.rushComboKnockUp, 1.0e-9);
        assertEquals(0.0, XenoServerConfig.liftComboKnockTravel, 1.0e-9);
        assertEquals(1.2, XenoServerConfig.liftComboKnockUp, 1.0e-6);
        assertEquals(0.8, XenoServerConfig.liftComboKnockDown, 1.0e-6);
    }

    @Test
    void liftTravelPresentUpMissingSeedsOnlyUp() {
        XenoServerConfig.Data d = new XenoServerConfig.Data();
        d.configVersion = 23;
        d.liftComboKnockTravel = 0.5;
        d.liftComboKnockUp = null;
        d.liftComboKnockDown = 0.0;
        XenoServerConfig.apply(d);
        assertEquals(0.5, XenoServerConfig.liftComboKnockTravel, 1.0e-6);
        assertEquals(0.9, XenoServerConfig.liftComboKnockUp, 1.0e-6);
        assertEquals(0.0, XenoServerConfig.liftComboKnockDown, 1.0e-9);
    }

    @Test
    void promoteDoesNotStripOldKnockNames() {
        com.google.gson.JsonObject json = new com.google.gson.JsonObject();
        json.addProperty("comboRouteKnockbackDistance", 1.2);
        json.addProperty("comboRouteKnockbackUp", 0.35);
        boolean promoted = XenoServerConfigKeys.promoteCanonicalFields(json);
        assertTrue(json.has("comboRouteKnockbackDistance"));
        assertEquals(1.2, json.get("comboRouteKnockbackDistance").getAsDouble(), 1.0e-6);
        assertTrue(json.has("comboRouteKnockbackUp"));
        assertFalse(json.has("rushComboKnockTravel"));
        assertTrue(!promoted || json.has("comboRouteKnockbackDistance"));
    }
}
