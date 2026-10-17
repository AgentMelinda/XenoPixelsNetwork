package net.bullettrain.xenopixelsmod.compat.npc;

import net.bullettrain.xenopixelsmod.npc.script.ScriptFunctionCatalog;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 2026-10-14: a body-steal script that could copy an NPC's look onto another NPC could not touch the
 * player half at all, because every appearance call took an NPC. A look borrow is offered by all three
 * XenoPixels globals - native, My NPCs, CustomNPCs - or the script that works in the editor fails on a
 * live server.
 */
class BridgeAppearanceApiTest {
    private static final List<String> BRIDGE_SOURCES = List.of(
            "src/main/java/net/bullettrain/xenopixelsmod/compat/npc/NpcXenoScriptApi.java",
            "src/main/java/net/bullettrain/xenopixelsmod/compat/npc/mynpcs/NpcXenoScriptApi.java");
    private static final List<String> BORROW_CALLS = List.of(
            "String stealAppearance(", "boolean restoreAppearance(", "boolean isAppearanceStolen(",
            "int getAppearanceStealTicksLeft(", "Map<String, Object> getNpcAppearance(",
            "Map<String, Object> getPlayerAppearance(");

    @Test void everyBridgeOffersTheBorrowAndTheReturn() throws Exception {
        for (String source : BRIDGE_SOURCES) {
            String file = read(source);
            for (String call : BORROW_CALLS) {
                assertTrue(file.contains(call), source + " is missing " + call);
            }
            assertTrue(file.contains("VERSION = \"29\""), source + " does not advertise API 29");
        }
    }

    @Test void theNativeGlobalAdvertisesTheBorrowAndAgreesOnTheVersion() {
        assertEquals("29", net.bullettrain.xenopixelsmod.npc.script.api.NativeXenoScriptApi.INSTANCE.getVersion());
        var calls = ScriptFunctionCatalog.calls();
        for (String call : List.of("stealAppearance", "restoreAppearance", "isAppearanceStolen",
                "getAppearanceStealTicksLeft", "getNpcAppearance", "getPlayerAppearance")) {
            String prefix = "XenoPixels." + call + "(";
            assertTrue(calls.stream().anyMatch(entry -> entry.startsWith(prefix)),
                    "the script editor cannot discover " + prefix);
        }
        assertTrue(calls.stream().noneMatch(entry -> entry.contains(".unwrap(") || entry.contains(".getClass(")),
                "a borrow call must not hand out the wrapped entity");
    }

    /** A missing NPC is an answer, not an exception - a script that mistargets gets NO_SOURCE back. */
    @Test void aBorrowWithNothingToCopyIsRefused() {
        assertEquals(NpcAppearanceMapping.Result.NO_SOURCE,
                PlayerAppearanceSteals.start(null, (net.minecraft.world.entity.Entity) null, 600L));
    }

    /**
     * A script can hold its player across hooks and hand back one that has since logged out. Every
     * native call has to answer that the way the CustomNPCs and My NPCs bridges do - with the refusal
     * it would give a player who was never there - because an exception lands in the server log and
     * stops the rest of the script.
     */
    @Test void aStalePlayerIsAnsweredRatherThanThrown() {
        net.bullettrain.xenopixelsmod.npc.script.api.NativeXenoScriptApi api =
                net.bullettrain.xenopixelsmod.npc.script.api.NativeXenoScriptApi.INSTANCE;
        assertEquals(NpcAppearanceMapping.Result.NO_SOURCE.name(), api.stealAppearance(null, null));
        assertFalse(api.restoreAppearance(null));
        assertFalse(api.isAppearanceStolen(null));
        assertEquals(0, api.getAppearanceStealTicksLeft(null));
        assertTrue(api.getPlayerAppearance(null).isEmpty());
    }

    private static String read(String relative) throws Exception {
        return java.nio.file.Files.readString(java.nio.file.Path.of(
                System.getProperty("xenopixels.projectDir"), relative));
    }
}
