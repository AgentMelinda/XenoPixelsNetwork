package net.bullettrain.xenopixelsmod.compat.npc;

/**
 * The race and class scaling DragonMineZ applies to a stat, for an NPC.
 *
 * <p>Every DMZ stat formula is {@code stat × statScaling × multipliers}, where {@code statScaling}
 * comes from the character's race and class — a warrior's strength is worth more than a
 * spiritualist's. XenoPixels looked this up in exactly one place, for vitality, so an NPC's health
 * scaled by race and class while its damage did not: two NPCs with the same strength and different
 * races punched identically, where two players would not. That is what "their DMZ damage doesn't
 * translate to actual stats" meant.
 *
 * <p>One lookup for every stat, so health and damage cannot disagree about what an NPC is.
 *
 * @see NpcCombatProfile#characterClass()
 */
final class NpcStatScaling {

    /** DragonMineZ's own fallback race when a profile has none. */
    private static final String DEFAULT_RACE = "human";

    private NpcStatScaling() {
    }

    /**
     * Scaling for one stat, or {@code 1.0} when it cannot be resolved.
     *
     * <p>Falls back rather than throwing at every level: this runs inside damage and health
     * calculations, DragonMineZ's config may not be loaded yet on an early call, and an NPC that
     * hits for its unscaled stats is a much smaller problem than one that cannot be hit at all.
     *
     * @param stat one of {@code STR SKP RES VIT PWR ENE STM DEF}, matching DMZ's own stat names
     */
    static double of(NpcCombatProfile profile, String stat) {
        if (profile == null || stat == null) return 1.0;
        try {
            String race = profile.raceId == null || profile.raceId.isBlank()
                    ? DEFAULT_RACE : profile.raceId;
            var raceConfig = com.dragonminez.common.config.ConfigManager.getRaceStats(race);
            if (raceConfig == null) return 1.0;
            var scaling = raceConfig.getClassStats(profile.characterClass()).getStatScaling();
            if (scaling == null) return 1.0;
            return valid(select(scaling, stat));
        } catch (Throwable ignored) {
            return 1.0;
        }
    }

    private static Double select(com.dragonminez.common.config.RaceStatsConfig.StatScaling scaling,
                                 String stat) {
        return switch (stat.toUpperCase(java.util.Locale.ROOT)) {
            case "STR" -> scaling.getStrengthScaling();
            case "SKP" -> scaling.getStrikePowerScaling();
            case "PWR" -> scaling.getKiPowerScaling();
            case "VIT" -> scaling.getVitalityScaling();
            case "ENE" -> scaling.getEnergyScaling();
            case "STM" -> scaling.getStaminaScaling();
            case "RES", "DEF" -> scaling.getDefenseScaling();
            default -> null;
        };
    }

    /** A scaling of zero or less would erase the stat entirely; treat it as unset. */
    private static double valid(Double value) {
        if (value == null) return 1.0;
        double raw = value;
        return Double.isFinite(raw) && raw > 0.0 ? raw : 1.0;
    }
}
