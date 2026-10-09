package net.bullettrain.xenopixelsmod.client.ki;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class HdKiFallbackPolicyTest {
    @Test void nativeRenderingIsSuppressedOnlyForReadyVisibleReplacement() {
        assertTrue(HdKiClient.canSuppress(true, true, true));
        assertFalse(HdKiClient.canSuppress(false, true, true));
        assertFalse(HdKiClient.canSuppress(true, false, true));
        assertFalse(HdKiClient.canSuppress(true, true, false));
    }
}
