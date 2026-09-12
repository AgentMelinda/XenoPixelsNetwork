package net.bullettrain.xenopixelsmod;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Locks the FML mod-sort edges this mod declares.
 *
 * <p>FML turns every {@code ordering} other than {@code NONE} into an edge in a graph it
 * topologically sorts, and a single cycle anywhere aborts the whole instance before any mod
 * loads. On 2026-09-11 a third-party DragonMineZ addon ({@code dmzcustomforms} 0.4.0) closed such
 * a loop through this mod — {@code Detected a mod dependency cycle: dmzcustomforms, dragonminez,
 * xenopixelsmod} — because we declared {@code AFTER dragonminez} while it declared itself
 * {@code AFTER xenopixelsmod} and ahead of DragonMineZ.
 *
 * <p>Two rules keep this mod from being draggable into that kind of loop again, and this test is
 * what stops them being quietly undone:
 * <ul>
 *   <li>never declare {@code BEFORE} on anything — it is the edge direction that lets another
 *       mod's {@code AFTER} close a loop through us;</li>
 *   <li>keep {@code dragonminez} unordered, because nothing here needs the edge (see the comment
 *       on that dependency in {@code neoforge.mods.toml} for the phase-ordering evidence).</li>
 * </ul>
 */
class ModMetadataOrderingTest {
    private static final Pattern BLOCK =
            Pattern.compile("(?m)^\\s*\\[\\[dependencies\\.xenopixelsmod\\]\\]\\s*$");

    @Test
    void noDependencyIsDeclaredBefore() {
        for (Map.Entry<String, String> dep : dependencies().entrySet()) {
            assertTrue(!"BEFORE".equals(dep.getValue()),
                    dep.getKey() + " must not use ordering=BEFORE: it lets another mod's AFTER "
                            + "close a mod-sort cycle through xenopixelsmod");
        }
    }

    @Test
    void dragonMineZIsRequiredButUnordered() {
        Map<String, String> deps = dependencies();
        assertTrue(deps.containsKey("dragonminez"), "dragonminez must stay a declared dependency");
        assertEquals("NONE", deps.get("dragonminez"),
                "dragonminez must stay unordered; see the comment in neoforge.mods.toml");
        assertTrue(toml().contains("modId=\"dragonminez\""));
    }

    @Test
    void theOrderingEdgesWeDoKeepAreTheExpectedOnes() {
        List<String> ordered = new ArrayList<>();
        dependencies().forEach((mod, ordering) -> {
            if (!"NONE".equals(ordering)) ordered.add(mod + "=" + ordering);
        });
        // Every remaining edge points out of this mod only; none of these mods declares any
        // ordering back toward xenopixelsmod, so none of them can form a loop through us.
        assertEquals(List.of("sable=AFTER", "sablecompanion=AFTER", "create=AFTER",
                        "computercraft=AFTER", "xaeroworldmap=AFTER", "customnpcs=AFTER",
                        "cnpcgeckoaddon=AFTER", "mynpcs=AFTER", "yawp=AFTER", "modernui=AFTER"),
                ordered);
    }

    /** Maps declared dependency modId to its ordering, in declaration order. */
    private static Map<String, String> dependencies() {
        String toml = toml();
        Map<String, String> deps = new LinkedHashMap<>();
        Matcher blocks = BLOCK.matcher(toml);
        List<Integer> starts = new ArrayList<>();
        while (blocks.find()) starts.add(blocks.end());
        for (int i = 0; i < starts.size(); i++) {
            int end = i + 1 < starts.size() ? toml.lastIndexOf("[[", starts.get(i + 1)) : toml.length();
            String body = toml.substring(starts.get(i), Math.max(starts.get(i), end));
            Matcher id = Pattern.compile("modId\\s*=\\s*\"([^\"]+)\"").matcher(body);
            Matcher ordering = Pattern.compile("ordering\\s*=\\s*\"([^\"]+)\"").matcher(body);
            if (id.find()) deps.put(id.group(1), ordering.find() ? ordering.group(1) : "NONE");
        }
        // minecraft and neoforge are structural and never ordered; drop them from the comparison.
        deps.remove("minecraft");
        deps.remove("neoforge");
        return deps;
    }

    private static String toml() {
        try (InputStream stream = ModMetadataOrderingTest.class
                .getResourceAsStream("/META-INF/neoforge.mods.toml")) {
            assertNotNull(stream, "processed neoforge.mods.toml is not on the test classpath");
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new AssertionError("could not read neoforge.mods.toml", exception);
        }
    }
}
