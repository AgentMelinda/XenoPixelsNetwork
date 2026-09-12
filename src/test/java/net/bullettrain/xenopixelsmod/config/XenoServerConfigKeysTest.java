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
    void chaseDefaultsReliableButPreservesExplicitSavedProbabilities() {
        XenoServerConfig.Data saved = XenoServerConfig.snapshot();
        try {
            XenoServerConfig.Data defaults = new XenoServerConfig.Data();
            assertEquals(1f, defaults.chaseSuccessChance);
            XenoServerConfig.apply(defaults);
            assertEquals(1f, XenoServerConfig.chaseSuccessChance);
            defaults.chaseSuccessChance = 0.45f;
            XenoServerConfig.apply(defaults);
            assertEquals(0.45f, XenoServerConfig.chaseSuccessChance);
            defaults.chaseSuccessChance = Float.NaN;
            XenoServerConfig.apply(defaults);
            assertEquals(1f, XenoServerConfig.chaseSuccessChance);
        } finally {
            XenoServerConfig.apply(saved);
        }
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
        assertTrue(XenoServerConfigKeys.suggest("chargec").contains("chargeOverchargeMaxPercent"));
        assertFalse(XenoServerConfigKeys.suggest("chargec").contains("chargecap"));
        assertTrue(XenoServerConfigKeys.suggest("chargeg").contains("chargeOverchargeGriefEnabled"));
        assertTrue(XenoServerConfigKeys.suggest("sparkingcd").contains("sparkingCooldownTicks"));
        assertFalse(XenoServerConfigKeys.suggest("sparkingcd").contains("sparkingcd"));
        assertEquals(1, XenoServerConfigKeys.suggest("sparkingcooldown").stream()
                .filter(s -> s.toLowerCase().contains("cooldown")).count());
    }

    @Test
    void aliasSetStillWritesTheCanonicalField() {
        int saved = XenoServerConfig.sparkingCooldownTicks;
        try {
            assertTrue(XenoServerConfigKeys.set("sparkingcd", "80").ok);
            assertEquals(80, XenoServerConfig.sparkingCooldownTicks);
        } finally {
            XenoServerConfig.sparkingCooldownTicks = saved;
        }
    }

    @Test
    void promoteCopiesMissingCanonicalFromAlias() {
        com.google.gson.JsonObject json = new com.google.gson.JsonObject();
        json.addProperty("sparkingcd", 500);
        assertTrue(XenoServerConfigKeys.promoteCanonicalFields(json));
        assertEquals(500, json.get("sparkingCooldownTicks").getAsInt());
        assertFalse(json.has("sparkingcd"));
    }

    @Test
    void promoteKeepsCanonicalWhenBothPresent() {
        com.google.gson.JsonObject json = new com.google.gson.JsonObject();
        json.addProperty("sparkingCooldownTicks", 200);
        json.addProperty("sparkingcd", 500);
        assertTrue(XenoServerConfigKeys.promoteCanonicalFields(json));
        assertEquals(200, json.get("sparkingCooldownTicks").getAsInt());
        assertFalse(json.has("sparkingcd"));
    }

    @Test
    void badNumberFails() {
        assertFalse(XenoServerConfigKeys.set("chargecap", "nope").ok);
        assertFalse(XenoServerConfigKeys.set("chargeovercharge", "maybe").ok);
    }

    @Test
    void hakaiFadeKeysRoundTripAndClamp() {
        boolean savedEnabled = XenoServerConfig.hakaiFadeEnabled;
        float savedMin = XenoServerConfig.hakaiFadeMinAlpha;
        float savedCurve = XenoServerConfig.hakaiFadeCurve;
        int savedRestore = XenoServerConfig.hakaiFadeRestoreTicks;
        float savedSpeed = XenoServerConfig.hakaiFadeSpeed;
        float savedBand = XenoServerConfig.hakaiFadeBand;
        int savedColor = XenoServerConfig.hakaiFxColor;
        int savedRim = XenoServerConfig.hakaiFxRimColor;
        boolean savedFx = XenoServerConfig.hakaiFxEnabled;
        boolean savedDust = XenoServerConfig.hakaiDustEnabled;
        boolean savedSil = XenoServerConfig.hakaiSilhouetteEnabled;
        int savedSilColor = XenoServerConfig.hakaiSilhouetteColor;
        int savedGlow = XenoServerConfig.hakaiGlowColor;
        try {
            assertTrue(XenoServerConfigKeys.set("hakaifade", "off").ok);
            assertFalse(XenoServerConfig.hakaiFadeEnabled);

            assertTrue(XenoServerConfigKeys.set("hakaifademin", "0").ok);
            assertEquals(0.0f, XenoServerConfig.hakaiFadeMinAlpha, 1.0e-5f);

            assertTrue(XenoServerConfigKeys.set("hakaifadecurve", "9").ok);
            assertEquals(4.0f, XenoServerConfig.hakaiFadeCurve, 1.0e-5f);
            assertTrue(XenoServerConfigKeys.set("hakaifadecurve", "0.01").ok);
            assertEquals(0.25f, XenoServerConfig.hakaiFadeCurve, 1.0e-5f);

            assertTrue(XenoServerConfigKeys.set("hakaifaderestore", "5000").ok);
            assertEquals(5000, XenoServerConfig.hakaiFadeRestoreTicks);
            assertTrue(XenoServerConfigKeys.set("hakaifaderestore", "9000").ok);
            assertEquals(6000, XenoServerConfig.hakaiFadeRestoreTicks);
            assertTrue(XenoServerConfigKeys.set("hakaiFadeRestoreTicks", "-5").ok);
            assertEquals(0, XenoServerConfig.hakaiFadeRestoreTicks);

            assertTrue(XenoServerConfigKeys.set("hakaifadespeed", "2").ok);
            assertEquals(2.0f, XenoServerConfig.hakaiFadeSpeed, 1.0e-5f);
            assertTrue(XenoServerConfigKeys.set("hakaifadespeed", "9").ok);
            assertEquals(4.0f, XenoServerConfig.hakaiFadeSpeed, 1.0e-5f);

            assertTrue(XenoServerConfigKeys.set("hakaifadeband", "0.01").ok);
            assertEquals(0.04f, XenoServerConfig.hakaiFadeBand, 1.0e-5f);

            assertTrue(XenoServerConfigKeys.set("hakaicolor", "0xF233F2").ok);
            assertEquals(0xF233F2, XenoServerConfig.hakaiFxColor);
            assertTrue(XenoServerConfigKeys.get("hakaiFxColor").message.contains("0xF233F2"));
            assertTrue(XenoServerConfigKeys.set("hakairim", "#FF73FF").ok);
            assertEquals(0xFF73FF, XenoServerConfig.hakaiFxRimColor);

            assertTrue(XenoServerConfigKeys.set("hakaifade", "on").ok);
            assertTrue(XenoServerConfigKeys.set("hakaifx", "off").ok);
            assertFalse(XenoServerConfig.hakaiFxEnabled);
            assertTrue(XenoServerConfig.hakaiFadeEnabled);
            assertTrue(XenoServerConfigKeys.set("hakaidust", "off").ok);
            assertFalse(XenoServerConfig.hakaiDustEnabled);
            assertTrue(XenoServerConfigKeys.set("hakaisilhouette", "off").ok);
            assertFalse(XenoServerConfig.hakaiSilhouetteEnabled);
            assertTrue(XenoServerConfigKeys.set("hakaisilhouettecolor", "0x112233").ok);
            assertEquals(0x112233, XenoServerConfig.hakaiSilhouetteColor);
            assertTrue(XenoServerConfigKeys.set("hakaiglowcolor", "#AABBCC").ok);
            assertEquals(0xAABBCC, XenoServerConfig.hakaiGlowColor);

            assertTrue(XenoServerConfigKeys.get("hakaiFadeMinAlpha").ok);
            assertTrue(XenoServerConfigKeys.suggest("hakaifade").contains("hakaiFadeEnabled"));
        } finally {
            XenoServerConfig.hakaiFadeEnabled = savedEnabled;
            XenoServerConfig.hakaiFadeMinAlpha = savedMin;
            XenoServerConfig.hakaiFadeCurve = savedCurve;
            XenoServerConfig.hakaiFadeRestoreTicks = savedRestore;
            XenoServerConfig.hakaiFadeSpeed = savedSpeed;
            XenoServerConfig.hakaiFadeBand = savedBand;
            XenoServerConfig.hakaiFxColor = savedColor;
            XenoServerConfig.hakaiFxRimColor = savedRim;
            XenoServerConfig.hakaiFxEnabled = savedFx;
            XenoServerConfig.hakaiDustEnabled = savedDust;
            XenoServerConfig.hakaiSilhouetteEnabled = savedSil;
            XenoServerConfig.hakaiSilhouetteColor = savedSilColor;
            XenoServerConfig.hakaiGlowColor = savedGlow;
        }
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
