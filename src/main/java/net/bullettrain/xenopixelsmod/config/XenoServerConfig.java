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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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

    // --- Form multipliers (public server balance: scales DMZ form / stack-form stat mults) ---
    /**
     * Global form power scale for this server. Affects form bonuses only (base form stays 1.0).
     * Formula: {@code 1 + (formMult - 1) * formStatMultiplier}.
     * <ul>
     *   <li>{@code 1.0} — stock DMZ form power</li>
     *   <li>{@code 2.0} — double form bonuses</li>
     *   <li>{@code 0.0} — forms give no stat bonus</li>
     * </ul>
     * Tunable live with {@code /xenoform set} or {@code config/xenopixelsmod-server.json}.
     */
    public static float formStatMultiplier = 1.0f;
    /**
     * Per-form overall power scales. Keys: {@code group.form} (e.g. {@code xenopixels_gods_forms.ssb})
     * or short form id ({@code ssb}). When missing, {@link #formStatMultiplier} is used.
     */
    public static final Map<String, Float> formPerFormMultipliers = new ConcurrentHashMap<>();
    /**
     * Global per-stat scales (all forms). Keys: {@code str}, {@code pwr}, {@code def}, {@code skp},
     * {@code stm}, {@code vit}, {@code ene}, {@code speed}. Default when missing: 1.0.
     */
    public static final Map<String, Float> formPerStatMultipliers = new ConcurrentHashMap<>();
    /**
     * Per-form per-stat scales. Outer key = form id, inner key = stat (str/pwr/…).
     * Example: {@code ssb.str = 5} multiplies only Super Saiyan Blue strength bonus.
     */
    public static final Map<String, Map<String, Float>> formPerFormStatMultipliers = new ConcurrentHashMap<>();
    /** DMZ form combat stats you can scale. */
    public static final String[] FORM_STAT_KEYS = {
            "str", "skp", "stm", "def", "vit", "pwr", "ene", "speed"
    };
    /** Minimum allowed form scale (global, per-form, or per-stat). */
    public static final float FORM_STAT_MULT_MIN = 0.0f;
    /** Maximum allowed form scale (global, per-form, or per-stat). */
    public static final float FORM_STAT_MULT_MAX = 1_000_000.0f;

    // --- KI overcharge (power release %) ---
    /** Enable bigger/harder KI attacks when release is above the threshold. */
    public static boolean kiOverchargeEnabled = true;
    /** Power-release % where overcharge scaling starts (default 175). */
    public static int kiOverchargeThreshold = 175;
    /** Size growth per 1% release above threshold (default 0.012 = +1.2%/pt). */
    public static float kiOverchargeSizePerPercent = 0.012f;
    /** Damage growth per 1% release above threshold. */
    public static float kiOverchargeDamagePerPercent = 0.015f;
    /** Explosion radius growth per 1% release above threshold. */
    public static float kiOverchargeExplosionPerPercent = 0.014f;
    /** Master multiplier applied to all overcharge growth. */
    public static float kiOverchargeMultiplier = 1.0f;
    /** Cap on overcharge scale factor (1 + growth), e.g. 3.0 = triple max. */
    public static float kiOverchargeMaxScale = 3.0f;

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
    /** Extra reach (blocks) for charged kick while holding S. */
    public static float kickDownRangeBonus = 4.0f;
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
        d.formStatMultiplier = formStatMultiplier; // boxed Float in Data
        d.formPerFormMultipliers = new LinkedHashMap<>(formPerFormMultipliers);
        d.formPerStatMultipliers = new LinkedHashMap<>(formPerStatMultipliers);
        d.formPerFormStatMultipliers = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, Float>> e : formPerFormStatMultipliers.entrySet()) {
            d.formPerFormStatMultipliers.put(e.getKey(), new LinkedHashMap<>(e.getValue()));
        }
        d.kiOverchargeEnabled = kiOverchargeEnabled;
        d.kiOverchargeThreshold = kiOverchargeThreshold;
        d.kiOverchargeSizePerPercent = kiOverchargeSizePerPercent;
        d.kiOverchargeDamagePerPercent = kiOverchargeDamagePerPercent;
        d.kiOverchargeExplosionPerPercent = kiOverchargeExplosionPerPercent;
        d.kiOverchargeMultiplier = kiOverchargeMultiplier;
        d.kiOverchargeMaxScale = kiOverchargeMaxScale;
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
        d.kickDownRangeBonus = kickDownRangeBonus;
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
        // Boxed Float: null when key missing from older configs → keep default 1.0
        if (d.formStatMultiplier != null) {
            formStatMultiplier = clampFormStatMultiplier(d.formStatMultiplier);
        }
        applyFormScaleMaps(d);
        kiOverchargeEnabled = d.kiOverchargeEnabled;
        kiOverchargeThreshold = Math.max(100, Math.min(500, d.kiOverchargeThreshold <= 0 ? 175 : d.kiOverchargeThreshold));
        kiOverchargeSizePerPercent = Math.max(0f, d.kiOverchargeSizePerPercent);
        kiOverchargeDamagePerPercent = Math.max(0f, d.kiOverchargeDamagePerPercent);
        kiOverchargeExplosionPerPercent = Math.max(0f, d.kiOverchargeExplosionPerPercent);
        kiOverchargeMultiplier = d.kiOverchargeMultiplier > 0f ? d.kiOverchargeMultiplier : 1f;
        kiOverchargeMaxScale = d.kiOverchargeMaxScale > 1f ? d.kiOverchargeMaxScale : 3f;
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
        kickDownRangeBonus = Math.max(0f, d.kickDownRangeBonus);
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

    /**
     * Overcharge scale for a given power-release %. Returns 1.0 at/below threshold.
     * Uses {@code size} growth curve as the base excess factor; callers apply their own per-% rates.
     */
    public static float kiOverchargeExcessPercent(int powerRelease) {
        if (!kiOverchargeEnabled) return 0f;
        int excess = powerRelease - kiOverchargeThreshold;
        return Math.max(0f, excess);
    }

    public static float kiOverchargeSizeScale(int powerRelease) {
        float excess = kiOverchargeExcessPercent(powerRelease);
        if (excess <= 0f) return 1f;
        float scale = 1f + excess * kiOverchargeSizePerPercent * kiOverchargeMultiplier;
        return Math.min(kiOverchargeMaxScale, scale);
    }

    public static float kiOverchargeDamageScale(int powerRelease) {
        float excess = kiOverchargeExcessPercent(powerRelease);
        if (excess <= 0f) return 1f;
        float scale = 1f + excess * kiOverchargeDamagePerPercent * kiOverchargeMultiplier;
        return Math.min(kiOverchargeMaxScale, scale);
    }

    public static float kiOverchargeExplosionScale(int powerRelease) {
        float excess = kiOverchargeExcessPercent(powerRelease);
        if (excess <= 0f) return 1f;
        float scale = 1f + excess * kiOverchargeExplosionPerPercent * kiOverchargeMultiplier;
        return Math.min(kiOverchargeMaxScale, scale);
    }

    /** Wire legacy DMZ HUD config field for older code paths. */
    public static boolean isDmzHudEnabled() {
        return dmzHudEnabled;
    }

    public static void setDmzHudEnabled(boolean enabled) {
        dmzHudEnabled = enabled;
        save();
    }

    public static float clampFormStatMultiplier(float value) {
        if (Float.isNaN(value) || Float.isInfinite(value)) return 1.0f;
        return Math.max(FORM_STAT_MULT_MIN, Math.min(FORM_STAT_MULT_MAX, value));
    }

    /** Applies global + per-form + per-stat scale maps from a config/sync payload. */
    public static void applyFormScaleMaps(Data d) {
        if (d == null) return;
        if (d.formStatMultiplier != null) {
            formStatMultiplier = clampFormStatMultiplier(d.formStatMultiplier);
        }
        formPerFormMultipliers.clear();
        if (d.formPerFormMultipliers != null) {
            for (Map.Entry<String, Float> e : d.formPerFormMultipliers.entrySet()) {
                if (e.getKey() == null || e.getKey().isBlank() || e.getValue() == null) continue;
                formPerFormMultipliers.put(normalizeFormKey(e.getKey()), clampFormStatMultiplier(e.getValue()));
            }
        }
        formPerStatMultipliers.clear();
        if (d.formPerStatMultipliers != null) {
            for (Map.Entry<String, Float> e : d.formPerStatMultipliers.entrySet()) {
                String stat = normalizeStatKey(e.getKey());
                if (stat.isEmpty() || e.getValue() == null) continue;
                formPerStatMultipliers.put(stat, clampFormStatMultiplier(e.getValue()));
            }
        }
        formPerFormStatMultipliers.clear();
        if (d.formPerFormStatMultipliers != null) {
            for (Map.Entry<String, Map<String, Float>> e : d.formPerFormStatMultipliers.entrySet()) {
                if (e.getKey() == null || e.getKey().isBlank() || e.getValue() == null) continue;
                String form = normalizeFormKey(e.getKey());
                Map<String, Float> inner = new ConcurrentHashMap<>();
                for (Map.Entry<String, Float> s : e.getValue().entrySet()) {
                    String stat = normalizeStatKey(s.getKey());
                    if (stat.isEmpty() || s.getValue() == null) continue;
                    inner.put(stat, clampFormStatMultiplier(s.getValue()));
                }
                if (!inner.isEmpty()) {
                    formPerFormStatMultipliers.put(form, inner);
                }
            }
        }
    }

    public static String normalizeFormKey(String key) {
        return key == null ? "" : key.trim().toLowerCase(Locale.ROOT);
    }

    /** Normalizes DMZ stat tokens: STR/pwr/power/strength → str, pwr, def, … */
    public static String normalizeStatKey(String stat) {
        if (stat == null) return "";
        String s = stat.trim().toLowerCase(Locale.ROOT);
        return switch (s) {
            case "strength", "melee" -> "str";
            case "power", "ki", "energy_pwr" -> "pwr";
            case "defense", "defence" -> "def";
            case "stamina", "stam" -> "stm";
            case "vitality", "hp", "health" -> "vit";
            case "energy", "ki_pool" -> "ene";
            case "strike", "skill" -> "skp";
            case "spd", "move", "flight" -> "speed";
            default -> s;
        };
    }

    public static boolean isKnownFormStat(String stat) {
        String s = normalizeStatKey(stat);
        for (String k : FORM_STAT_KEYS) {
            if (k.equals(s)) return true;
        }
        return false;
    }

    public static void setFormStatMultiplier(float value) {
        formStatMultiplier = clampFormStatMultiplier(value);
        save();
    }

    /** Set or replace per-form overall scale. Key should be {@code group.form} or short form id. */
    public static void setPerFormMultiplier(String formKey, float value) {
        String k = normalizeFormKey(formKey);
        if (k.isEmpty() || "global".equals(k) || "*".equals(k)) {
            setFormStatMultiplier(value);
            return;
        }
        formPerFormMultipliers.put(k, clampFormStatMultiplier(value));
        save();
    }

    /** Global scale for one combat stat (all forms). */
    public static void setPerStatMultiplier(String stat, float value) {
        String s = normalizeStatKey(stat);
        if (!isKnownFormStat(s)) return;
        formPerStatMultipliers.put(s, clampFormStatMultiplier(value));
        save();
    }

    /** Scale one stat for one form only. */
    public static void setPerFormStatMultiplier(String formKey, String stat, float value) {
        String f = normalizeFormKey(formKey);
        String s = normalizeStatKey(stat);
        if (f.isEmpty() || !isKnownFormStat(s)) return;
        if ("global".equals(f) || "*".equals(f)) {
            setPerStatMultiplier(s, value);
            return;
        }
        formPerFormStatMultipliers
                .computeIfAbsent(f, k -> new ConcurrentHashMap<>())
                .put(s, clampFormStatMultiplier(value));
        save();
    }

    /** Remove a per-form overall override (falls back to global). */
    public static boolean clearPerFormMultiplier(String formKey) {
        String k = normalizeFormKey(formKey);
        if (k.isEmpty()) return false;
        boolean removed = formPerFormMultipliers.remove(k) != null;
        if (removed) save();
        return removed;
    }

    public static boolean clearPerStatMultiplier(String stat) {
        String s = normalizeStatKey(stat);
        boolean removed = formPerStatMultipliers.remove(s) != null;
        if (removed) save();
        return removed;
    }

    public static boolean clearPerFormStatMultiplier(String formKey, String stat) {
        String f = normalizeFormKey(formKey);
        String s = normalizeStatKey(stat);
        if ("global".equals(f) || "*".equals(f)) {
            return clearPerStatMultiplier(s);
        }
        Map<String, Float> inner = formPerFormStatMultipliers.get(f);
        if (inner == null) return false;
        boolean removed = inner.remove(s) != null;
        if (inner.isEmpty()) formPerFormStatMultipliers.remove(f);
        if (removed) save();
        return removed;
    }

    public static void clearAllPerFormMultipliers() {
        boolean any = !formPerFormMultipliers.isEmpty()
                || !formPerStatMultipliers.isEmpty()
                || !formPerFormStatMultipliers.isEmpty();
        formPerFormMultipliers.clear();
        formPerStatMultipliers.clear();
        formPerFormStatMultipliers.clear();
        if (any) save();
    }

    private static Float lookupPerFormMap(Map<String, Float> map, String formKey) {
        if (map == null || map.isEmpty() || formKey == null || formKey.isBlank()) return null;
        String k = normalizeFormKey(formKey);
        Float exact = map.get(k);
        if (exact != null) return exact;
        int dot = k.lastIndexOf('.');
        if (dot >= 0 && dot + 1 < k.length()) {
            Float shortMatch = map.get(k.substring(dot + 1));
            if (shortMatch != null) return shortMatch;
        }
        for (Map.Entry<String, Float> e : map.entrySet()) {
            String stored = e.getKey();
            if (stored.equals(k) || stored.endsWith("." + k)) return e.getValue();
        }
        return null;
    }

    private static Map<String, Float> lookupPerFormStatMap(String formKey) {
        if (formKey == null || formKey.isBlank() || formPerFormStatMultipliers.isEmpty()) return null;
        String k = normalizeFormKey(formKey);
        Map<String, Float> exact = formPerFormStatMultipliers.get(k);
        if (exact != null) return exact;
        int dot = k.lastIndexOf('.');
        if (dot >= 0 && dot + 1 < k.length()) {
            Map<String, Float> shortMatch = formPerFormStatMultipliers.get(k.substring(dot + 1));
            if (shortMatch != null) return shortMatch;
        }
        for (Map.Entry<String, Map<String, Float>> e : formPerFormStatMultipliers.entrySet()) {
            String stored = e.getKey();
            if (stored.equals(k) || stored.endsWith("." + k)) return e.getValue();
        }
        return null;
    }

    /**
     * Overall form scale (no per-stat): exact per-form → short-id → global.
     */
    public static float formScaleFor(String formKey) {
        if (formKey == null || formKey.isBlank()) {
            return formStatMultiplier;
        }
        Float override = lookupPerFormMap(formPerFormMultipliers, formKey);
        return override != null ? override : formStatMultiplier;
    }

    /**
     * Combined scale for form + combat stat.
     * {@code overall × globalStat × formStat} (missing pieces default to 1.0 for the last two;
     * overall falls back to {@link #formStatMultiplier}).
     */
    public static float formScaleFor(String formKey, String stat) {
        float overall = formScaleFor(formKey);
        float statScale = 1.0f;
        String s = normalizeStatKey(stat);
        if (!s.isEmpty()) {
            Float globalStat = formPerStatMultipliers.get(s);
            if (globalStat != null) statScale *= globalStat;
            Map<String, Float> formStats = lookupPerFormStatMap(formKey);
            if (formStats != null) {
                Float formStat = formStats.get(s);
                if (formStat != null) statScale *= formStat;
            }
        }
        return overall * statScale;
    }

    public static Map<String, Float> perFormMultipliersView() {
        return Collections.unmodifiableMap(formPerFormMultipliers);
    }

    public static Map<String, Float> perStatMultipliersView() {
        return Collections.unmodifiableMap(formPerStatMultipliers);
    }

    public static Map<String, Map<String, Float>> perFormStatMultipliersView() {
        return Collections.unmodifiableMap(formPerFormStatMultipliers);
    }

    /**
     * Applies server form scale to a DMZ form (or stack-form) stat mult.
     * Base form ({@code 1.0}) is unchanged; only the bonus above 1 is scaled.
     */
    public static double scaleFormMultiplier(double formMult) {
        return scaleFormMultiplier(formMult, null, null);
    }

    public static double scaleFormMultiplier(double formMult, String formKey) {
        return scaleFormMultiplier(formMult, formKey, null);
    }

    /**
     * @param formKey active form id ({@code group.form}) or null
     * @param stat    DMZ stat token (STR/PWR/…) or null for overall form scale only
     */
    public static double scaleFormMultiplier(double formMult, String formKey, String stat) {
        float m = formScaleFor(formKey, stat);
        if (Math.abs(m - 1.0f) < 1.0e-6f) return formMult;
        return 1.0 + (formMult - 1.0) * (double) m;
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
        /**
         * Global form bonus scale (see {@link XenoServerConfig#formStatMultiplier}).
         * Boxed so older JSON without the key stays {@code null} (use default 1.0).
         */
        public Float formStatMultiplier = 1.0f;
        /** Per-form overall overrides (key → scale). Null/empty = none. */
        public Map<String, Float> formPerFormMultipliers = new LinkedHashMap<>();
        /** Global per-stat overrides (str/pwr/def/…). */
        public Map<String, Float> formPerStatMultipliers = new LinkedHashMap<>();
        /** Per-form per-stat overrides. */
        public Map<String, Map<String, Float>> formPerFormStatMultipliers = new LinkedHashMap<>();
        public boolean kiOverchargeEnabled = true;
        public int kiOverchargeThreshold = 175;
        public float kiOverchargeSizePerPercent = 0.012f;
        public float kiOverchargeDamagePerPercent = 0.015f;
        public float kiOverchargeExplosionPerPercent = 0.014f;
        public float kiOverchargeMultiplier = 1.0f;
        public float kiOverchargeMaxScale = 3.0f;
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
        public float kickDownRangeBonus = 4.0f;
        public int maxComboSteps = 5;
        public int chargeMaxTicks = 28;
    }
}
