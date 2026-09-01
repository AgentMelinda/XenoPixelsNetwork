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
    public static final String BEAM = "beam";         // sustained beam ceiling and ramp
    public static final String BARRAGE = "barrage";   // longer ki volley window
    public static final String GUIDE = "guide";       // lock-on homing range / turn
    /** Gates whether Hakai can be used at all -- not a stacking buff like the others. */
    public static final String HAKAI = "hakai";

    public static final Map<String, SkillDef> DEFS = new LinkedHashMap<>();

    static {
        DEFS.put(POWER, new SkillDef(POWER, "Power Strike", "Melee / combo damage +8% per level", 1, 3));
        DEFS.put(GUARD, new SkillDef(GUARD, "Iron Guard", "Guard damage reduction +5% per level", 1, 3));
        DEFS.put(SPARKING, new SkillDef(SPARKING, "Spark Drive", "Sparking meter build +15% per level", 1, 3));
        DEFS.put(ULTIMATE, new SkillDef(ULTIMATE, "Finisher Focus", "Ultimate damage +12% per level", 1, 3));
        DEFS.put(BEAM, new SkillDef(BEAM, "Wave Mastery",
                "Sustained beams grow bigger, faster - higher ceiling and quicker ramp", 1, 3));
        DEFS.put(BARRAGE, new SkillDef(BARRAGE, "Volley Mastery",
                "Barrages fire longer and come off cooldown faster (server duration/cd config)", 1, 3));
        DEFS.put(GUIDE, new SkillDef(GUIDE, "Ki Guidance",
                "Hold Left Alt (or Mouse 5) to home lock-on ki, barrages, and surged beams", 1, 3));
        DEFS.put(HAKAI, new SkillDef(HAKAI, "Hakai",
                "Unlocks the Hakai erasure technique (Ctrl + left click)", 3, 1));
    }

    private CombatSkills() {}

    /** @param maxLevel a one-shot unlock (like Hakai) uses 1; stacking buffs use 3. */
    public record SkillDef(String id, String title, String desc, int pointCostPerLevel, int maxLevel) {}

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

    public static boolean hakaiUnlocked(ServerPlayer player) {
        return level(player, HAKAI) >= 1;
    }

    /**
     * Progress toward the next Wave Mastery level, awarded for sustaining a beam.
     *
     * <p>Granted as a skill point rather than a hidden XP bar so it flows through the same
     * unlock the other four skills use - the player spends it when they choose, and mastery
     * cannot silently level mid-fight and change how a beam behaves under them.
     */
    public static void awardBeamProgress(ServerPlayer player) {
        awardSkillProgress(player, BEAM);
    }

    public static void awardBarrageProgress(ServerPlayer player) {
        awardSkillProgress(player, BARRAGE);
    }

    private static void awardSkillProgress(ServerPlayer player, String skillId) {
        if (player == null) return;
        XenoCapabilities.get(player).ifPresent(data -> {
            if (data.getSkillLevel(skillId) >= 3) return;
            data.setSkillPoints(data.getSkillPoints() + 1);
        });
    }

    /** @return null on success, error message otherwise */
    public static String tryUnlock(ServerPlayer player, String skillId) {
        SkillDef def = DEFS.get(skillId == null ? "" : skillId.toLowerCase());
        if (def == null) {
            return "Unknown skill. Try: power, guard, sparking, ultimate, beam, barrage, guide, hakai";
        }
        XenoPlayerData data = XenoCapabilities.get(player).orElse(null);
        if (data == null) return "No player data";
        int cur = data.getSkillLevel(def.id);
        if (cur >= def.maxLevel) {
            return def.title + " is already max level (" + def.maxLevel + ")";
        }
        int cost = def.pointCostPerLevel;
        if (data.getSkillPoints() < cost) {
            return "Need " + cost + " skill point(s). You have " + data.getSkillPoints();
        }
        data.setSkillPoints(data.getSkillPoints() - cost);
        data.setSkillLevel(def.id, cur + 1);
        return null;
    }
}
