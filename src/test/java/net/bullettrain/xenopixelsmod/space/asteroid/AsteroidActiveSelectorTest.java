package net.bullettrain.xenopixelsmod.space.asteroid;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class AsteroidActiveSelectorTest {
    @Test void retainsOnlyNearestBodiesWithinPerPlayerAndGlobalLimits() {
        var first = candidate(1, 1);
        var second = candidate(2, 2);
        var third = candidate(3, 3);
        var viewer = List.of(new double[]{0, 0, 0});
        assertEquals(Set.of(first.id()), AsteroidActiveSelector.retain(
                List.of(first, second, third), viewer, 10, 1, 8, null, Set.of()));
        assertEquals(Set.of(first.id(), second.id()), AsteroidActiveSelector.retain(
                List.of(first, second, third), viewer, 10, 3, 2, null, Set.of()));
    }

    @Test void overlappingViewersDoNotExceedEachPerPlayerLimit() {
        var nearBothA = candidate(1, 0.5);
        var nearBothB = candidate(2, 1.5);
        var leftOnly = candidate(3, -1.5);
        var viewers = List.of(new double[]{0, 0, 0}, new double[]{1, 0, 0});
        assertEquals(Set.of(nearBothA.id()), AsteroidActiveSelector.retain(
                List.of(leftOnly, nearBothB, nearBothA), viewers, 2, 1, 8, null, Set.of()));
    }

    @Test void pendingBuildReservesGlobalAndNearbyPlayerSlots() {
        var building = candidate(0, 0.5);
        var activeA = candidate(1, 1);
        var activeB = candidate(2, 2);
        var viewer = List.of(new double[]{0, 0, 0});
        assertTrue(AsteroidActiveSelector.retain(List.of(activeA, activeB), viewer, 10, 1, 3,
                building, Set.of()).isEmpty());
        assertEquals(Set.of(activeA.id()), AsteroidActiveSelector.retain(
                List.of(activeA, activeB), viewer, 10, 2, 2, building, Set.of()));
    }

    @Test void pinnedFractureConsumesTheLastGlobalSlotAndRejectsPendingBuild() {
        var cut = candidate(4, 1);
        var building = candidate(5, 2);
        var viewer = List.of(new double[]{0, 0, 0});

        var selection = AsteroidActiveSelector.select(List.of(cut), viewer, 10, 2, 1,
                building, Set.of(cut.id()));

        assertEquals(Set.of(cut.id()), selection.retained());
        assertFalse(selection.keepBuilding());
    }

    @Test void pinnedFractureConsumesTheLastViewerSlotAndRejectsNearbyBuild() {
        var cut = candidate(6, 1);
        var building = candidate(7, 2);
        var viewer = List.of(new double[]{0, 0, 0});

        var selection = AsteroidActiveSelector.select(List.of(cut), viewer, 10, 1, 4,
                building, Set.of(cut.id()));

        assertEquals(Set.of(cut.id()), selection.retained());
        assertFalse(selection.keepBuilding());
    }

    @Test void pinnedFractureDoesNotBypassZeroLimits() {
        var cut = candidate(4, 1_000);
        assertEquals(Set.of(), AsteroidActiveSelector.retain(
                List.of(cut), List.of(), 10, 0, 0, null, Set.of(cut.id())));
        assertTrue(AsteroidActiveSelector.retain(List.of(cut), List.of(), 10, 1, 2, null, Set.of()).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> AsteroidActiveSelector.retain(
                List.of(cut), List.of(), -1, 1, 2, null, Set.of()));
    }

    @Test void pinnedFracturesArePrioritizedButStillFitReducedGlobalAndViewerCaps() {
        var nearest = candidate(11, 0.5);
        var middle = candidate(12, 1.5);
        var farthest = candidate(13, 2.5);
        var viewer = List.of(new double[]{0, 0, 0});

        assertEquals(Set.of(nearest.id(), middle.id()), AsteroidActiveSelector.retain(
                List.of(farthest, middle, nearest), viewer, 10, 2, 2, null,
                Set.of(nearest.id(), middle.id(), farthest.id())));
        assertEquals(Set.of(nearest.id()), AsteroidActiveSelector.retain(
                List.of(farthest, middle, nearest), viewer, 10, 1, 4, null,
                Set.of(nearest.id(), middle.id(), farthest.id())));
    }

    @Test void detachedBodiesAndFragmentsConsumeGlobalAndNearbyViewerCapacity() {
        var active = candidate(8, 1);
        var viewer = List.of(new double[]{0, 0, 0});
        var nearbyDetached = List.of(new double[]{2, 0, 0});

        var atViewerLimit = AsteroidActiveSelector.select(List.of(active), viewer, 10, 1, 4,
                null, Set.of(), nearbyDetached);
        var atGlobalLimit = AsteroidActiveSelector.select(List.of(active), viewer, 10, 2, 2,
                null, Set.of(), List.of(new double[]{20, 0, 0}, new double[]{30, 0, 0}));

        assertTrue(atViewerLimit.retained().isEmpty(), "an already-live nearby body occupies the viewer slot");
        assertTrue(atGlobalLimit.retained().isEmpty(), "already-live bodies occupy global slots even when distant");
    }

    @Test void distantDetachedBodiesDoNotConsumeAViewersLocalLimit() {
        var active = candidate(9, 1);
        var viewer = List.of(new double[]{0, 0, 0});

        var selection = AsteroidActiveSelector.select(List.of(active), viewer, 10, 1, 4,
                null, Set.of(), List.of(new double[]{1_000, 0, 0}));

        assertEquals(Set.of(active.id()), selection.retained());
    }

    @Test void unlocatedLegacyDetachedBodiesReserveGlobalAndPerViewerCapacity() {
        var active = candidate(10, 1);
        var viewer = List.of(new double[]{0, 0, 0});

        var perViewerFull = AsteroidActiveSelector.select(List.of(active), viewer, 10, 1, 8,
                null, Set.of(), List.of(), 1);
        var globalFull = AsteroidActiveSelector.select(List.of(active), viewer, 10, 4, 2,
                null, Set.of(), List.of(), 2);

        assertTrue(perViewerFull.retained().isEmpty());
        assertTrue(globalFull.retained().isEmpty());
    }

    private static AsteroidField.Candidate candidate(long id, double x) {
        return new AsteroidField.Candidate(new AsteroidField.ObjectId(id, 0, 0, 0), id, x, 0, 0,
                AsteroidField.MIN_RADIUS, false);
    }
}
