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
    private final float savedExplosion = XenoServerConfig.kiExplosionMaxRadius;
    private final boolean savedNpcSizeHitbox = XenoServerConfig.xenoNpcSizeScalesHitbox;

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
        XenoServerConfig.kiExplosionMaxRadius = savedExplosion;
        XenoServerConfig.xenoNpcSizeScalesHitbox = savedNpcSizeHitbox;
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
    void nativeNpcSizeHitboxCanBeSwitchedThroughXenoset() {
        assertTrue(XenoServerConfigKeys.set("npcsizehitbox", "off").ok);
        assertFalse(XenoServerConfig.xenoNpcSizeScalesHitbox);
        assertFalse(XenoServerConfig.snapshot().xenoNpcSizeScalesHitbox);
        assertTrue(XenoServerConfigKeys.set("xenoNpcSizeScalesHitbox", "on").ok);
        assertTrue(XenoServerConfig.xenoNpcSizeScalesHitbox);
        XenoServerConfig.Data olderConfig = XenoServerConfig.snapshot();
        olderConfig.xenoNpcSizeScalesHitbox = null;
        XenoServerConfig.apply(olderConfig);
        assertTrue(XenoServerConfig.xenoNpcSizeScalesHitbox);
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
    void explosionRadiusCapIsIndependentOfDestruction() {
        assertTrue(XenoServerConfigKeys.set("explosion", "12").ok);
        assertEquals(12.0f, XenoServerConfig.kiExplosionMaxRadius, 0.01f);
        assertEquals(12.0f, XenoServerConfig.clampKiExplosionRadius(40.0f), 0.01f);
        assertTrue(XenoServerConfigKeys.set("explosion", "0").ok);
        assertEquals(0.0f, XenoServerConfig.kiExplosionMaxRadius, 0.01f);
        assertEquals(40.0f, XenoServerConfig.clampKiExplosionRadius(40.0f), 0.01f);
        assertTrue(XenoServerConfigKeys.suggest("maxexp").contains("kiExplosionMaxRadius"));
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

    @Test
    void superCounterToggleAndWindowRoundTrip() {
        boolean savedOn = XenoServerConfig.bt3SuperCounterEnabled;
        int savedWindow = XenoServerConfig.superCounterWindowTicks;
        float savedCost = XenoServerConfig.superCounterKiCost;
        float savedScale = XenoServerConfig.superCounterDamageScale;
        try {
            assertTrue(XenoServerConfigKeys.set("supercounter", "false").ok);
            assertFalse(XenoServerConfig.bt3SuperCounterEnabled);
            assertTrue(XenoServerConfigKeys.set("counter", "true").ok);
            assertTrue(XenoServerConfig.bt3SuperCounterEnabled);
            assertTrue(XenoServerConfigKeys.set("counterwindow", "20").ok);
            assertEquals(20, XenoServerConfig.superCounterWindowTicks);
            assertTrue(XenoServerConfigKeys.set("counterkicost", "25").ok);
            assertEquals(25.0f, XenoServerConfig.superCounterKiCost, 0.01f);
            assertTrue(XenoServerConfigKeys.set("counterscale", "2").ok);
            assertEquals(2.0f, XenoServerConfig.superCounterDamageScale, 0.01f);
            assertTrue(XenoServerConfigKeys.suggest("superc").contains("bt3SuperCounterEnabled"));
        } finally {
            XenoServerConfig.bt3SuperCounterEnabled = savedOn;
            XenoServerConfig.superCounterWindowTicks = savedWindow;
            XenoServerConfig.superCounterKiCost = savedCost;
            XenoServerConfig.superCounterDamageScale = savedScale;
        }
    }

    @Test
    void multiFormRadiusClampsAndAiRoundTrips() {
        float savedRadius = XenoServerConfig.multiFormRadius;
        String savedAi = XenoServerConfig.multiFormAi;
        try {
            assertTrue(XenoServerConfigKeys.set("multiformradius", "0.1").ok);
            assertEquals(0.5f, XenoServerConfig.clampedMultiFormRadius(), 0.01f);
            assertTrue(XenoServerConfigKeys.set("multiformdistance", "99").ok);
            assertEquals(16.0f, XenoServerConfig.clampedMultiFormRadius(), 0.01f);
            assertTrue(XenoServerConfigKeys.set("multiFormRadius", "3.5").ok);
            assertEquals(3.5f, XenoServerConfig.multiFormRadius, 0.01f);

            assertTrue(XenoServerConfigKeys.set("cloneai", "BRAIN").ok);
            assertEquals("brain", XenoServerConfig.normalizeMultiFormAi(XenoServerConfig.multiFormAi));
            assertTrue(XenoServerConfig.multiFormUsesBrain());
            assertFalse(XenoServerConfig.multiFormUsesMultiFormBrain());
            // The copies' own brain is a third value; picking it must not read as either of the
            // other two, or a player comparing the modes would be told they are on one of them.
            assertTrue(XenoServerConfigKeys.set("multiformai", "MULTIFORM").ok);
            assertEquals("multiform",
                    XenoServerConfig.normalizeMultiFormAi(XenoServerConfig.multiFormAi));
            assertTrue(XenoServerConfig.multiFormUsesMultiFormBrain());
            assertFalse(XenoServerConfig.multiFormUsesBrain());
            assertTrue(XenoServerConfigKeys.set("multiformai", "nope").ok);
            assertEquals("clone", XenoServerConfig.multiFormAi);
            assertFalse(XenoServerConfig.multiFormUsesBrain());
            assertFalse(XenoServerConfig.multiFormUsesMultiFormBrain());
            assertTrue(XenoServerConfigKeys.suggest("multiformr").contains("multiFormRadius"));
            assertTrue(XenoServerConfigKeys.suggest("multiforma").contains("multiFormAi"));
        } finally {
            XenoServerConfig.multiFormRadius = savedRadius;
            XenoServerConfig.multiFormAi = savedAi;
        }
    }

    @Test
    void multiFormProtectAndZanzokenRingKeysRoundTrip() {
        boolean savedRet = XenoServerConfig.multiFormRetaliate;
        boolean savedHost = XenoServerConfig.multiFormHostile;
        boolean savedVanish = XenoServerConfig.multiFormVanish;
        double savedDeflect = XenoServerConfig.brainDeflectMinDistance;
        boolean savedHit = XenoServerConfig.zanzokenRingHitable;
        boolean savedDisp = XenoServerConfig.zanzokenRingDisperseAll;
        try {
            assertTrue(XenoServerConfigKeys.set("multiformretaliate", "off").ok);
            assertFalse(XenoServerConfig.multiFormRetaliate);
            assertTrue(XenoServerConfigKeys.set("multiformhostile", "on").ok);
            assertTrue(XenoServerConfig.multiFormHostile);
            assertTrue(XenoServerConfigKeys.set("multiformvanish", "false").ok);
            assertFalse(XenoServerConfig.multiFormVanish);
            assertTrue(XenoServerConfigKeys.set("npckideflectmin", "7").ok);
            assertEquals(7.0, XenoServerConfig.clampedBrainDeflectMinDistance(), 0.01);
            assertTrue(XenoServerConfigKeys.set("deflectmindistance", "99").ok);
            assertEquals(32.0, XenoServerConfig.clampedBrainDeflectMinDistance(), 0.01);
            assertTrue(XenoServerConfigKeys.set("zanzokenhitable", "off").ok);
            assertFalse(XenoServerConfig.zanzokenRingHitable);
            assertTrue(XenoServerConfigKeys.set("zanzokenpopone", "true").ok);
            assertFalse(XenoServerConfig.zanzokenRingDisperseAll);
            assertTrue(XenoServerConfigKeys.set("zanzokendisperseall", "true").ok);
            assertTrue(XenoServerConfig.zanzokenRingDisperseAll);
        } finally {
            XenoServerConfig.multiFormRetaliate = savedRet;
            XenoServerConfig.multiFormHostile = savedHost;
            XenoServerConfig.multiFormVanish = savedVanish;
            XenoServerConfig.brainDeflectMinDistance = savedDeflect;
            XenoServerConfig.zanzokenRingHitable = savedHit;
            XenoServerConfig.zanzokenRingDisperseAll = savedDisp;
        }
    }

    @Test
    void npcAttackStartRadiusDefaultsToOneAndClamps() {
        double saved = XenoServerConfig.npcAttackStartRadius;
        try {
            assertEquals(1.0, new XenoServerConfig.Data().npcAttackStartRadius, 0.01);
            assertEquals(1.0, XenoServerConfig.npcAttackStartRadius, 0.01);
            assertTrue(XenoServerConfigKeys.set("npcAttackStartRadius", "2.5").ok);
            assertEquals(2.5, XenoServerConfig.clampedNpcAttackStartRadius(), 0.01);
            assertTrue(XenoServerConfigKeys.set("attackradius", "0.1").ok);
            assertEquals(XenoServerConfig.NPC_ATTACK_START_RADIUS_MIN,
                    XenoServerConfig.clampedNpcAttackStartRadius(), 0.01);
            assertTrue(XenoServerConfigKeys.set("npcattackradius", "99").ok);
            assertEquals(XenoServerConfig.NPC_ATTACK_START_RADIUS_MAX,
                    XenoServerConfig.clampedNpcAttackStartRadius(), 0.01);
            assertTrue(XenoServerConfigKeys.set("attackstartradius", "4.5").ok);
            assertEquals(4.5, XenoServerConfig.npcAttackStartRadius, 0.01);
            // The gate reads the clamped getter, so a malformed saved value cannot widen reach.
            XenoServerConfig.npcAttackStartRadius = Double.NaN;
            assertEquals(1.0, XenoServerConfig.clampedNpcAttackStartRadius(), 0.01);
            XenoServerConfig.Data d = XenoServerConfig.snapshot();
            d.npcAttackStartRadius = 250.0;
            XenoServerConfig.apply(d);
            assertEquals(XenoServerConfig.NPC_ATTACK_START_RADIUS_MAX,
                    XenoServerConfig.npcAttackStartRadius, 0.01);
        } finally {
            XenoServerConfig.npcAttackStartRadius = saved;
        }
    }

    @Test
    void combatControllerModeKeyDefaultsLegacyAndNormalizes() {
        String saved = XenoServerConfig.combatControllerMode;
        try {
            XenoServerConfig.Data defaults = new XenoServerConfig.Data();
            assertEquals("legacy", defaults.combatControllerMode);

            assertTrue(XenoServerConfigKeys.set("combatmode", "BT3_MANUAL").ok);
            assertEquals("bt3_manual", XenoServerConfig.combatControllerMode);
            assertTrue(XenoServerConfig.isBt3ManualController());

            // Garbage never lands in the field: the setter normalizes it back to legacy.
            assertTrue(XenoServerConfigKeys.set("combatControllerMode", "nonsense").ok);
            assertEquals("legacy", XenoServerConfig.combatControllerMode);
            assertFalse(XenoServerConfig.isBt3ManualController());

            assertTrue(XenoServerConfigKeys.set("bt3mode", "bt3_manual").ok);
            XenoServerConfigKeys.Result get = XenoServerConfigKeys.get("controllermode");
            assertTrue(get.ok, get.message);
            assertTrue(get.message.contains("bt3_manual"));
            assertTrue(XenoServerConfigKeys.suggest("combatc").contains("combatControllerMode"));
        } finally {
            XenoServerConfig.combatControllerMode = saved;
        }
    }

    @Test
    void applyNormalizesAMalformedSavedControllerMode() {
        XenoServerConfig.Data saved = XenoServerConfig.snapshot();
        try {
            XenoServerConfig.Data data = new XenoServerConfig.Data();
            data.combatControllerMode = "BT3-Manual";
            XenoServerConfig.apply(data);
            assertEquals("bt3_manual", XenoServerConfig.combatControllerMode);
            data.combatControllerMode = null;
            XenoServerConfig.apply(data);
            assertEquals("legacy", XenoServerConfig.combatControllerMode);
            assertEquals("legacy", XenoServerConfig.snapshot().combatControllerMode);
        } finally {
            XenoServerConfig.apply(saved);
        }
    }

    @Test
    void cloneDetectRangeKeysRoundTripAndClamp() {
        double savedMulti = XenoServerConfig.multiFormDetectRange;
        double savedZan = XenoServerConfig.zanzokenDetectRange;
        try {
            assertTrue(XenoServerConfigKeys.set("multiformdetect", "64").ok);
            assertEquals(64.0, XenoServerConfig.clampedMultiFormDetectRange(), 0.01);
            assertTrue(XenoServerConfigKeys.set("multiformleash", "0").ok);
            assertEquals(32.0, XenoServerConfig.clampedMultiFormDetectRange(), 0.01);
            assertTrue(XenoServerConfigKeys.set("multiFormDetectRange", "999").ok);
            assertEquals(128.0, XenoServerConfig.clampedMultiFormDetectRange(), 0.01);

            assertTrue(XenoServerConfigKeys.set("zandetect", "16").ok);
            assertEquals(16.0, XenoServerConfig.clampedZanzokenDetectRange(), 0.01);
            assertTrue(XenoServerConfigKeys.set("zanzokendetect", "12.5").ok);
            assertEquals(12.5, XenoServerConfig.clampedZanzokenDetectRange(), 0.01);
            assertTrue(XenoServerConfigKeys.suggest("multiformd").contains("multiFormDetectRange"));
            assertTrue(XenoServerConfigKeys.suggest("zanzokend").contains("zanzokenDetectRange"));
        } finally {
            XenoServerConfig.multiFormDetectRange = savedMulti;
            XenoServerConfig.zanzokenDetectRange = savedZan;
        }
    }

    /** 2026-09-28 owner: effect sizes and switches are set with /xenoset. */
    @Test
    void effekseerSizesAndSwitchesAreXenosetKeys() {
        XenoServerConfig.Data saved = XenoServerConfig.snapshot();
        try {
            assertTrue(XenoServerConfigKeys.set("effekseerPunchScale", "0.5").ok);
            assertEquals(0.5f, XenoServerConfig.effekseerPunchScale, 1e-6);
            assertTrue(XenoServerConfigKeys.set("effekseerPunchScale", "99").ok);
            assertEquals(3.0f, XenoServerConfig.effekseerPunchScale, 1e-6, "clamped like the config file");
            assertTrue(XenoServerConfigKeys.set("effekseerHakaiScale", "1.5").ok);
            assertEquals(1.5f, XenoServerConfig.effekseerHakaiScale, 1e-6);
            assertTrue(XenoServerConfigKeys.set("effekseerMissileScale", "0.01").ok);
            assertEquals(0.05f, XenoServerConfig.effekseerMissileScale, 1e-6);
            assertTrue(XenoServerConfigKeys.set("effekseerEnabled", "false").ok);
            assertFalse(XenoServerConfig.effekseerEnabled);
            for (String k : new String[]{"effekseerPunches", "effekseerHakai", "effekseerMissiles",
                    "effekseerRange", "effekseerMissileRange", "effekseerPunchesPerTick"}) {
                assertTrue(XenoServerConfigKeys.get(k).ok, k);
            }
            assertTrue(XenoServerConfigKeys.set("effekseerRange", "1").ok);
            assertEquals(8, XenoServerConfig.effekseerRange);
        } finally {
            XenoServerConfig.apply(saved);
        }
    }

    @Test
    void effekseerCategoryScalesSurviveTheConfigFile() {
        XenoServerConfig.Data d = new XenoServerConfig.Data();
        assertEquals(1.0f, d.effekseerHakaiScale, 1e-6);
        assertEquals(1.0f, d.effekseerMissileScale, 1e-6);
        XenoServerConfig.Data saved = XenoServerConfig.snapshot();
        try {
            d.effekseerHakaiScale = 2.5f;
            d.effekseerMissileScale = Float.NaN;
            XenoServerConfig.apply(d);
            assertEquals(2.5f, XenoServerConfig.snapshot().effekseerHakaiScale, 1e-6);
            assertEquals(1.0f, XenoServerConfig.effekseerMissileScale, 1e-6);
        } finally {
            XenoServerConfig.apply(saved);
        }
    }

    @Test
    void sparkingAndShipThrusterEffectKeys() {
        XenoServerConfig.Data saved = XenoServerConfig.snapshot();
        try {
            assertTrue(XenoServerConfigKeys.set("effekseerSparking", "false").ok);
            assertFalse(XenoServerConfig.effekseerSparking);
            assertTrue(XenoServerConfigKeys.set("effekseerShipThrusters", "false").ok);
            assertFalse(XenoServerConfig.effekseerShipThrusters);
            assertTrue(XenoServerConfigKeys.set("effekseerSparkingScale", "9").ok);
            assertEquals(5.0f, XenoServerConfig.effekseerSparkingScale, 1e-6);
            assertTrue(XenoServerConfigKeys.set("thrustersize", "0.7").ok);
            assertEquals(0.7f, XenoServerConfig.effekseerThrusterScale, 1e-6);
            XenoServerConfig.Data d = new XenoServerConfig.Data();
            assertTrue(d.effekseerSparking && d.effekseerShipThrusters);
            assertEquals(1.0f, d.effekseerSparkingScale, 1e-6);
            assertEquals(1.0f, d.effekseerThrusterScale, 1e-6);
            d.effekseerThrusterScale = Float.NaN;
            XenoServerConfig.apply(d);
            assertEquals(1.0f, XenoServerConfig.effekseerThrusterScale, 1e-6);
        } finally {
            XenoServerConfig.apply(saved);
        }
    }

    @Test
    void sparkingSmoothIsAXenosetKey() {
        XenoServerConfig.Data saved = XenoServerConfig.snapshot();
        try {
            assertTrue(XenoServerConfigKeys.set("sparkingsmooth", "true").ok);
            assertTrue(XenoServerConfig.effekseerSparkingSmooth);
        } finally {
            XenoServerConfig.apply(saved);
        }
    }

    @Test
    void eachEffectScaleIsAXenosetKeyNamedAfterTheEffect() {
        XenoServerConfig.Data saved = XenoServerConfig.snapshot();
        try {
            assertTrue(XenoServerConfigKeys.set("missile_explosion", "2").ok);
            assertEquals(2.0f, XenoServerConfig.slotScale(net.bullettrain.xenopixelsmod.fx.effek.EffectSlot.MISSILE_EXPLOSION), 1e-6);
            assertTrue(XenoServerConfigKeys.set("fxscale_punch_impact", "99").ok);
            assertEquals(50.0f, XenoServerConfig.slotScale(net.bullettrain.xenopixelsmod.fx.effek.EffectSlot.PUNCH_IMPACT), 1e-6);
            assertTrue(XenoServerConfigKeys.get("sparking_flight").message.contains("3.5"));
            XenoServerConfig.Data d = XenoServerConfig.snapshot();
            XenoServerConfig.apply(new XenoServerConfig.Data());
            XenoServerConfig.apply(d);
            assertEquals(2.0f, XenoServerConfig.slotScale(net.bullettrain.xenopixelsmod.fx.effek.EffectSlot.MISSILE_EXPLOSION), 1e-6,
                    "saved with the config");
        } finally {
            XenoServerConfig.apply(saved);
        }
    }
}
