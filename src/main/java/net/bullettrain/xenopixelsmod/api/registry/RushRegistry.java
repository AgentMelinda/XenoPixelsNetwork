package net.bullettrain.xenopixelsmod.api.registry;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The catalogue of cinematic rush choreographies, and the place addons add their own.
 *
 * <p>A rush is chosen for a fighter by looking at their active form first and their race second,
 * falling back to a universal profile. Both lookups are <b>substring</b> matches against a
 * normalised name: the text is lower-cased, underscores and hyphens become spaces, and runs of
 * whitespace collapse. So the alias {@code ssb} matches an active form of {@code SSB} or
 * {@code Super_Saiyan_Blue_2}.
 *
 * <h2>Registering</h2>
 *
 * <p>Call from your mod's common setup. Registration is thread-safe, but nothing guarantees a rush
 * registered after a player is already mid-rush will apply to that one.
 *
 * <pre>{@code
 * RushRegistry.registerForm(
 *         Bt3RushDefinition.standard("ultra_instinct", "ultra_instinct"),
 *         "ultra instinct", "ui");
 * }</pre>
 *
 * <h2>The shadowing trap</h2>
 *
 * <p>Because matching is by substring and the first match wins, a form whose name <em>contains</em>
 * a built-in alias is claimed by that built-in. {@code super_saiyan_4} contains
 * {@code super saiyan}, so it would resolve to the plain Super Saiyan rush no matter what you
 * registered. When your form is a more specific case of an existing one, register it with
 * {@link Precedence#BEFORE_BUILT_INS} so it is tested first.
 */
public final class RushRegistry {

	/** Where a form registration sits relative to the rushes XenoPixels ships. */
	public enum Precedence {
		/**
		 * Tested after every built-in. The right choice for a form with a distinctive name, and
		 * the default, because it cannot take an existing form away from the base mod.
		 */
		AFTER_BUILT_INS,
		/**
		 * Tested before every built-in. Needed when your form's name contains a built-in alias -
		 * see the shadowing trap above.
		 */
		BEFORE_BUILT_INS
	}

	private record Matcher(String alias, Bt3RushDefinition definition) {}

	private static final Bt3RushDefinition UNIVERSAL = Bt3RushDefinition.standard("universal", "universal");

	private static final List<Matcher> PRIORITY_FORMS = new CopyOnWriteArrayList<>();
	private static final List<Matcher> FORMS = new CopyOnWriteArrayList<>();
	private static final List<Matcher> RACES = new CopyOnWriteArrayList<>();
	private static final Map<String, Bt3RushDefinition> BY_ID =
			Collections.synchronizedMap(new LinkedHashMap<>());

	/** Snapshot taken at the end of the static block, before any addon can register. */
	private static final List<Bt3RushDefinition> BUILT_INS;

	static {
		BY_ID.put(UNIVERSAL.id(), UNIVERSAL);

		registerRace(Bt3RushDefinition.standard("saiyan", "saiyan"), "saiyan");
		registerRace(Bt3RushDefinition.standard("human", "human"), "human", "earthling");
		registerRace(Bt3RushDefinition.standard("namekian", "namekian"), "namek");
		registerRace(Bt3RushDefinition.standard("majin", "majin"), "majin");
		registerRace(Bt3RushDefinition.standard("arcosian", "arcosian"), "arcos", "frieza");

		builtInForm("super_saiyan_blue", "blue", "ssb");
		builtInForm("super_saiyan_god", "god", "ssg");
		builtInForm("super_saiyan_3", "ssj3");
		builtInForm("super_saiyan_2", "ssj2");
		builtInForm("super_saiyan", "ssj");
		builtInForm("golden_arcosian", "golden", "gold form");
		builtInForm("potential_unleashed", "ultimate form");
		builtInForm("orange_namekian", "orange");
		builtInForm("giant_namekian", "giant", "great namek");
		builtInForm("pure_majin", "kid majin");

		BUILT_INS = List.copyOf(BY_ID.values());
	}

	private RushRegistry() {}

	/**
	 * Adds a rush for a race, matched against the player's race name.
	 *
	 * @param definition the choreography to run
	 * @param aliases    substrings that identify the race, such as {@code arcos} and {@code frieza}
	 */
	public static void registerRace(Bt3RushDefinition definition, String... aliases) {
		add(RACES, definition, aliases);
	}

	/** Adds a rush for a form, tested after every built-in. */
	public static void registerForm(Bt3RushDefinition definition, String... aliases) {
		registerForm(definition, Precedence.AFTER_BUILT_INS, aliases);
	}

	/** Adds a rush for a form at the given precedence. */
	public static void registerForm(Bt3RushDefinition definition, Precedence precedence, String... aliases) {
		add(precedence == Precedence.BEFORE_BUILT_INS ? PRIORITY_FORMS : FORMS, definition, aliases);
	}

	/**
	 * The rushes XenoPixels itself ships, without anything addons have added.
	 *
	 * <p>Useful when you need to tell shipped content from added content - our own animation assets
	 * only exist for these, and an addon is expected to ship its own.
	 */
	public static List<Bt3RushDefinition> builtIns() {
		return BUILT_INS;
	}

	/** The fallback used when neither the form nor the race matches anything. */
	public static Bt3RushDefinition universal() {
		return UNIVERSAL;
	}

	/** Looks a rush up by its exact id, falling back to {@link #universal()}. */
	public static Bt3RushDefinition byId(String id) {
		if (id == null || id.isBlank()) return UNIVERSAL;
		return BY_ID.getOrDefault(id, UNIVERSAL);
	}

	/** Every registered rush, each appearing once, in registration order. */
	public static Collection<Bt3RushDefinition> all() {
		synchronized (BY_ID) {
			return List.copyOf(BY_ID.values());
		}
	}

	/** Picks the rush for a fighter: form first, then race, then the universal fallback. */
	public static Bt3RushDefinition resolve(String race, String activeForm) {
		String form = normalize(activeForm);
		if (!form.isEmpty()) {
			Bt3RushDefinition byForm = firstMatch(PRIORITY_FORMS, form);
			if (byForm == null) byForm = firstMatch(FORMS, form);
			if (byForm != null) return byForm;
		}
		Bt3RushDefinition byRace = firstMatch(RACES, normalize(race));
		return byRace != null ? byRace : UNIVERSAL;
	}

	private static Bt3RushDefinition firstMatch(List<Matcher> matchers, String needle) {
		if (needle.isEmpty()) return null;
		for (Matcher matcher : matchers) {
			if (needle.contains(matcher.alias())) return matcher.definition();
		}
		return null;
	}

	private static void add(List<Matcher> target, Bt3RushDefinition definition, String... aliases) {
		if (definition == null) throw new IllegalArgumentException("definition");
		BY_ID.putIfAbsent(definition.id(), definition);
		target.add(new Matcher(normalize(definition.id()), definition));
		if (aliases == null) return;
		for (String alias : aliases) {
			String key = normalize(alias);
			if (!key.isEmpty()) target.add(new Matcher(key, definition));
		}
	}

	private static void builtInForm(String id, String... aliases) {
		add(FORMS, Bt3RushDefinition.standard(id, id), aliases);
	}

	private static String normalize(String value) {
		if (value == null) return "";
		return value.toLowerCase(Locale.ROOT).replace('_', ' ').replace('-', ' ').trim()
				.replaceAll("\s+", " ");
	}
}
