package net.bullettrain.xenopixelsmod.client.anim.studio;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnimOscillatorTest {

    @Test
    void aSineRunsThroughItsCycle() {
        AnimOscillator wave = AnimOscillator.sine("waist", AnimChannel.Kind.ROTATION, 1, 10f, 1.0);
        assertEquals(0f, wave.valueAt(0.0), 0.001f);
        assertEquals(10f, wave.valueAt(0.25), 0.001f);
        assertEquals(0f, wave.valueAt(0.5), 0.001f);
        assertEquals(-10f, wave.valueAt(0.75), 0.001f);
        assertEquals(0f, wave.valueAt(1.0), 0.001f, "and comes back round");
    }

    @Test
    void phaseShiftsTheCycle() {
        AnimOscillator plain = AnimOscillator.sine("waist", AnimChannel.Kind.ROTATION, 0, 10f, 1.0);
        AnimOscillator shifted = new AnimOscillator("waist", AnimChannel.Kind.ROTATION, 0,
                10f, 1.0, 0.25, AnimOscillator.Wave.SINE);
        assertEquals(plain.valueAt(0.25), shifted.valueAt(0.0), 0.001f);
    }

    @Test
    void orbitIsAQuarterCycleAheadOfSine() {
        AnimOscillator sine = AnimOscillator.sine("waist", AnimChannel.Kind.ROTATION, 0, 10f, 1.0);
        AnimOscillator orbit = new AnimOscillator("waist", AnimChannel.Kind.ROTATION, 1,
                10f, 1.0, 0.0, AnimOscillator.Wave.ORBIT);
        // Together they trace a circle: x and y are never at their peak at the same moment.
        assertEquals(0f, sine.valueAt(0.0), 0.001f);
        assertEquals(10f, orbit.valueAt(0.0), 0.001f);
        for (double t = 0; t < 1.0; t += 0.05) {
            double radius = Math.hypot(sine.valueAt(t), orbit.valueAt(t));
            assertEquals(10.0, radius, 0.01, "a circle keeps its radius");
        }
    }

    @Test
    void triangleAndPendulumStayInsideTheAmplitude() {
        for (AnimOscillator.Wave shape : AnimOscillator.Wave.values()) {
            AnimOscillator wave = new AnimOscillator("head", AnimChannel.Kind.ROTATION, 0,
                    12f, 0.8, 0.0, shape);
            for (double t = 0; t < 2.0; t += 0.017) {
                assertTrue(Math.abs(wave.valueAt(t)) <= 12.0001f,
                        shape + " overshot its amplitude at " + t);
            }
        }
    }

    @Test
    void aPeriodIsNeverZero() {
        AnimOscillator wave = new AnimOscillator("head", AnimChannel.Kind.ROTATION, 0,
                5f, 0.0, 0.0, AnimOscillator.Wave.SINE);
        assertTrue(wave.period() >= AnimOscillator.MIN_PERIOD);
        assertEquals(0f, wave.valueAt(0.0), 0.001f);
    }

    /** The point of baking: the keys must say what the live wave said. */
    @Test
    void bakedKeysMatchTheLiveEvaluation() {
        AnimOscillator wave = AnimOscillator.sine("waist", AnimChannel.Kind.ROTATION, 0, 20f, 1.0);
        AnimChannel channel = new AnimChannel(AnimChannel.Kind.ROTATION);
        int written = wave.bake(channel, 0.0, 1.0, 0.1);

        assertEquals(11, written);
        for (double t = 0.0; t <= 1.0; t += 0.1) {
            assertEquals(wave.valueAt(t), channel.valueAt(t)[0], 0.001f,
                    "baked key at " + t + " should match the wave");
        }
    }

    @Test
    void bakingIsAdditiveOverWhateverWasThere() {
        AnimChannel channel = new AnimChannel(AnimChannel.Kind.ROTATION);
        channel.put(0.0, 30, 0, 0);
        channel.put(1.0, 30, 0, 0);
        AnimOscillator wave = AnimOscillator.sine("waist", AnimChannel.Kind.ROTATION, 0, 10f, 1.0);
        wave.bake(channel, 0.0, 1.0, 0.25);
        assertEquals(40f, channel.valueAt(0.25)[0], 0.001f, "30 of pose plus 10 of wave");
    }

    @Test
    void applyToAddsToTheRightChannelAndAxis() {
        AnimBonePose pose = AnimBonePose.rot(5, 0, 0);
        AnimOscillator.sine("head", AnimChannel.Kind.ROTATION, 0, 10f, 1.0)
                .applyTo(pose, 0.25);
        assertEquals(15f, pose.rotX, 0.001f);

        AnimBonePose moved = new AnimBonePose();
        new AnimOscillator("head", AnimChannel.Kind.POSITION, 1, 2f, 1.0, 0.0,
                AnimOscillator.Wave.SINE).applyTo(moved, 0.25);
        assertEquals(2f, moved.posY, 0.001f);
        assertTrue(moved.hasPosition(), "driving a channel means the pose owns it");
    }
}
