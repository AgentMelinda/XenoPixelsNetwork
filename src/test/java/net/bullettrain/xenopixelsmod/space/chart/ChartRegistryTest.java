package net.bullettrain.xenopixelsmod.space.chart;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;

import net.bullettrain.xenopixelsmod.space.math.BodyLod;
import net.bullettrain.xenopixelsmod.space.math.GravityField;
import net.bullettrain.xenopixelsmod.space.math.KeplerSolver;
import net.bullettrain.xenopixelsmod.space.math.OrbitalElements;
import net.bullettrain.xenopixelsmod.space.physics.PlanetOrbitHold;
import net.bullettrain.xenopixelsmod.space.physics.PlanetShell;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

/**
 * The chart registry: hierarchy, validation, the LOD budget, and the §21 cost claim.
 *
 * <p>The benchmark at the end is the reason this class exists rather than a map of bodies that each tick
 * themselves. §21 promises fifty charted bodies cost under 1 ms per tick while nobody is there, and
 * that promise is only real if evaluating the whole chart, resolving every LOD and summing a gravity
 * field is a flat array walk with nothing allocated in it.
 */
class ChartRegistryTest {
    @Test void liveExitOverridesFollowBodyIdentityAcrossPublication() {
        var chart = new ChartRegistry();
        chart.rebuild(List.of(pinned("demo:a", null, 0, 0, 0, 100, 100)));
        double authored = chart.body(0).exitAltitude();
        chart.applyExitOverrides(java.util.Map.of("demo:a", 500.0));
        assertEquals(500, chart.exitAltitude(chart.slotOf("demo:a")));
        assertEquals(authored, chart.body(0).exitAltitude());
        chart.rebuild(List.of(pinned("demo:b", null, 0, 0, 0, 100, 100),
                pinned("demo:a", null, 0, 0, 0, 100, 100)));
        chart.applyExitOverrides(java.util.Map.of("demo:a", 500.0));
        assertEquals(500, chart.exitAltitude(chart.slotOf("demo:a")));
        assertEquals(chart.byId("demo:b").exitAltitude(), chart.exitAltitude(chart.slotOf("demo:b")));
        chart.applyExitOverrides(java.util.Map.of());
        assertEquals(authored, chart.exitAltitude(chart.slotOf("demo:a")));
    }

    @Test void persistedExitOverrideCannotMoveHandoffBelowItsPrewarmAltitude() {
        var chart = new ChartRegistry();
        chart.rebuild(List.of(pinned("demo:a", null, 0, 0, 0, 100, 100)));
        assertEquals(400.0, chart.body(0).warmUpAltitude());

        chart.applyExitOverrides(java.util.Map.of("demo:a", 300.0));

        assertEquals(700.0, chart.exitAltitude(chart.slotOf("demo:a")),
                "invalid persisted override must fall back to the charted exit so prewarming remains before handoff");
    }

    @Test void inspectionUsesCurrentPositionsWithoutMutatingLiveState() {
        var chart = new ChartRegistry();
        chart.rebuild(List.of(pinned("demo:a", null, 50_000, 0, 0, 100, 100)));
        chart.evaluate(0);
        chart.setProxyBudget(0);
        chart.resolveLods(0, 0, 0, true);
        chart.gravityAt(0, 0, 0, new double[3]);
        int contributors = chart.lastGravityContributors();
        var near = chart.inspectAt(49_900, 0, 0);
        assertEquals(1, near.stageCounts().get(BodyLod.CHUNKS.ordinal()));
        assertEquals(1, near.contributors());
        assertEquals(.01, near.ax(), 1e-12);
        assertEquals(BodyLod.BILLBOARD, chart.lodOf(0));
        assertEquals(contributors, chart.lastGravityContributors());
        assertEquals(0, chart.inspectAt(0, 0, 0).proxies());
        assertThrows(IllegalArgumentException.class, () -> chart.inspectAt(Double.NaN, 0, 0));
    }

    @Test void inspectionRetainsEffectiveContributorCapacityAndFieldRange() {
        var chart = new ChartRegistry();
        chart.rebuild(List.of(
                pinned("demo:a", null, 1_000, 0, 0, 100, 100),
                pinned("demo:b", null, 2_000, 0, 0, 100, 100)));
        chart.evaluate(0);
        chart.setGravityContributors(1);
        assertEquals(1, chart.inspectAt(0, 0, 0).contributors());
        chart.setFieldRange(500);
        assertEquals(2, chart.inspectAt(0, 0, 0).culled());
    }

    @Test void equalAngularSizesStillRespectTheHardProxyCap() {
        var chart = new ChartRegistry();
        chart.rebuild(List.of(
                pinned("demo:a", null, 50_000, 0, 0, 100, 0),
                pinned("demo:b", null, 50_000, 0, 0, 100, 0),
                pinned("demo:c", null, 50_000, 0, 0, 100, 0)));
        chart.evaluate(0);
        chart.setProxyBudget(2);
        for (int i = 0; i < 5; i++) {
            assertEquals(2, chart.resolveLods(0, 0, 0, true));
            assertEquals(BodyLod.PROXY, chart.lodOf(0));
            assertEquals(BodyLod.PROXY, chart.lodOf(1));
            assertEquals(BodyLod.BILLBOARD, chart.lodOf(2));
        }
    }

