package net.bullettrain.xenopixelsmod.client.combat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AfterimageFadeTest {
    @Test
    void modeOneStartsAtConfiguredAlphaNotSolid() {
        assertEquals(0.55f, AfterimageFade.alpha(1, 0.55f, 0, 200), 1.0e-5f);
        assertTrue(AfterimageFade.alpha(1, 0.55f, 100, 200) < 0.55f);
    }
}
