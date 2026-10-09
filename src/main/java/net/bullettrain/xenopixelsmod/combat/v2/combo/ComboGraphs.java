package net.bullettrain.xenopixelsmod.combat.v2.combo;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.neoforged.fml.loading.FMLPaths;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The combo graph the server is running.
 *
 * <p>The bundled routes are {@code data/xenopixelsmod/combat_v2/combo_graph.json} in the jar. A
 * {@code config/xenopixelsmod/combat_v2_combo_graph.json} replaces them whole, which is how a
 * server authors its own routes without code. Replace rather than merge: a half-overridden graph
 * can point at nodes the other half renamed, and a route that silently changes shape is worse
 * than one that is plainly somebody's file.
 *
 * <p>A broken override is refused with the reason logged and the bundled graph stays in force,
 * so a typo cannot leave a server with no combat.
 */
public final class ComboGraphs {
    private static final String RESOURCE = "/data/xenopixelsmod/combat_v2/combo_graph.json";
    private static volatile ComboGraph active;

    private ComboGraphs() {
    }

    public static ComboGraph active() {
        ComboGraph graph = active;
        return graph == null ? reload() : graph;
    }

    public static synchronized ComboGraph reload() {
        ComboGraph graph = bundled();
        try {
            Path override = FMLPaths.CONFIGDIR.get().resolve("xenopixelsmod")
                    .resolve("combat_v2_combo_graph.json");
            if (Files.exists(override)) {
                try (Reader reader = Files.newBufferedReader(override, StandardCharsets.UTF_8)) {
                    graph = ComboGraphParser.parse(reader);
                    XenoPixelsMod.LOGGER.info("Combat v2: using combo graph override {} ({} nodes)",
                            override, graph.size());
                } catch (RuntimeException e) {
                    XenoPixelsMod.LOGGER.error(
                            "Combat v2: combo graph override {} refused, bundled graph kept: {}",
                            override, e.getMessage());
                }
            }
        } catch (Exception | LinkageError ignored) {
            // No FML (unit tests) or an unreadable config dir: the bundled graph stands.
        }
        active = graph;
        return graph;
    }

    /** The graph shipped in the jar. Throws if it is missing or invalid: that is a build error. */
    public static ComboGraph bundled() {
        try (InputStream in = ComboGraphs.class.getResourceAsStream(RESOURCE)) {
            if (in == null) throw new IllegalStateException("missing " + RESOURCE);
            return ComboGraphParser.parse(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (java.io.IOException e) {
            throw new IllegalStateException("unreadable " + RESOURCE, e);
        }
    }
}