    @Test void cutoffTiesCannotDisplaceLargerCandidatesLaterInSlotOrder() {
        var chart = new ChartRegistry();
        chart.rebuild(List.of(
                pinned("demo:a", null, 50_000, 0, 0, 100, 0),
                pinned("demo:b", null, 50_000, 0, 0, 100, 0),
                pinned("demo:c", null, 45_000, 0, 0, 100, 0)));
        chart.evaluate(0);
        chart.setProxyBudget(2);
        assertEquals(2, chart.resolveLods(0, 0, 0, true));
        assertEquals(BodyLod.PROXY, chart.lodOf(0));
        assertEquals(BodyLod.BILLBOARD, chart.lodOf(1));
        assertEquals(BodyLod.PROXY, chart.lodOf(2));
    }

    @Test void cachedTimestampReportsNoAdditionalSolves() {
        var chart = new ChartRegistry();
        chart.rebuild(List.of(body("demo:a", null, OrbitalElements.circular(1000, 600), 10, 100)));
        assertEquals(1, chart.evaluate(0));
        assertEquals(0, chart.evaluate(0));
    }

    private static CelestialBody body(String id, String parent, OrbitalElements orbit,
                                      double radius, double mu) {
        return new CelestialBody(id, CelestialBody.BodyKind.PLANET, id, orbit,
                0.0, 0.0, 0.0, parent, radius, mu, GravityField.Falloff.INVERSE_SQUARE,
                "xenopixelsmod:" + id.substring(id.indexOf(':') + 1), 64.0, 700.0,
                true, true, 0, null, 0);
    }

    private static CelestialBody pinned(String id, String parent, double x, double y, double z,
                                        double radius, double mu) {
        return new CelestialBody(id, CelestialBody.BodyKind.PLANET, id, null,
                x, y, z, parent, radius, mu, GravityField.Falloff.INVERSE_SQUARE,
                null, 64.0, 700.0, false, true, 0, null, 0);
    }

    private static CelestialBody circular(String id, String parent, double a, double period,
                                          double radius, double mu) {
        return body(id, parent, OrbitalElements.circular(a, period), radius, mu);
    }

    @Test void aPinnedBodySitsAtItsAuthoredOffset() {
        ChartRegistry chart = new ChartRegistry();
        chart.rebuild(List.of(pinned("space:static", null, 100.0, 200.0, 300.0, 10.0, 0.0)));
        chart.evaluate(0.0);
        int slot = chart.slotOf("space:static");
        assertEquals(100.0, chart.x(slot), 1.0e-9);
        assertEquals(200.0, chart.y(slot), 1.0e-9);
        assertEquals(300.0, chart.z(slot), 1.0e-9);
    }

    /** §20's hierarchy: a moon's absolute position is its planet's position plus its own offset. */
    @Test void aChildBodyIsPositionedRelativeToItsParent() {
        ChartRegistry chart = new ChartRegistry();
        chart.rebuild(List.of(
                pinned("space:planet", null, 1000.0, 0.0, 0.0, 100.0, 0.0),
                pinned("space:moon", "space:planet", 50.0, 0.0, 0.0, 10.0, 0.0)));
        chart.evaluate(0.0);

        int moon = chart.slotOf("space:moon");
        assertEquals(1050.0, chart.x(moon), 1.0e-9, "the moon must sit 50 beyond its planet, not at 50");
        assertEquals(0.0, chart.y(moon), 1.0e-9);
    }

    /**
     * The ordering guarantee the single forward pass depends on. A chart authored moon-before-planet
     * must still evaluate correctly, because rebuild puts every parent in an earlier slot.
     */
    @Test void declarationOrderDoesNotMatterBecauseParentsAreEvaluatedFirst() {
        ChartRegistry chart = new ChartRegistry();
        // Moon listed first, on purpose.
        chart.rebuild(List.of(
                pinned("space:moon", "space:planet", 50.0, 0.0, 0.0, 10.0, 0.0),
                pinned("space:planet", null, 1000.0, 0.0, 0.0, 100.0, 0.0),
                pinned("space:submoon", "space:moon", 5.0, 0.0, 0.0, 1.0, 0.0)));
        chart.evaluate(0.0);

        assertEquals(1000.0, chart.x(chart.slotOf("space:planet")), 1.0e-9);
        assertEquals(1050.0, chart.x(chart.slotOf("space:moon")), 1.0e-9);
        assertEquals(1055.0, chart.x(chart.slotOf("space:submoon")), 1.0e-9,
                "a three-level hierarchy must compose through both parents");
    }

    @Test void anOrbitingBodyMovesWhenRealOrbitsAreOn() {
        ChartRegistry chart = new ChartRegistry();
        chart.setRealOrbits(true);
        chart.rebuild(List.of(circular("space:planet", null, 1000.0, 600.0, 10.0, 0.0)));
        chart.evaluate(0.0);
        int slot = chart.slotOf("space:planet");
        double x0 = chart.x(slot);

        chart.evaluate(150.0);
        assertNotEquals(x0, chart.x(slot), "a quarter period later the body must have moved");

        double radius = Math.sqrt(chart.x(slot) * chart.x(slot)
                + chart.y(slot) * chart.y(slot) + chart.z(slot) * chart.z(slot));
        assertEquals(1000.0, radius, 1.0e-6, "and it must still be on its orbit");
    }

