package net.bullettrain.xenopixelsmod.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Server-authoritative feature flags and combat balance.
 * Written to {@code config/xenopixelsmod-server.json}.
 */
public final class XenoServerConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FMLPaths.CONFIGDIR.get().resolve("xenopixelsmod-server.json");

    // --- HUD / DMZ ---
    /** When false, clients block DMZ vanilla HUD overlays. */
    public static boolean dmzHudEnabled = false;
    /** Install/patch DMZ form JSON + skill offerings on boot. */
    public static boolean dmzContentBootstrap = true;

    // --- Combat master switches ---
    public static boolean bt3CombatEnabled = true;
    public static boolean bt3ComboEnabled = true;
    public static boolean bt3VanishEnabled = true;
    public static boolean bt3ChaseDashEnabled = true;
    public static boolean bt3BackstepEnabled = true;
    public static boolean bt3FinisherEnabled = true;
    public static boolean bt3ChargeAttackEnabled = true;
    public static boolean bt3DragonDashEnabled = true;
    /**
     * Chance (0..1) that chase dash / dragon-dash chase phase succeeds.
     * Default 0.50 (50%). Set 0.45 for 45%.
     */
    public static float chaseSuccessChance = 0.50f;

    // --- Balance ---
    public static double vanishMaxRange = 7.0;
    public static double chaseMaxRange = 14.0;
    public static double backstepMaxRange = 10.0;
    public static double chargeAttackRange = 5.0;
    public static double dragonDashRange = 16.0;
    // --- KI costs ---
    public static float vanishKiCost = 8.0f;
    public static float chaseKiCost = 12.0f;
    public static float backstepKiCost = 6.0f;
    public static float comboKiCost = 1.5f;
    public static float finisherKiCost = 5.0f;
    public static float dragonDashKiCost = 10.0f;

    // --- Stamina costs ---
    /** Base stamina on fist charge release (scales with charge %). */
    public static float fistChargeStaminaCost = 18.0f;
    /** Base stamina on kick charge release (scales with charge %). */
    public static float kickChargeStaminaCost = 18.0f;
    /** Legacy alias; prefer fistChargeStaminaCost / kickChargeStaminaCost. */
    @Deprecated
    public static float chargeStaminaCost = 18.0f;
    /** Stamina drained per second while holding any charge (client warn + release scale). */
    public static float chargeHoldStaminaPerSec = 4.0f;
    public static float dragonDashStaminaCost = 22.0f;
    /** Extra stamina when releasing kick with W/S vertical bias. */
    public static float kickVerticalExtraStamina = 4.0f;

    // --- Damage / launch ---
    public static float comboDamageScale = 1.0f;
    public static float finisherDamageScale = 1.35f;
    public static float chargeDamageScale = 1.8f;
    public static float kickDamageScale = 1.15f;
    /** Upward launch multiplier when holding W during charged kick. */
    public static float kickUpLaunch = 1.35f;
    /** Downward launch multiplier when holding S during charged kick. */
    public static float kickDownLaunch = 1.15f;
    public static int maxComboSteps = 5;
    /** Ticks to reach full charge (20 = 1s). */
    public static int chargeMaxTicks = 28;

    private XenoServerConfig() {}

    public static void load() {
        if (!Files.exists(PATH)) {
            save();
            return;
        }
        try (Reader reader = Files.newBufferedReader(PATH)) {
            Data data = GSON.fromJson(reader, Data.class);
            if (data == null) return;
            apply(data);
        } catch (IOException e) {
            XenoPixelsMod.LOGGER.warn("Failed to load server config", e);
        }
    }

    public static void save() {
        try {
            Files.createDirectories(PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(PATH)) {
                GSON.toJson(snapshot(), writer);
            }
        } catch (IOException e) {
            XenoPixelsMod.LOGGER.warn("Failed to save server config", e);
        }
    }

    public static Data snapshot() {
        Data d = new Data();
        d.dmzHudEnabled = dmzHudEnabled;
        d.dmzContentBootstrap = dmzContentBootstrap;
        d.bt3CombatEnabled = bt3CombatEnabled;
        d.bt3ComboEnabled = bt3ComboEnabled;
        d.bt3VanishEnabled = bt3VanishEnabled;
        d.bt3ChaseDashEnabled = bt3ChaseDashEnabled;
        d.bt3BackstepEnabled = bt3BackstepEnabled;
        d.bt3FinisherEnabled = bt3FinisherEnabled;
        d.bt3ChargeAttackEnabled = bt3ChargeAttackEnabled;
        d.bt3DragonDashEnabled = bt3DragonDashEnabled;
        d.chaseSuccessChance = chaseSuccessChance;
        d.vanishMaxRange = vanishMaxRange;
        d.chaseMaxRange = chaseMaxRange;
        d.backstepMaxRange = backstepMaxRange;
        d.chargeAttackRange = chargeAttackRange;
        d.dragonDashRange = dragonDashRange;
        d.vanishKiCost = vanishKiCost;
        d.chaseKiCost = chaseKiCost;
        d.backstepKiCost = backstepKiCost;
        d.comboKiCost = comboKiCost;
        d.finisherKiCost = finisherKiCost;
        d.dragonDashKiCost = dragonDashKiCost;
        d.fistChargeStaminaCost = fistChargeStaminaCost;
        d.kickChargeStaminaCost = kickChargeStaminaCost;
        d.chargeStaminaCost = chargeStaminaCost;
        d.chargeHoldStaminaPerSec = chargeHoldStaminaPerSec;
        d.dragonDashStaminaCost = dragonDashStaminaCost;
        d.kickVerticalExtraStamina = kickVerticalExtraStamina;
        d.comboDamageScale = comboDamageScale;
        d.finisherDamageScale = finisherDamageScale;
        d.chargeDamageScale = chargeDamageScale;
        d.kickDamageScale = kickDamageScale;
        d.kickUpLaunch = kickUpLaunch;
        d.kickDownLaunch = kickDownLaunch;
        d.maxComboSteps = maxComboSteps;
        d.chargeMaxTicks = chargeMaxTicks;
        return d;
    }

    public static void apply(Data d) {
        if (d == null) return;
        dmzHudEnabled = d.dmzHudEnabled;
        dmzContentBootstrap = d.dmzContentBootstrap;
        bt3CombatEnabled = d.bt3CombatEnabled;
        bt3ComboEnabled = d.bt3ComboEnabled;
        bt3VanishEnabled = d.bt3VanishEnabled;
        bt3ChaseDashEnabled = d.bt3ChaseDashEnabled;
        bt3BackstepEnabled = d.bt3BackstepEnabled;
        bt3FinisherEnabled = d.bt3FinisherEnabled;
        bt3ChargeAttackEnabled = d.bt3ChargeAttackEnabled;
        bt3DragonDashEnabled = d.bt3DragonDashEnabled;
        chaseSuccessChance = d.chaseSuccessChance < 0f ? 0.50f : Math.max(0f, Math.min(1f, d.chaseSuccessChance));
        vanishMaxRange = d.vanishMaxRange > 0 ? d.vanishMaxRange : 7.0;
        chaseMaxRange = d.chaseMaxRange > 0 ? d.chaseMaxRange : 14.0;
        backstepMaxRange = d.backstepMaxRange > 0 ? d.backstepMaxRange : 10.0;
        chargeAttackRange = d.chargeAttackRange > 0 ? d.chargeAttackRange : 5.0;
        dragonDashRange = d.dragonDashRange > 0 ? d.dragonDashRange : 16.0;
        vanishKiCost = Math.max(0f, d.vanishKiCost);
        chaseKiCost = Math.max(0f, d.chaseKiCost);
        backstepKiCost = Math.max(0f, d.backstepKiCost);
        comboKiCost = Math.max(0f, d.comboKiCost);
        finisherKiCost = Math.max(0f, d.finisherKiCost);
        dragonDashKiCost = Math.max(0f, d.dragonDashKiCost);

        // Prefer new split stamina fields; fall back to legacy chargeStaminaCost
        float legacyCharge = d.chargeStaminaCost > 0f ? d.chargeStaminaCost : 18f;
        fistChargeStaminaCost = Math.max(0f, d.fistChargeStaminaCost > 0f ? d.fistChargeStaminaCost : legacyCharge);
        kickChargeStaminaCost = Math.max(0f, d.kickChargeStaminaCost > 0f ? d.kickChargeStaminaCost : legacyCharge);
        chargeStaminaCost = Math.max(0f, legacyCharge);
        chargeHoldStaminaPerSec = Math.max(0f, d.chargeHoldStaminaPerSec);
        dragonDashStaminaCost = Math.max(0f, d.dragonDashStaminaCost);
        kickVerticalExtraStamina = Math.max(0f, d.kickVerticalExtraStamina);

        comboDamageScale = d.comboDamageScale > 0 ? d.comboDamageScale : 1f;
        finisherDamageScale = d.finisherDamageScale > 0 ? d.finisherDamageScale : 1.35f;
        chargeDamageScale = d.chargeDamageScale > 0 ? d.chargeDamageScale : 1.8f;
        kickDamageScale = d.kickDamageScale > 0 ? d.kickDamageScale : 1.15f;
        kickUpLaunch = d.kickUpLaunch > 0 ? d.kickUpLaunch : 1.35f;
        kickDownLaunch = d.kickDownLaunch > 0 ? d.kickDownLaunch : 1.15f;
        maxComboSteps = Math.max(1, Math.min(8, d.maxComboSteps <= 0 ? 5 : d.maxComboSteps));
        chargeMaxTicks = Math.max(10, Math.min(80, d.chargeMaxTicks <= 0 ? 28 : d.chargeMaxTicks));
    }

    public static float fistReleaseStamina(float charge01) {
        float c = Math.max(0f, Math.min(1f, charge01));
        return fistChargeStaminaCost * (0.45f + 0.55f * c);
    }

    public static float kickReleaseStamina(float charge01, int vertical) {
        float c = Math.max(0f, Math.min(1f, charge01));
        float base = kickChargeStaminaCost * (0.45f + 0.55f * c);
        if (vertical != 0) base += kickVerticalExtraStamina * (0.5f + 0.5f * c);
        return base;
    }

    /** Wire legacy DMZ HUD config field for older code paths. */
    public static boolean isDmzHudEnabled() {
        return dmzHudEnabled;
    }

    public static void setDmzHudEnabled(boolean enabled) {
        dmzHudEnabled = enabled;
        save();
    }

    public static class Data {
        public boolean dmzHudEnabled = false;
        public boolean dmzContentBootstrap = true;
        public boolean bt3CombatEnabled = true;
        public boolean bt3ComboEnabled = true;
        public boolean bt3VanishEnabled = true;
        public boolean bt3ChaseDashEnabled = true;
        public boolean bt3BackstepEnabled = true;
        public boolean bt3FinisherEnabled = true;
        public boolean bt3ChargeAttackEnabled = true;
        public boolean bt3DragonDashEnabled = true;
        public float chaseSuccessChance = 0.50f;
        public double vanishMaxRange = 7.0;
        public double chaseMaxRange = 14.0;
        public double backstepMaxRange = 10.0;
        public double chargeAttackRange = 5.0;
        public double dragonDashRange = 16.0;
        public float vanishKiCost = 8.0f;
        public float chaseKiCost = 12.0f;
        public float backstepKiCost = 6.0f;
        public float comboKiCost = 1.5f;
        public float finisherKiCost = 5.0f;
        public float dragonDashKiCost = 10.0f;
        public float fistChargeStaminaCost = 18.0f;
        public float kickChargeStaminaCost = 18.0f;
        public float chargeStaminaCost = 18.0f;
        public float chargeHoldStaminaPerSec = 4.0f;
        public float dragonDashStaminaCost = 22.0f;
        public float kickVerticalExtraStamina = 4.0f;
        public float comboDamageScale = 1.0f;
        public float finisherDamageScale = 1.35f;
        public float chargeDamageScale = 1.8f;
        public float kickDamageScale = 1.15f;
        public float kickUpLaunch = 1.35f;
        public float kickDownLaunch = 1.15f;
        public int maxComboSteps = 5;
        public int chargeMaxTicks = 28;
    }
}
