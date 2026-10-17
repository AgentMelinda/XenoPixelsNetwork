package net.bullettrain.xenopixelsmod.space.asteroid;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AsteroidCellScanCursorTest {
    @Test void resumesAcrossBudgetsAndVisitsEachCellOncePerSweep() {
        var cursor = new AsteroidCellScanCursor(new AsteroidCellScanCursor.Bounds(-1, 0, 4, 5, -2, -1));
        var visited = new ArrayList<AsteroidField.Cell>();

        var first = cursor.visit(3, visited::add);
        var second = cursor.visit(3, visited::add);
        var third = cursor.visit(2, visited::add);

        assertEquals(3, first.visited());
        assertFalse(first.completedSweep());
        assertEquals(3, second.visited());
        assertFalse(second.completedSweep());
        assertEquals(2, third.visited());
        assertTrue(third.completedSweep());
        assertEquals(8, visited.size());
        assertEquals(8, new HashSet<>(visited).size());
        assertEquals(List.of(
                new AsteroidField.Cell(-1, 4, -2), new AsteroidField.Cell(0, 4, -2),
                new AsteroidField.Cell(-1, 5, -2), new AsteroidField.Cell(0, 5, -2),
                new AsteroidField.Cell(-1, 4, -1), new AsteroidField.Cell(0, 4, -1),
                new AsteroidField.Cell(-1, 5, -1), new AsteroidField.Cell(0, 5, -1)), visited);
        assertEquals(0, cursor.nextIndex());
    }

    @Test void neverVisitsMoreThanTheConfiguredBudget() {
        var cursor = new AsteroidCellScanCursor(new AsteroidCellScanCursor.Bounds(0, 19, 0, 19, 0, 19));
        int[] count = {0};

        var result = cursor.visit(137, ignored -> count[0]++);

        assertEquals(137, result.visited());
        assertEquals(137, count[0]);
        assertFalse(result.completedSweep());
        assertEquals(137, result.nextIndex());
    }

    @Test void rejectsInvalidBoundsAndBudgets() {
        assertThrows(IllegalArgumentException.class,
                () -> new AsteroidCellScanCursor.Bounds(1, 0, 0, 0, 0, 0));
        assertThrows(ArithmeticException.class,
                () -> new AsteroidCellScanCursor.Bounds(Long.MIN_VALUE, Long.MAX_VALUE, 0, 0, 0, 0));
        var cursor = new AsteroidCellScanCursor(new AsteroidCellScanCursor.Bounds(0, 0, 0, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> cursor.visit(0, ignored -> {}));
    }
}
