package net.bullettrain.xenopixelsmod.npc.script;

import net.bullettrain.xenopixelsmod.npc.script.api.ScriptNpc;
import net.bullettrain.xenopixelsmod.npc.script.api.ScriptPlayer;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 2026-09-28: xenopixels_ecma_full.js failed in game with "event.player.isSneaking is not a
 * function". Every method the cross-runtime examples call on {@code player} / {@code npc} must
 * exist on the native wrappers, or the script breaks only on native NPCs.
 */
class ExampleWrapperCallsTest {
    private static Set<String> methods(Class<?> type) {
        return Arrays.stream(type.getMethods()).map(java.lang.reflect.Method::getName).collect(Collectors.toSet());
    }

    @Test
    void examplesOnlyCallMethodsTheNativeWrappersHave() throws Exception {
        Path folder = Path.of(System.getProperty("xenopixels.projectDir"), "examples", "customnpcs");
        Set<String> player = methods(ScriptPlayer.class);
        Set<String> npc = methods(ScriptNpc.class);
        Set<String> world = methods(net.bullettrain.xenopixelsmod.npc.script.api.ScriptWorld.class);
        Pattern playerCall = Pattern.compile("\\bplayer\\.([A-Za-z]+)\\(");
        Pattern npcCall = Pattern.compile("\\bnpc\\.([A-Za-z]+)\\(");
        Pattern worldCall = Pattern.compile("\\bworld\\.([A-Za-z]+)\\(");
        List<String> missing = new ArrayList<>();
        try (var files = Files.list(folder)) {
            for (Path script : files.filter(p -> p.getFileName().toString().startsWith("xenopixels_")
                    && p.toString().endsWith(".js")).toList()) {
                String text = Files.readString(script);
                var m = playerCall.matcher(text);
                while (m.find()) if (!player.contains(m.group(1))) missing.add(script.getFileName() + ": player." + m.group(1));
                m = npcCall.matcher(text);
                while (m.find()) if (!npc.contains(m.group(1))) missing.add(script.getFileName() + ": npc." + m.group(1));
                m = worldCall.matcher(text);
                while (m.find()) if (!world.contains(m.group(1))) missing.add(script.getFileName() + ": world." + m.group(1));
            }
        }
        assertEquals(List.of(), missing);
    }
}
