package net.bullettrain.xenopixelsmod.api.dmz;

import com.dragonminez.common.init.entities.IBattlePower;
import com.dragonminez.common.init.entities.MobBattlePowerHelper;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.StatsProvider;
import com.dragonminez.common.stats.character.Character;
import com.dragonminez.common.stats.skills.Skills;
import com.dragonminez.compat.util.LazyOptional;
import net.minecraft.world.entity.Entity;

import java.util.Optional;

/**
 * Safe reads of a player's DragonMineZ state.
 *
 * <p>Getting at that state directly means walking a four-step chain - fetch the capability, check
 * it is present, unwrap it, then check the data has finished loading - and skipping any step gives
 * you nulls or zeroes that look like real values. A player who has just joined has an attachment
 * but no loaded data yet, so {@code getBattlePower()} on it returns 0 rather than the truth.
 * Everything here does the whole chain and reports absence honestly.
 *
 * <p>Works on either side, but the client only has data for players it is tracking.
 *
 * <p>These are reads. To change DragonMineZ state, mutate {@link StatsData} on the server and then
 * push the change with {@link DmzSync} - a mutation without a sync leaves the client showing stale
 * numbers.
 */
public final class DmzAccess {

	private DmzAccess() {}

	/**
	 * The entity's DragonMineZ stats, or empty when it is not a player, has no data attached, or
	 * has not finished loading. Never throws.
	 */
	public static Optional<StatsData> stats(Entity entity) {
		if (entity == null) return Optional.empty();
		try {
			LazyOptional<StatsData> holder = StatsProvider.get(StatsCapability.INSTANCE, entity);
			if (!holder.isPresent()) return Optional.empty();
			StatsData data = holder.orElse(null);
			if (data == null || !data.isDataLoaded()) return Optional.empty();
			return Optional.of(data);
		} catch (Throwable notAvailable) {
			// Reads happen during rendering and damage handling, where a thrown exception costs a
			// frame or a hit. Absence is always a valid answer here.
			return Optional.empty();
		}
	}

	/** True when this entity is a player whose DragonMineZ data is loaded and safe to read. */
	public static boolean isReady(Entity entity) {
		return stats(entity).isPresent();
	}

	/** The player's race id, or an empty string when unavailable. */
	public static String race(Entity entity) {
		return character(entity).map(Character::getRace).orElse("");
	}

	/**
	 * The player's active transformation, or an empty string when they are in base form or the
	 * data is unavailable. Pair with {@link #activeFormGroup(Entity)} - a form id is only unique
	 * within its group.
	 */
	public static String activeForm(Entity entity) {
		return character(entity).map(Character::getActiveForm).orElse("");
	}

	/** The group the active transformation belongs to, or an empty string. */
	public static String activeFormGroup(Entity entity) {
		return character(entity).map(Character::getActiveFormGroup).orElse("");
	}

	/** The active stack form, which layers on top of a regular form, or an empty string. */
	public static String activeStackForm(Entity entity) {
		return character(entity).map(Character::getActiveStackForm).orElse("");
	}

	/** The player's battle power, or {@code 0} when unavailable. */
	public static float battlePower(Entity entity) {
		return stats(entity).map(StatsData::getBattlePower).orElse(0f);
	}

	/** One place a power level can come from; may throw when that source is not ready. */
	@FunctionalInterface
	public interface PowerSource {
		double get() throws Exception;
	}

	/**
	 * The DragonMineZ power level ki sense and the scouter show, for any entity - player, NPC or
	 * mob (2026-09-29). {@link #battlePower} reads only a player's stats and is 0 for everything
	 * else; this is the one to compare fighters by.
	 *
	 * <p>DMZ's {@code LivingEntityMixin} makes every {@code LivingEntity} an {@link IBattlePower};
	 * that cached figure is what ki sense displays, and a Xeno NPC overrides it with its real one.
	 */
	public static double powerLevel(Entity entity) {
		if (!(entity instanceof net.minecraft.world.entity.LivingEntity living)) return 0.0;
		return pick(living instanceof net.minecraft.world.entity.player.Player,
				() -> battlePower(living),
				() -> ((IBattlePower) (Object) living).getBattlePower(),
				() -> {
					StatsData npc = net.bullettrain.xenopixelsmod.compat.npc.NpcDmzStats.stats(living);
					return npc == null ? 0.0 : npc.getBattlePower();
				},
				() -> MobBattlePowerHelper.calculate(living));
	}

	/**
	 * The first power level above zero: a player's own stats, then the value DMZ caches on every
	 * living entity (what ki sense shows), then an NPC's Xeno DMZ stats, then DMZ's own mob
	 * formula. A source that throws is skipped. Non-players never read the player capability.
	 */
	static double pick(boolean player, PowerSource playerStats, PowerSource cached,
					   PowerSource npcStats, PowerSource mobFormula) {
		PowerSource[] order = player
				? new PowerSource[]{playerStats, cached}
				: new PowerSource[]{cached, npcStats, mobFormula};
		for (PowerSource source : order) {
			try {
				double v = source.get();
				if (Double.isFinite(v) && v > 0.0) return v;
			} catch (Throwable ignored) {
				// Not ready or not present: the next source answers.
			}
		}
		return 0.0;
	}

	/** Whether a DragonMineZ skill is currently switched on, for example {@code fly}. */
	public static boolean isSkillActive(Entity entity, String skill) {
		if (skill == null || skill.isBlank()) return false;
		return skills(entity).map(s -> s.isSkillActive(skill)).orElse(false);
	}

	/** A skill's level, or {@code 0} when unavailable or not learned. */
	public static int skillLevel(Entity entity, String skill) {
		if (skill == null || skill.isBlank()) return 0;
		return skills(entity).map(s -> s.getSkillLevel(skill)).orElse(0);
	}

	private static Optional<Character> character(Entity entity) {
		return stats(entity).map(StatsData::getCharacter).filter(c -> c != null);
	}

	private static Optional<Skills> skills(Entity entity) {
		return stats(entity).map(StatsData::getSkills).filter(s -> s != null);
	}
}
