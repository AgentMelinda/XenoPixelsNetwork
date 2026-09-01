package net.bullettrain.xenopixelsmod.compat.npc;

import com.dragonminez.common.config.ConfigManager;
import com.dragonminez.common.config.FormConfig;
import com.dragonminez.common.stats.extras.FormMasteries;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Reads live DragonMineZ {@link FormConfig.FormData} without constructing
 * {@code StatsData} (that type is Player-only).
 */
public final class NpcFormLookup {
    private NpcFormLookup() {}

    public static List<String> groups(String race) {
        try {
            Map<String, FormConfig> all = ConfigManager.getAllFormsForRace(
                    race == null || race.isBlank() ? "human" : race.trim());
            if (all == null || all.isEmpty()) {
                return List.of();
            }
            List<String> keys = new ArrayList<>(all.keySet());
            keys.sort(String::compareTo);
            return keys;
        } catch (Throwable ignored) {
            return List.of();
        }
    }

    public static List<String> forms(String race, String group) {
        if (group == null || group.isBlank()) {
            return List.of();
        }
        try {
            FormConfig cfg = ConfigManager.getFormGroup(
                    race == null || race.isBlank() ? "human" : race.trim(), group);
            if (cfg == null || cfg.getForms() == null || cfg.getForms().isEmpty()) {
                return List.of();
            }
            Map<String, FormConfig.FormData> map = cfg.getForms();
            List<String> keys = new ArrayList<>(map.keySet());
            keys.sort(Comparator
                    .comparingInt((String k) -> {
                        FormConfig.FormData data = map.get(k);
                        Integer level = data == null ? null : data.getUnlockOnSkillLevel();
                        return level == null ? 0 : level;
                    })
                    .thenComparing(String::compareTo));
            return keys;
        } catch (Throwable ignored) {
            return List.of();
        }
    }

    public static List<String> stackGroups() {
        try {
            Map<String, FormConfig> all = ConfigManager.getAllStackForms();
            if (all == null || all.isEmpty()) return List.of();
            List<String> keys = new ArrayList<>(all.keySet());
            keys.sort(String::compareTo);
            return keys;
        } catch (Throwable ignored) {
            return List.of();
        }
    }

    public static List<String> stackForms(String group) {
        if (group == null || group.isBlank()) return List.of();
        try {
            FormConfig cfg = ConfigManager.getStackFormGroup(group);
            if (cfg == null || cfg.getForms() == null) return List.of();
            Map<String, FormConfig.FormData> map = cfg.getForms();
            List<String> keys = new ArrayList<>(map.keySet());
            keys.sort(Comparator.comparingInt((String key) -> {
                Integer level = map.get(key) == null ? null : map.get(key).getUnlockOnSkillLevel();
                return level == null ? 0 : level;
            }).thenComparing(String::compareTo));
            return keys;
        } catch (Throwable ignored) {
            return List.of();
        }
    }

