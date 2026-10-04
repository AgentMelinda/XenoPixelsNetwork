package net.bullettrain.xenopixelsmod.npc.script;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs the real nested Nashorn, not a stub: the engine is on the test classpath through the same
 * {@code implementation} dependency the jar nests.
 */
class BundledNashornTest {

    @AfterEach
    void reset() {
        NpcScriptEngines.reset();
    }

    @Test
    void theBundledEngineIsFoundAndSandboxed() {
        NpcScriptEngine engine = NpcScriptEngines.current();
        assertTrue(engine.available(), "nashorn-core must be on the classpath: " + engine.unavailableReason());
        assertEquals("nashorn", engine.name());
    }

    @Test
    void aNamedEntrypointIsFoundInTheScriptsOwnScope() {
        // The reported defect: evaluate into fresh bindings, then invoke against the default
        // context, and "no function" every time.
        NpcScriptResult result = NpcScriptEngines.current().run(
                "function greet() { return 'hi ' + who; }", NpcScriptScope.builder()
                        .putString("who", "goku").build(), "greet");
        assertTrue(result.ok(), result.describe());
        assertEquals("hi goku", result.value());
    }

    @Test
    void topLevelStateSurvivesBetweenHookCalls() {
        NpcScriptEngine.Instance instance = NpcScriptEngines.current().instantiate(
                "var count = 0; function tick() { count++; return count; }", NpcScriptScope.EMPTY);
        assertTrue(instance.ok(), instance.loadResult().describe());
        assertTrue(instance.hasFunction("tick"));
        assertFalse(instance.hasFunction("interact"));
        assertFalse(instance.hasFunction("count"), "a variable is not a function");
        instance.call("tick");
        assertEquals(2.0, Double.parseDouble(instance.call("tick").value()));
    }

    @Test
    void twoInstancesDoNotShareGlobals() {
        NpcScriptEngine engine = NpcScriptEngines.current();
        NpcScriptEngine.Instance a = engine.instantiate("var x = 1; function get() { return x; }",
                NpcScriptScope.EMPTY);
        NpcScriptEngine.Instance b = engine.instantiate("var x = 2; function get() { return x; }",
                NpcScriptScope.EMPTY);
        assertEquals("1", a.call("get").value());
        assertEquals("2", b.call("get").value());
    }

    @Test
    void hookArgumentsArrivePositionally() {
        NpcScriptEngine.Instance instance = NpcScriptEngines.current().instantiate(
                "function damaged(event) { return event.amount * 2; }", NpcScriptScope.EMPTY);
        java.util.Map<String, Object> event = new java.util.HashMap<>();
        event.put("amount", 4);
        assertEquals(8.0, Double.parseDouble(instance.call("damaged", event).value()));
    }

    @Test
    void hostClassesAreUnreachable() {
        NpcScriptEngine engine = NpcScriptEngines.current();
        assertFalse(engine.evaluate("Java.type('java.lang.System')", NpcScriptScope.EMPTY).ok(),
                "--no-java removes the Java global");
        assertFalse(engine.evaluate("java.lang.System.exit(0)", NpcScriptScope.EMPTY).ok(),
                "and the package globals");
        assertFalse(engine.evaluate("Packages.java.lang.Runtime.getRuntime()", NpcScriptScope.EMPTY).ok());
    }

    /** Stand-in for ScriptNpc: a public class with public methods, bound into the scope. */
    public static final class Greeter {
        public String greet(String who) {
            return "hi " + who;
        }

        public int twice(int n) {
            return n * 2;
        }
    }

    @Test
    void boundApiObjectsStayCallableUnderTheSandbox() {
        // The class filter refuses every class *lookup*; methods on objects this mod binds must
        // still work, or the whole npc/world/event API is dead under the sandbox.
        NpcScriptResult result = NpcScriptEngines.current().evaluate(
                "api.greet('goku') + ' ' + api.twice(21)",
                NpcScriptScope.builder().put("api", new Greeter()).build());
        assertTrue(result.ok(), result.describe());
        assertEquals("hi goku 42", result.value());
    }

    @Test
    void aBoundObjectIsNoDoorToReflection() {
        NpcScriptResult result = NpcScriptEngines.current().evaluate(
                "api.getClass().forName('java.lang.Runtime')",
                NpcScriptScope.builder().put("api", new Greeter()).build());
        assertFalse(result.ok(), "getClass/forName must not escape the sandbox: " + result.describe());
    }

    @Test
    void nativeXenoPixelsGlobalIsCallableUnderTheSandbox() {
        NpcScriptResult result = NpcScriptEngines.current().evaluate(
                "XenoPixels.getVersion()",
                NpcScriptScope.builder().put("XenoPixels",
                        net.bullettrain.xenopixelsmod.npc.script.api.NativeXenoScriptApi.INSTANCE).build());
        assertTrue(result.ok(), result.describe());
        assertEquals("28", result.value());
    }

    @Test
    void unchangedCustomNpcExamplesCompileInBundledEngine() throws Exception {
        Path folder = Path.of(System.getProperty("xenopixels.projectDir"),
                "examples", "customnpcs");
        try (var files = Files.list(folder)) {
            // The 22 cross-runtime examples; native-only xenoapi_* examples have their own check.
            var scripts = files.filter(path -> path.toString().endsWith(".js")
                    && path.getFileName().toString().startsWith("xenopixels_")).sorted().toList();
            assertEquals(22, scripts.size());
            for (Path script : scripts) {
                NpcScriptResult result = NpcScriptEngines.current().compile(
                        Files.readString(script), script.getFileName().toString());
                assertTrue(result.ok(), script + ": " + result.describe());
            }
        }
    }

    @Test
    void unchangedExamplesCallMethodsTheNativeFacadeActuallyExposes() throws Exception {
        Path folder = Path.of(System.getProperty("xenopixels.projectDir"),
                "examples", "customnpcs");
        var available = java.util.Arrays.stream(
                net.bullettrain.xenopixelsmod.npc.script.api.NativeXenoScriptApi.class.getMethods())
                .map(java.lang.reflect.Method::getName).collect(java.util.stream.Collectors.toSet());
        var call = java.util.regex.Pattern.compile("XenoPixels\\.([A-Za-z][A-Za-z0-9_]*)");
        try (var files = Files.list(folder)) {
            for (Path script : files.filter(path -> path.toString().endsWith(".js")).toList()) {
                var matcher = call.matcher(Files.readString(script));
                while (matcher.find()) {
                    assertTrue(available.contains(matcher.group(1)),
                            script.getFileName() + " calls missing XenoPixels." + matcher.group(1));
                }
            }
        }
    }
}
