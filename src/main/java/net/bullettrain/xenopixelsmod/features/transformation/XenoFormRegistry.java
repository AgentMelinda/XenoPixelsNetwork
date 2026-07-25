package net.bullettrain.xenopixelsmod.features.transformation;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.config.XenoFeaturesConfig;
import net.bullettrain.xenopixelsmod.features.transformation.TransformationManager.FormStats;
import net.bullettrain.xenopixelsmod.features.transformation.TransformationManager.TransformationType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Registers XenoPixels custom DMZ forms as this mod's official form catalog.
 * Base stats mirror the bundled form JSON; {@link XenoFeaturesConfig} multiplies them.
 */
public final class XenoFormRegistry {

    /** groupId -> ordered form ids in that group. */
    private static final Map<String, List<String>> GROUPS = new LinkedHashMap<>();

    private XenoFormRegistry() {
    }

    /**
     * Register (or re-register) every custom XenoPixels form with config-scaled stats.
     */
    public static void registerAll() {
        if (!XenoFeaturesConfig.registerCustomForms || !XenoFeaturesConfig.transformationEnabled) {
            XenoPixelsMod.LOGGER.info("XenoFormRegistry: custom form registration skipped (disabled in config)");
            return;
        }

        GROUPS.clear();
        TransformationManager.clearRegisteredForms();

        // --- Super Saiyan Legend (superforms skill, levels 9–14) ---
        register("supersaiyan_legend", "ssj5", "Super Saiyan 5", TransformationType.SUPER_SAIYAN_3,
                stats(4.5f, 4.5f, 3.4f, 1.05f, 1.05f, 1.1f, 1.12f, 4.5f));
        register("supersaiyan_legend", "ssj6", "Super Saiyan 6", TransformationType.SUPER_SAIYAN_3,
                stats(5.25f, 5.25f, 3.9f, 1.06f, 1.06f, 1.12f, 1.14f, 5.25f));
        register("supersaiyan_legend", "ssj7", "Super Saiyan 7", TransformationType.SUPER_SAIYAN_3,
                stats(6.0f, 6.0f, 4.4f, 1.07f, 1.07f, 1.14f, 1.16f, 6.0f));
        register("supersaiyan_legend", "ssj8", "Super Saiyan 8", TransformationType.GOD_SAIYAN,
                stats(6.9f, 6.9f, 5.0f, 1.08f, 1.08f, 1.16f, 1.18f, 6.9f));
        register("supersaiyan_legend", "ssj9", "Super Saiyan 9", TransformationType.GOD_SAIYAN,
                stats(7.8f, 7.8f, 5.6f, 1.09f, 1.09f, 1.18f, 1.20f, 7.8f));
        register("supersaiyan_legend", "ssj10", "Super Saiyan 10", TransformationType.ULTRA_INSTINCT,
                stats(9.0f, 9.0f, 6.4f, 1.1f, 1.1f, 1.2f, 1.22f, 9.0f));

        // --- XenoPixels Fan Super Saiyan (SSJ5–10, formType xenopixels_fan_ss) ---
        register("xenopixels_fan_ss", "ssj5", "Fan Super Saiyan 5", TransformationType.SUPER_SAIYAN_3,
                stats(4.5f, 4.5f, 3.4f, 1.05f, 1.05f, 1.1f, 1.12f, 4.5f));
        register("xenopixels_fan_ss", "ssj6", "Fan Super Saiyan 6", TransformationType.SUPER_SAIYAN_3,
                stats(5.25f, 5.25f, 3.9f, 1.06f, 1.06f, 1.12f, 1.14f, 5.25f));
        register("xenopixels_fan_ss", "ssj7", "Fan Super Saiyan 7", TransformationType.SUPER_SAIYAN_3,
                stats(6.0f, 6.0f, 4.4f, 1.07f, 1.07f, 1.14f, 1.16f, 6.0f));
        register("xenopixels_fan_ss", "ssj8", "Fan Super Saiyan 8", TransformationType.GOD_SAIYAN,
                stats(6.9f, 6.9f, 5.0f, 1.08f, 1.08f, 1.16f, 1.18f, 6.9f));
        register("xenopixels_fan_ss", "ssj9", "Fan Super Saiyan 9", TransformationType.GOD_SAIYAN,
                stats(7.8f, 7.8f, 5.6f, 1.09f, 1.09f, 1.18f, 1.20f, 7.8f));
        register("xenopixels_fan_ss", "ssj10", "Fan Super Saiyan 10", TransformationType.ULTRA_INSTINCT,
                stats(9.0f, 9.0f, 6.4f, 1.1f, 1.1f, 1.2f, 1.22f, 9.0f));

        // --- XenoPixels Gods Forms ---
        register("xenopixels_gods_forms", "ssg", "Super Saiyan God", TransformationType.GOD_SAIYAN,
                stats(4.8f, 4.8f, 3.8f, 1.08f, 1.08f, 1.2f, 1.22f, 4.8f));
        register("xenopixels_gods_forms", "ssb", "Super Saiyan Blue", TransformationType.GOD_SAIYAN,
                stats(5.6f, 5.6f, 4.4f, 1.1f, 1.1f, 1.25f, 1.24f, 5.6f));
        register("xenopixels_gods_forms", "ssbe", "Super Saiyan Blue Evolution", TransformationType.GOD_SAIYAN,
                stats(6.5f, 6.5f, 5.0f, 1.12f, 1.12f, 1.28f, 1.26f, 6.5f));
        register("xenopixels_gods_forms", "ssrose", "Super Saiyan Rose", TransformationType.GOKU_BLACK,
                stats(5.8f, 5.8f, 4.5f, 1.1f, 1.1f, 1.22f, 1.23f, 5.8f));
        register("xenopixels_gods_forms", "ssrose_evolution", "Super Saiyan Rose Evolution", TransformationType.GOKU_BLACK,
                stats(7.2f, 7.2f, 5.5f, 1.12f, 1.12f, 1.3f, 1.26f, 7.2f));
        register("xenopixels_gods_forms", "ui_sign", "Ultra Instinct Sign", TransformationType.ULTRA_INSTINCT,
                stats(6.8f, 6.8f, 5.4f, 1.1f, 1.1f, 1.28f, 1.3f, 6.8f));
        register("xenopixels_gods_forms", "ui", "Mastered Ultra Instinct", TransformationType.ULTRA_INSTINCT,
                stats(8.0f, 8.0f, 6.2f, 1.12f, 1.12f, 1.35f, 1.35f, 8.0f));
        register("xenopixels_gods_forms", "ue", "Ultra Ego", TransformationType.VEGETA_TREE,
                stats(8.4f, 8.4f, 5.8f, 1.15f, 1.15f, 1.32f, 1.28f, 8.4f));

        // --- Legendary ---
        register("xenopixels_saga_forms", "trunks_ikari", "Trunks Ikari", TransformationType.TURLES_FORM,
                stats(4.2f, 4.2f, 3.4f, 1.08f, 1.1f, 1.1f, 1.15f, 4.0f));

        XenoPixelsMod.LOGGER.info(
                "XenoFormRegistry: registered {} custom forms in {} groups (globalMult={})",
                TransformationManager.getAllForms().size(),
                GROUPS.size(),
                XenoFeaturesConfig.formGlobalMultiplier);
    }

