package net.bullettrain.xenopixelsmod.npc.script;

import net.bullettrain.xenopixelsmod.npc.store.XenoNpcScripts;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import javax.script.AbstractScriptEngine;
import javax.script.Bindings;
import javax.script.Compilable;
import javax.script.CompiledScript;
import javax.script.Invocable;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineFactory;
import javax.script.ScriptException;
import javax.script.SimpleBindings;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The engine seam's own contract: what callers may rely on when this build ships no engine, and what
 * the JSR-223 adapter does with an engine it is handed.
 *
 * <p>No real JavaScript engine is involved. This build bundles none - that is an owner decision, see
 * {@link NpcScriptEngine} - and the seam exists precisely so the storage, the editor, and the run
 * path can be written and tested without one. The two stubs below stand in for the shapes an engine
 * can have: one that can only evaluate, and one that also compiles and calls functions by name.
 */
class NpcScriptEngineSeamTest {

    @AfterEach
    void putTheRealDiscoveryBack() {
        NpcScriptEngines.reset();
    }

    // ---------------------------------------------------------------- the fallback

    @Test
    void theFallbackRefusesEveryOperationAndSaysThereIsNoEngine() {
        // The honest answer is the whole point of this class. A silent success here would let an
        // operator believe their script ran.
        NoScriptEngine engine = new NoScriptEngine();
        assertEquals("none", engine.name());
        assertFalse(engine.available());
        assertEquals(NpcScriptEngine.NO_ENGINE, engine.unavailableReason());

        for (NpcScriptResult result : List.of(
                engine.compile("1 + 1", "test"),
                engine.evaluate("1 + 1", NpcScriptScope.EMPTY),
                engine.run("1 + 1", NpcScriptScope.EMPTY, "onInteract"))) {
            assertFalse(result.ok());
            assertEquals(NpcScriptEngine.NO_ENGINE, result.error());
            assertTrue(result.engineMissing());
            assertEquals("none", result.engine());
        }
    }

    @Test
    void aCustomFallbackReasonSurvivesIntoTheResult() {
        NpcScriptResult result = new NoScriptEngine("this server has no python")
                .evaluate("x = 1", NpcScriptScope.EMPTY);
        assertFalse(result.ok());
        assertTrue(result.error().contains("python"));
        // Not the generic message, so a caller cannot mistake a language-specific refusal for the mod
        // shipping no runtime at all. Those are different fixes.
        assertFalse(result.engineMissing());
    }

    @Test
    void anUnknownLanguageIsRefusedRatherThanRunOnTheJavaScriptEngine() {
        // Falling back to JS for language "python" would run the wrong thing and report success.
        NpcScriptEngine engine = NpcScriptEngines.forLanguage("python");
        assertFalse(engine.available());
        assertTrue(engine.unavailableReason().contains("python"));
    }

    @Test
    void aJavaScriptLanguageNameResolvesToWhateverThisServerActuallyHas() {
        PlainEngine plain = new PlainEngine();
        NpcScriptEngine wrapper = new Jsr223ScriptEngine(plain, "stubbed");
        NpcScriptEngines.useForTesting(wrapper);
        // Case is not significant, and a blank language means "the default", which is ECMAScript.
        assertSame(wrapper, NpcScriptEngines.forLanguage("ECMAScript"));
        assertSame(wrapper, NpcScriptEngines.forLanguage("javascript"));
        assertSame(wrapper, NpcScriptEngines.forLanguage(""));
        assertTrue(NpcScriptEngines.describe().contains("stubbed"), NpcScriptEngines.describe());
    }

    @Test
    void theDiscoverySentenceDescribesTheEngineCallersWillActuallyGet() {
        // Whatever this build has, the sentence the editor prints has to be true about it, because it
        // is what an operator quotes in a bug report.
        NpcScriptEngine current = NpcScriptEngines.current();
        assertTrue(NpcScriptEngines.describe().startsWith("script engine: "));
        NpcScriptEngines.useForTesting(new NoScriptEngine());
        assertTrue(NpcScriptEngines.describe().contains("none"), NpcScriptEngines.describe());
        NpcScriptEngines.useForTesting(new Jsr223ScriptEngine(new PlainEngine(), "stubbed"));
        assertTrue(NpcScriptEngines.describe().contains("stubbed"), NpcScriptEngines.describe());
        assertEquals(current.name().length() > 0, true);
    }

    // ---------------------------------------------------------------- results

