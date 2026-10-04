package net.bullettrain.xenopixelsmod.compat.npc;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 2026-09-28: the owner's player script ran in My NPCs' Player Scripts, where XenoPixels.bubble did
 * not exist, so no bubble showed. Every XenoPixels global - native, My NPCs, CustomNPCs - has it.
 */
class BridgeBubbleApiTest {
    private static long bubbleOverloads(String className) throws Exception {
        Class<?> type = Class.forName(className, false, BridgeBubbleApiTest.class.getClassLoader());
        return Arrays.stream(type.getMethods()).filter(m -> m.getName().equals("bubble")).count();
    }

    @Test
    void everyXenoPixelsGlobalHasBubble() throws Exception {
        assertEquals(3, bubbleOverloads("net.bullettrain.xenopixelsmod.compat.npc.mynpcs.NpcXenoScriptApi"));
        // CustomNPCs' API is compile-only (not on the test runtime), so its bridge is checked in source.
        String cnpc = java.nio.file.Files.readString(java.nio.file.Path.of(System.getProperty("xenopixels.projectDir"),
                "src/main/java/net/bullettrain/xenopixelsmod/compat/npc/NpcXenoScriptApi.java"));
        String signature = "public boolean bubble(IEntity<?> entity";
        assertEquals(3, (cnpc.length() - cnpc.replace(signature, "").length()) / signature.length());
        assertEquals(3, bubbleOverloads("net.bullettrain.xenopixelsmod.npc.script.api.NativeXenoScriptApi"));
    }
}
