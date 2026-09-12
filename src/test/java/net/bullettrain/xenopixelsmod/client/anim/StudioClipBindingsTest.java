package net.bullettrain.xenopixelsmod.client.anim;

import net.bullettrain.xenopixelsmod.combat.anim.Bt3AnimationIntent;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StudioClipBindingsTest {

    @Test
    void jsonRoundTrips() {
        Map<Bt3AnimationIntent, String> bindings = new EnumMap<>(Bt3AnimationIntent.class);
        bindings.put(Bt3AnimationIntent.JAB_RIGHT, "my_jab");
        bindings.put(Bt3AnimationIntent.HEAVY_FINISH, "big_finish");

        Map<Bt3AnimationIntent, String> parsed = StudioClipBindings.parse(
                StudioClipBindings.write(bindings));

        assertEquals(2, parsed.size());
        assertEquals("my_jab", parsed.get(Bt3AnimationIntent.JAB_RIGHT));
        assertEquals("big_finish", parsed.get(Bt3AnimationIntent.HEAVY_FINISH));
    }

    @Test
    void unknownIntentsAreIgnoredRatherThanThrowing() {
        Map<Bt3AnimationIntent, String> parsed = StudioClipBindings.parse(
                "{\"NOT_A_REAL_MOVE\": \"clip\", \"JAB_LEFT\": \"left_one\"}");
        assertEquals(1, parsed.size());
        assertEquals("left_one", parsed.get(Bt3AnimationIntent.JAB_LEFT));
    }

    @Test
    void clipNamesAreSanitisedOnTheWayIn() {
        Map<Bt3AnimationIntent, String> parsed = StudioClipBindings.parse(
                "{\"JAB_LEFT\": \"My Clip!\"}");
        assertEquals("my_clip_", parsed.get(Bt3AnimationIntent.JAB_LEFT));
    }

    @Test
    void malformedJsonParsesToNothing() {
        assertTrue(StudioClipBindings.parse("not json at all").isEmpty());
        assertTrue(StudioClipBindings.parse("").isEmpty());
    }

    @Test
    void intentLookupIsCaseInsensitive() {
        assertEquals(Bt3AnimationIntent.JAB_RIGHT, StudioClipBindings.intentOf("jab_right"));
        assertEquals(Bt3AnimationIntent.JAB_RIGHT, StudioClipBindings.intentOf("  JAB_RIGHT "));
        assertNull(StudioClipBindings.intentOf("nope"));
        assertNull(StudioClipBindings.intentOf(null));
    }
}
