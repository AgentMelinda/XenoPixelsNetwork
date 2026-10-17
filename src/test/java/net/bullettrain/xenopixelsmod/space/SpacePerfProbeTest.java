package net.bullettrain.xenopixelsmod.space;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class SpacePerfProbeTest {
    @Test void percentilesUseBoundedRawSamplesAndReset() {
        var p = new SpacePerfProbe(1);
        for (int i = 1; i <= 100; i++) p.record(0, i * 1000L);
        assertEquals(50, p.percentileMicros(0, 0.50));
        assertEquals(95, p.percentileMicros(0, 0.95));
        p.reset();
        assertEquals(0, p.percentileMicros(0, 0.95));
    }
}
