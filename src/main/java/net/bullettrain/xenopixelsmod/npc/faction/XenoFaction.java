package net.bullettrain.xenopixelsmod.npc.faction;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * One faction: who it is, how it feels about the player, and who it does not get on with.
 *
 * <p>NPCs have carried a {@code faction} string since the editor gained the field, and nothing has
 * ever read it - it was a label with no meaning behind it. This is the meaning.
 *
 * <p>Standing is a single number per player, not a matrix. A player is liked or disliked by a
 * faction; the fixed {@code hostileTo} list records faction relationships for authored content.
 * Automatic native target acquisition currently uses player standing and the explicit mob list.
 *
 * @param id the faction's own id, lower case
 * @param name what to show a player
 * @param color 0xRRGGBB, for the name and any badge
 * @param hostileTo ids of factions with an authored hostile relationship
 * @param defaultStanding where a player starts, before any quest or kill moves it
 * @param attackedByMobs persisted compatibility setting for mob attacks on faction members
 * @param aggressiveToMobs whether faction NPCs acquire listed ordinary mobs
 * @param attackableMobs entity type IDs eligible for automatic acquisition
 */
public record XenoFaction(String id, String name, int color, List<String> hostileTo,
                          int defaultStanding, boolean attackedByMobs,
                          boolean aggressiveToMobs, List<String> attackableMobs) {

    /** Older saved factions stay passive toward mobs until an operator opts in. */
    public XenoFaction(String id, String name, int color, List<String> hostileTo,
                       int defaultStanding, boolean attackedByMobs) {
        this(id, name, color, hostileTo, defaultStanding, attackedByMobs, false, List.of());
    }

    /** Below this a faction's NPCs turn on the player. */
    public static final int HOSTILE_BELOW = -100;

    /** At or above this a faction's NPCs will defend the player. */
    public static final int FRIENDLY_AT = 100;

    /** Standing is clamped to this range, so one long grind cannot put it out of reach. */
    public static final int MIN_STANDING = -1000;
    public static final int MAX_STANDING = 1000;

    public XenoFaction {
        id = id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
        name = name == null || name.isBlank() ? id : name;
        color = color & 0xFFFFFF;
        hostileTo = List.copyOf(hostileTo == null ? List.of() : hostileTo);
        defaultStanding = clampStanding(defaultStanding);
        attackableMobs = (attackableMobs == null ? List.<String>of() : attackableMobs).stream()
                .filter(value -> value != null && !value.isBlank())
                .map(value -> value.trim().toLowerCase(Locale.ROOT))
                .distinct().limit(64).toList();
    }

    /** Keeps a standing inside the range every comparison here assumes. */
    public static int clampStanding(int standing) {
        return Math.max(MIN_STANDING, Math.min(MAX_STANDING, standing));
    }

    /** How this faction treats a player at {@code standing}. */
    public static Attitude attitudeAt(int standing) {
        if (standing <= HOSTILE_BELOW) {
            return Attitude.HOSTILE;
        }
        return standing >= FRIENDLY_AT ? Attitude.FRIENDLY : Attitude.NEUTRAL;
    }

    /** What a faction will do about a player. */
    public enum Attitude {
        HOSTILE,
        NEUTRAL,
        FRIENDLY
    }

    /** Whether authored content marks {@code otherId} as hostile. */
    public boolean isHostileTo(String otherId) {
        if (otherId == null || otherId.isBlank()) {
            return false;
        }
        String key = otherId.trim().toLowerCase(Locale.ROOT);
        // A faction is never hostile to itself, whatever a pack writes - that would have its own
        // guards fighting each other the moment two stood near one another.
        return !key.equals(id) && hostileTo.contains(key);
    }

    /**
     * Parses one faction file.
     *
     * @throws IllegalArgumentException with a reason, so the loader can report which file and why
     */
    public static XenoFaction fromJson(String id, JsonElement element) {
        if (element == null || !element.isJsonObject()) {
            throw new IllegalArgumentException("faction must be a JSON object");
        }
        JsonObject root = element.getAsJsonObject();

        String name = root.has("name") ? root.get("name").getAsString() : id;
        int color = root.has("color") ? parseColor(root.get("color").getAsString()) : 0xFFFFFF;
        int standing = root.has("default_standing") ? root.get("default_standing").getAsInt() : 0;
        boolean attacked = !root.has("attacked_by_mobs") || root.get("attacked_by_mobs").getAsBoolean();

        List<String> hostile = new ArrayList<>();
        if (root.has("hostile_to")) {
            JsonArray array = root.getAsJsonArray("hostile_to");
            for (JsonElement entry : array) {
                String other = entry.getAsString();
                if (other != null && !other.isBlank()) {
                    hostile.add(other.trim().toLowerCase(Locale.ROOT));
                }
            }
        }
        List<String> mobs = new ArrayList<>();
        if (root.has("attackable_mobs")) {
            for (JsonElement entry : root.getAsJsonArray("attackable_mobs")) {
                mobs.add(entry.getAsString());
            }
        }
        boolean aggressive = root.has("aggressive_to_mobs")
                && root.get("aggressive_to_mobs").getAsBoolean();
        return new XenoFaction(id, name, color, hostile, standing, attacked, aggressive, mobs);
    }

    /** Accepts {@code #RRGGBB} or a bare hex string; anything else is white rather than a crash. */
    private static int parseColor(String raw) {
        if (raw == null || raw.isBlank()) {
            return 0xFFFFFF;
        }
        String hex = raw.trim();
        if (hex.startsWith("#")) {
            hex = hex.substring(1);
        }
        try {
            return Integer.parseInt(hex, 16) & 0xFFFFFF;
        } catch (NumberFormatException ignored) {
            return 0xFFFFFF;
        }
    }
}
