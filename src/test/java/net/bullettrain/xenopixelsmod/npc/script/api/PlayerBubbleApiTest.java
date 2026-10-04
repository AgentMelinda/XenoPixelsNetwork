package net.bullettrain.xenopixelsmod.npc.script.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** 2026-09-28 owner question: "do players have bubbles too?" - scripts can put one over a player. */
class PlayerBubbleApiTest {
    @Test
    void playersAndEntitiesCanSayInABubble() {
        assertDoesNotThrow(() -> ScriptPlayer.class.getMethod("say", String.class));
        assertDoesNotThrow(() -> ScriptPlayer.class.getMethod("say", String.class, String.class));
        assertDoesNotThrow(() -> ScriptPlayer.class.getMethod("say", String.class, String.class, String.class));
        // Object: typed XenoAPI hooks (died, levelUp...) hand scripts an IPlayer, the others a
        // wrapper; XenoPixels.bubble(event.player, ...) has to work in both.
        assertDoesNotThrow(() -> NativeXenoScriptApi.class.getMethod("bubble", Object.class, String.class));
        assertDoesNotThrow(() -> NativeXenoScriptApi.class.getMethod("bubble", Object.class, String.class, String.class));
        assertDoesNotThrow(() -> NativeXenoScriptApi.class.getMethod("bubble", Object.class, String.class,
                String.class, String.class));
        assertEquals(false, NativeXenoScriptApi.INSTANCE.bubble("not an entity", "hi"));
    }
}
