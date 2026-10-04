package net.bullettrain.xenopixelsmod.npc.importer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Where My NPCs and CustomNPCs keep their content, found by convention.
 *
 * <p>By convention and not by asking them. {@code NpcWorldMigrator} resolves its source directory
 * by reflecting {@code espi.mynpcs.controllers.ServerCloneController}, which is correct for a
 * converter that runs <em>alongside</em> My NPCs and wrong for this: an operator migrating away
 * from it will not keep it installed in order to be migrated away from, so an importer that needs
 * it on the classpath cannot run at the moment it is wanted.
 *
 * <p>There are two roots, not one. Dialogs, quests and the {@code .dat} blobs sit under the world
 * folder, but the 1.5.0 install writes <b>clones to the game directory</b>
 * ({@code run/mynpcs/clones}). A single-root assumption finds the content and silently misses every
 * clone.
 */
public final class ForeignNpcRoots {

    /** One directory worth reading, and which mod wrote it. */
    public record Root(String mod, Path path) {
    }

    private ForeignNpcRoots() {
    }

    /**
     * Every content root that actually exists.
     *
     * @param worldPath the world folder, which holds the authored content
     * @param gameDir   the game directory, which holds clones
     */
    public static List<Root> discover(Path worldPath, Path gameDir) {
        List<Root> roots = new ArrayList<>();
        addIfPresent(roots, "mynpcs", worldPath, "mynpcs");
        addIfPresent(roots, "customnpcs", worldPath, "customnpcs");
        // The second root. Clones live beside the game directory in the 1.5.0 install, not under
        // the world - so a migration that only looked in the world would report success having
        // imported no clone at all.
        addIfPresent(roots, "mynpcs-clones", gameDir, "mynpcs", "clones");
        addIfPresent(roots, "customnpcs-clones", gameDir, "customnpcs", "clones");
        return List.copyOf(roots);
    }

    private static void addIfPresent(List<Root> roots, String mod, Path base, String... parts) {
        if (base == null) {
            return;
        }
        Path candidate = base;
        for (String part : parts) {
            candidate = candidate.resolve(part);
        }
        if (Files.isDirectory(candidate)) {
            roots.add(new Root(mod, candidate));
        }
    }
}
