package net.bullettrain.xenopixelsmod.compat.npc.clone;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Decides what a CustomNPCs world-data migration should do, without touching a filesystem.
 *
 * <p>Split from the copying so the rules can be tested. The rules are short but the consequences are
 * not: this runs automatically against somebody's server world, and "never overwrite" is the whole
 * reason it is safe to do that.
 *
 * <p>Both mods write the same folder layout — {@code clones dialogs linkednpcs playerdata quests
 * recipes.dat schematics scripts} — and the contents match. Comparing a world opened under both, the
 * dialog files differed only in a timestamp, the playerdata was byte-identical, and no world-data
 * file contains a {@code customnpcs:} namespace at all. So this is a copy, with one exception:
 * clones really are serialised entities and do carry an {@code id}, so those are converted on the
 * way through.
 */
public final class NpcMigrationPlan {

    /** The folder inside the world data whose files need converting rather than copying. */
    private static final String CLONES = "clones/";

    /** What should happen to one file. */
    public enum Action {
        /** Copy it across byte for byte. */
        COPY,
        /** Read it, convert the NPC tag, and write the result. */
        CONVERT,
        /** Leave it: My NPCs already has a file at that path, and its own data wins. */
        SKIP
    }

    /** One file's fate. Paths are relative to the mod's world-data folder, with '/' separators. */
    public record Step(String path, Action action) {}

    private NpcMigrationPlan() {
    }

    /**
     * Works out what to do with every file found on the CustomNPCs side.
     *
     * @param source      relative paths present under the world's {@code customnpcs} folder
     * @param destination relative paths already present under its {@code mynpcs} folder
     * @return one step per source file, in a stable order
     */
    public static List<Step> plan(Collection<String> source, Collection<String> destination) {
        Set<String> existing = new LinkedHashSet<>();
        if (destination != null) {
            for (String path : destination) {
                if (path != null) existing.add(normalise(path));
            }
        }
        List<Step> steps = new ArrayList<>();
        if (source == null) {
            return steps;
        }
        List<String> ordered = new ArrayList<>();
        for (String path : source) {
            if (path != null && !path.isBlank()) ordered.add(normalise(path));
        }
        ordered.sort(String::compareTo);
        for (String path : ordered) {
            if (existing.contains(path)) {
                steps.add(new Step(path, Action.SKIP));
            } else if (needsConversion(path)) {
                steps.add(new Step(path, Action.CONVERT));
            } else {
                steps.add(new Step(path, Action.COPY));
            }
        }
        return steps;
    }

    /** How many steps take a given action, for the summary line. */
    public static int count(List<Step> steps, Action action) {
        int total = 0;
        for (Step step : steps) {
            if (step.action() == action) total++;
        }
        return total;
    }

    /** Whether every step is a skip, which is what a second run over the same world looks like. */
    public static boolean nothingToDo(List<Step> steps) {
        return count(steps, Action.COPY) == 0 && count(steps, Action.CONVERT) == 0;
    }

    /**
     * A clone file, which carries an entity id and therefore needs converting.
     *
     * <p>Matched on the folder rather than the extension: the clones folder holds {@code .json}
     * files, but so does {@code dialogs}, and a dialog must be copied untouched.
     */
    private static boolean isClone(String path) {
        return path.startsWith(CLONES) && path.toLowerCase(Locale.ROOT).endsWith(".json");
    }

    private static boolean needsConversion(String path) {
        return isClone(path) || NpcWorldDataConverter.isStructured(path)
                || NpcWorldDataConverter.isScript(path);
    }

    private static String normalise(String path) {
        String out = path.replace('\\', '/').trim();
        while (out.startsWith("/")) {
            out = out.substring(1);
        }
        return out;
    }
}
