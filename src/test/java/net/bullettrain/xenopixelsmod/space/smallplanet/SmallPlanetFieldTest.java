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

    @Test void proceduralSlotsReportOnlySlotsSupportedBySurfaceAddressSchema() {
        assertEquals(0, SmallPlanetField.proceduralSlotCount(systemWithSlots(0)));
        assertEquals(1, SmallPlanetField.proceduralSlotCount(systemWithSlots(1)));
        assertEquals(4, SmallPlanetField.proceduralSlotCount(systemWithSlots(4)));
        assertEquals(8, SmallPlanetField.proceduralSlotCount(systemWithSlots(8)));
        assertEquals(4, SmallPlanetField.proceduralSlotCount(SOL));
        assertEquals(new SmallPlanetField.SlotSummary(8, 8, 0), SmallPlanetField.summarizeSlots(systemWithSlots(8)));
        assertEquals(new SmallPlanetField.SlotSummary(0, 4, 0), SmallPlanetField.summarizeSlots(SOL));
    }

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

    @Test void nearbyQueryUsesTheBoundedHostedSystemResolver() {
        var expected = SmallPlanetField.generateSystem(WORLD_SEED, SYSTEM,
                SmallPlanetField.MAX_PLANETS_PER_SYSTEM, List.of(), List.of()).getFirst();
        var resolved = new java.util.concurrent.atomic.AtomicBoolean();

        var nearby = SmallPlanetField.nearby(WORLD_SEED, expected.x(), expected.y(), expected.z(), 0,
                SmallPlanetField.MAX_PLANETS_PER_SYSTEM, List.of(), List.of(), cell -> {
                    if (cell.equals(SYSTEM.cell())) {
                        resolved.set(true);
                        return SYSTEM;
                    }
                    return null;
                });

        assertTrue(resolved.get(), "nearby physicalization must consult the server's mapped-system resolver");
        assertTrue(nearby.stream().anyMatch(candidate -> candidate.id().equals(expected.id())));
    }

    @Test void SolGetsProceduralPlanetsButChartAndPlanetSlotLimitsStillApply() {
        var solBodies = SmallPlanetField.generateSystem(WORLD_SEED, SOL, Integer.MAX_VALUE, List.of(), List.of());
        assertEquals(4, solBodies.size());
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

    @Test void allEightCatalogSlotsGenerateWithStableDistinctIdentities() {
        var eightSlotSystem = systemWithSlots(8);
        var bodies = SmallPlanetField.generateSystem(WORLD_SEED, eightSlotSystem, Integer.MAX_VALUE,
                List.of(), List.of());
        assertEquals(8, bodies.size());
        assertEquals(java.util.stream.IntStream.range(0, 8).boxed().toList(),
                bodies.stream().map(body -> body.id().slot()).toList());
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

    @Test void orbitingQuerySelectsCurrentPositionsButPreservesSeededAnchorIdentity() {
        var anchor = SmallPlanetField.generateSystem(WORLD_SEED, SYSTEM, SmallPlanetField.MAX_PLANETS_PER_SYSTEM,
                List.of(), List.of()).get(0);
        var orbit = SmallPlanetOrbit.fromCandidate(anchor, SYSTEM.x(), SYSTEM.y(), SYSTEM.z());
        double time = orbit.periodSeconds() * 0.25;
        double[] current = new double[3];
        orbit.positionAt(time, current);
        var nearby = SmallPlanetField.orbitingNearby(WORLD_SEED, current[0], current[1], current[2], 0,
                SmallPlanetField.MAX_PLANETS_PER_SYSTEM, List.of(), List.of(),
                cell -> UniverseField.systemAt(WORLD_SEED, cell), 0, 0, 0, time);

        var found = nearby.stream().filter(value -> value.anchor().id().equals(anchor.id())).findFirst().orElseThrow();
        assertEquals(anchor.x(), found.anchor().x(), 0.0, "phase-zero anchor remains available for persistence");
        assertEquals(anchor.y(), found.anchor().y(), 0.0);
        assertEquals(anchor.z(), found.anchor().z(), 0.0);
        var evaluated = found.at(time);
        assertEquals(current[0], evaluated.x(), 1.0e-8);
        assertEquals(current[1], evaluated.y(), 1.0e-8);
        assertEquals(current[2], evaluated.z(), 1.0e-8);
        assertTrue(nearby.size() <= 9 * SmallPlanetField.MAX_PLANETS_PER_SYSTEM);
    }

    @Test void farLodSweepQueryKeepsAnOrbitThatWillEnterRangeAfterTheCacheIsBuilt() {
        var anchor = SmallPlanetField.generateSystem(WORLD_SEED, SYSTEM, SmallPlanetField.MAX_PLANETS_PER_SYSTEM,
                List.of(), List.of()).get(0);
        var orbit = SmallPlanetOrbit.fromCandidate(anchor, SYSTEM.x(), SYSTEM.y(), SYSTEM.z());
        double[] opposite = new double[3];
        orbit.positionAt(orbit.periodSeconds() * 0.5, opposite);
        var resolver = (java.util.function.Function<UniverseField.Cell, UniverseField.StarSystem>) cell ->
                UniverseField.systemAt(WORLD_SEED, cell);

        var currentOnly = SmallPlanetField.orbitingNearby(WORLD_SEED, opposite[0], opposite[1], opposite[2], 0,
                SmallPlanetField.MAX_PLANETS_PER_SYSTEM, List.of(), List.of(), resolver, 0, 0, 0, 0);
        var swept = SmallPlanetField.orbitingPotentiallyNearby(WORLD_SEED, opposite[0], opposite[1], opposite[2], 0,
                SmallPlanetField.MAX_PLANETS_PER_SYSTEM, List.of(), List.of(), resolver, 0, 0, 0, 0);

        assertTrue(currentOnly.stream().noneMatch(value -> value.anchor().id().equals(anchor.id())),
                "the anchor is on the far side of its orbit at the phase-zero query time");
        assertTrue(swept.stream().anyMatch(value -> value.anchor().id().equals(anchor.id())),
                "far LOD cache must retain an orbit whose swept sphere intersects the range");
    }

    @Test void homeSmallPlanetOrbitUsesChartSolFocusInsteadOfProceduralDisplayMarker() {
        var anchor = new SmallPlanetField.Candidate(new SmallPlanetField.ObjectId(SOL.cell(), 0, 0, 0),
                7, 2_000, 700, 0, 16, SmallPlanetField.Profile.ROCK, 0.3, false);
        var moved = SmallPlanetField.atOrbitTime(anchor, SOL, 0, 0, 0,
                SmallPlanetOrbit.scaledPeriodSeconds(2_000) * 0.25);
        assertTrue(Math.abs(moved.y() - SOL.y()) > 100,
                "the test uses charted Sol at y=0, not the procedural home marker at y=512");
        double distanceFromChartSol = Math.sqrt(moved.x() * moved.x() + moved.y() * moved.y() + moved.z() * moved.z());
        assertEquals(Math.sqrt(2_000 * 2_000 + 700 * 700), distanceFromChartSol, 1.0e-7);
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
        throw new AssertionError("test seed must contain a non-home system with at least four planet slots");
    }

    private static UniverseField.StarSystem systemWithSlots(int slots) {
        return new UniverseField.StarSystem(SYSTEM.cell(), SYSTEM.seed(), SYSTEM.name(), SYSTEM.starClass(),
                SYSTEM.x(), SYSTEM.y(), SYSTEM.z(), slots);
    }
}