    @Test
    void aMultiLineEngineErrorIsCollapsedToOneLineForTheStatusRow() {
        NpcScriptResult result = NpcScriptResult.failure("stub",
                "TypeError: x is not a function\n    at script:3\n    at script:9", 3);
        String line = result.describe();
        assertFalse(line.contains("\n"), line);
        assertTrue(line.startsWith("line 3: "), line);
        assertTrue(line.contains("TypeError"), line);
        assertTrue(line.contains("(+2 more lines)"), line);
        assertTrue(line.length() <= 200, line);
    }

    @Test
    void anOverlongEngineErrorIsCutRatherThanWrappingThePanel() {
        NpcScriptResult result = NpcScriptResult.failure("stub", "x".repeat(5000), -1);
        assertTrue(result.describe().length() <= 200, result.describe());
        assertTrue(result.describe().endsWith("..."), result.describe());
    }

    @Test
    void anEngineThatGivesNoMessageStillProducesSomethingToShow() {
        NpcScriptResult result = NpcScriptResult.failure("stub", "   ", -1);
        assertFalse(result.ok());
        assertEquals("script failed without a message", result.describe());
    }

    @Test
    void aSuccessfulRunDescribesItsValueAndAnAbsentValueReadsAsOk() {
        assertEquals("42", NpcScriptResult.success("stub", 42).describe());
        assertEquals("ok", NpcScriptResult.success("stub", null).describe());
        assertEquals("ok", NpcScriptResult.success("stub", "undefined").describe());
    }

    // ---------------------------------------------------------------- the adapter

    @Test
    void theAdapterReportsTheEnginesOwnLineNumberWhenAScriptThrows() {
        PlainEngine engine = new PlainEngine();
        engine.throwAt = new ScriptException("boom", "npc_script.js", 7, 2);
        NpcScriptResult result = new Jsr223ScriptEngine(engine, "stub")
                .evaluate("throw new Error('boom')", NpcScriptScope.EMPTY);
        assertFalse(result.ok());
        assertTrue(result.error().contains("boom"), result.error());
        assertEquals(7, result.line());
        assertTrue(result.describe().startsWith("line 7: "), result.describe());
    }

    @Test
    void theAdapterPassesTheScopeThroughAsFreshBindingsEveryRun() {
        PlainEngine engine = new PlainEngine();
        NpcScriptScope scope = NpcScriptScope.builder()
                .putString("player", "Caius")
                .putInt("clicks", 3)
                .build();
        Jsr223ScriptEngine adapter = new Jsr223ScriptEngine(engine, "stub");
        assertTrue(adapter.evaluate("1", scope).ok());
        assertEquals("Caius", engine.lastBindings.get("player"));
        assertEquals(3, engine.lastBindings.get("clicks"));
        assertEquals(1, engine.created.size());

        // A second run must not inherit globals from the first, so each eval builds its own map.
        assertTrue(adapter.evaluate("2", NpcScriptScope.EMPTY).ok());
        assertEquals(2, engine.created.size());
        assertFalse(engine.created.get(0) == engine.created.get(1));
        assertFalse(engine.created.get(1).containsKey("player"));
    }

    @Test
    void anEngineThatCannotCompileSaysSoInsteadOfRunningTheScript() {
        // The tempting shortcut - evaluate and see whether it throws - is a side effect the caller did
        // not ask for, so refusing is the correct behaviour.
        PlainEngine engine = new PlainEngine();
        NpcScriptResult result = new Jsr223ScriptEngine(engine, "stub").compile("1 + 1", "greet");
        assertFalse(result.ok());
        assertTrue(result.error().contains("cannot check syntax"), result.error());
        assertTrue(result.error().startsWith("greet: "), result.error());
        assertTrue(engine.evals.isEmpty());
    }

    @Test
    void anEngineThatCanCompileIsAskedAndItsAnswerIsPassedThrough() {
        FullEngine engine = new FullEngine();
        NpcScriptEngine adapter = new Jsr223ScriptEngine(engine, "stub");
        assertTrue(adapter.compile("1 + 1", "greet").ok());
        assertFalse(adapter.compile("1 +!", "greet").ok());
        assertFalse(adapter.compile("   ", "greet").ok());
        assertTrue(engine.evals.isEmpty(), "compiling must not evaluate");
    }

