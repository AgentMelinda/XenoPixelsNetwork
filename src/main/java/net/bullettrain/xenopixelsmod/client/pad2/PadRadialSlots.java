package net.bullettrain.xenopixelsmod.client.pad2;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Which actions the in-game radial shows, and in what order.
 *
 * <p>Controlify's own radial holds <b>eight</b>: {@code RadialItems.createBindings} allocates
 * {@code new RadialItem[8]} and reads eight entries out of its saved {@code radialActions} list.
 * This mod registers roughly twenty-two radial candidates — eleven explicit actions, one per
 * DragonMineZ technique slot, and three mode actions — so most of them could never be reached.
 *
 * <p>This class decides the widened list. It is deliberately <b>free of Controlify and Minecraft
 * types</b>: it works on binding id strings alone, so the ordering rules below can be tested
 * without a controller, a client, or Controlify on the classpath. Resolving an id to a real
 * binding is {@code PadWideRadial}'s job.
 *
 * <p>The first eight entries are always whatever Controlify itself is configured with. That is not
 * a detail — Controlify's own config screen still edits those eight, and a player who has already
 * arranged them must not find them moved or replaced the first time this runs.
 */
public final class PadRadialSlots {

    /**
     * How many entries the widened radial shows.
     *
     * <p>Twenty. The ring is laid out at 2π/N with a radius of {@code Math.max(…, 43f)}, so it
     * keeps growing past this — but the icons are 16px and the labels are drawn at the rim, and
     * past twenty they start colliding on a 1080p screen at default GUI scale.
     */
    public static final int MAX_SLOTS = 20;

    /**
     * How many of those Controlify itself owns.
     *
     * <p>Matches the {@code new RadialItem[8]} in {@code RadialItems.createBindings}. Read from
     * the incoming array rather than assumed, so a Controlify build that changes it does not
     * silently drop or duplicate entries here; this constant is the fallback and the documentation.
     */
    public static final int CONTROLIFY_SLOTS = 8;

    /** Controlify's own id for "nothing in this slot". */
    public static final String EMPTY_ACTION = "controlify:empty";

    /**
     * What fills slots nine and up before anybody has chosen.
     *
     * <p>Ordered the way the bindings are grouped where they are registered: technique slots
     * first, because those are the ones a player reaches for mid-fight and the ones DragonMineZ
     * itself can only reach with Alt+1..4 and Ctrl+1..4 — chords no gamepad can produce. Then
     * combat, targeting, and party.
     *
     * <p>Ids that do not resolve are simply shown empty, so listing a technique slot that a
     * particular DragonMineZ build does not have costs nothing.
     */
    public static final List<String> DEFAULT_EXTRAS = List.of(
            // DragonMineZ ships eight technique slots and this mod's own hotbar draws eight, so
            // listing four left half of them unreachable on a pad even after the widening.
            "xenopixelsmod:technique_slot_1",
            "xenopixelsmod:technique_slot_2",
            "xenopixelsmod:technique_slot_3",
            "xenopixelsmod:technique_slot_4",
            "xenopixelsmod:technique_slot_5",
            "xenopixelsmod:technique_slot_6",
            "xenopixelsmod:technique_slot_7",
            "xenopixelsmod:technique_slot_8",
            // DMZ's own ki-attack radial, which had no gamepad route at all before.
            "xenopixelsmod:utility_menu",
            "xenopixelsmod:ki_blast_cancel",
            "xenopixelsmod:ki_guidance",
            "xenopixelsmod:target_lock");

    private PadRadialSlots() {
    }

    /**
     * The full ordered slot list: Controlify's own, then ours.
     *
     * <p>Rules, in the order they bite:
     * <ul>
     *   <li>Controlify's entries come first and are never reordered or dropped, <b>including its
     *       empty slots</b> — an empty slot is a position a player deliberately left clear, and
     *       closing the gap would shift every action after it onto a different angle.</li>
     *   <li>An extra already present among Controlify's eight is skipped, so binding it in both
     *       places shows it once rather than twice.</li>
     *   <li>The result is capped at {@link #MAX_SLOTS}.</li>
     * </ul>
     *
     * @param controlifyActions the ids Controlify itself is configured with, in its order
     * @param extras            this mod's additions, in the player's order
     */
    public static List<String> merge(List<String> controlifyActions, List<String> extras) {
        List<String> merged = new ArrayList<>(MAX_SLOTS);
        Set<String> seen = new LinkedHashSet<>();

        for (String id : controlifyActions == null ? List.<String>of() : controlifyActions) {
            if (merged.size() >= MAX_SLOTS) {
                break;
            }
            String clean = clean(id);
            merged.add(clean);
            // Empties never count as "seen": several slots may legitimately be empty, and an extra
            // is not a duplicate of nothing.
            if (!clean.isEmpty() && !EMPTY_ACTION.equals(clean)) {
                seen.add(clean);
            }
        }

        for (String id : extras == null ? List.<String>of() : extras) {
            if (merged.size() >= MAX_SLOTS) {
                break;
            }
            String clean = clean(id);
            if (clean.isEmpty() || EMPTY_ACTION.equals(clean) || !seen.add(clean)) {
                continue;
            }
            merged.add(clean);
        }
        return List.copyOf(merged);
    }

    /**
     * Adds one id to the end of an extras list.
     *
     * @return the new list, or the original when it is full or already holds that id
     */
    public static List<String> add(List<String> extras, String id) {
        List<String> current = extras == null ? List.of() : extras;
        String clean = clean(id);
        if (clean.isEmpty() || current.contains(clean) || current.size() >= MAX_SLOTS) {
            return List.copyOf(current);
        }
        List<String> next = new ArrayList<>(current);
        next.add(clean);
        return List.copyOf(next);
    }

    /** Removes one id. Returns the original list when it was not there. */
    public static List<String> remove(List<String> extras, String id) {
        List<String> current = extras == null ? List.of() : extras;
        List<String> next = new ArrayList<>(current);
        next.remove(clean(id));
        return List.copyOf(next);
    }

    /**
     * Moves the entry at {@code from} to {@code to}, shifting the rest.
     *
     * <p>Out-of-range indices leave the list alone rather than throwing: this is driven by a
     * command a player types, and a typo should say nothing happened rather than print a stack
     * trace.
     */
    public static List<String> move(List<String> extras, int from, int to) {
        List<String> current = extras == null ? List.of() : extras;
        if (from < 0 || to < 0 || from >= current.size() || to >= current.size() || from == to) {
            return List.copyOf(current);
        }
        List<String> next = new ArrayList<>(current);
        next.add(to, next.remove(from));
        return List.copyOf(next);
    }

    /**
     * Trims and lower-cases one id.
     *
     * <p>A {@code ResourceLocation} is lower-case by construction, so an id typed with capitals
     * into the command would otherwise never match one read off a binding.
     */
    public static String clean(String id) {
        return id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
    }
}