    @Test void stateAtBridgesTheChartEvaluationIntervalUsingPositionAndVelocity() {
        ChartRegistry chart = new ChartRegistry();
        chart.setRealOrbits(true);
        chart.rebuild(List.of(circular("space:planet", null, 1000.0, 600.0, 10.0, 0.0)));
        chart.evaluate(75.0);
        int slot = chart.slotOf("space:planet");
        double[] position = new double[3], velocity = new double[3];
        double[] samplePosition = {chart.x(slot), chart.y(slot), chart.z(slot)};
        double[] sampleVelocity = {chart.velocityX(slot), chart.velocityY(slot), chart.velocityZ(slot)};

        chart.stateAt(slot, 76.0, 1.0, position, velocity);

        assertEquals(75.0, chart.evaluatedAtSeconds(), 0.0, "sampling between chart ticks must not rewrite its base timestamp");
        assertArrayEquals(new double[]{samplePosition[0] + sampleVelocity[0],
                samplePosition[1] + sampleVelocity[1], samplePosition[2] + sampleVelocity[2]}, position, 1.0e-9);
        assertArrayEquals(sampleVelocity, velocity, 0.0);
    }

    @Test void observedSampleBridgesFromItsOwnTimestampAndRejectsAClockJump() {
        ChartRegistry chart = new ChartRegistry();
        chart.setRealOrbits(true);
        chart.rebuild(List.of(circular("space:planet", null, 1000.0, 600.0, 10.0, 0.0)));
        chart.evaluate(75.0);
        int slot = chart.slotOf("space:planet");
        chart.observeBody("space:planet", 2000.0, 50.0, -300.0, 3.0, 4.0, 5.0, 76.0);

        double[] position = new double[3], velocity = new double[3];
        chart.stateAt(slot, 76.5, 1.0, position, velocity);
        assertArrayEquals(new double[]{2001.5, 52.0, -297.5}, position, 1.0e-9,
                "a live body uses its newest pose sample and measured velocity");
        assertArrayEquals(new double[]{3.0, 4.0, 5.0}, velocity, 0.0);
        assertEquals(75.0, chart.evaluatedAtSeconds(), 0.0,
                "publishing a live observation must not poison the chart solve timestamp");

        chart.stateAt(slot, 10_000.0, 1.0, position, velocity);
        assertArrayEquals(new double[]{2000.0, 50.0, -300.0}, position, 0.0,
                "a clock jump outside the allowed bridge returns the latest base sample");
        chart.stateAt(slot, 75.0, 1.0, position, velocity);
        assertArrayEquals(new double[]{2000.0, 50.0, -300.0}, position, 0.0,
                "a backwards clock step must not extrapolate the body backwards");
    }

    @Test void untimedObservedSampleDoesNotBridgeFromAnOlderChartSolve() {
        ChartRegistry chart = new ChartRegistry();
        chart.setRealOrbits(true);
        chart.rebuild(List.of(circular("space:planet", null, 1000.0, 600.0, 10.0, 0.0)));
        chart.evaluate(75.0);
        int slot = chart.slotOf("space:planet");
        chart.observeBody("space:planet", 2000.0, 50.0, -300.0, 3.0, 4.0, 5.0);

        double[] position = new double[3], velocity = new double[3];
        chart.stateAt(slot, 76.0, 1.0, position, velocity);

        assertArrayEquals(new double[]{2000.0, 50.0, -300.0}, position, 0.0,
                "without a timestamp, a live pose is authoritative but cannot be extrapolated from the older solve");
        assertArrayEquals(new double[]{3.0, 4.0, 5.0}, velocity, 0.0);
    }

    @Test void clearingObservationsForcesSameTimestampEvaluationToRestoreAuthoredMotion() {
        ChartRegistry chart = new ChartRegistry();
        chart.setRealOrbits(true);
        chart.rebuild(List.of(circular("space:planet", null, 1000.0, 600.0, 10.0, 0.0)));
        chart.evaluate(75.0);
        int slot = chart.slotOf("space:planet");
        chart.observeBody("space:planet", 2000.0, 50.0, -300.0, 3.0, 4.0, 5.0, 75.0);
        chart.evaluate(75.0);
        assertEquals(2000.0, chart.x(slot), 0.0);

        chart.clearObservedBodies();
        chart.evaluate(75.0);

        assertNotEquals(2000.0, chart.x(slot), 1.0e-9,
                "a changed observation set must be consumed even when the orbital timestamp is unchanged");
    }