    public static FormConfig.FormData stackForm(String group, String form) {
        if (group == null || group.isBlank() || form == null || form.isBlank()) return null;
        try {
            return ConfigManager.getStackForm(group, form);
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static String step(List<String> list, String current, int dir) {
        if (list == null || list.isEmpty()) {
            return current == null ? "" : current;
        }
        int i = list.indexOf(current);
        if (i < 0) {
            return list.get(0);
        }
        int n = Math.floorMod(i + dir, list.size());
        return list.get(n);
    }

    public static void grantMastery(NpcCombatProfile profile, String group, String form) {
        if (profile == null || group == null || form == null || group.isBlank() || form.isBlank()) {
            return;
        }
        FormConfig.FormData data = form(profile.raceId, group, form);
        double max = maxMastery(data);
        profile.masteries.setMastery(group, form, max, max);
    }

    public static FormConfig.FormData form(String race, String group, String form) {
        if (group == null || group.isBlank() || form == null || form.isBlank()) {
            return null;
        }
        try {
            FormConfig.FormData data = ConfigManager.getForm(race, group, form);
            if (data != null) {
                return data;
            }
            data = ConfigManager.getForm(group, group, form);
            if (data != null) {
                return data;
            }
            for (String loaded : ConfigManager.getLoadedRaces()) {
                data = ConfigManager.getForm(loaded, group, form);
                if (data != null) {
                    return data;
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    public static double power(FormConfig.FormData data) {
        if (data == null || data.getPwrMultiplier() == null) {
            return 1.0;
        }
        double p = data.getPwrMultiplier();
        return p > 0.0 ? p : 1.0;
    }

    /** Returns the active form multiplier for one of DMZ's six primary stat ids. */
    public static double multiplier(FormConfig.FormData data, String stat) {
        if (data == null || stat == null) {
            return 1.0;
        }
        Double value = switch (stat.toUpperCase(java.util.Locale.ROOT)) {
            case "STR" -> data.getStrMultiplier();
            case "SKP" -> data.getSkpMultiplier();
            case "STM" -> data.getStmMultiplier();
            case "DEF", "RES" -> data.getDefMultiplier();
            case "VIT" -> data.getVitMultiplier();
            case "PWR" -> data.getPwrMultiplier();
            case "ENE" -> data.getEneMultiplier();
            default -> null;
        };
        return value != null && Double.isFinite(value) && value > 0.0 ? value : 1.0;
    }

    public static FormConfig.FormData activeForm(NpcCombatProfile profile) {
        if (profile == null || profile.formGroup == null || profile.formGroup.isBlank()
                || profile.formId == null || profile.formId.isBlank()) {
            return null;
        }
        return form(profile.raceId, profile.formGroup, profile.formId);
    }

    public static double multiplier(NpcCombatProfile profile, String stat) {
        double base = masteryMultiplier(activeForm(profile), stat,
                mastery(profile == null ? null : profile.masteries,
                        profile == null ? "" : profile.formGroup,
                        profile == null ? "" : profile.formId));
        double stack = masteryMultiplier(activeStackForm(profile), stat,
                mastery(profile == null ? null : profile.stackMasteries,
                        profile == null ? "" : profile.stackGroup,
                        profile == null ? "" : profile.stackId));
        try {
            if (ConfigManager.getServerConfig().getGameplay().getMultiplicationInsteadOfAdditionForMultipliers()) {
                return base * stack;
            }
        } catch (Throwable ignored) {
        }
        return 1.0 + (base - 1.0) + (stack - 1.0);
    }

    public static FormConfig.FormData activeStackForm(NpcCombatProfile profile) {
        return profile == null ? null : stackForm(profile.stackGroup, profile.stackId);
    }

    public static double masteryMultiplier(FormConfig.FormData data, String stat, double mastery) {
        double base = multiplier(data, stat);
        if (data == null || base <= 1.0) return base;
        double max = maxMastery(data);
        if (max <= 0.0) return base;
        double ratio = Math.max(0.0, Math.min(1.0, mastery / max));
        Double maxStats = data.getMaxStatsMultiplier();
        double factor = maxStats == null ? 1.0 : 1.0 + ratio * (maxStats - 1.0);
        return base * factor;
    }

    public static boolean compatible(NpcCombatProfile profile, FormConfig.FormData normal,
                                     String normalGroup, FormConfig.FormData stack, String stackGroup) {
        if (normal == null || stack == null) return true;
        try {
            return !normal.isIncompatibleWith(stackGroup, stack.getName())
                    && !stack.isIncompatibleWith(normalGroup, normal.getName());
        } catch (Throwable ignored) {
            return true;
        }
    }

    private static double mastery(FormMasteries values, String group, String form) {
        if (values == null || group == null || form == null || group.isBlank() || form.isBlank()) return 0.0;
        return values.getMastery(group, form);
    }

    public static boolean hasLightnings(NpcCombatProfile profile) {
        FormConfig.FormData data = activeForm(profile);
        return data != null && Boolean.TRUE.equals(data.getHasLightnings());
    }

    public static int lightningRgb(NpcCombatProfile profile) {
        FormConfig.FormData data = activeForm(profile);
        if (data != null) {
            java.util.OptionalInt parsed = NpcCombatProfile.parseHexColor(data.getLightningColor());
            if (parsed.isPresent()) {
                return parsed.getAsInt();
            }
        }
        return auraRgb(data).orElse(0xD9F4FF);
    }

    public static double maxMastery(FormConfig.FormData data) {
        if (data == null || data.getMaxMastery() == null) {
            return 100.0;
        }
        double m = data.getMaxMastery();
        return m > 0.0 ? m : 100.0;
    }

    /** Packed RGB from DMZ form aura color, or empty if the form has none. */
    public static java.util.OptionalInt auraRgb(FormConfig.FormData data) {
        if (data == null) {
            return java.util.OptionalInt.empty();
        }
        float[] rgb = data.getRgbAuraColor();
        if (rgb != null && rgb.length >= 3) {
            int r = Math.max(0, Math.min(255, Math.round(rgb[0] * 255f)));
            int g = Math.max(0, Math.min(255, Math.round(rgb[1] * 255f)));
            int b = Math.max(0, Math.min(255, Math.round(rgb[2] * 255f)));
            if (r != 0 || g != 0 || b != 0) {
                return java.util.OptionalInt.of((r << 16) | (g << 8) | b);
            }
        }
        String hex = data.getAuraColor();
        if (hex != null && !hex.isBlank()) {
            return NpcCombatProfile.parseHexColor(hex);
        }
        return java.util.OptionalInt.empty();
    }
}
