package net.bullettrain.xenopixelsmod.space.dragonball;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DragonBallPlanetShapeTest {
    @Test void starHasPointsAndNotches() {
        assertTrue(DragonBallPlanetShape.inFivePointStar(0, 0, 1), "centre");
        assertTrue(DragonBallPlanetShape.inFivePointStar(0, 0.95, 1), "top point tip");
        assertFalse(DragonBallPlanetShape.inFivePointStar(0, -0.95, 1), "bottom notch between two points");
        assertFalse(DragonBallPlanetShape.inFivePointStar(0, 1.05, 1), "outside");
    }

    @Test void starsOnlyOnFrontFace() {
        assertTrue(DragonBallPlanetShape.inStar(1, 0, 0, 1));
        assertFalse(DragonBallPlanetShape.inStar(1, 0, 0, -1));
        assertFalse(DragonBallPlanetShape.inStar(7, 0, 0, -1));
        for (int stars = 1; stars <= 7; stars++) assertFalse(DragonBallPlanetShape.inStar(stars, 0.99, 0, 0.14), "rim");
    }

    @Test void hollowShellCountsAndEveryStarCountShowsInlay() {
        int radius = 16;
        for (int stars = 1; stars <= 7; stars++) {
            int shell = 0, inlay = 0;
            for (int x = -radius - 1; x <= radius + 1; x++)
                for (int y = -radius - 1; y <= radius + 1; y++)
                    for (int z = -radius - 1; z <= radius + 1; z++) {
                        int v = DragonBallPlanetShape.voxel(stars, radius, 2, x, y, z);
                        if (v == 1) shell++; else if (v == 2) inlay++;
                    }
            double expected = 4.0 / 3 * Math.PI * (Math.pow(radius + 0.5, 3) - Math.pow(radius - 1.5, 3));
            assertEquals(expected, shell + inlay, expected * 0.05, "shell volume, stars=" + stars);
            assertTrue(inlay >= 8 * stars, "visible inlay for " + stars + " stars: " + inlay);
        }
        assertEquals(0, DragonBallPlanetShape.voxel(1, radius, 2, 0, 0, 0), "hollow centre");
        assertEquals(1, DragonBallPlanetShape.voxel(1, 4, 99, 0, 0, 0), "solid when thickness >= radius");
    }
}