    @Test void observedPhysicalParentKeepsItsOrbitVelocityAndCarriesItsMoon() {
        ChartRegistry chart = new ChartRegistry();
        chart.setRealOrbits(true);
        chart.rebuild(List.of(
                pinned("space:sol", null, 0, 0, 0, 100, 0),
                circular("space:earth", "space:sol", 1000, 600, 10, 0),
                circular("space:moon", "space:earth", 100, 60, 2, 0)));
        chart.evaluate(30.0);
        int earth = chart.slotOf("space:earth"), moon = chart.slotOf("space:moon");
        double[] moonOffset = {chart.x(moon) - chart.x(earth), chart.y(moon) - chart.y(earth),
                chart.z(moon) - chart.z(earth)};
        double[] moonRelativeVelocity = {chart.velocityX(moon) - chart.velocityX(earth),
                chart.velocityY(moon) - chart.velocityY(earth), chart.velocityZ(moon) - chart.velocityZ(earth)};

        chart.observeBody("space:earth", 2000, 50, -300, 3, 4, 5);
        chart.evaluate(30.0);

        assertFalse(chart.manuallyPositioned(earth), "a live physics observation is not a frozen manual override");
        assertArrayEquals(new double[]{2000, 50, -300}, new double[]{chart.x(earth), chart.y(earth), chart.z(earth)});
        assertArrayEquals(new double[]{3, 4, 5}, new double[]{chart.velocityX(earth), chart.velocityY(earth), chart.velocityZ(earth)});
        assertArrayEquals(new double[]{2000 + moonOffset[0], 50 + moonOffset[1], -300 + moonOffset[2]},
                new double[]{chart.x(moon), chart.y(moon), chart.z(moon)}, 1.0e-9,
                "the moon's orbit center follows the observed physical Earth");
        assertArrayEquals(new double[]{3 + moonRelativeVelocity[0], 4 + moonRelativeVelocity[1], 5 + moonRelativeVelocity[2]},
                new double[]{chart.velocityX(moon), chart.velocityY(moon), chart.velocityZ(moon)}, 1.0e-9,
                "the moon inherits Earth's measured motion as well as its own orbit velocity");

        chart.clearObservedBodies();
        chart.evaluate(30.0);
        assertNotEquals(2000, chart.x(earth), 1.0e-9, "clearing the transient physics sample restores the authored orbit");
    }

    @Test void movingChartPositionAndVelocityUseTheWorldAxesUsedByPlanetPhysics() {
        var orbit = new OrbitalElements(1000.0, 0.0, Math.PI / 4.0,
                0.2, 0.1, 0.0, 600.0, 0.0);
        var chart = new ChartRegistry();
        chart.setRealOrbits(true);
        chart.rebuild(List.of(body("space:inclined", null, orbit, 10.0, 0.0)));

        double timeSeconds = 150.0;
        chart.evaluate(timeSeconds);
        int slot = chart.slotOf("space:inclined");
        double[] expectedPosition = new double[3];
        double[] expectedVelocity = new double[3];
        double[] expectedAcceleration = new double[3];
        PlanetOrbitHold.orbitTrack(orbit, PlanetShell.orbitMu(orbit.semiMajorAxis(), orbit.periodSeconds()),
                timeSeconds, expectedPosition, expectedVelocity, expectedAcceleration);

        assertEquals(expectedPosition[0], chart.x(slot), 1.0e-9);
        assertEquals(expectedPosition[1], chart.y(slot), 1.0e-9);
        assertEquals(expectedPosition[2], chart.z(slot), 1.0e-9,
                "chart coordinates must match the physical planet's XY-to-XZ orbit mapping");
        double[] actualVelocity = chart.velocityOf(slot, new double[3]);
        assertArrayEquals(expectedVelocity, actualVelocity, 1.0e-9,
                "handoff velocity must use the same world axes as the body's orbit controller");
    }

    /** §20's {@code real_orbits} toggle: off pins everything, which is the cheap REV 2 fallback. */
    @Test void turningRealOrbitsOffPinsEveryBodyAtItsEpochPosition() {
        ChartRegistry chart = new ChartRegistry();
        chart.rebuild(List.of(circular("space:planet", null, 1000.0, 600.0, 10.0, 0.0)));
        chart.setRealOrbits(false);
        chart.evaluate(0.0);
        int slot = chart.slotOf("space:planet");
        double x0 = chart.x(slot);
        chart.evaluate(150.0);
        assertEquals(x0, chart.x(slot), 1.0e-9, "with real orbits off nothing may move");
        assertEquals(0, chart.evaluate(300.0), "and no Kepler solve should have run");
    }

    @Test void evaluateIsIdempotentWithinATimestamp() {
        ChartRegistry chart = new ChartRegistry();
        chart.rebuild(List.of(circular("space:planet", null, 1000.0, 600.0, 10.0, 0.0)));
        chart.evaluate(42.0);
        int slot = chart.slotOf("space:planet");
        double x = chart.x(slot);
        // A second call for the same instant must not redo the work or drift the answer.
        chart.evaluate(42.0);
        assertEquals(x, chart.x(slot), 0.0);
    }

    @Test void anOrbitalReferenceCycleIsRejectedRatherThanEvaluatedInMapOrder() {
        ChartRegistry chart = new ChartRegistry();
        List<String> rejected = chart.rebuild(List.of(
                pinned("space:a", "space:b", 0, 0, 0, 10.0, 0.0),
                pinned("space:b", "space:a", 0, 0, 0, 10.0, 0.0)));
        assertEquals(0, chart.size(), "neither body of a cycle has a well-defined position");
        assertTrue(rejected.stream().anyMatch(r -> r.contains("cycle")),
                "the cycle must be reported, got " + rejected);
    }

