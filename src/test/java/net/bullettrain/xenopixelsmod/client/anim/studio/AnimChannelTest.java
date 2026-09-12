package net.bullettrain.xenopixelsmod.client.anim.studio;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnimChannelTest {

    private static AnimChannel rotation() {
        return new AnimChannel(AnimChannel.Kind.ROTATION);
    }

    @Test
    void anEmptyChannelReportsItsRestValue() {
        assertEquals(0f, rotation().valueAt(1.0)[0], 0.0001f);
        assertEquals(1f, new AnimChannel(AnimChannel.Kind.SCALE).valueAt(1.0)[0], 0.0001f,
                "scale rests at 1, not 0");
        assertEquals(1f, new AnimChannel(AnimChannel.Kind.VISIBILITY).valueAt(1.0)[0], 0.0001f,
                "a bone with no visibility keys is visible");
    }

    @Test
    void keysInterpolateBetweenTheirOwnTimes() {
        AnimChannel channel = rotation();
        channel.put(0.0, 0, 0, 0);
        channel.put(0.5, 90, 0, 0);
        assertEquals(0f, channel.valueAt(0.0)[0], 0.001f);
        assertEquals(45f, channel.valueAt(0.25)[0], 0.001f);
        assertEquals(90f, channel.valueAt(0.5)[0], 0.001f);
    }

    @Test
    void beforeTheFirstAndAfterTheLastKeyTheValueHolds() {
        AnimChannel channel = rotation();
        channel.put(0.4, 30, 0, 0);
        channel.put(0.8, 60, 0, 0);
        assertEquals(30f, channel.valueAt(0.0)[0], 0.001f);
        assertEquals(60f, channel.valueAt(5.0)[0], 0.001f);
    }

    /** The whole point of the rewrite: a time is not forced onto a 1/20 s grid. */
    @Test
    void keysCanSitBetweenTicks() {
        AnimChannel channel = rotation();
        channel.put(0.25, 10, 0, 0);
        channel.put(0.31, 20, 0, 0);
        assertEquals(2, channel.size());
        assertEquals(0.25, channel.firstTime(), 0.0001);
        assertEquals(0.31, channel.lastTime(), 0.0001);
    }

    @Test
    void writingTheSameTimeTwiceReplacesTheKey() {
        AnimChannel channel = rotation();
        channel.put(0.25, 10, 0, 0);
        channel.put(0.25, 40, 0, 0);
        assertEquals(1, channel.size());
        assertEquals(40f, channel.at(0.25).x, 0.001f);
    }

    @Test
    void aReplacementKeepsTheEasingAlreadyThere() {
        AnimChannel channel = rotation();
        channel.put(0.5, new AnimKey(10, 0, 0, "easeinoutsine"));
        channel.put(0.5, 40, 0, 0);
        assertEquals("easeinoutsine", channel.at(0.5).easing,
                "re-posing a frame should not silently drop its easing");
    }

    @Test
    void timesAreQuantisedSoLookupsAreExact() {
        AnimChannel channel = rotation();
        channel.put(1.0 / 3.0, 5, 0, 0);
        assertTrue(channel.has(0.333));
        assertEquals(0.333, channel.firstTime(), 0.0000001);
    }

    @Test
    void keysCanBeFoundAndRemoved() {
        AnimChannel channel = rotation();
        channel.put(0.1, 1, 0, 0);
        channel.put(0.2, 2, 0, 0);
        channel.put(0.3, 3, 0, 0);
        assertEquals(0.2, channel.nextTime(0.1), 0.0001);
        assertEquals(0.2, channel.prevTime(0.3), 0.0001);
        assertEquals(0.2, channel.floorTime(0.25), 0.0001);
        assertEquals(0.3, channel.ceilingTime(0.25), 0.0001);
        assertTrue(channel.remove(0.2));
        assertFalse(channel.remove(0.2));
        assertEquals(0.3, channel.nextTime(0.1), 0.0001);
        assertNull(channel.nextTime(0.3));
    }

    @Test
    void stepEasingHoldsUntilTheKeyItArrivesAt() {
        AnimChannel channel = rotation();
        channel.put(0.0, 0, 0, 0);
        channel.put(1.0, new AnimKey(90, 0, 0, "step"));
        assertEquals(0f, channel.valueAt(0.99)[0], 0.001f);
        assertEquals(90f, channel.valueAt(1.0)[0], 0.001f);
    }

    @Test
    void visibilityNeverBlends() {
        AnimChannel channel = new AnimChannel(AnimChannel.Kind.VISIBILITY);
        channel.put(0.0, 1, 0, 0);
        channel.put(1.0, 0, 0, 0);
        assertEquals(1f, channel.valueAt(0.5)[0], 0.001f, "a bone is shown or hidden, never half");
        assertEquals(0f, channel.valueAt(1.0)[0], 0.001f);
    }

    @Test
    void copyIsIndependent() {
        AnimChannel channel = rotation();
        channel.put(0.5, 10, 0, 0);
        AnimChannel copy = channel.copy();
        channel.at(0.5).x = 99f;
        assertEquals(10f, copy.at(0.5).x, 0.001f);
    }
}
