package net.bullettrain.xenopixelsmod.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Feature toggles and form stat multipliers.
 * Written to {@code config/xenopixelsmod-features.json}.
 *
 * <p>Form multipliers stack as:
 * {@code baseJsonStat * formGlobalMultiplier * formStatMultipliers[stat] * formOverrides[formId][stat]}
 */
public final class XenoFeaturesConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FMLPaths.CONFIGDIR.get().resolve("xenopixelsmod-features.json");

    // --- Stub feature flags (Qwen scaffold — OFF by default; enabling only runs in-memory init) ---
    /** STUB: in-memory skill nodes only. Not a DMZ skill tree UI. */
    public static boolean skillTreeEnabled = false;
    /** STUB: cosmetic maps only. Does not alter DMZ character creator. */
    public static boolean customizationEnabled = false;
    /** STUB: dummy training sessions — not BT3 combat combos. */
    public static boolean comboTrainingEnabled = false;
    /** STUB: does not change world time/weather. */
    public static boolean timeManipulationEnabled = false;
    /** STUB: no teacher NPCs / DMZ XP. */
    public static boolean teachingEnabled = false;
    /** STUB: no boss dungeon content. */
    public static boolean bossChallengeEnabled = false;
    /** STUB: no dual-wield combat hooks. */
    public static boolean twinArtEnabled = false;
    /** STUB: no dojo dimension / quest board. */
    public static boolean dojoEnabled = false;
    /** STUB: particle registry only; combat uses DmzAnimHelper. */
    public static boolean moveEffectsEnabled = false;
    /** Enables XenoFormRegistry menu catalog (forms themselves are LIVE via DMZ bootstrap). */
    public static boolean transformationEnabled = true;

    // --- Form registration / balance ---
    /** When true, install and register XenoPixels custom DMZ forms as mod forms. */
    public static boolean registerCustomForms = true;
    /** When true, rewrite installed DMZ form JSON multipliers using this config. */
    public static boolean applyFormMultipliersToDmzJson = true;
    /** Global scale applied to every form combat stat (1.0 = unchanged). */
    public static float formGlobalMultiplier = 1.0f;
    /**
     * Extra multiplier applied only to form {@code speedMultiplier} (movement / flight speed in DMZ).
     * Stacks on top of formGlobalMultiplier and formStatMultipliers["speed"].
     * Default 1.25 = 25% faster flight/move in our custom forms after bootstrap.
     */
    public static float formFlySpeedMultiplier = 1.25f;

    /** Per-stat global scales (str, pwr, def, stm, vit, ene, speed, skp). */
    public static final Map<String, Float> formStatMultipliers = new HashMap<>();

    /**
     * Per-form overrides: formId -> (stat -> scale).
     * Example: {@code "ssj10": {"str": 1.15, "pwr": 1.1}}
     */
    public static final Map<String, Map<String, Float>> formOverrides = new HashMap<>();

    private static final String[] STAT_KEYS = {
            "str", "pwr", "def", "stm", "vit", "ene", "speed", "skp"
    };

    static {
        resetDefaultStatMultipliers();
    }

    private XenoFeaturesConfig() {
    }

    private static void resetDefaultStatMultipliers() {
        formStatMultipliers.clear();
        for (String key : STAT_KEYS) {
            formStatMultipliers.put(key, 1.0f);
        }
    }

    public static void load() {
        if (!Files.exists(PATH)) {
            save();
            return;
        }
        try (Reader reader = Files.newBufferedReader(PATH)) {
            Data data = GSON.fromJson(reader, Data.class);
            if (data != null) {
                apply(data);
            }
        } catch (IOException e) {
            XenoPixelsMod.LOGGER.warn("Failed to load features config", e);
        }
    }

    public static void save() {
        try {
            Files.createDirectories(PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(PATH)) {
                GSON.toJson(snapshot(), writer);
            }
        } catch (IOException e) {
            XenoPixelsMod.LOGGER.warn("Failed to save features config", e);
        }
    }

    public static Data snapshot() {
        Data d = new Data();
        d.skillTreeEnabled = skillTreeEnabled;
        d.customizationEnabled = customizationEnabled;
        d.comboTrainingEnabled = comboTrainingEnabled;
        d.timeManipulationEnabled = timeManipulationEnabled;
        d.teachingEnabled = teachingEnabled;
        d.bossChallengeEnabled = bossChallengeEnabled;
        d.twinArtEnabled = twinArtEnabled;
        d.dojoEnabled = dojoEnabled;
        d.moveEffectsEnabled = moveEffectsEnabled;
        d.transformationEnabled = transformationEnabled;
        d.registerCustomForms = registerCustomForms;
        d.applyFormMultipliersToDmzJson = applyFormMultipliersToDmzJson;
        d.formGlobalMultiplier = formGlobalMultiplier;
        d.formFlySpeedMultiplier = formFlySpeedMultiplier;
        d.formStatMultipliers = new HashMap<>(formStatMultipliers);
        d.formOverrides = new HashMap<>();
        for (Map.Entry<String, Map<String, Float>> e : formOverrides.entrySet()) {
            d.formOverrides.put(e.getKey(), new HashMap<>(e.getValue()));
        }
        return d;
    }

    public static void apply(Data d) {
        if (d == null) {
            return;
        }
        skillTreeEnabled = d.skillTreeEnabled;
        customizationEnabled = d.customizationEnabled;
        comboTrainingEnabled = d.comboTrainingEnabled;
        timeManipulationEnabled = d.timeManipulationEnabled;
        teachingEnabled = d.teachingEnabled;
        bossChallengeEnabled = d.bossChallengeEnabled;
        twinArtEnabled = d.twinArtEnabled;
        dojoEnabled = d.dojoEnabled;
        moveEffectsEnabled = d.moveEffectsEnabled;
        transformationEnabled = d.transformationEnabled;
        registerCustomForms = d.registerCustomForms;
        applyFormMultipliersToDmzJson = d.applyFormMultipliersToDmzJson;
        formGlobalMultiplier = d.formGlobalMultiplier > 0f ? d.formGlobalMultiplier : 1.0f;
        formFlySpeedMultiplier = d.formFlySpeedMultiplier > 0f ? d.formFlySpeedMultiplier : 1.0f;

        formStatMultipliers.clear();
        resetDefaultStatMultipliers();
        if (d.formStatMultipliers != null) {
            for (Map.Entry<String, Float> e : d.formStatMultipliers.entrySet()) {
                if (e.getKey() != null && e.getValue() != null && e.getValue() > 0f) {
                    formStatMultipliers.put(e.getKey().toLowerCase(), e.getValue());
                }
            }
        }

        formOverrides.clear();
        if (d.formOverrides != null) {
            for (Map.Entry<String, Map<String, Float>> e : d.formOverrides.entrySet()) {
                if (e.getKey() == null || e.getValue() == null) {
                    continue;
                }
                Map<String, Float> cleaned = new HashMap<>();
                for (Map.Entry<String, Float> s : e.getValue().entrySet()) {
                    if (s.getKey() != null && s.getValue() != null && s.getValue() > 0f) {
                        cleaned.put(s.getKey().toLowerCase(), s.getValue());
                    }
                }
                formOverrides.put(e.getKey().toLowerCase(), cleaned);
            }
        }
    }

    /**
     * Effective scale for a form stat after global + per-stat + per-form overrides.
     */
    public static float effectiveStatScale(String formId, String statKey) {
        float scale = formGlobalMultiplier > 0f ? formGlobalMultiplier : 1.0f;
        String stat = statKey != null ? statKey.toLowerCase() : "";
        scale *= formStatMultipliers.getOrDefault(stat, 1.0f);
        if (formId != null) {
            Map<String, Float> overrides = formOverrides.get(formId.toLowerCase());
            if (overrides != null) {
                scale *= overrides.getOrDefault(stat, 1.0f);
            }
        }
        return scale > 0f ? scale : 1.0f;
    }

    public static float scaleStat(String formId, String statKey, float baseValue) {
        float v = baseValue * effectiveStatScale(formId, statKey);
        // Extra flight/move scale for DMZ speedMultiplier field
        if (statKey != null && "speed".equalsIgnoreCase(statKey)) {
            v *= formFlySpeedMultiplier > 0f ? formFlySpeedMultiplier : 1.0f;
        }
        return v;
    }

    public static class Data {
        public boolean skillTreeEnabled = false;
        public boolean customizationEnabled = false;
        public boolean comboTrainingEnabled = false;
        public boolean timeManipulationEnabled = false;
        public boolean teachingEnabled = false;
        public boolean bossChallengeEnabled = false;
        public boolean twinArtEnabled = false;
        public boolean dojoEnabled = false;
        public boolean moveEffectsEnabled = false;
        public boolean transformationEnabled = true;
        public boolean registerCustomForms = true;
        public boolean applyFormMultipliersToDmzJson = true;
        public float formGlobalMultiplier = 1.0f;
        public float formFlySpeedMultiplier = 1.25f;
        public Map<String, Float> formStatMultipliers = new HashMap<>();
        public Map<String, Map<String, Float>> formOverrides = new HashMap<>();

        public Data() {
            for (String key : STAT_KEYS) {
                formStatMultipliers.put(key, 1.0f);
            }
        }
    }
}