    private static FormStats stats(
            float str, float pwr, float def, float stm, float vit, float ene, float speed, float skp) {
        return new FormStats(str, pwr, def, stm, vit, ene, speed, skp);
    }

    private static void register(
            String groupId,
            String formId,
            String displayName,
            TransformationType type,
            FormStats base) {
        FormStats scaled = scale(formId, base);
        String fullId = groupId + "." + formId;
        // Always register full id; short id only if free (avoids ssj5 legend vs fan clash)
        TransformationManager.registerForm(fullId, displayName, type, groupId, formId, scaled);
        if (TransformationManager.getForm(formId).isEmpty()) {
            TransformationManager.registerForm(formId, displayName, type, groupId, formId, scaled);
        }

        GROUPS.computeIfAbsent(groupId, k -> new ArrayList<>()).add(formId);
    }

    private static FormStats scale(String formId, FormStats base) {
        return new FormStats(
                XenoFeaturesConfig.scaleStat(formId, "str", base.str()),
                XenoFeaturesConfig.scaleStat(formId, "pwr", base.pwr()),
                XenoFeaturesConfig.scaleStat(formId, "def", base.def()),
                XenoFeaturesConfig.scaleStat(formId, "stm", base.stm()),
                XenoFeaturesConfig.scaleStat(formId, "vit", base.vit()),
                XenoFeaturesConfig.scaleStat(formId, "ene", base.ene()),
                XenoFeaturesConfig.scaleStat(formId, "speed", base.speed()),
                XenoFeaturesConfig.scaleStat(formId, "skp", base.skp()));
    }

