package net.bullettrain.xenopixelsmod.features.progression;

import net.bullettrain.xenopixelsmod.capability.XenoCapabilities;
import net.bullettrain.xenopixelsmod.capability.XenoPlayerData;
import net.minecraft.server.level.ServerPlayer;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Slim combat skill tree — 4 skills, max level 3 each.
 * Unlocked with skill points from quests / dummy milestones.
 */
public final class CombatSkills {
    public static final String POWER = "power";       // outgoing damage
    public static final String GUARD = "guard";       // better guard reduction
    public static final String SPARKING = "sparking"; // faster meter build
    public static final String ULTIMATE = "ultimate"; // ultimate damage

    public static final Map<String, SkillDef> DEFS = new LinkedHashMap<>();

    static {
        DEFS.put(POWER, new SkillDef(POWER, "Power Strike", "Melee / combo damage +8% per level", 1));
        DEFS.put(GUARD, new SkillDef(GUARD, "Iron Guard", "Guard damage reduction +5% per level", 1));
        DEFS.put(SPARKING, new SkillDef(SPARKING, "Spark Drive", "Sparking meter build +15% per level", 1));
        DEFS.put(ULTIMATE, new SkillDef(ULTIMATE, "Finisher Focus", "Ultimate damage +12% per level", 1));
    }

    private CombatSkills() {}

    public record SkillDef(String id, String title, String desc, int pointCostPerLevel) {}

    public static int level(ServerPlayer player, String skillId) {
        if (player == null) return 0;
        return XenoCapabilities.get(player)
                .map(d -> d.getSkillLevel(skillId))
                .orElse(0);
    }

    public static float powerMult(ServerPlayer player) {
        return 1f + 0.08f * level(player, POWER);
    }

    public static float guardBonus(ServerPlayer player) {
        return 0.05f * level(player, GUARD);
    }

    public static float sparkingBuildMult(ServerPlayer player) {
        return 1f + 0.15f * level(player, SPARKING);
    }

    public static float ultimateMult(ServerPlayer player) {
        return 1f + 0.12f * level(player, ULTIMATE);
    }

    /** @return null on success, error message otherwise */
    public static String tryUnlock(ServerPlayer player, String skillId) {
        SkillDef def = DEFS.get(skillId == null ? "" : skillId.toLowerCase());
        if (def == null) return "Unknown skill. Try: power, guard, sparking, ultimate";
        XenoPlayerData data = XenoCapabilities.get(player).orElse(null);
        if (data == null) return "No player data";
        int cur = data.getSkillLevel(def.id);
        if (cur >= 3) return def.title + " is already max level (3)";
        int cost = def.pointCostPerLevel;
        if (data.getSkillPoints() < cost) {
            return "Need " + cost + " skill point(s). You have " + data.getSkillPoints();
        }
        data.setSkillPoints(data.getSkillPoints() - cost);
        data.setSkillLevel(def.id, cur + 1);
        return null;
    }
}
