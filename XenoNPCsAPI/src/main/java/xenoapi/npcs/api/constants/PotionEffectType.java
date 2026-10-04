package xenoapi.npcs.api.constants;

import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;

import java.util.ArrayList;
import java.util.List;

public final class PotionEffectType {
	public static final int NONE = 0;
	public static final int FIRE = 666;


	public static final int SPEED = 1;
	public static final int SLOWNESS = 2;
	public static final int HASTE = 3;
	public static final int MINING_FATIGUE = 4;
	public static final int STRENGTH = 5;
	public static final int INSTANT_HEALTH = 6;
	public static final int INSTANT_DAMAGE = 7;
	public static final int JUMP_BOOST = 8;
	public static final int NAUSEA = 9;
	public static final int REGENERATION = 10;
	public static final int RESISTANCE = 11;
	public static final int FIRE_RESISTANCE = 12;
	public static final int WATER_BREATHING = 13;
	public static final int INVISIBILITY = 14;
	public static final int BLINDNESS = 15;
	public static final int NIGHT_VISION = 16;
	public static final int HUNGER = 17;
	public static final int WEAKNESS = 18;
	public static final int POISON = 19;
	public static final int WITHER = 20;
	public static final int HEALTH_BOOST = 21;
	public static final int ABSORPTION = 22;
	public static final int SATURATION = 23;
	public static final int GLOWING = 24;
	public static final int LEVITATION = 25;
	public static final int LUCK = 26;
	public static final int UNLUCK = 27;
	public static final int SLOW_FALLING = 28;
	public static final int CONDUIT_POWER = 29;
	public static final int DOLPHINS_GRACE = 30;
	public static final int BAD_OMEN = 31;
	public static final int HERO_OF_THE_VILLAGE = 32;



	/**
	 * Holders in the same order as the int constants above, constant n is at index n - 1
	 */
	private static final List<Holder<MobEffect>> TYPES = List.of(
			MobEffects.MOVEMENT_SPEED, MobEffects.MOVEMENT_SLOWDOWN, MobEffects.DIG_SPEED, MobEffects.DIG_SLOWDOWN,
			MobEffects.DAMAGE_BOOST, MobEffects.HEAL, MobEffects.HARM, MobEffects.JUMP,
			MobEffects.CONFUSION, MobEffects.REGENERATION, MobEffects.DAMAGE_RESISTANCE, MobEffects.FIRE_RESISTANCE,
			MobEffects.WATER_BREATHING, MobEffects.INVISIBILITY, MobEffects.BLINDNESS, MobEffects.NIGHT_VISION,
			MobEffects.HUNGER, MobEffects.WEAKNESS, MobEffects.POISON, MobEffects.WITHER,
			MobEffects.HEALTH_BOOST, MobEffects.ABSORPTION, MobEffects.SATURATION, MobEffects.GLOWING,
			MobEffects.LEVITATION, MobEffects.LUCK, MobEffects.UNLUCK, MobEffects.SLOW_FALLING,
			MobEffects.CONDUIT_POWER, MobEffects.DOLPHINS_GRACE, MobEffects.BAD_OMEN, MobEffects.HERO_OF_THE_VILLAGE);

	/**
	 * @return The effect for one of the constants above, or null for NONE, FIRE or an unknown id
	 */
	public static Holder<MobEffect> getMCType(int effect) {
		if(effect < SPEED || effect > TYPES.size())
			return null;

		return TYPES.get(effect - 1);
	}

	public static List<Holder<MobEffect>> getMCAllTypes() {
		return new ArrayList<>(TYPES);
	}
}