    public static Map<String, List<String>> getGroups() {
        Map<String, List<String>> copy = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> e : GROUPS.entrySet()) {
            copy.put(e.getKey(), Collections.unmodifiableList(new ArrayList<>(e.getValue())));
        }
        return Collections.unmodifiableMap(copy);
    }

    public static List<String> getFormIds() {
        List<String> ids = new ArrayList<>();
        for (List<String> group : GROUPS.values()) {
            ids.addAll(group);
        }
        return ids;
    }

    /**
     * Build menu/catalog entries describing registered forms with live scaled stats.
     */
    public static List<MenuFormEntry> buildMenuEntries() {
        List<MenuFormEntry> entries = new ArrayList<>();
        for (Map.Entry<String, List<String>> group : GROUPS.entrySet()) {
            entries.add(new MenuFormEntry(
                    "group_" + group.getKey(),
                    prettyGroup(group.getKey()),
                    "Form group · " + group.getValue().size() + " forms",
                    "XenoPixels custom group registered as mod forms. Multipliers from xenopixelsmod-features.json.",
                    0xFF42A5F5,
                    null));
            for (String formId : group.getValue()) {
                String fullId = group.getKey() + "." + formId;
                TransformationManager.TransformationForm form =
                        TransformationManager.getForm(fullId)
                                .or(() -> TransformationManager.getForm(formId))
                                .orElse(null);
                if (form == null) {
                    continue;
                }
                FormStats s = form.getStats();
                String detail = String.format(
                        "STR x%.2f · PWR x%.2f · DEF x%.2f · SPD x%.2f · ENE x%.2f (config-scaled)",
                        s.str(), s.pwr(), s.def(), s.speed(), s.ene());
                entries.add(new MenuFormEntry(
                        fullId,
                        form.getDisplayName(),
                        group.getKey() + " · " + formId,
                        detail,
                        accentFor(formId),
                        s));
            }
        }
        return entries;
    }

    private static String prettyGroup(String groupId) {
        return switch (groupId) {
            case "supersaiyan_legend" -> "Super Saiyan Legend (SSJ5–10)";
            case "xenopixels_fan_ss" -> "XenoPixels Fan Super Saiyan (SSJ5–10)";
            case "xenopixels_gods_forms" -> "XenoPixels Gods Forms";
            case "xenopixels_saga_forms" -> "XenoPixels Saga Forms";
            default -> groupId;
        };
    }

    private static int accentFor(String formId) {
        return switch (formId) {
            case "ssj5" -> 0xFFE1BEE7;
            case "ssj6" -> 0xFFC62828;
            case "ssj7" -> 0xFF7B1FA2;
            case "ssj8" -> 0xFFB3E5FC;
            case "ssj9" -> 0xFFFFF59D;
            case "ssj10" -> 0xFFE53935;
            case "ssg" -> 0xFFE53935;
            case "ssb" -> 0xFF1E88E5;
            case "ssbe" -> 0xFF1565C0;
            case "ssrose", "ssrose_evolution" -> 0xFFFF69B4;
            case "ui_sign", "ui" -> 0xFFECEFF1;
            case "ue" -> 0xFF7B1FA2;
            case "trunks_ikari" -> 0xFF90CAF9;
            default -> 0xFF42A5F5;
        };
    }

    public record MenuFormEntry(
            String id,
            String title,
            String subtitle,
            String detail,
            int accent,
            FormStats stats) {
    }
}
