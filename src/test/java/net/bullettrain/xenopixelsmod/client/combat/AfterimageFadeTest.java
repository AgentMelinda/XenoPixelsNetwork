package net.bullettrain.xenopixelsmod.client.combat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AfterimageFadeTest {
    @Test
    void modesHaveDistinctExpectedCurves() {
        assertEquals(0.25f, AfterimageFade.alpha(1, 0.5f, 10, 20), 0.0001f);
        assertEquals(0.5f, AfterimageFade.alpha(2, 0.5f, 10, 20), 0.0001f);
        assertEquals(0.5f, AfterimageFade.alpha(3, 0.5f, 10, 20), 0.0001f);
        assertEquals(0.0f, AfterimageFade.alpha(2, 0.5f, 20, 20), 0.0001f);
    }
}