    @Test
    void anEngineThatCannotCallByNameStillEvaluatesAndNamesTheLimit() {
        PlainEngine engine = new PlainEngine();
        NpcScriptResult result = new Jsr223ScriptEngine(engine, "stub")
                .run("1 + 1", NpcScriptScope.EMPTY, "onInteract");
        assertFalse(result.ok());
        assertTrue(result.error().contains("cannot call a function by name"), result.error());
        assertEquals(1, engine.evals.size(), "the script itself did run first");
    }

    @Test
    void aBlankEntrypointEvaluatesWithoutTryingToCallAnything() {
        PlainEngine engine = new PlainEngine();
        NpcScriptResult result = new Jsr223ScriptEngine(engine, "stub")
                .run("1 + 1", NpcScriptScope.EMPTY, "   ");
        assertTrue(result.ok());
        assertEquals(1, engine.evals.size());
    }

    @Test
    void aNamedFunctionIsCalledAfterTheScriptEvaluates() {
        FullEngine engine = new FullEngine();
        NpcScriptResult result = new Jsr223ScriptEngine(engine, "stub")
                .run("function onInteract(){}", NpcScriptScope.EMPTY, "onInteract");
        assertTrue(result.ok(), result.describe());
        assertEquals("called:onInteract", result.value());
        assertEquals(1, engine.evals.size());
    }

    @Test
    void onlyAnIdentifierIsAcceptedAsAnEntrypoint() {
        // The entrypoint reaches invokeFunction as a name, so anything else is either a typo or
        // somebody trying to smuggle code through a field meant to hold an identifier.
        PlainEngine engine = new PlainEngine();
        Jsr223ScriptEngine adapter = new Jsr223ScriptEngine(engine, "stub");
        for (String bad : List.of("1up", "a b", "onInteract(); alert(1)", "a-b", "a".repeat(65))) {
            NpcScriptResult result = adapter.run("1 + 1", NpcScriptScope.EMPTY, bad);
            assertFalse(result.ok(), bad);
            assertTrue(result.error().startsWith("not a function name"), result.error());
        }
        assertTrue(engine.evals.isEmpty(), "a rejected name must not run the script either");
    }

    @Test
    void aMissingFunctionIsReportedAsSuchRatherThanAsAScriptError() {
        FullEngine engine = new FullEngine();
        engine.noSuchMethod = true;
        NpcScriptResult result = new Jsr223ScriptEngine(engine, "stub")
                .run("1 + 1", NpcScriptScope.EMPTY, "onInteract");
        assertFalse(result.ok());
        assertTrue(result.error().contains("no function onInteract()"), result.error());
    }

    @Test
    void aScriptThatThrowsInsideAFunctionIsNotBlamedOnTheFunctionLookup() {
        FullEngine engine = new FullEngine();
        engine.throwAt = new ScriptException("boom");
        NpcScriptResult result = new Jsr223ScriptEngine(engine, "stub")
                .run("1 +!", NpcScriptScope.EMPTY, "onInteract");
        assertFalse(result.ok());
        assertTrue(result.error().contains("boom"), result.error());
        assertFalse(result.error().contains("no function"), result.error());
    }

    // ---------------------------------------------------------------- the runner

    @Test
    void theRunnerSaysWhenNoScriptIsStoredUnderThatId() {
        // No store is open in a unit test, which is exactly the "nothing here" case; the message has
        // to name the id rather than blame the engine.
        XenoScriptRunner.Outcome outcome = XenoScriptRunner.run("greet", "", NpcScriptScope.EMPTY);
        assertFalse(outcome.ok());
        assertTrue(outcome.describe().contains("no script stored"), outcome.describe());
        assertTrue(outcome.transcript().contains("greet"), outcome.transcript());
    }

    @Test
    void theRunnerDistinguishesAnNpcWithNoBindingFromAnEngineThatIsMissing() {
        XenoScriptRunner.Outcome none = XenoScriptRunner.runForProfile("", "", NpcScriptScope.EMPTY);
        assertFalse(none.ok());
        assertTrue(none.describe().contains("no script selected"), none.describe());
        assertFalse(none.engineMissing());
    }

    @Test
    void theRunnerRefusesEmptySourceBeforeAskingAnyEngine() {
        PlainEngine engine = new PlainEngine();
        NpcScriptEngines.useForTesting(new Jsr223ScriptEngine(engine, "stub"));
        XenoScriptRunner.Outcome outcome = XenoScriptRunner.runText(
                new XenoNpcScripts.Script("greet", "Greet", "ecmascript", true, "  "), "",
                NpcScriptScope.EMPTY);
        assertFalse(outcome.ok());
        assertTrue(outcome.describe().contains("no source text"), outcome.describe());
        assertTrue(engine.evals.isEmpty());
    }