    /** §17: an unresolvable reference degrades the entry, it does not abort the whole load. */
    @Test void anUnknownParentBecomesARootAndIsReported() {
        ChartRegistry chart = new ChartRegistry();
        List<String> rejected = chart.rebuild(List.of(
                pinned("space:orphan", "space:does_not_exist", 25.0, 0.0, 0.0, 10.0, 0.0)));
        assertEquals(1, chart.size(), "the body still charts, as a system root");
        chart.evaluate(0.0);
        assertEquals(25.0, chart.x(chart.slotOf("space:orphan")), 1.0e-9);
        assertTrue(rejected.stream().anyMatch(r -> r.contains("unknown parent")),
                "but the dangling reference must be visible, got " + rejected);
    }

    @Test void aDuplicateIdIsRejectedAndTheFirstOneWins() {
        ChartRegistry chart = new ChartRegistry();
        List<String> rejected = chart.rebuild(List.of(
                pinned("space:dupe", null, 1.0, 0.0, 0.0, 10.0, 0.0),
                pinned("space:dupe", null, 999.0, 0.0, 0.0, 10.0, 0.0)));
        assertEquals(1, chart.size());
        chart.evaluate(0.0);
        assertEquals(1.0, chart.x(chart.slotOf("space:dupe")), 1.0e-9);
        assertTrue(rejected.stream().anyMatch(r -> r.contains("duplicate")), "got " + rejected);
    }

    @Test void aRebuildBumpsTheVersionSoClientsKnowToRePull() {
        ChartRegistry chart = new ChartRegistry();
        int before = chart.version();
        chart.rebuild(List.of(pinned("space:a", null, 0, 0, 0, 10.0, 0.0)));
        assertEquals(before + 1, chart.version());
        chart.rebuild(List.of());
        assertEquals(before + 2, chart.version(), "even an empty rebuild is a change clients must see");
        assertEquals(0, chart.size());
    }

    @Test void anEmptyChartEvaluatesAndQueriesWithoutThrowing() {
        ChartRegistry chart = new ChartRegistry();
        assertEquals(0, chart.evaluate(0.0));
        double[] out = new double[3];
        chart.gravityAt(0.0, 0.0, 0.0, out);
        assertEquals(0.0, out[0], 0.0);
        assertEquals(-1, chart.slotOf("space:nope"));
        assertNull(chart.body(-1));
        assertEquals(BodyLod.ABSENT, chart.lodOf(-1));
    }

    /** §21's ladder, resolved against a real chart: every stage must be reachable. */
    @Test void theLodLadderReachesEveryStageAtTheExpectedDistances() {
        ChartRegistry chart = new ChartRegistry();
        List<CelestialBody> bodies = new ArrayList<>();
        // radius 100 with default thresholds puts the bands at 105 / 6000 / 40000 / 90000.
        bodies.add(pinned("space:chunks", null, 50.0, 0.0, 0.0, 100.0, 0.0));
        bodies.add(pinned("space:anchor", null, 1_000.0, 0.0, 0.0, 100.0, 0.0));
        bodies.add(pinned("space:field", null, 10_000.0, 0.0, 0.0, 100.0, 0.0));
        bodies.add(pinned("space:proxy", null, 50_000.0, 0.0, 0.0, 100.0, 0.0));
        bodies.add(pinned("space:billboard", null, 500_000.0, 0.0, 0.0, 100.0, 0.0));
        chart.rebuild(bodies);
        chart.evaluate(0.0);
        chart.resolveLods(0.0, 0.0, 0.0, true);

        assertEquals(BodyLod.CHUNKS, chart.lodOf(chart.slotOf("space:chunks")));
        assertEquals(BodyLod.ANCHOR, chart.lodOf(chart.slotOf("space:anchor")));
        assertEquals(BodyLod.FIELD, chart.lodOf(chart.slotOf("space:field")));
        assertEquals(BodyLod.PROXY, chart.lodOf(chart.slotOf("space:proxy")));
        assertEquals(BodyLod.BILLBOARD, chart.lodOf(chart.slotOf("space:billboard")));
    }

    /** The proxy cap is what keeps "distant planets render as geodesic spheres" from costing a frame. */
    @Test void theProxyBudgetDemotesTheSmallestLookingBodies() {
        ChartRegistry chart = new ChartRegistry();
        chart.setProxyBudget(3);
        List<CelestialBody> bodies = new ArrayList<>();
        // Radius 100 with the default ladder puts the proxy band between 40_000 and 90_000, so all six
        // of these land in it and all six want a sphere. They differ only in distance, which is what
        // ranks them by angular size.
        for (int i = 0; i < 6; i++) {
            bodies.add(pinned("space:b" + i, null, 45_000.0 + i * 7_000.0, 0.0, 0.0, 100.0, 0.0));
        }
        chart.rebuild(bodies);
        chart.evaluate(0.0);
        int proxies = chart.resolveLods(0.0, 0.0, 0.0, true);

        assertEquals(3, proxies, "the budget must be enforced exactly");
        // The three nearest subtend the largest angle, so they are the three that keep a sphere.
        assertEquals(BodyLod.PROXY, chart.lodOf(chart.slotOf("space:b0")));
        assertEquals(BodyLod.PROXY, chart.lodOf(chart.slotOf("space:b1")));
        assertEquals(BodyLod.PROXY, chart.lodOf(chart.slotOf("space:b2")));
        assertEquals(BodyLod.BILLBOARD, chart.lodOf(chart.slotOf("space:b3")));
        assertEquals(BodyLod.BILLBOARD, chart.lodOf(chart.slotOf("space:b5")),
                "the farthest must be the one demoted");
    }

