package net.bullettrain.xenopixelsmod.api.dmz;

import com.dragonminez.common.config.ConfigManager;
import com.dragonminez.common.config.FormConfig;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;

/**
 * Reads DragonMineZ form definitions without needing a player.
 *
 * <p>Forms are configuration, not code: they live under {@code config/dragonminez/races/} and a
 * server owner can edit or add them. So the set of forms is only known at runtime, and asking the
 * config is the only honest way to find out what exists. This is what XenoPixels itself uses to
 * drive transformations on entities that have no DragonMineZ stats of their own, such as NPCs.
 *
 * <p>Every method returns empty rather than throwing when the config has not loaded yet, which is
 * the normal state before the server finishes starting.
 *
 * <p>For a specific player's <em>current</em> form, use {@link DmzAccess} instead.
 */
public final class DmzForms {

	private DmzForms() {}

	/** Every form group defined for a race, keyed by group name. Empty when the race is unknown. */
	public static Map<String, FormConfig> groupsForRace(String race) {
		if (race == null || race.isBlank()) return Collections.emptyMap();
		try {
			Map<String, FormConfig> groups = ConfigManager.getAllFormsForRace(race);
			return groups == null ? Collections.emptyMap() : groups;
		} catch (Throwable notLoaded) {
			return Collections.emptyMap();
		}
	}

	/** One form group for a race, or empty when the race or group is unknown. */
	public static Optional<FormConfig> group(String race, String group) {
		if (race == null || race.isBlank() || group == null || group.isBlank()) return Optional.empty();
		try {
			return Optional.ofNullable(ConfigManager.getFormGroup(race, group));
		} catch (Throwable notLoaded) {
			return Optional.empty();
		}
	}

	/**
	 * One form's data - its multipliers, drains, colours and aura.
	 *
	 * @param race  the race the form belongs to
	 * @param group the form group, since a form id is only unique within its group
	 * @param form  the form id
	 */
	public static Optional<FormConfig.FormData> form(String race, String group, String form) {
		if (form == null || form.isBlank()) return Optional.empty();
		return group(race, group)
				.map(FormConfig::getForms)
				.map(forms -> forms.get(form));
	}

	/** Every stack form group. Stack forms layer on top of a regular form. */
	public static Map<String, FormConfig> stackGroups() {
		try {
			Map<String, FormConfig> groups = ConfigManager.getAllStackForms();
			return groups == null ? Collections.emptyMap() : groups;
		} catch (Throwable notLoaded) {
			return Collections.emptyMap();
		}
	}

	/** One stack form's data, or empty when the group or form is unknown. */
	public static Optional<FormConfig.FormData> stackForm(String group, String form) {
		if (group == null || group.isBlank() || form == null || form.isBlank()) return Optional.empty();
		try {
			FormConfig config = ConfigManager.getStackFormGroup(group);
			if (config == null || config.getForms() == null) return Optional.empty();
			return Optional.ofNullable(config.getForms().get(form));
		} catch (Throwable notLoaded) {
			return Optional.empty();
		}
	}
}
