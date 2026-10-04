package net.bullettrain.xenopixelsmod.npc.script;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import org.openjdk.nashorn.api.scripting.ClassFilter;
import org.openjdk.nashorn.api.scripting.NashornScriptEngineFactory;

import javax.script.ScriptEngine;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds the bundled Nashorn engine with host access shut off.
 *
 * <p>{@code --no-java} removes the {@code Java}, {@code Packages} and {@code java} globals, and a
 * {@link ClassFilter} that answers false for every name makes any remaining class lookup fail;
 * Nashorn also denies reflection whenever a class filter is installed. Scripts can still call the
 * public methods of objects this mod binds into their scope, which is the whole API surface.
 *
 * <p>Isolated in its own class because it is the only code that links against Nashorn's API
 * types: if the nested jar is missing, {@link #create} reports it instead of the caller failing to
 * load.
 */
final class NashornSandbox {
    /** Every host class is refused. */
    static final ClassFilter DENY_ALL = className -> false;

    private NashornSandbox() {}

    static List<String> options() {
        List<String> args = new ArrayList<>(List.of("--no-java", "--language=es6"));
        String extra = System.getProperty(NpcScriptEngines.NASHORN_ARGS, "").trim();
        if (!extra.isEmpty()) {
            for (String arg : extra.split("\s+")) {
                if (!arg.isBlank() && !arg.startsWith("--no-java") && !args.contains(arg)) {
                    args.add(arg);
                }
            }
        }
        return args;
    }

    static ScriptEngine create(ClassLoader loader) {
        try {
            NashornScriptEngineFactory factory = new NashornScriptEngineFactory();
            return factory.getScriptEngine(options().toArray(String[]::new), loader, DENY_ALL);
        } catch (LinkageError | RuntimeException e) {
            XenoPixelsMod.LOGGER.warn("ModNetwork.script: nested Nashorn could not start: {}", e.toString());
            return null;
        }
    }
}