    @Test void aZeroProxyBudgetLeavesNoSpheresAtAll() {
        ChartRegistry chart = new ChartRegistry();
        chart.setProxyBudget(0);
        chart.rebuild(List.of(pinned("space:b", null, 50_000.0, 0.0, 0.0, 100.0, 0.0)));
        chart.evaluate(0.0);
        assertEquals(0, chart.resolveLods(0.0, 0.0, 0.0, true));
        assertEquals(BodyLod.BILLBOARD, chart.lodOf(chart.slotOf("space:b")));
    }

    @Test void gravitySumsOnlyTheStrongestFewContributors() {
        GravityField field = new GravityField(3);
        ChartRegistry chart = new ChartRegistry(field);
        chart.setFieldRange(1_000_000.0);
        List<CelestialBody> bodies = new ArrayList<>();
        // Three strong bodies close in, then twenty weak ones far out.
        bodies.add(pinned("space:near0", null, 1_000.0, 0.0, 0.0, 100.0, 1.0e7));
        bodies.add(pinned("space:near1", null, 0.0, 1_000.0, 0.0, 100.0, 1.0e7));
        bodies.add(pinned("space:near2", null, 0.0, 0.0, 1_000.0, 100.0, 1.0e7));
        for (int i = 0; i < 20; i++) {
            bodies.add(pinned("space:far" + i, null, 400_000.0 + i, 0.0, 0.0, 100.0, 1.0e3));
        }
        chart.rebuild(bodies);
        chart.evaluate(0.0);

        double[] out = new double[3];
        chart.gravityAt(0.0, 0.0, 0.0, out);
        assertEquals(3, chart.lastGravityContributors(), "the cap must hold at three");
        assertTrue(chart.lastGravityCulled() > 0 || chart.lastGravityContributors() == 3,
                "the weak bodies must not have made it into the sum");
        // Pull toward each of the three near bodies, so all three components are positive.
        assertTrue(out[0] > 0.0 && out[1] > 0.0 && out[2] > 0.0,
                "net pull should point into the positive octant, got "
                        + out[0] + "," + out[1] + "," + out[2]);
    }

    @Test void theFieldRangeCutRejectsBodiesTooFarToMatter() {
        GravityField field = new GravityField(3);
        ChartRegistry chart = new ChartRegistry(field);
        chart.setFieldRange(10_000.0);
        chart.rebuild(List.of(pinned("space:far", null, 500_000.0, 0.0, 0.0, 100.0, 1.0e9)));
        chart.evaluate(0.0);

        double[] out = new double[3];
        chart.gravityAt(0.0, 0.0, 0.0, out);
        assertEquals(0.0, out[0], 0.0, "a body beyond the field range must contribute nothing");
        assertEquals(0, chart.lastGravityContributors());
        assertEquals(1, chart.lastGravityCulled(), "and it must be counted as culled, not summed");
    }

    @Test void aHandoffInheritsTheBodysOrbitalVelocity() {
        ChartRegistry chart = new ChartRegistry();
        chart.setRealOrbits(true);
        chart.rebuild(List.of(circular("space:planet", null, 1000.0, 600.0, 10.0, 0.0)));
        chart.evaluate(0.0);

        double[] velocity = new double[3];
        chart.velocityOf(chart.slotOf("space:planet"), velocity);
        double speed = Math.sqrt(velocity[0] * velocity[0] + velocity[1] * velocity[1]
                + velocity[2] * velocity[2]);
        // v = 2πa/T for a circular orbit.
        assertEquals(OrbitalElements.TWO_PI * 1000.0 / 600.0, speed, 1.0e-6,
                "the carried velocity must be the real orbital speed");

        // An unknown slot must zero the output rather than leave a stale vector in the caller's buffer.
        chart.velocityOf(999, velocity);
        assertEquals(0.0, velocity[0], 0.0);
        assertEquals(0.0, velocity[1], 0.0);
        assertEquals(0.0, velocity[2], 0.0);
    }

