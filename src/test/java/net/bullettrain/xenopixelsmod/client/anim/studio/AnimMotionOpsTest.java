package net.bullettrain.xenopixelsmod.client.anim.studio;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnimMotionOpsTest {

    private static AnimChannel rotationAt(double time, float x) {
        AnimChannel channel = new AnimChannel(AnimChannel.Kind.ROTATION);
        channel.put(time, x, 0, 0);
        return channel;
    }

    @Test
    void settleReturnsTheChannelToRest() {
        AnimChannel channel = rotationAt(0.0, 80f);
        double landed = AnimMotionOps.settle(channel, 0.0, 0.5, "easeinoutsine");

        assertEquals(0.5, landed, 0.0001);
        assertEquals(80f, channel.valueAt(0.0)[0], 0.001f, "the starting pose is pinned");
        assertEquals(0f, channel.valueAt(0.5)[0], 0.001f, "and it arrives at rest");
        assertTrue(channel.valueAt(0.25)[0] < 80f && channel.valueAt(0.25)[0] > 0f,
                "the middle is on the way, not at either end");
        assertEquals("easeinoutsine", channel.at(0.5).easing);
    }

    @Test
    void settleFromMidMotionHoldsWhereverItWas() {
        AnimChannel channel = new AnimChannel(AnimChannel.Kind.ROTATION);
        channel.put(0.0, 0, 0, 0);
        channel.put(1.0, 100, 0, 0);
        AnimMotionOps.settle(channel, 0.5, 0.5, AnimEasing.LINEAR);
        assertEquals(50f, channel.at(0.5).x, 0.001f, "the settle starts from the live value");
        assertEquals(0f, channel.valueAt(1.0)[0], 0.001f);
    }

    @Test
    void scaleSettlesBackToOneNotZero() {
        AnimChannel channel = new AnimChannel(AnimChannel.Kind.SCALE);
        channel.put(0.0, 3, 3, 3);
        AnimMotionOps.settle(channel, 0.0, 0.5, AnimEasing.LINEAR);
        assertEquals(1f, channel.valueAt(0.5)[0], 0.001f);
    }

    @Test
    void smoothPinsTheEndsAndPullsTheMiddleIn() {
        AnimChannel channel = new AnimChannel(AnimChannel.Kind.ROTATION);
        channel.put(0.0, 0, 0, 0);
        channel.put(0.1, 40, 0, 0);
        channel.put(0.2, 0, 0, 0);
        channel.put(0.3, 40, 0, 0);
        channel.put(0.4, 0, 0, 0);

        float spikeBefore = channel.at(0.1).x;
        int moved = AnimMotionOps.smooth(channel, 0.0, 0.4, 1.0f);

        assertEquals(3, moved);
        assertEquals(0f, channel.at(0.0).x, 0.001f, "the first key is pinned");
        assertEquals(0f, channel.at(0.4).x, 0.001f, "and so is the last");
        assertTrue(channel.at(0.1).x < spikeBefore, "the spike is pulled in");
    }

    @Test
    void smoothWithNoStrengthChangesNothing() {
        AnimChannel channel = new AnimChannel(AnimChannel.Kind.ROTATION);
        channel.put(0.0, 0, 0, 0);
        channel.put(0.1, 40, 0, 0);
        channel.put(0.2, 0, 0, 0);
        assertEquals(0, AnimMotionOps.smooth(channel, 0.0, 0.2, 0f));
        assertEquals(40f, channel.at(0.1).x, 0.001f);
    }

    @Test
    void bridgeReproducesTheCurveItSampled() {
        AnimChannel channel = new AnimChannel(AnimChannel.Kind.ROTATION);
        channel.put(0.0, 0, 0, 0);
        channel.put(1.0, new AnimKey(100, 0, 0, "easeinoutsine"));

        float quarterBefore = channel.valueAt(0.25)[0];
        int written = AnimMotionOps.bridge(channel, 0.0, 1.0, 3);

        assertEquals(3, written);
        assertEquals(5, channel.size());
        assertEquals(quarterBefore, channel.at(0.25).x, 0.001f,
                "the sampled key sits exactly on the eased curve");
    }

    @Test
    void simplifyDropsKeysTheNeighboursAlreadyDescribe() {
        AnimChannel channel = new AnimChannel(AnimChannel.Kind.ROTATION);
        // A straight ramp sampled every tick: every interior key is redundant.
        for (int i = 0; i <= 20; i++) {
            channel.put(i / 20.0, i * 5f, 0, 0);
        }
        assertEquals(21, channel.size());
        int removed = AnimMotionOps.simplify(channel, 0.5);
        assertEquals(19, removed);
        assertEquals(2, channel.size(), "a straight line needs two keys");
        assertEquals(50f, channel.valueAt(0.5)[0], 0.001f, "and still reads the same");
    }

    @Test
    void simplifyKeepsTheShapeItNeeds() {
        AnimChannel channel = new AnimChannel(AnimChannel.Kind.ROTATION);
        for (int i = 0; i <= 20; i++) {
            double t = i / 20.0;
            channel.put(t, (float) (Math.sin(t * Math.PI) * 90.0), 0, 0);
        }
        AnimMotionOps.simplify(channel, 1.0);
        assertTrue(channel.size() > 2, "an arc cannot be two keys");
        assertTrue(channel.size() < 21, "but it does not need twenty-one");
        assertEquals(90f, channel.valueAt(0.5)[0], 6f, "the peak survives");
    }

    @Test
    void simplifyLeavesShortChannelsAlone() {
        AnimChannel channel = rotationAt(0.0, 10f);
        channel.put(0.5, 20, 0, 0);
        assertEquals(0, AnimMotionOps.simplify(channel, 1.0));
        assertEquals(2, channel.size());
    }
}
