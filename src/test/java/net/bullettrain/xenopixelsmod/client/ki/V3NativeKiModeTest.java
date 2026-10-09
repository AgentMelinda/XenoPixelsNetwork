package net.bullettrain.xenopixelsmod.client.ki;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class V3NativeKiModeTest {
    @Test void nativeModeOnlyBypassesHdForOwnedProjectiles() {
        assertTrue(HdKiClient.allowsHd(true, true));
        assertTrue(HdKiClient.allowsHd(true, false));
        assertFalse(HdKiClient.allowsHd(false, true));
        assertTrue(HdKiClient.allowsHd(false, false));
    }
}