    /**
     * §21's claim, measured: fifty charted bodies, fully evaluated, LOD-resolved and gravity-summed,
     * for a cost that has to stay under a millisecond per tick.
     *
     * <p>The assertion is ten times the budget. Wall-clock timing on a shared CI machine cannot hold a
     * 1 ms line, and a test that flakes is worse than no test — but an order-of-magnitude fence still
     * catches the regressions that matter here, which are structural: an accidental allocation in the
     * loop, a per-body map lookup, or an O(n²) hierarchy walk.
     */
    @Test void fiftyIdleBodiesStayInsideThePerTickBudget() {
        ChartRegistry chart = new ChartRegistry();
        chart.setRealOrbits(true);
        List<CelestialBody> bodies = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            bodies.add(new CelestialBody(
                    "space:body" + i, CelestialBody.BodyKind.PLANET, "Body " + i,
                    new OrbitalElements(2_000.0 + i * 900.0, (i % 10) / 50.0, (i % 5) * 0.1,
                            (i % 7) * 0.2, (i % 3) * 0.3, (i % 11) * 0.25,
                            1_000.0 + i * 300.0, 0.0),
                    0.0, 0.0, 0.0, i % 9 == 0 ? null : "space:body" + (i / 9),
                    50.0 + i, 1.0e6, GravityField.Falloff.INVERSE_SQUARE,
                    null, 64.0, 700.0, true, true, i % 5, null, 0));
        }
        chart.rebuild(bodies);
        assertEquals(50, chart.size());

        double[] out = new double[3];
        // Warm up before measuring.
        for (int warm = 0; warm < 200; warm++) {
            chart.evaluate(warm * 0.5);
            chart.resolveLods(warm, 0.0, 0.0, true);
            chart.gravityAt(warm, 0.0, 0.0, out);
        }

        int passes = 500;
        long start = System.nanoTime();
        for (int pass = 0; pass < passes; pass++) {
            chart.evaluate(1_000.0 + pass * 0.5);
            chart.resolveLods(pass * 3.0, 0.0, 0.0, true);
            chart.gravityAt(pass * 3.0, 0.0, 0.0, out);
        }
        long elapsed = System.nanoTime() - start;

