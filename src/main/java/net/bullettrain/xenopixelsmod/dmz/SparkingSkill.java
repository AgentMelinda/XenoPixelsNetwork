package net.bullettrain.xenopixelsmod.dmz;

import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.StatsProvider;
import com.dragonminez.common.stats.skills.Skills;
import net.minecraft.server.level.ServerPlayer;

/**
 * Sparking as a DragonMineZ skill.
 *
 * <p>It is a real entry in DMZ's {@code skills.json} rather than a Xeno-only flag, so it shows up
 * in the skill list, can be bought from a master, and is saved and synced by DMZ itself. The entry
 * and the master offerings are installed by {@link DmzContentBootstrap} from
 * {@code data/xenopixelsmod/dmz/skills_patch.json}.
 *
 * <p><b>Why level 13 of {@code potentialunlock}.</b> DMZ's release ceiling is
 * {@code 50 + potentialunlock_level * 5}, and {@code potentialunlock} has exactly thirteen cost
 * tiers — so level 13 is both the last one and the point where a player caps out at 115%. Sparking
 * is what lifts that ceiling, so it becomes available exactly when the normal one has run out.
 * A player who gets there is granted it outright; a master is the other way in.
 */
public final class SparkingSkill {

    /** Skill id, matching the entry installed into DMZ's skills.json. */
    public static final String ID = "sparking";

    /** Maxed {@code potentialunlock}: thirteen tiers, and a 115% release ceiling. */
    public static final int REQUIRED_POTENTIAL_LEVEL = 13;

    /** One level; the skill is a gate, not a ladder. */
    private static final int MAX_LEVEL = 1;

    private SparkingSkill() {
    }

    /** Whether this player has Sparking at all. */
    public static boolean has(ServerPlayer player) {
        Skills skills = skillsOf(player);
        return skills != null && skills.hasSkill(ID) && skills.getSkillLevel(ID) > 0;
    }

    /**
     * Grants Sparking once the player has maxed {@code potentialunlock}.
     *
     * <p>Safe to call repeatedly: it returns immediately when the skill is already held, and
     * {@code registerDefaultSkill} only creates the entry when it is missing.
     *
     * @return true when this call is what granted it
     */
    public static boolean grantIfEligible(ServerPlayer player) {
        Skills skills = skillsOf(player);
        if (skills == null) return false;
        if (skills.hasSkill(ID) && skills.getSkillLevel(ID) > 0) return false;
        if (skills.getSkillLevel("potentialunlock") < REQUIRED_POTENTIAL_LEVEL) return false;

        skills.registerDefaultSkill(ID, MAX_LEVEL);
        skills.setSkillLevel(ID, MAX_LEVEL);
        return true;
    }

    private static Skills skillsOf(ServerPlayer player) {
        if (player == null) return null;
        StatsData data = StatsProvider.get(StatsCapability.INSTANCE, player).orElse(null);
        return data != null ? data.getSkills() : null;
    }
}
