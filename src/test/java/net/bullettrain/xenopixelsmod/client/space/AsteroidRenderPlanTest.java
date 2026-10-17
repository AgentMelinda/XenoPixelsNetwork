package net.bullettrain.xenopixelsmod.client.space;

import net.bullettrain.xenopixelsmod.space.asteroid.AsteroidField;
import net.bullettrain.xenopixelsmod.space.asteroid.AsteroidFieldSnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class AsteroidRenderPlanTest {
    private static final AsteroidField.Belt BELT = new AsteroidField.Belt("test", 0, 0, 0, 0, 1800, 1800, false);
    private static final AsteroidFieldSnapshot FIELD = new AsteroidFieldSnapshot(1, 72,
            List.of(BELT));

    @Test void selectionIsRepeatableAndNearestFirst() {
        var a = AsteroidRenderPlan.visible(FIELD, Set.of(), 0, 0, 0, 2048, 512);
        var b = AsteroidRenderPlan.visible(FIELD, Set.of(), 0, 0, 0, 2048, 512);
        assertEquals(a, b);
        for (int i = 1; i < a.size(); i++) assertTrue(distanceSq(a.get(i - 1)) <= distanceSq(a.get(i)));
    }

    @Test void physicalPresenceSuppressesTheSameStableIds() {
        var all = AsteroidRenderPlan.visible(FIELD, Set.of(), 0, 0, 0, 2048, 512);
        assertFalse(all.isEmpty());
        var omitted = AsteroidRenderPlan.visible(FIELD, Set.of(all.get(0).id()), 0, 0, 0, 2048, 512);
        assertFalse(omitted.stream().anyMatch(candidate -> candidate.id().equals(all.get(0).id())));
        if (all.size() < AsteroidRenderPlan.MAX_VISIBLE) assertEquals(all.size() - 1, omitted.size());
        else assertEquals(AsteroidRenderPlan.MAX_VISIBLE, omitted.size());
    }

    @Test void hardVisibleCapIsEnforced() {
        assertEquals(3, AsteroidRenderPlan.visible(FIELD, Set.of(), 0, 0, 0, 2048, 3).size());
        assertThrows(IllegalArgumentException.class,
                () -> AsteroidRenderPlan.visible(FIELD, Set.of(), 0, 0, 0, 4096, 513));
    }

    @Test void candidatesOutsideTheDrawSphereAreOmitted() {
        assertTrue(AsteroidRenderPlan.visible(FIELD, Set.of(), 0, 0, 0, 1, 512).stream()
                .allMatch(candidate -> distanceSq(candidate) <= 1));
    }

    private static double distanceSq(AsteroidField.Candidate candidate) {
        return candidate.x() * candidate.x() + candidate.y() * candidate.y() + candidate.z() * candidate.z();
    }
}
