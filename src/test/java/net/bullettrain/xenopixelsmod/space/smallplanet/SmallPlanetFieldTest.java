package net.bullettrain.xenopixelsmod.space.smallplanet;

import net.bullettrain.xenopixelsmod.space.asteroid.AsteroidField;
import net.bullettrain.xenopixelsmod.space.universe.UniverseField;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class SmallPlanetFieldTest {
    private static final long WORLD_SEED = 0x51A11L;
    private static final UniverseField.StarSystem SOL = UniverseField.systemAt(WORLD_SEED,
            new UniverseField.Cell(0, 0));
    private static final UniverseField.StarSystem SYSTEM = findSystemWithPlanets();

    @Test void generatedPlanetsInOuterSafeSystemStayInsideTheVanillaWorldBorder() {
        UniverseField.StarSystem edge = null;
        long seed = WORLD_SEED;
        for (int attempt = 0; attempt < 100 && (edge == null || edge.planetSlots() == 0); attempt++, seed++)
            edge = UniverseField.systemAt(seed, new UniverseField.Cell(UniverseField.MAX_CELL, UniverseField.MAX_CELL));
        assertNotNull(edge);
        assertTrue(edge.planetSlots() > 0);

        var planets = SmallPlanetField.generateSystem(seed - 1, edge, SmallPlanetField.MAX_PLANETS_PER_SYSTEM,
                List.of(), List.of());
        assertFalse(planets.isEmpty());
        for (var planet : planets) {
            assertTrue(Math.abs(planet.x()) + planet.radius() < UniverseField.WORLD_BORDER_LIMIT);
            assertTrue(Math.abs(planet.z()) + planet.radius() < UniverseField.WORLD_BORDER_LIMIT);
        }
    }

    @Test void generationIsDeterministicAndHardCappedPerSystem() {
        var first = SmallPlanetField.generateSystem(WORLD_SEED, SYSTEM, Integer.MAX_VALUE, List.of(), List.of());
        var second = SmallPlanetField.generateSystem(WORLD_SEED, SYSTEM, Integer.MAX_VALUE, List.of(), List.of());

        assertEquals(first, second);
        assertEquals(Math.min(SYSTEM.planetSlots(), SmallPlanetField.MAX_PLANETS_PER_SYSTEM), first.size());
        assertTrue(first.size() <= SYSTEM.planetSlots());
        assertNotEquals(first, SmallPlanetField.generateSystem(WORLD_SEED + 1, SYSTEM,
                SmallPlanetField.MAX_PLANETS_PER_SYSTEM, List.of(), List.of()));
        assertTrue(SmallPlanetField.generateSystem(WORLD_SEED, SYSTEM, 0, List.of(), List.of()).isEmpty());
    }

    @Test void SolGetsProceduralPlanetsButChartAndPlanetSlotLimitsStillApply() {
        var solBodies = SmallPlanetField.generateSystem(WORLD_SEED, SOL, Integer.MAX_VALUE, List.of(), List.of());
        assertEquals(SmallPlanetField.MAX_PLANETS_PER_SYSTEM, solBodies.size());
        assertEquals(solBodies, SmallPlanetField.generateSystem(WORLD_SEED, SOL,
                Integer.MAX_VALUE, List.of(), List.of()));
        assertTrue(SmallPlanetField.generateSystem(WORLD_SEED, SOL, 2, List.of(), List.of()).size() <= 2);
        var chartedSol = new SmallPlanetField.Sphere(SOL.x(), SOL.y(), SOL.z(), 100_000);
        assertTrue(SmallPlanetField.generateSystem(WORLD_SEED, SOL, Integer.MAX_VALUE,
                List.of(chartedSol), List.of()).isEmpty());
        var twoSlots = new UniverseField.StarSystem(SYSTEM.cell(), SYSTEM.seed(), SYSTEM.name(), SYSTEM.starClass(),
                SYSTEM.x(), SYSTEM.y(), SYSTEM.z(), 2);
        assertEquals(2, SmallPlanetField.generateSystem(WORLD_SEED, twoSlots, Integer.MAX_VALUE,
                List.of(), List.of()).size());
        var noSlots = new UniverseField.StarSystem(SYSTEM.cell(), SYSTEM.seed(), SYSTEM.name(), SYSTEM.starClass(),
                SYSTEM.x(), SYSTEM.y(), SYSTEM.z(), 0);
        assertTrue(SmallPlanetField.generateSystem(WORLD_SEED, noSlots, Integer.MAX_VALUE,
                List.of(), List.of()).isEmpty());
    }

    @Test void increasingRequestedCapDoesNotReshuffleEarlierPlanetSlots() {
        var two = SmallPlanetField.generateSystem(WORLD_SEED, SYSTEM, 2, List.of(), List.of());
        var four = SmallPlanetField.generateSystem(WORLD_SEED, SYSTEM, 4, List.of(), List.of());
        assertTrue(two.size() <= four.size());
        assertEquals(two, four.subList(0, two.size()));
    }

    @Test void generatedBodiesHaveBoundedRadiusSeededPaletteAndDeadDefault() {
        var bodies = SmallPlanetField.generateSystem(WORLD_SEED, SYSTEM,
                SmallPlanetField.MAX_PLANETS_PER_SYSTEM, List.of(), List.of());
        Set<SmallPlanetField.ObjectId> ids = new HashSet<>();
        for (var body : bodies) {
            assertTrue(ids.add(body.id()), "duplicate generated id " + body.id());
            assertTrue(body.radius() >= SmallPlanetField.MIN_RADIUS);
            assertTrue(body.radius() <= SmallPlanetField.MAX_RADIUS);
            assertTrue(body.oreRichness() >= 0 && body.oreRichness() <= 1);
            assertFalse(body.nativeLife(), "generated systems should default to dead worlds");
            assertNotEquals(0, body.profile().surfaceArgb);
            assertTrue(Math.abs(body.x() - (SYSTEM.x() + body.id().sectorX() * SmallPlanetField.SECTOR_SIZE))
                    <= 0.35 * SmallPlanetField.SECTOR_SIZE);
            assertTrue(Math.abs(body.z() - (SYSTEM.z() + body.id().sectorZ() * SmallPlanetField.SECTOR_SIZE))
                    <= 0.35 * SmallPlanetField.SECTOR_SIZE);
            assertTrue(body.y() >= SmallPlanetField.MAX_RADIUS);
            assertTrue(body.y() <= SmallPlanetField.VOIDSEA_HEIGHT - SmallPlanetField.MAX_RADIUS);
            assertEquals(body.radius() <= SmallPlanetField.WALKABLE_RADIUS_LIMIT,
                    body.surfaceMode() == SmallPlanetField.SurfaceMode.WALKABLE);
        }
        for (int i = 0; i < bodies.size(); i++) for (int j = i + 1; j < bodies.size(); j++) {
            var first = bodies.get(i);
            var second = bodies.get(j);
            double dx = first.x() - second.x(), dy = first.y() - second.y(), dz = first.z() - second.z();
            double minimum = first.radius() + second.radius() + SmallPlanetField.ORBITAL_CLEARANCE;
            assertTrue(dx * dx + dy * dy + dz * dz >= minimum * minimum,
                    "generated small planets overlap their clearance volume");
        }
    }

    @Test void chartedBodiesAndAsteroidBeltVolumesExcludeSmallPlanets() {
        var allSpace = new SmallPlanetField.Sphere(SYSTEM.x(), SYSTEM.y(), SYSTEM.z(), 100_000);
        assertTrue(SmallPlanetField.generateSystem(WORLD_SEED, SYSTEM, SmallPlanetField.MAX_PLANETS_PER_SYSTEM,
                List.of(allSpace), List.of()).isEmpty());

        var solidRing = new AsteroidField.Belt("test:wide_ring", SYSTEM.x(), SYSTEM.y(), SYSTEM.z(),
                0, 100_000, 1_024, false);
        assertTrue(SmallPlanetField.generateSystem(WORLD_SEED, SYSTEM, SmallPlanetField.MAX_PLANETS_PER_SYSTEM,
                List.of(), List.of(solidRing)).isEmpty());
    }

    @Test void obstacleInputOrderDoesNotChangeGeneratedPlanets() {
        var near = new SmallPlanetField.Sphere(SYSTEM.x() + 10_000, SYSTEM.y(), SYSTEM.z(), 1_000);
        var far = new SmallPlanetField.Sphere(SYSTEM.x() + 200_000, SYSTEM.y(), SYSTEM.z(), 10_000);
        var first = SmallPlanetField.generateSystem(WORLD_SEED, SYSTEM, SmallPlanetField.MAX_PLANETS_PER_SYSTEM,
                List.of(near, far), List.of());
        var second = SmallPlanetField.generateSystem(WORLD_SEED, SYSTEM, SmallPlanetField.MAX_PLANETS_PER_SYSTEM,
                List.of(far, near), List.of());
        assertEquals(first, second);
    }

    @Test void neighboringSystemPlanetFieldsDoNotOverlap() {
        var bodies = new java.util.ArrayList<SmallPlanetField.Candidate>();
        var cell = SYSTEM.cell();
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            var system = UniverseField.systemAt(WORLD_SEED,
                    new UniverseField.Cell(cell.x() + dx, cell.z() + dz));
            if (system != null && !system.cell().home())
                bodies.addAll(SmallPlanetField.generateSystem(WORLD_SEED, system,
                        SmallPlanetField.MAX_PLANETS_PER_SYSTEM, List.of(), List.of()));
        }
        for (int i = 0; i < bodies.size(); i++) for (int j = i + 1; j < bodies.size(); j++) {
            var first = bodies.get(i);
            var second = bodies.get(j);
            double dx = first.x() - second.x(), dy = first.y() - second.y(), dz = first.z() - second.z();
            double minimum = first.radius() + second.radius() + SmallPlanetField.ORBITAL_CLEARANCE;
            assertTrue(dx * dx + dy * dy + dz * dz >= minimum * minimum,
                    "neighboring system small planets overlap: " + first.id() + " / " + second.id());
        }
    }

    @Test void nearbyQueryFindsBodiesAcrossCellEdgesAndHasAFixedCandidateBound() {
        var bodies = new java.util.ArrayList<SmallPlanetField.Candidate>();
        var cell = SYSTEM.cell();
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            var system = UniverseField.systemAt(WORLD_SEED,
                    new UniverseField.Cell(cell.x() + dx, cell.z() + dz));
            if (system != null) bodies.addAll(SmallPlanetField.generateSystem(WORLD_SEED, system,
                    SmallPlanetField.MAX_PLANETS_PER_SYSTEM, List.of(), List.of()));
        }
        assertTrue(bodies.size() <= 9 * SmallPlanetField.MAX_PLANETS_PER_SYSTEM);
        assertFalse(bodies.isEmpty());
        var target = bodies.get(bodies.size() - 1);
        var nearby = SmallPlanetField.nearby(WORLD_SEED, target.x(), target.y(), target.z(), 0,
                SmallPlanetField.MAX_PLANETS_PER_SYSTEM, List.of(), List.of());
        assertTrue(nearby.contains(target), "candidate at the query point should be returned");
        assertTrue(nearby.size() <= 9 * SmallPlanetField.MAX_PLANETS_PER_SYSTEM);
        for (var candidate : nearby) {
            double dx = candidate.x() - target.x(), dy = candidate.y() - target.y(), dz = candidate.z() - target.z();
            assertTrue(dx * dx + dy * dy + dz * dz <= candidate.radius() * (double) candidate.radius());
        }
    }

    @Test void nearbyQueryRejectsInvalidRangesAndNonFinitePositions() {
        assertThrows(IllegalArgumentException.class, () -> SmallPlanetField.nearby(WORLD_SEED,
                0, 512, 0, SmallPlanetField.MAX_NEARBY_RANGE + 1, 4, List.of(), List.of()));
        assertThrows(IllegalArgumentException.class, () -> SmallPlanetField.nearby(WORLD_SEED,
                Double.NaN, 512, 0, 100, 4, List.of(), List.of()));
    }

    private static UniverseField.StarSystem findSystemWithPlanets() {
        for (int x = -10; x <= 10; x++) for (int z = -10; z <= 10; z++) {
            var system = UniverseField.systemAt(WORLD_SEED, new UniverseField.Cell(x, z));
            if (system != null && system.planetSlots() >= SmallPlanetField.MAX_PLANETS_PER_SYSTEM) return system;
        }
        throw new AssertionError("test seed must contain a non-home system with four planet slots");
    }
}
