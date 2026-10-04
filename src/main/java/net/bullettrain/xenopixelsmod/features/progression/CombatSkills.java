package net.bullettrain.xenopixelsmod.features.progression;

import net.bullettrain.xenopixelsmod.capability.XenoCapabilities;
import net.bullettrain.xenopixelsmod.capability.XenoPlayerData;
import net.minecraft.server.level.ServerPlayer;
import net.bullettrain.xenopixelsmod.command.XenoPermissions;

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
    /** Gates whether Zanzoken can be used at all -- a one-shot unlock, like {@link #HAKAI}. */
    public static final String ZANZOKEN = "zanzoken";
    /** Gates whether Shi Shin No Ken (Multiform) can be used at all -- a one-shot unlock. */
    public static final String MULTIFORM = "multiform";
    /** Gates the grant-only rush-combo DMZ strike. */
    public static final String RUSHCOMBO = "rushcombo";
    /** Gates the grant-only lift-combo DMZ strike. */
    public static final String LIFTCOMBO = "liftcombo";
    /** Gates the four existing Xeno rush strikes when {@code rushAutoUnlock} is off. */
    public static final String RUSH = "rush";

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
                "Unlocks Hakai (hold J, or /xenohakai use)", 3, 1));
        DEFS.put(ZANZOKEN, new SkillDef(ZANZOKEN, "Zanzoken",
                "Unlocks Zanzoken (read a swing to dodge, leaving afterimages)", 3, 1));
        DEFS.put(MULTIFORM, new SkillDef(MULTIFORM, "Shi Shin No Ken",
                "Unlocks Shi Shin No Ken (divide into four bodies)", 3, 1));
        DEFS.put(RUSHCOMBO, new SkillDef(RUSHCOMBO, "Rush Combo",
                "Unlocks the Xeno rush-combo strike (grant first, then mastery)", 3, 3));
        DEFS.put(LIFTCOMBO, new SkillDef(LIFTCOMBO, "Lift Combo",
                "Unlocks the Xeno lift-combo strike (grant first, then mastery)", 3, 3));
        DEFS.put(RUSH, new SkillDef(RUSH, "Xeno Rush",
                "Unlocks the four Xeno rush strikes when auto-unlock is off (grant only)", 3, 1));
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

    public static boolean zanzokenUnlocked(ServerPlayer player) {
        return level(player, ZANZOKEN) >= 1;
    }

    public static boolean multiFormUnlocked(ServerPlayer player) {
        return level(player, MULTIFORM) >= 1;
    }

    public static boolean exclusive(String skillId) {
        return HAKAI.equals(skillId) || ZANZOKEN.equals(skillId) || MULTIFORM.equals(skillId)
                || RUSHCOMBO.equals(skillId) || LIFTCOMBO.equals(skillId) || RUSH.equals(skillId);
    }

    /**
     * Pure self-unlock gate used by {@link #tryUnlock} and unit tests.
     *
     * <p>Returns {@code null} when a non-admin may spend skill points on {@code skillId}.
     * Exclusive combo/rush skills always refuse unless {@code administrator} is true.
     * Permission API is applied by the caller so this stays Minecraft-free.
     */
    public static String selfUnlockRefusal(String skillId, boolean administrator) {
        return selfUnlockRefusal(skillId, administrator, 0);
    }

    /**
     * Exclusive skills refuse only the first unlock. After an administrator grant, further
     * levels are ordinary mastery the player can buy with skill points.
     */
    public static String selfUnlockRefusal(String skillId, boolean administrator, int currentLevel) {
        SkillDef def = DEFS.get(skillId == null ? "" : skillId.toLowerCase());
        if (def == null) {
            return unknownSkillMessage();
        }
        if (exclusive(def.id) && currentLevel <= 0 && !administrator) {
            return def.title + " is exclusive and must be granted by an administrator";
        }
        return null;
    }

    private static String unknownSkillMessage() {
        return "Unknown skill. Try: power, guard, sparking, ultimate, beam, barrage, guide, hakai, zanzoken, multiform, rushcombo, liftcombo, rush";
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
        return tryUnlock(player, skillId, false);
    }

    public static String grant(ServerPlayer player, String skillId) {
        return tryUnlock(player, skillId, true);
    }

    /** Revokes every level of a skill without refunding points. */
    public static String revoke(ServerPlayer player, String skillId) {
        SkillDef def = DEFS.get(skillId == null ? "" : skillId.toLowerCase());
        if (def == null) {
            return unknownSkillMessage();
        }
        XenoPlayerData data = XenoCapabilities.get(player).orElse(null);
        if (data == null) return "No player data";
        if (data.getSkillLevel(def.id()) <= 0) return def.title + " is not unlocked";
        data.setSkillLevel(def.id(), 0);
        net.bullettrain.xenopixelsmod.combat.technique.XenoSlotTechniques.revoke(player, def.id());
        net.bullettrain.xenopixelsmod.combat.technique.XenoComboStrikes.revoke(player, def.id());
        return null;
    }

    private static String tryUnlock(ServerPlayer player, String skillId, boolean administrator) {
        SkillDef def = DEFS.get(skillId == null ? "" : skillId.toLowerCase());
        if (def == null) {
            return unknownSkillMessage();
        }
        XenoPlayerData data = XenoCapabilities.get(player).orElse(null);
        if (data == null) return "No player data";
        int cur = data.getSkillLevel(def.id);
        if (exclusive(def.id) && cur <= 0 && !administrator
                && !XenoPermissions.hasPermission(player, XenoPermissions.SKILL_EXCLUSIVE_GRANT)) {
            return selfUnlockRefusal(skillId, false, cur);
        }
        if (cur >= def.maxLevel) {
            return def.title + " is already max level (" + def.maxLevel + ")";
        }
        int cost = def.pointCostPerLevel;
        if (!administrator) {
            if (data.getSkillPoints() < cost) {
                return "Need " + cost + " skill point(s). You have " + data.getSkillPoints();
            }
            data.setSkillPoints(data.getSkillPoints() - cost);
        }
        data.setSkillLevel(def.id, cur + 1);
        // A slot technique is granted to DMZ by the skill system, so a fresh unlock has to be
        // pushed through now or it would only appear after a relog.
        net.bullettrain.xenopixelsmod.combat.technique.XenoSlotTechniques.unlock(player);
        net.bullettrain.xenopixelsmod.combat.technique.XenoComboStrikes.unlock(player);
        return null;
    }
}
