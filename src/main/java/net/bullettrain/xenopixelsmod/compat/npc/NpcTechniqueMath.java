package net.bullettrain.xenopixelsmod.compat.npc;

import com.dragonminez.common.config.ConfigManager;
import com.dragonminez.common.stats.techniques.KiAttackData;
import com.dragonminez.common.stats.techniques.StrikeAttackData;

/** NPC equivalents of DMZ technique cost formulas which normally require Player-only StatsData. */
final class NpcTechniqueMath {
    private NpcTechniqueMath() {}

    static double kiCost(NpcCombatProfile profile, KiAttackData data) {
        if (profile == null || data == null) return Double.POSITIVE_INFINITY;
        double output = Math.max(0.0, profile.kiDamage()) * data.getActualDamageMultiplier();
        double complexity = data.getActualSize() * 5.0
                + data.getActualSpeed() * 5.0
                + data.getActualArmorPenetration() * 0.2;
        double config = Math.max(0.0, ConfigManager.getTechniqueConfig()
                .getKiTypeConfig(data.getKiType()).getKiCostMultiplier());
        return Math.max(5.0, (output * 0.5 + complexity) * config / 2.0)
                * profile.chargeFactor();
    }

    static double strikeCost(NpcCombatProfile profile, StrikeAttackData data) {
        if (profile == null || data == null) return Double.POSITIVE_INFINITY;
        var config = ConfigManager.getTechniqueConfig().getStrikeConfig(data.getId());
        double output = profile.strikeDamage() * data.getActualDamageMultiplier()
                * Math.max(0.0, config.getDamageMultiplier());
        return Math.max(5.0, output * 0.35 * Math.max(0.0, config.getKiCostMultiplier()) / 2.0);
    }
}