    @Test
    void theRunnerAddsLogAndScriptBindingsAroundTheCallersOwn() {
        FullEngine engine = new FullEngine();
        NpcScriptEngines.useForTesting(new Jsr223ScriptEngine(engine, "stub"));
        XenoScriptRunner.Outcome outcome = XenoScriptRunner.runText(
                new XenoNpcScripts.Script("greet", "Greet", "ecmascript", true, "1 + 1"),
                "", NpcScriptScope.builder().putString("player", "Caius").build());
        assertTrue(outcome.ok(), outcome.describe());
        assertTrue(engine.lastBindings.get("log") instanceof NpcScriptLog);
        assertEquals("greet", engine.lastBindings.get("script"));
        assertEquals("Greet", engine.lastBindings.get("scriptName"));
        assertEquals("Caius", engine.lastBindings.get("player"));
    }

    @Test
    void theTranscriptPutsPrintedLinesBeforeTheVerdict() {
        NpcScriptLog.Collector log = new NpcScriptLog.Collector();
        log.line("first");
        log.line("second");
        log.line(null);
        assertEquals("first\nsecond\nnull", log.joined());
        assertEquals(3, log.lines());

        XenoScriptRunner.Outcome outcome = new XenoScriptRunner.Outcome(
                NpcScriptResult.success("stub", 7), log.joined());
        String transcript = outcome.transcript();
        assertTrue(transcript.startsWith("first\nsecond\nnull"), transcript);
        assertTrue(transcript.endsWith("7"), transcript);
    }

    @Test
    void theScriptLogStopsGrowingOnceItIsLongEnoughToShow() {
        NpcScriptLog.Collector log = new NpcScriptLog.Collector();
        for (int i = 0; i < 400; i++) {
            log.line("padding padding padding padding padding padding");
        }
        assertTrue(log.joined().length() < 4400, "cap is honoured: " + log.joined().length());
        assertTrue(log.lines() < 120, "printing stops: " + log.lines());
    }

    // ---------------------------------------------------------------- stubs

    /** A JSR-223 engine that only records what it was asked to do. */
    private static class PlainEngine extends AbstractScriptEngine {
        final List<Bindings> created = new ArrayList<>();
        final List<String> evals = new ArrayList<>();
        Bindings lastBindings;
        ScriptException throwAt;
        boolean noSuchMethod;

        @Override
        public Object eval(String script, ScriptContext context) throws ScriptException {
            evals.add(script);
            if (throwAt != null) {
                throw throwAt;
            }
            return script;
        }

        @Override
        public Object eval(Reader reader, ScriptContext context) throws ScriptException {
            return eval("from-reader", context);
        }

        @Override
        public Bindings createBindings() {
            Bindings bindings = new SimpleBindings();
            created.add(bindings);
            lastBindings = bindings;
            return bindings;
        }

        @Override
        public ScriptEngineFactory getFactory() {
            return null;
        }
    }

    /** The same engine, with the two optional JSR-223 capabilities a real one usually has. */
    private static final class FullEngine extends PlainEngine implements Compilable, Invocable {

        @Override
        public CompiledScript compile(String script) throws ScriptException {
            // "!" is the agreed marker for unparsable source inside these tests.
            if (script.contains("!")) {
                throw new ScriptException("unparsable");
            }
            return new CompiledScript() {
                @Override
                public Object eval() {
                    return script;
                }

                @Override
                public Object eval(ScriptContext context) {
                    return script;
                }

                @Override
                public ScriptEngine getEngine() {
                    return FullEngine.this;
                }
            };
        }

        @Override
        public CompiledScript compile(java.io.Reader reader) throws ScriptException {
            return compile("from-reader");
        }

        @Override
        public Object invokeFunction(String name, Object... args)
                throws ScriptException, NoSuchMethodException {
            if (noSuchMethod) {
                throw new NoSuchMethodException(name);
            }
            if (throwAt != null) {
                throw throwAt;
            }
            return "called:" + name;
        }

        @Override
        public Object invokeMethod(Object thiz, String name, Object... args)
                throws ScriptException, NoSuchMethodException {
            return invokeFunction(name, args);
        }

        @Override
        public <T> T getInterface(Class<T> clasz) {
            return null;
        }

        @Override
        public <T> T getInterface(Object thiz, Class<T> clasz) {
            return null;
        }
    }
}
