package net.bullettrain.xenopixelsmod.npc.script.api.xeno;

import net.bullettrain.xenopixelsmod.npc.script.NpcScriptEngine;
import net.bullettrain.xenopixelsmod.npc.script.NpcScriptEngines;
import net.bullettrain.xenopixelsmod.npc.script.NpcScriptResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The native-only XenoAPI examples ({@code xenoapi_*.js}) parse in the bundled, sandboxed engine.
 * Syntax only: no hook runs, so this proves nothing about in-game behaviour. The cross-runtime
 * examples are checked by BundledNashornTest.
 */
class ExampleScriptsCompileTest {
    @AfterEach
    void reset() {
        NpcScriptEngines.reset();
    }

    @Test
    void xenoApiExamplesCompile() throws Exception {
        Path dir = Path.of(System.getProperty("xenopixels.projectDir", ".")).resolve("examples/customnpcs");
        List<Path> scripts;
        try (Stream<Path> files = Files.list(dir)) {
            scripts = files.filter(p -> p.getFileName().toString().startsWith("xenoapi_")
                    && p.toString().endsWith(".js")).sorted().toList();
        }
        assertEquals(6, scripts.size(), "xenoapi_* examples: " + scripts);
        NpcScriptEngine engine = NpcScriptEngines.current();
        assertTrue(engine.available(), engine.unavailableReason());
        List<String> failures = new ArrayList<>();
        for (Path script : scripts) {
            NpcScriptResult result = engine.compile(Files.readString(script), script.getFileName().toString());
            if (!result.ok()) failures.add(script.getFileName() + ": " + result.describe());
        }
        assertEquals(List.of(), failures);
    }
}