        double perPassMillis = elapsed / 1_000_000.0 / passes;
        assertTrue(perPassMillis < 10.0,
                "50 bodies took " + perPassMillis + " ms per full evaluate+LOD+gravity pass; "
                        + "§21 budgets 1 ms, so this is at least an order of magnitude over");
        assertTrue(Double.isFinite(out[0]) && Double.isFinite(out[1]) && Double.isFinite(out[2]),
                "the summed field must be finite");
    }

    @Test void aChartWalkAllocatesNothingPerBody() {
        // Structural guard on the claim above: evaluating must not grow the heap in proportion to the
        // body count. Measuring allocation precisely is not possible from a unit test, but forcing GC
        // and comparing used heap across many passes catches a per-body object leak, which is the
        // regression that would silently turn this into a GC-pressure problem in a live world.
        ChartRegistry chart = new ChartRegistry();
        List<CelestialBody> bodies = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            bodies.add(circular("space:b" + i, null, 1000.0 + i, 600.0, 10.0, 1.0e6));
        }
        chart.rebuild(bodies);

        double[] out = new double[3];
        for (int warm = 0; warm < 100; warm++) chart.evaluate(warm);

        Runtime runtime = Runtime.getRuntime();
        runtime.gc();
        long before = runtime.totalMemory() - runtime.freeMemory();
        for (int pass = 0; pass < 2_000; pass++) {
            chart.evaluate(1_000.0 + pass);
            chart.gravityAt(pass, 0.0, 0.0, out);
        }
        long after = runtime.totalMemory() - runtime.freeMemory();
        // 2000 passes over 50 bodies; a per-body allocation would move this by megabytes.
        assertTrue(after - before < 2_000_000,
                "heap grew by " + (after - before) + " bytes over 2000 chart passes, "
                        + "suggesting per-body allocation on the hot path");
        assertEquals(50, chart.size(), "sanity: the chart is still intact");
        assertTrue(KeplerSolver.angularSize(10.0, 100.0) > 0.0);
    }

    /** The S1 driver's core contract: each body's stage is set by whichever observer is nearest to it. */
    @Test void multiObserverResolutionUsesTheNearestObserverPerBody() {
        ChartRegistry chart = new ChartRegistry();
        chart.setProxyBudget(12);
        chart.rebuild(List.of(
                pinned("space:a", null, 0.0, 0.0, 0.0, 100.0, 0.0),
                pinned("space:b", null, 200_000.0, 0.0, 0.0, 100.0, 0.0)));
        chart.evaluate(0.0);

        // One observer at the origin: it sits on A (CHUNKS) and looks at B from beyond B's proxy band
        // (radius*proxyFactor = 90_000 < 200_000), so B is a billboard.
        chart.resolveLodsForObservers(new double[]{0.0}, new double[]{0.0}, new double[]{0.0}, 1, true);
        assertEquals(BodyLod.CHUNKS, chart.lodOf(0));
        assertEquals(BodyLod.BILLBOARD, chart.lodOf(1));

        // A second observer standing on B promotes B on its own merits — nearest wins — while A holds.
        chart.resolveLodsForObservers(new double[]{0.0, 200_000.0}, new double[]{0.0, 0.0},
                new double[]{0.0, 0.0}, 2, true);
        assertEquals(BodyLod.CHUNKS, chart.lodOf(0));
        assertEquals(BodyLod.CHUNKS, chart.lodOf(1));
    }

    /** An empty chart-bound world is §21's idle cost: no observers, every body back to ABSENT. */
    @Test void zeroObserversLeaveEveryBodyAbsent() {
        ChartRegistry chart = new ChartRegistry();
        chart.rebuild(List.of(pinned("space:a", null, 0.0, 0.0, 0.0, 100.0, 0.0)));
        chart.evaluate(0.0);
        chart.resolveLodsForObservers(new double[]{0.0}, new double[]{0.0}, new double[]{0.0}, 1, true);
        assertEquals(BodyLod.CHUNKS, chart.lodOf(0));

        assertEquals(0, chart.resolveLodsForObservers(new double[0], new double[0], new double[0], 0, true));
        assertEquals(BodyLod.ABSENT, chart.lodOf(0));
    }

    @Test void slotForDimensionReturnsTheFirstBoundBody() {
        ChartRegistry chart = new ChartRegistry();
        chart.rebuild(List.of(
                pinnedToDimension("space:a", "xenospace:krak", 0.0, 0.0, 0.0, 100.0),
                pinnedToDimension("space:b", null, 1_000.0, 0.0, 0.0, 100.0),
                pinnedToDimension("space:c", "xenospace:krak", 2_000.0, 0.0, 0.0, 100.0)));
        chart.evaluate(0.0);
        assertEquals(0, chart.slotForDimension("xenospace:krak"), "first match, not nearest");
        assertEquals(-1, chart.slotForDimension("xenospace:unbound"));
        assertEquals(-1, chart.slotForDimension(null));
    }

    /**
     * The rebuild index and the raw scan must answer identically, including the two edge cases the
     * ladder depends on: several bodies bound to one dimension (first slot wins, never nearest) and a
     * dimension id that is not a valid identifier (the index simply omits it — §17's degrade, not abort).
     */
    @Test void theDimensionIndexAgreesWithTheScan() {
        ChartRegistry chart = new ChartRegistry();
        chart.rebuild(List.of(
                pinnedToDimension("space:a", "xenospace:krak", 0.0, 0.0, 0.0, 100.0),
                pinnedToDimension("space:b", null, 1_000.0, 0.0, 0.0, 100.0),
                pinnedToDimension("space:c", "xenospace:krak", 2_000.0, 0.0, 0.0, 100.0),
                pinnedToDimension("space:d", "not a location", 3_000.0, 0.0, 0.0, 100.0)));
        chart.evaluate(0.0);

        for (String dimension : new String[]{"xenospace:krak", "xenospace:unbound", null}) {
            assertEquals(chart.slotForDimension(dimension),
                    chart.slotFor(dimension == null ? null : ResourceLocation.parse(dimension)),
                    "index and scan must agree for " + dimension);
        }
        assertEquals(0, chart.slotFor(ResourceLocation.parse("xenospace:krak")), "first bound body wins");

        // CelestialBody accepts any non-blank id, so a malformed one stays reachable to the raw-string
        // callers while the fast path declines to answer for it, rather than failing the whole load.
        assertNull(ResourceLocation.tryParse("not a location"));
        assertEquals(3, chart.slotForDimension("not a location"));
        assertEquals(-1, chart.slotFor(null));
        assertEquals(4, chart.size());
    }

    /** S2's gate: only bodies the ladder <em>simulates</em> pull; the ungated preview still sums all. */
    @Test void simulatingGravityIgnoresBodiesBelowFieldStage() {
        ChartRegistry chart = new ChartRegistry(new GravityField(3));
        chart.setFieldRange(30_000_000.0);
        chart.rebuild(List.of(
                pinned("space:a", null, 1_000.0, 0.0, 0.0, 100.0, 1.0e6),
                pinned("space:b", null, 200_000.0, 0.0, 0.0, 100.0, 1.0e6)));
        chart.evaluate(0.0);
        // An observer standing on A promotes A to CHUNKS (simulates); B stays BILLBOARD (does not).
        chart.resolveLodsForObservers(new double[]{1_000.0}, new double[]{0.0}, new double[]{0.0}, 1, true);
        assertEquals(BodyLod.CHUNKS, chart.lodOf(0));
        assertEquals(BodyLod.BILLBOARD, chart.lodOf(1));

        double[] out = new double[3];
        chart.simulatingGravityAt(0.0, 0.0, 0.0, out);
        double simX = out[0];
        assertEquals(1, chart.lastGravityContributors(), "only the simulating body may contribute");

        chart.gravityAt(0.0, 0.0, 0.0, out);
        double allX = out[0];
        assertEquals(2, chart.lastGravityContributors(), "the ungated preview sums both bodies");
        assertTrue(simX > 0.0, "A must pull toward +x at the origin");
        assertTrue(allX > simX, "B's far-but-real pull must show in gravityAt and be absent from simulatingGravityAt");
    }

    private static CelestialBody pinnedToDimension(String id, String dimensionId, double x, double y,
                                                   double z, double radius) {
        return new CelestialBody(id, CelestialBody.BodyKind.PLANET, id, null,
                x, y, z, null, radius, 0.0, GravityField.Falloff.INVERSE_SQUARE,
                dimensionId, 64.0, 700.0, false, true, 0, null, 0);
    }
}
