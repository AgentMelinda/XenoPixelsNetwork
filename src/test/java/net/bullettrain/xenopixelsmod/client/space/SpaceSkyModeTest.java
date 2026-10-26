package net.bullettrain.xenopixelsmod.client.space;

import net.bullettrain.xenopixelsmod.space.math.VoidSkyGradient;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The sky switch's vocabulary and the void ramp's arithmetic, neither of which needs a render thread. */
class SpaceSkyModeTest {
    @Test void storedValueResolvesInsteadOfFailing() {
        assertEquals(SpaceSkyMode.STARS, SpaceSkyMode.byName("stars"));
        assertEquals(SpaceSkyMode.STARS, SpaceSkyMode.byName("  STARS "));
        assertEquals(SpaceSkyMode.VOID, SpaceSkyMode.byName("void"));
        assertEquals(SpaceSkyMode.OFF, SpaceSkyMode.byName("off"));
        // Missing/invalid values use the intended default black star field.
        assertEquals(SpaceSkyMode.STARS, SpaceSkyMode.byName(null));
        assertEquals(SpaceSkyMode.STARS, SpaceSkyMode.byName("galaxy"));
    }

    @Test void modeIdsAreTheCommandLiterals() {
        assertEquals("void", SpaceSkyMode.VOID.id);
        assertEquals("stars", SpaceSkyMode.STARS.id);
        assertEquals("off", SpaceSkyMode.OFF.id);
        assertEquals(3, SpaceSkyMode.values().length);
    }

    @Test void voidRampGoesFromAPurpleNadirToAnAlmostBlackZenith() {
        assertEquals(0xFF2B0F3F, VoidSkyGradient.argb(0.0));
        assertEquals(0xFF030107, VoidSkyGradient.argb(1.0));
        assertEquals(0xFF170823, VoidSkyGradient.argb(0.5));
    }

    @Test void voidRampIsClampedOpaqueAndFadesEveryChannelDown() {
        assertEquals(VoidSkyGradient.argb(0.0), VoidSkyGradient.argb(-64.0));
        assertEquals(VoidSkyGradient.argb(1.0), VoidSkyGradient.argb(64.0));
        int previous = VoidSkyGradient.argb(0.0);
        for (double t = 0.05; t <= 1.001; t += 0.05) {
            int next = VoidSkyGradient.argb(t);
            assertEquals(0xFF000000, next & 0xFF000000, "the dome is never see-through");
            for (int shift = 0; shift <= 16; shift += 8) {
                assertTrue((next >> shift & 0xFF) <= (previous >> shift & 0xFF),
                        "channel at shift " + shift + " brightened on the way up: "
                                + Integer.toHexString(previous) + " -> " + Integer.toHexString(next));
            }
            previous = next;
        }
    }
}
