package net.bullettrain.xenopixelsmod.compat.linearreader;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class LinearClaimCoverageTest {
    @AfterEach void clear() { LinearClaimCoverage.invalidate(); }
    private void claim(LinearClaimCoverage.Box... boxes) {
        LinearClaimCoverage.publish(LinearClaimCoverage.generation(), Map.of("test:world",
                new LinearClaimCoverage.Dimension(-64, 319, List.of(boxes))));
    }
    private boolean covers(String file) { return LinearClaimCoverage.covers("test:world", Path.of(file)); }

    @Test void partialBoundaryPartialHeightAndUnionRemainMca() {
        claim(new LinearClaimCoverage.Box(0, -64, 0, 510, 319, 511));
        assertFalse(covers("r.0.0.mca"));
        claim(new LinearClaimCoverage.Box(0, 0, 0, 511, 319, 511));
        assertFalse(covers("r.0.0.mca"));
        claim(new LinearClaimCoverage.Box(0, -64, 0, 255, 319, 511),
                new LinearClaimCoverage.Box(256, -64, 0, 511, 319, 511));
        assertFalse(covers("r.0.0.mca"));
    }

    @Test void exactBoundsNegativeCoordinatesAndMalformedNames() {
        claim(new LinearClaimCoverage.Box(-512, -64, -512, -1, 319, -1));
        assertTrue(covers("r.-1.-1.mca"));
        assertFalse(covers("r.0.-1.mca"));
        assertFalse(covers("r.36028797018963968.0.mca"));
        assertFalse(covers("r.999999999999999999999.0.mca"));
        assertFalse(covers("backup.mca"));
        assertFalse(LinearClaimCoverage.covers("other:dimension", Path.of("r.-1.-1.mca")));
    }

    @Test void staleSnapshotCannotBePublishedAfterAClaimMutation() {
        long old = LinearClaimCoverage.generation();
        claim(new LinearClaimCoverage.Box(0, -64, 0, 511, 319, 511));
        assertTrue(covers("r.0.0.mca"));
        LinearClaimCoverage.invalidate();
        LinearClaimCoverage.publish(old, Map.of("test:world", new LinearClaimCoverage.Dimension(-64, 319,
                List.of(new LinearClaimCoverage.Box(0, -64, 0, 511, 319, 511)))));
        assertFalse(covers("r.0.0.mca"));
    }
}
