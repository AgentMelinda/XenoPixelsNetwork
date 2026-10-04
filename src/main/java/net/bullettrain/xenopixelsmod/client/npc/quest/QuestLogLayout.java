package net.bullettrain.xenopixelsmod.client.npc.quest;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.ToIntFunction;

/**
 * Grouping, ordering and paging for the quest log.
 *
 * <p>Deliberately free of any Minecraft type. The panel that draws the log cannot be instantiated
 * in a unit test - {@code InventoryScreen} needs a running client - so every rule worth pinning
 * lives here instead, where a test can reach it.
 */
public final class QuestLogLayout {

    /** What a quest with no declared category is filed under. */
    public static final String GENERAL = "General";

    private QuestLogLayout() {
    }

    /**
     * The category buttons, in the order they are drawn.
     *
     * <p>Natural order, so {@code Side 2} precedes {@code Side 10}. Lexicographic order does the
     * opposite, and it is the most visible thing a log can get wrong.
     *
     * <p>{@link #GENERAL} sorts first when present: it holds every quest written before the
     * category field existed, and burying those under named chapters would hide them.
     */
    public static List<String> categories(List<ClientQuests.Entry> quests) {
        Set<String> seen = new LinkedHashSet<>();
        for (ClientQuests.Entry quest : quests == null ? List.<ClientQuests.Entry>of() : quests) {
            seen.add(quest.category().isBlank() ? GENERAL : quest.category());
        }
        List<String> out = new ArrayList<>(seen);
        out.sort((a, b) -> {
            if (a.equals(GENERAL) || b.equals(GENERAL)) {
                return a.equals(b) ? 0 : a.equals(GENERAL) ? -1 : 1;
            }
            return naturalCompare(a, b);
        });
        return List.copyOf(out);
    }

    /** The quests filed under one category, in the order the server sent them. */
    public static List<ClientQuests.Entry> inCategory(List<ClientQuests.Entry> quests,
                                                      String category) {
        List<ClientQuests.Entry> out = new ArrayList<>();
        String wanted = category == null || category.isBlank() ? GENERAL : category;
        for (ClientQuests.Entry quest : quests == null ? List.<ClientQuests.Entry>of() : quests) {
            String own = quest.category().isBlank() ? GENERAL : quest.category();
            if (own.equalsIgnoreCase(wanted)) {
                out.add(quest);
            }
        }
        return List.copyOf(out);
    }

    /**
     * Compares two names the way a reader orders them: digit runs compare as numbers.
     *
     * <p>Case-insensitive, because two categories differing only in case are one category to a
     * person, and a case-sensitive tie-break would flip their order between openings.
     */
    public static int naturalCompare(String left, String right) {
        String a = left == null ? "" : left;
        String b = right == null ? "" : right;
        int i = 0;
        int j = 0;
        while (i < a.length() && j < b.length()) {
            char ca = a.charAt(i);
            char cb = b.charAt(j);
            if (Character.isDigit(ca) && Character.isDigit(cb)) {
                int startA = i;
                int startB = j;
                while (i < a.length() && Character.isDigit(a.charAt(i))) {
                    i++;
                }
                while (j < b.length() && Character.isDigit(b.charAt(j))) {
                    j++;
                }
                // Compared by digit-run length first, so "10" beats "2" without parsing a number
                // that may be longer than any integer type can hold.
                String numA = stripLeadingZeros(a.substring(startA, i));
                String numB = stripLeadingZeros(b.substring(startB, j));
                if (numA.length() != numB.length()) {
                    return numA.length() - numB.length();
                }
                int cmp = numA.compareTo(numB);
                if (cmp != 0) {
                    return cmp;
                }
                continue;
            }
            int cmp = Character.compare(Character.toLowerCase(ca), Character.toLowerCase(cb));
            if (cmp != 0) {
                return cmp;
            }
            i++;
            j++;
        }
        return (a.length() - i) - (b.length() - j);
    }

    private static String stripLeadingZeros(String digits) {
        int k = 0;
        while (k < digits.length() - 1 && digits.charAt(k) == '0') {
            k++;
        }
        return digits.substring(k);
    }

    /**
     * Splits already-wrapped lines into pages.
     *
     * <p>Wrapping needs the client's font to measure pixel widths and so stays in the panel; this
     * is the part with the off-by-one in it, so it lives where a test can reach it.
     *
     * <p>Always returns at least one page. Zero pages would mean a quest with no journal entry
     * showed a "1 / 0" indicator over an empty body, and would hide its objectives with it.
     */
    public static List<List<String>> paginate(List<String> lines, int linesPerPage) {
        List<String> all = lines == null ? List.of() : lines;
        // A page size of zero or less is not a caller error worth crashing the inventory screen
        // over: it comes from a panel height divided by a font height, which can reach zero at a
        // small GUI scale. One page holding everything is the graceful reading.
        if (linesPerPage < 1) {
            return List.of(List.copyOf(all));
        }
        if (all.isEmpty()) {
            return List.of(List.of());
        }
        List<List<String>> pages = new ArrayList<>();
        for (int start = 0; start < all.size(); start += linesPerPage) {
            pages.add(List.copyOf(all.subList(start, Math.min(all.size(), start + linesPerPage))));
        }
        return List.copyOf(pages);
    }

    /** Keeps a scrolled list inside its viewport when entries are added or removed. */
    public static int scrollOffset(int current, double scrollY, int itemCount, int visibleRows) {
        int max = Math.max(0, itemCount - Math.max(1, visibleRows));
        int step = scrollY < 0 ? Math.max(1, (int) Math.ceil(-scrollY))
                : scrollY > 0 ? -Math.max(1, (int) Math.ceil(scrollY)) : 0;
        return Math.max(0, Math.min(max, current + step));
    }

    /** Returns at most {@code count} entries beginning at a clamped list offset. */
    public static <T> List<T> visibleSlice(List<T> items, int offset, int count) {
        List<T> values = items == null ? List.of() : items;
        int start = Math.max(0, Math.min(values.size(), offset));
        int end = Math.min(values.size(), start + Math.max(0, count));
        return List.copyOf(values.subList(start, end));
    }

    /** Places the journal left of the inventory tab rail, keeping the right side clear for viewers. */
    public static int panelLeft(int inventoryLeft) {
        return Math.max(4, inventoryLeft - 248 - 6 - 28 - 4);
    }

    /**
     * Shortens {@code text} so it fits {@code maxWidth}, measured by {@code width}.
     *
     * <p>A category or title wider than its column paints into the next one. The panel asks the
     * font; tests pass a stand-in, because the font needs a client.
     */
    public static String clip(String text, int maxWidth, ToIntFunction<String> width) {
        String value = text == null ? "" : text;
        if (width == null || maxWidth <= 0) {
            return "";
        }
        if (width.applyAsInt(value) <= maxWidth) {
            return value;
        }
        String ellipsis = "...";
        int budget = maxWidth - width.applyAsInt(ellipsis);
        if (budget <= 0) {
            return ellipsis;
        }
        int end = value.length();
        while (end > 0 && width.applyAsInt(value.substring(0, end)) > budget) {
            end--;
        }
        return value.substring(0, end) + ellipsis;
    }
}
