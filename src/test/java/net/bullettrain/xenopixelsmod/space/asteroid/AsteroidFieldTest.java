package net.bullettrain.xenopixelsmod.space.asteroid;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AsteroidFieldTest {
    private static final AsteroidField.Belt BELT = new AsteroidField.Belt(
            "main", 0, 0, 0, 2200, 3800, 160, false);

    @Test void sameSeedAndCellProduceStableCandidatesAndIds() {
        var cell = new AsteroidField.Cell(7, 0, -2);
        var first = AsteroidField.inCell(123456789L, cell, BELT);
        var second = AsteroidField.inCell(123456789L, cell, BELT);
        assertEquals(first, second);
        assertTrue(first.size() <= AsteroidField.CANDIDATES_PER_CELL);
        assertNotEquals(AsteroidField.inCell(987654321L, cell, BELT), first);
    }

    @Test void generatedObjectsRespectBeltBoundsAndRadiusCap() {
        var generated = new java.util.ArrayList<AsteroidField.Candidate>();
        for (long y = -1; y <= 0; y++) for (long x = -12; x <= 12; x++) for (long z = -12; z <= 12; z++) {
            for (var candidate : AsteroidField.inCell(42L, new AsteroidField.Cell(x, y, z), BELT)) {
                assertTrue(BELT.contains(candidate.x(), candidate.y(), candidate.z()));
                assertTrue(candidate.radius() >= AsteroidField.MIN_RADIUS);
                assertTrue(candidate.radius() <= AsteroidField.MAX_RADIUS);
                assertFalse(candidate.icy());
                generated.add(candidate);
            }
        }
        for (int i = 0; i < generated.size(); i++) for (int j = i + 1; j < generated.size(); j++)
            assertFalse(AsteroidField.overlaps(generated.get(i), generated.get(j)),
                    "accepted candidates overlap: " + generated.get(i).id() + " and " + generated.get(j).id());
    }

    @Test void adjacentCellQueriesAgreeAndDoNotDuplicateIds() {
        var cells = List.of(new AsteroidField.Cell(5, 0, 0), new AsteroidField.Cell(6, 0, 0),
                new AsteroidField.Cell(5, 0, 1), new AsteroidField.Cell(6, 0, 1));
        var ids = new HashSet<AsteroidField.ObjectId>();
        for (var cell : cells) for (var candidate : AsteroidField.inCell(77L, cell, BELT))
            assertTrue(ids.add(candidate.id()), "duplicate object id " + candidate.id());
        for (var cell : cells) {
            assertEquals(AsteroidField.inCell(77L, cell, BELT), AsteroidField.inCell(77L, cell, BELT));
        }
    }

    @Test void overlapWinnerUsesStableIdAndDoesNotDependOnInputOrder() {
        var low = new AsteroidField.Candidate(new AsteroidField.ObjectId(0, 0, 0, 0), 1, 0, 0, 0, 16, false);
        var high = new AsteroidField.Candidate(new AsteroidField.ObjectId(0, 0, 0, 1), 2, 20, 0, 0, 16, false);
        var candidates = List.of(high, low);
        assertEquals(List.of(low), AsteroidField.arbitrate(candidates));
        assertEquals(List.of(low), AsteroidField.arbitrate(List.of(low, high)));
    }

    @Test void beltValidationRejectsInvalidBounds() {
        assertThrows(IllegalArgumentException.class,
                () -> new AsteroidField.Belt("", 0, 0, 0, 0, 10, 1, false));
        assertThrows(IllegalArgumentException.class,
                () -> new AsteroidField.Belt("bad", 0, 0, 0, 10, 10, 1, false));
        assertThrows(IllegalArgumentException.class,
                () -> new AsteroidField.Belt("bad", 0, 0, 0, 0, 10, Double.NaN, false));
    }

    @Test void candidateSelectorsKeepOnlyBoundedUniqueNearestResults() {
        var first = candidate(0, false);
        var second = candidate(1, false);
        var third = candidate(2, false);
        var fourth = candidate(3, false);
        var perPlayer = new AsteroidCandidateSelector(3);
        perPlayer.offer(first, 9);
        perPlayer.offer(second, 4);
        perPlayer.offer(third, 1);
        perPlayer.offer(fourth, 0.25);
        perPlayer.offer(second, 0.5); // The same asteroid seen through another belt/player keeps its nearer distance.
        assertEquals(3, perPlayer.size());
        assertEquals(List.of(fourth.id(), second.id(), third.id()),
                perPlayer.ranked().stream().map(ranked -> ranked.candidate().id()).toList());
        assertEquals(List.of(0.25, 0.5, 1.0),
                perPlayer.ranked().stream().map(AsteroidCandidateSelector.Ranked::distanceSquared).toList());

        var global = new AsteroidCandidateSelector(2);
        perPlayer.offerAllTo(global);
        assertEquals(2, global.size());
        assertEquals(List.of(fourth.id(), second.id()),
                global.ranked().stream().map(ranked -> ranked.candidate().id()).toList());
        assertEquals(0, new AsteroidCandidateSelector(0).size());
        assertThrows(IllegalArgumentException.class, () -> new AsteroidCandidateSelector(-1));
    }

    @Test void duplicateCandidateTieUsesStableMaterialProfile() {
        var frozen = candidate(4, true);
        var rock = candidate(4, false);
        var selector = new AsteroidCandidateSelector(1);
        selector.offer(frozen, 16);
        selector.offer(rock, 16);
        assertEquals(1, selector.size());
        assertFalse(selector.ranked().getFirst().candidate().icy());
    }

    private static AsteroidField.Candidate candidate(long cellX, boolean icy) {
        var id = new AsteroidField.ObjectId(cellX, 0, 0, 0);
        return new AsteroidField.Candidate(id, cellX, cellX, 0, 0, AsteroidField.MIN_RADIUS, icy);
    }
}
