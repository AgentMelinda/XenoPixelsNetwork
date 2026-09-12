package net.bullettrain.xenopixelsmod.anim;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class XenoClipLibraryTest {

    private static String clipJson(String animation) {
        return "{\"format_version\":\"1.8.0\",\"animations\":{\"" + animation
                + "\":{\"loop\":false,\"animation_length\":0.5,\"bones\":{}}}}";
    }

    @Test
    void aWellFormedClipIsAccepted() {
        assertNull(XenoClipLibrary.validate("my_jab", clipJson("combat.xeno_my_jab")));
    }

    @Test
    void rubbishIsRefusedWithAReason() {
        assertNotNull(XenoClipLibrary.validate("my_jab", "not json"));
        assertNotNull(XenoClipLibrary.validate("my_jab", ""));
        assertNotNull(XenoClipLibrary.validate("my_jab", "{}"));
        assertNotNull(XenoClipLibrary.validate("my_jab", "{\"animations\":{}}"),
                "a file with no animations in it is not a clip");
    }

    @Test
    void aNameThatIsNotAPlainClipNameIsRefused() {
        assertNotNull(XenoClipLibrary.validate("../escape", clipJson("x")));
        assertNotNull(XenoClipLibrary.validate("Has Spaces", clipJson("x")));
        assertNotNull(XenoClipLibrary.validate("", clipJson("x")));
    }

    @Test
    void anOversizedClipIsRefused() {
        StringBuilder padding = new StringBuilder();
        padding.append("x".repeat(XenoClipLibrary.MAX_CLIP_BYTES + 16));
        String huge = "{\"animations\":{\"combat.xeno_big\":{\"bones\":{},\"pad\":\""
                + padding + "\"}}}";
        String problem = XenoClipLibrary.validate("big", huge);
        assertNotNull(problem);
        assertTrue(problem.contains("KB"), "the refusal should say how big is too big");
    }

    @Test
    void namesAreSanitisedTheSameWayClipNamesAre() {
        assertEquals("my_clip_", XenoClipLibrary.sanitize("My Clip!"));
        assertEquals("jab_right", XenoClipLibrary.sanitize("JAB_RIGHT"));
        assertEquals("", XenoClipLibrary.sanitize(null));
    }

    @Test
    void unbundleRoundTripsWhatWasBundled() {
        String bundle = "{\"one\":" + clipJson("combat.xeno_one")
                + ",\"two\":" + clipJson("combat.xeno_two") + "}";
        Map<String, String> clips = XenoClipLibrary.unbundle(bundle);
        assertEquals(2, clips.size());
        assertTrue(clips.containsKey("one"));
        assertTrue(clips.get("two").contains("combat.xeno_two"));
    }

    @Test
    void unbundleDropsEntriesItWouldNotHaveAccepted() {
        String bundle = "{\"good\":" + clipJson("combat.xeno_good")
                + ",\"bad\":{\"animations\":{}}"
                + ",\"worse\":\"a string, not an object\"}";
        Map<String, String> clips = XenoClipLibrary.unbundle(bundle);
        assertEquals(1, clips.size());
        assertTrue(clips.containsKey("good"));
    }

    @Test
    void unbundleSurvivesRubbish() {
        assertTrue(XenoClipLibrary.unbundle("not json").isEmpty());
        assertTrue(XenoClipLibrary.unbundle("").isEmpty());
        assertTrue(XenoClipLibrary.unbundle(null).isEmpty());
    }
}
