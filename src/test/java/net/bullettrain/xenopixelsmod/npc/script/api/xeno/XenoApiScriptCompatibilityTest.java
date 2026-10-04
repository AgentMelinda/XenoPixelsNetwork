package net.bullettrain.xenopixelsmod.npc.script.api.xeno;

import net.bullettrain.xenopixelsmod.npc.script.NpcScriptEngine;
import net.bullettrain.xenopixelsmod.npc.script.NpcScriptEngines;
import net.bullettrain.xenopixelsmod.npc.script.NpcScriptHost;
import net.bullettrain.xenopixelsmod.npc.script.NpcScriptResult;
import net.bullettrain.xenopixelsmod.npc.script.NpcScriptScope;
import net.bullettrain.xenopixelsmod.npc.script.api.NativeXenoScriptApi;
import net.bullettrain.xenopixelsmod.npc.script.api.ScriptEvent;
import net.bullettrain.xenopixelsmod.npc.script.api.ScriptNpc;
import net.bullettrain.xenopixelsmod.npc.script.api.ScriptNpcTestAccess;
import net.bullettrain.xenopixelsmod.npc.script.api.ScriptTimers;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import xenoapi.npcs.api.NpcAPI;

import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Runs the bundled, sandboxed Nashorn over the real bindings: the old {@code XenoPixels} facade
 * and the new {@code XenoAPI} entry point side by side, as a tab sees them.
 */
class XenoApiScriptCompatibilityTest {
    private final ScriptNpc npc = ScriptNpcTestAccess.npc(new CompoundTag(), new HashMap<>(), new ScriptTimers());

    @AfterEach
    void reset() {
        NpcScriptEngines.reset();
    }

    private NpcScriptScope scope() {
        return NpcScriptScope.builder()
                .put("npc", npc)
                .put("XenoPixels", NativeXenoScriptApi.INSTANCE)
                .put("XenoAPI", NpcAPI.Instance())
                .put("apiTemp", XenoDataAdapter.of(npc::getTempdata))
                .build();
    }

    private NpcScriptResult run(String source) {
        NpcScriptEngine engine = NpcScriptEngines.current();
        assertTrue(engine.available(), engine.unavailableReason());
        return engine.run("function main() { " + source + " }", scope(), "main");
    }

    @Test
    void bindingHelpListsTheNewEntryPointBesideTheOldOnes() {
        assertTrue(NpcScriptHost.BINDINGS.contains("XenoAPI"));
        assertTrue(NpcScriptHost.BINDINGS.containsAll(java.util.List.of("npc", "world", "XenoPixels")));
    }

    @Test
    void existingFacadeCallsStillResolve() {
        NpcScriptResult result = run("return XenoPixels.getVersion();");
        assertTrue(result.ok(), result.describe());
        assertEquals("28", result.value());
        assertEquals("false", run("return XenoPixels.isChatCancelled(null);").value());
    }

    @Test
    void conversionEntryPointsAcceptNullWithoutOverloadAmbiguity() {
        NpcScriptResult result = run("return XenoPixels.toXeno(null) === null && XenoPixels.fromXeno(null) === null;");
        assertTrue(result.ok(), result.describe());
        assertEquals("true", result.value());
    }

    @Test
    void xenoApiValuesAreUsableFromTheSandbox() {
        NpcScriptResult pos = run("return XenoAPI.getIPos(1, 2, 3).up().north(2).getZ();");
        assertTrue(pos.ok(), pos.describe());
        assertEquals(1.0, Double.parseDouble(pos.value()));
        NpcScriptResult nbt = run("var t = XenoAPI.stringToNbt('{a:7}'); t.setInteger('b', 2); return t.getInteger('a') + t.getInteger('b');");
        assertTrue(nbt.ok(), nbt.describe());
        assertEquals(9.0, Double.parseDouble(nbt.value()));
    }

    @Test
    void apiDataWritesReachTheNativeNpcBindingInsideAScript() {
        NpcScriptResult result = run("apiTemp.put('phase', 'charge'); return npc.getTempdata().get('phase');");
        assertTrue(result.ok(), result.describe());
        assertEquals("charge", result.value());
    }

    @Test
    void unsupportedAndServerlessCallsSurfaceAsScriptErrors() {
        NpcScriptResult noServer = run("return XenoAPI.getIWorlds();");
        assertFalse(noServer.ok(), "a world lookup without a server must not look like success");
        assertTrue(noServer.describe().contains("running server"), noServer.describe());
        NpcScriptResult unsupported = run("return XenoAPI.getRecipes();");
        assertFalse(unsupported.ok());
        assertTrue(unsupported.describe().contains("NpcAPI.getRecipes"), unsupported.describe());
    }

    @Test
    void theEventObjectKeepsItsFieldsAndApi() {
        NpcScriptEngine.Instance tab = NpcScriptEngines.current().instantiate(
                "function interact(e) { return (e.API != null) + ',' + (e.npc === npc) + ',' + e.hook; }", scope());
        assertTrue(tab.ok(), tab.loadResult().describe());
        ScriptEvent event = new ScriptEvent("interact", npc, null, null, null, 0.0f);
        NpcScriptResult result = tab.call("interact", event);
        assertTrue(result.ok(), result.describe());
        assertEquals("true,true,interact", result.value());
    }
}
