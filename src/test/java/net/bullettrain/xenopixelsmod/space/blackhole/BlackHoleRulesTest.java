package net.bullettrain.xenopixelsmod.space.blackhole;

import com.google.gson.JsonParser;
import net.bullettrain.xenopixelsmod.space.chart.CelestialBody;
import net.bullettrain.xenopixelsmod.space.chart.ChartDefinition;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.*;

class BlackHoleRulesTest {
    private static final BlackHoleRules.Hole HOLE = new BlackHoleRules.Hole("t:hole", 0, 0, 0, 40, 600, 6000, 0.04);

    @Test void bandsAreConcentric() {
        assertEquals(BlackHoleRules.Band.HORIZON, BlackHoleRules.band(HOLE, 10));
        assertEquals(BlackHoleRules.Band.HORIZON, BlackHoleRules.band(HOLE, 40));
        assertEquals(BlackHoleRules.Band.TIDAL, BlackHoleRules.band(HOLE, 300));
        assertEquals(BlackHoleRules.Band.LENS, BlackHoleRules.band(HOLE, 3000));
        assertEquals(BlackHoleRules.Band.OUTSIDE, BlackHoleRules.band(HOLE, 7000));
        assertEquals(BlackHoleRules.Band.OUTSIDE, BlackHoleRules.band(HOLE, Double.NaN));
    }

    @Test void pullGrowsInwardAndIsCapped() {
        assertEquals(0, BlackHoleRules.pullPerTick(HOLE, 3000));
        assertEquals(0.04, BlackHoleRules.pullPerTick(HOLE, 600), 1e-9);
        assertTrue(BlackHoleRules.pullPerTick(HOLE, 300) > BlackHoleRules.pullPerTick(HOLE, 500));
        assertEquals(0.04 * 8, BlackHoleRules.pullPerTick(HOLE, 41), 1e-9, "capped so it stays escapable");
    }

    @Test void escapeMeterAndOxygenFactor() {
        assertEquals(0, BlackHoleRules.escapeFraction(HOLE, 40));
        assertEquals(1, BlackHoleRules.escapeFraction(HOLE, 600));
        assertEquals(0.5, BlackHoleRules.escapeFraction(HOLE, 320), 1e-9);
        assertEquals(3, BlackHoleRules.oxygenFactor(BlackHoleRules.Band.TIDAL, 3));
        assertEquals(1, BlackHoleRules.oxygenFactor(BlackHoleRules.Band.LENS, 3));
    }

    @Test void invalidHolesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new BlackHoleRules.Hole("t:x", 0, 0, 0, 600, 40, 6000, 0.04));
        assertThrows(IllegalArgumentException.class, () -> new BlackHoleRules.Hole("t:x", 0, 0, 0, 40, 600, 100, 0.04));
        assertThrows(IllegalArgumentException.class, () -> new BlackHoleRules.Hole("t:x", 0, 0, 0, 40, 600, 6000, -1));
    }

    @Test void starterHoleResourceParses() throws Exception {
        var json = JsonParser.parseString(Files.readString(net.bullettrain.xenopixelsmod.RepoRoot.of("src/main/resources/data/xenopixelsmod/xenospace/planet/maw_9.json")));
        var body = ChartDefinition.parse("xenopixelsmod:maw_9", json.getAsJsonObject());
        assertEquals(CelestialBody.BodyKind.BLACK_HOLE, body.kind());
        assertFalse(body.enterable());
    }
}
