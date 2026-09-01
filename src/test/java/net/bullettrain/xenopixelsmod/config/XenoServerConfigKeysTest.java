package net.bullettrain.xenopixelsmod.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class XenoServerConfigKeysTest {

    private final float savedCap = XenoServerConfig.chargeOverchargeMaxPercent;
    private final boolean savedCharge = XenoServerConfig.chargeOverchargeEnabled;
    private final boolean savedGrief = XenoServerConfig.chargeOverchargeGriefEnabled;
    private final int savedRange = XenoServerConfig.guidanceControlRange;
    private final float savedTurn = XenoServerConfig.guidanceTurnRate;
    private final float savedSize = XenoServerConfig.kiProjectileMaxSize;
    private final float savedSurge = XenoServerConfig.beamSurgeMaxLength;
    private final boolean savedSurgeOn = XenoServerConfig.beamSurgeEnabled;

    @AfterEach
    void restore() {
        XenoServerConfig.chargeOverchargeMaxPercent = savedCap;
        XenoServerConfig.chargeOverchargeEnabled = savedCharge;
        XenoServerConfig.chargeOverchargeGriefEnabled = savedGrief;
        XenoServerConfig.guidanceControlRange = savedRange;
        XenoServerConfig.guidanceTurnRate = savedTurn;
        XenoServerConfig.kiProjectileMaxSize = savedSize;
        XenoServerConfig.beamSurgeMaxLength = savedSurge;
        XenoServerConfig.beamSurgeEnabled = savedSurgeOn;
        XenoServerConfig.applyGuidanceOverrides();
    }

    @Test
    void chargecapSetsPercentNotEnable() {
        XenoServerConfig.chargeOverchargeEnabled = true;
        XenoServerConfigKeys.Result result = XenoServerConfigKeys.set("chargecap", "800");
        assertTrue(result.ok, result.message);
        assertEquals(800.0f, XenoServerConfig.chargeOverchargeMaxPercent, 0.01f);
        assertTrue(XenoServerConfig.chargeOverchargeEnabled);
    }

    @Test
    void unknownKeyFails() {
        XenoServerConfigKeys.Result result = XenoServerConfigKeys.set("not_a_real_key", "1");
        assertFalse(result.ok);
    }

    @Test
    void boolOnOffAliases() {
        XenoServerConfigKeys.Result off = XenoServerConfigKeys.set("chargegrief", "off");
        assertTrue(off.ok, off.message);
        assertFalse(XenoServerConfig.chargeOverchargeGriefEnabled);
        XenoServerConfigKeys.Result on = XenoServerConfigKeys.set("chargeOverchargeGriefEnabled", "true");
        assertTrue(on.ok, on.message);
        assertTrue(XenoServerConfig.chargeOverchargeGriefEnabled);
    }

    @Test
    void guideRangeClampsAndWiresOverride() {
        XenoServerConfigKeys.Result result = XenoServerConfigKeys.set("guiderange", "64");
        assertTrue(result.ok, result.message);
        assertEquals(64, XenoServerConfig.guidanceControlRange);
        assertEquals(64.0, net.bullettrain.xenopixelsmod.combat.technique.KiGuidanceMath.controlRange(0), 0.01);
    }

    @Test
    void maxSizeAndSurgeLengthRoundTrip() {
        assertTrue(XenoServerConfigKeys.set("maxsize", "120").ok);
        assertEquals(120.0f, XenoServerConfig.kiProjectileMaxSize, 0.01f);
        assertTrue(XenoServerConfigKeys.set("maxlength", "96").ok);
        assertEquals(96.0f, XenoServerConfig.beamSurgeMaxLength, 0.01f);
        XenoServerConfigKeys.Result get = XenoServerConfigKeys.get("beamSurgeMaxLength");
        assertTrue(get.ok);
        assertTrue(get.message.contains("96"));
    }

    @Test
    void suggestFindsAliases() {
        assertTrue(XenoServerConfigKeys.suggest("chargec").contains("chargecap"));
        assertTrue(XenoServerConfigKeys.suggest("chargeg").contains("chargegrief"));
    }

    @Test
    void badNumberFails() {
        assertFalse(XenoServerConfigKeys.set("chargecap", "nope").ok);
        assertFalse(XenoServerConfigKeys.set("chargeovercharge", "maybe").ok);
    }

    @Test
    void npcSayKeyToggles() {
        boolean saved = XenoServerConfig.npcSayEnabled;
        try {
            assertTrue(XenoServerConfigKeys.set("npcsay", "off").ok);
            assertFalse(XenoServerConfig.npcSayEnabled);
            assertTrue(XenoServerConfigKeys.set("npcSayEnabled", "on").ok);
            assertTrue(XenoServerConfig.npcSayEnabled);
        } finally {
            XenoServerConfig.npcSayEnabled = saved;
        }
    }
}
