package net.bullettrain.xenopixelsmod.client.anim.studio;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnimSceneTest {
    @Test
    void jsonRoundTripKeepsClipOrder() {
        AnimScene scene = new AnimScene("intro");
        scene.clips.add("jab");
        scene.clips.add("kick");
        AnimScene parsed = AnimScene.fromJson(scene.toJson());
        assertEquals("intro", parsed.name);
        assertEquals("training_dummy", parsed.actor);
        assertEquals(2, parsed.clips.size());
        assertEquals("kick", parsed.clips.get(1));
        assertTrue(parsed.toJson().contains("\"jab\""));
    }
}
