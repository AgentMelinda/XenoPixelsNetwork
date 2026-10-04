package net.bullettrain.xenopixelsmod.dmz.form;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Validates race / form-group ids for the race form-group maker (PR-D6a, KD15).
 *
 * <p>Only races that already ship under {@code data/xenopixelsmod/dmz/races/} (same set as DMZ's
 * {@code RaceSelectionScreen}) are accepted. Unknown race ids are rejected; this class never
 * creates {@code races/<id>/} folders.
 *
 * <p>Persistence stays on the existing Form Studio path:
 * {@code FormEditorNetwork.save} → {@link DmzFormEditorService#save} (session backup unchanged).
 * This guard only checks identity before that path.
 */
public final class RaceFormGroupGuard {
    /**
     * Races with installed Xeno/DMZ race content. Mirrors
     * {@code data/xenopixelsmod/dmz/races/*} and DMZ race selection — not a new-race registrar.
     */
    private static final Set<String> KNOWN_RACES = Set.of(
            "saiyan", "human", "namekian", "frostdemon", "majin", "bioandroid"
    );

    /**
     * Form-group stems under those race folders (see
     * {@code src/main/resources/data/xenopixelsmod/dmz/races/&lt;race&gt;/forms/*.json}).
     */
    private static final Map<String, Set<String>> INSTALLED_GROUPS = installedGroups();

    private RaceFormGroupGuard() {
    }

    /** Known DMZ/Xeno race ids the maker may target. */
    public static Set<String> knownRaces() {
        return KNOWN_RACES;
    }

    /** Installed form-group ids for {@code race}, or empty when the race is unknown. */
    public static Set<String> installedGroups(String race) {
        String key = normal(race);
        Set<String> groups = INSTALLED_GROUPS.get(key);
        return groups == null ? Set.of() : groups;
    }

    /** Rejects race ids that are not already known (KD15 — no new race registration). */
    public static Result validateRace(String race) {
        String key = normal(race);
        if (key.isEmpty() || !isId(key)) {
            return Result.fail("Race id must start a-z0-9, then a-z0-9_.-");
        }
        if (!KNOWN_RACES.contains(key)) {
            return Result.fail("Unknown race id: " + key + " (form-group maker targets existing races only)");
        }
        return Result.pass();
    }

    /**
     * Accepts a form group on a known race when it is installed under that race's forms folder
     * or already owned in {@link DmzFormMetadataRegistry}.
     */
    public static Result validateGroup(String race, String group) {
        Result raceResult = validateRace(race);
        if (!raceResult.ok()) return raceResult;
        String raceKey = normal(race);
        String groupKey = normal(group);
        if (groupKey.isEmpty() || !isId(groupKey)) {
            return Result.fail("Group id must start a-z0-9, then a-z0-9_.-");
        }
        if (installedGroups(raceKey).contains(groupKey)) {
            return Result.pass();
        }
        if (DmzFormMetadataRegistry.get(DmzFormKind.NORMAL, raceKey, groupKey) != null) {
            return Result.pass();
        }
        return Result.fail("Unknown form group '" + groupKey + "' for race '" + raceKey + "'");
    }

    private static Map<String, Set<String>> installedGroups() {
        Map<String, Set<String>> map = new LinkedHashMap<>();
        put(map, "saiyan",
                "supersaiyan_legend",
                "xenopixels_fan_ss",
                "xenopixels_gods_forms",
                "xenopixels_hakaishin",
                "xenopixels_saga_forms");
        put(map, "human", "xenopixels_hakaishin");
        put(map, "namekian", "xenopixels_hakaishin");
        put(map, "frostdemon", "xenopixels_dark_frieza", "xenopixels_hakaishin");
        put(map, "majin", "xenopixels_hakaishin");
        put(map, "bioandroid", "xenopixels_hakaishin");
        Map<String, Set<String>> frozen = new LinkedHashMap<>();
        for (Map.Entry<String, Set<String>> entry : map.entrySet()) {
            frozen.put(entry.getKey(), Collections.unmodifiableSet(entry.getValue()));
        }
        return Collections.unmodifiableMap(frozen);
    }

    private static void put(Map<String, Set<String>> map, String race, String... groups) {
        Set<String> set = new LinkedHashSet<>();
        for (String group : groups) set.add(group);
        map.put(race, set);
    }

    private static String normal(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean isId(String value) {
        return value.matches("[a-z0-9][a-z0-9_.-]*") && !value.contains("..");
    }

    /** Validation outcome for maker / Task 8 gates. */
    public record Result(boolean success, String message) {
        public boolean ok() {
            return success;
        }

        public static Result pass() {
            return new Result(true, "");
        }

        public static Result fail(String message) {
            return new Result(false, message == null ? "Invalid" : message);
        }
    }
}
