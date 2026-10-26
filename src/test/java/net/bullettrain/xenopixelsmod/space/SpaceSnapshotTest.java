package net.bullettrain.xenopixelsmod.space;

import com.google.gson.JsonObject;
import io.netty.buffer.Unpooled;
import net.bullettrain.xenopixelsmod.space.chart.CelestialBody;
import net.bullettrain.xenopixelsmod.space.chart.ChartDefinition;
import net.bullettrain.xenopixelsmod.space.chart.ChartRegistry;
import net.bullettrain.xenopixelsmod.space.math.BodyLod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.joml.Vector3d;

import static org.junit.jupiter.api.Assertions.*;

class SpaceSnapshotTest {
    @Test void snapshotObserverAppendMatchesScheduledPassSpaceFilteringAndCoordinates() {
        ChartRegistry chart = new ChartRegistry();
        JsonObject boundBody = new JsonObject();
        JsonObject boundPosition = new JsonObject();
        boundPosition.addProperty("x", 1_000);
        boundPosition.addProperty("y", 200);
        boundPosition.addProperty("z", -300);
        boundBody.add("position", boundPosition);
        boundBody.addProperty("radius_blocks", 50);
        boundBody.addProperty("sky_style", 0);
        boundBody.addProperty("dimension", "demo:chart");
        boundBody.addProperty("orbital_motion", false);
        chart.rebuild(List.of(ChartDefinition.parse("demo:chart", boundBody)));
        chart.evaluate(0.0);
        Set<ResourceLocation> listed = Set.of(ResourceLocation.parse("xenospace_spike:orbit"));
        double[] xs = new double[4], ys = new double[4], zs = new double[4];
        xs[0] = 77;

        int count = SpaceLodDriver.appendSnapshotObserver(chart, ResourceLocation.parse("minecraft:overworld"),
                listed, 12, 34, 56, xs, ys, zs, 0);
        assertEquals(0, count, "ordinary dimensions must not become LOD observers");
        assertEquals(77.0, xs[0], 0.0, "excluded dimension must not write scratch state");

        count = SpaceLodDriver.appendSnapshotObserver(chart, listed.iterator().next(), listed,
                12, 34, 56, xs, ys, zs, count);
        assertEquals(1, count);
        assertEquals(12, xs[0], 0.0);
        assertEquals(34, ys[0], 0.0);
        assertEquals(56, zs[0], 0.0);

        ResourceLocation chartDimension = ResourceLocation.parse("demo:chart");
        count = SpaceLodDriver.appendSnapshotObserver(chart, chartDimension, listed,
                -10, -20, -30, xs, ys, zs, count);
        assertEquals(2, count);
        assertEquals(chart.x(0), xs[1], 0.0);
        assertEquals(chart.y(0), ys[1], 0.0);
        assertEquals(chart.z(0), zs[1], 0.0);
    }

    @Test void sharedSurfaceObserverIsPlacedAtTheBodyWhoseCellTheyOccupy() {
        var moonCell = net.bullettrain.xenopixelsmod.space.smallplanet.PlanetSurfaceCells
                .forChartBody("xenopixelsmod:moon");
        var moon = new CelestialBody("xenopixelsmod:moon", CelestialBody.BodyKind.MOON, "Moon", null,
                110, 210, 310, null, 8, 0.0, null, "xenopixelsmod:planet_surfaces",
                200, 700, false, false, 0, null, 0);
        var phobosCell = net.bullettrain.xenopixelsmod.space.smallplanet.PlanetSurfaceCells
                .forChartBody("xenopixelsmod:phobos");
        var phobos = new CelestialBody("xenopixelsmod:phobos", CelestialBody.BodyKind.MOON, "Phobos", null,
                410, 510, 610, null, 8, 0.0, null, "xenopixelsmod:planet_surfaces",
                200, 700, false, false, 0, null, 0);
        ChartRegistry chart = new ChartRegistry();
        chart.rebuild(List.of(moon, phobos));
        chart.evaluate(0.0);
        double[] xs = new double[2], ys = new double[2], zs = new double[2];
        int count = SpaceLodDriver.appendSnapshotObserver(chart,
                ResourceLocation.parse("xenopixelsmod:planet_surfaces"), Set.of(),
                phobosCell.centerBlockX(), 0, phobosCell.centerBlockZ(), xs, ys, zs, 0);
        assertEquals(1, count);
        assertEquals(chart.x(chart.slotOf("xenopixelsmod:phobos")), xs[0]);
        assertEquals(chart.y(chart.slotOf("xenopixelsmod:phobos")), ys[0]);
        assertEquals(chart.z(chart.slotOf("xenopixelsmod:phobos")), zs[0]);
        assertNotEquals(moonCell, phobosCell);
    }

    @Test void wireRoundTripPreservesBoundedVisualState() {
        var packet = new SpaceSnapshot(7, List.of(new SpaceSnapshot.Body("xenopixelsmod:earth", 0,
                500, 100, 200, 100)));
        var buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            SpaceSnapshot.encode(packet, buf);
            assertEquals(packet, SpaceSnapshot.decode(buf));
            assertEquals(0, buf.readableBytes());
        } finally {
            buf.release();
        }
    }

    @Test void decodeRejectsOversizeCountBeforeBodyAllocation() {
        var buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buf.writeVarInt(1);
            buf.writeVarInt(SpaceSnapshot.MAX_BODIES + 1);
            assertThrows(IllegalArgumentException.class, () -> SpaceSnapshot.decode(buf));
        } finally {
            buf.release();
        }
    }

    @Test void invalidCoordinatesStylesDuplicatesAndRevisionsAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new SpaceSnapshot.Body("demo:earth", 0, Double.NaN, 0, 0, 100));
        assertThrows(IllegalArgumentException.class,
                () -> new SpaceSnapshot.Body("demo:earth", CelestialBody.MAX_VISUAL_STYLES, 0, 0, 0, 100));
        assertThrows(IllegalArgumentException.class,
                () -> new SpaceSnapshot.Body("demo:earth", 0, 0, 0, 0, 0));
        var a = new SpaceSnapshot.Body("demo:earth", 0, 0, 0, 0, 100);
        assertThrows(IllegalArgumentException.class, () -> new SpaceSnapshot(1, List.of(a, a)));
        assertThrows(IllegalArgumentException.class, () -> new SpaceSnapshot(-1, List.of()));
    }

    /**
     * The packet is the ladder's decision, so the stages that must not appear are the ones this slice
     * exists to keep out: an unlit billboard would be a draw call per body, and a body whose real chunks
     * are loaded would be a sphere drawn over the ground under the player's feet.
     */
    @Test void fromChartDrawsTheProxyStagesAndNeitherExtreme() {
        ChartRegistry chart = new ChartRegistry();
        // Every body is r=50, so the ladder's radius multiples land each one on a different rung:
        // chunk 52.5, anchor 3000, field 20000, proxy 45000.
        chart.rebuild(List.of(
                pinned("demo:inside", 10, 0, 0, 50, 0),        // 0.2 radii -> CHUNKS
                pinned("demo:near", 1_000, 0, 0, 50, 1),       // 20 -> ANCHOR
                pinned("demo:pulling", 5_000, 0, 0, 50, 2),    // 100 -> FIELD
                pinned("demo:far", 25_000, 0, 0, 50, 0),       // 500 -> PROXY
                pinned("demo:beyond", 100_000, 0, 0, 50, 1))); // 2000 -> BILLBOARD
        chart.evaluate(0.0);
        chart.resolveLods(0.0, 0.0, 0.0, true);

        var snapshot = SpaceSnapshot.fromChart(chart, 2);
        assertEquals(3, snapshot.bodies().size());
        assertEquals(List.of("demo:near", "demo:pulling", "demo:far"),
                snapshot.bodies().stream().map(SpaceSnapshot.Body::id).toList());
        assertEquals(1, snapshot.bodies().getFirst().style());
        assertEquals(1_000.0, snapshot.bodies().getFirst().x(), 0.0);
        assertEquals(50.0, snapshot.bodies().getFirst().radius(), 0.0);
        assertEquals(2, snapshot.revision());

        var live = SpaceSnapshot.fromChart(chart, 2, Map.of("demo:near", new Vector3d(1_200, 40, -60)));
        assertEquals(1_200.0, live.bodies().getFirst().x(), 0.0);
        assertEquals(40.0, live.bodies().getFirst().y(), 0.0);
        assertNotEquals(SpaceSnapshot.signature(chart, 2), SpaceSnapshot.signature(chart, 2,
                Map.of("demo:near", new Vector3d(1_200, 40, -60))));
    }

    @Test void loginSnapshotIncludesNearbyBodiesAfterObserverLodResolution() {
        ChartRegistry chart = new ChartRegistry();
        chart.rebuild(List.of(pinned("demo:nearby", 1_000, 0, 0, 50, 0)));
        chart.evaluate(0.0);
        chart.resolveLodsForObservers(new double[]{1_000_000}, new double[]{0}, new double[]{0}, 1, true);
        assertEquals(BodyLod.BILLBOARD, chart.lodOf(0));

        assertTrue(SpaceSnapshot.fromChart(chart, 1).bodies().isEmpty(),
                "the previous far-away observer pass leaves this chart body absent");
        chart.resolveLodsForObservers(new double[]{2_000}, new double[]{0}, new double[]{0}, 1, true);
        assertEquals(BodyLod.ANCHOR, chart.lodOf(0));

        assertEquals(List.of("demo:nearby"), SpaceSnapshot.fromChart(chart, 1).bodies().stream()
                .map(SpaceSnapshot.Body::id).toList());
    }

    /** A chart wider than the wire cap loses spheres, not skies: {@link #fromChart} truncates. */
    @Test void anOverCapChartTruncatesInsteadOfThrowing() {
        List<CelestialBody> bodies = new ArrayList<>();
        for (int i = 0; i < SpaceSnapshot.MAX_BODIES + 8; i++)
            bodies.add(pinned("demo:body" + i, 25_000, 0, 0, 50, i % CelestialBody.MAX_VISUAL_STYLES));
        ChartRegistry chart = new ChartRegistry();
        chart.setProxyBudget(SpaceSnapshot.MAX_BODIES + 8);
        chart.rebuild(bodies);
        chart.evaluate(0.0);
        chart.resolveLods(0.0, 0.0, 0.0, true);

        assertEquals(SpaceSnapshot.MAX_BODIES, SpaceSnapshot.fromChart(chart, 1).bodies().size());
    }

    @Test void liveUnchartedPlanetsAreKeptAheadOfChartBodiesAndRoundTrip() {
        List<CelestialBody> chartBodies = new ArrayList<>();
        for (int i = 0; i < SpaceSnapshot.MAX_BODIES + 8; i++)
            chartBodies.add(pinned("demo:body" + i, 25_000, 0, 0, 50, i % CelestialBody.MAX_VISUAL_STYLES));
        ChartRegistry chart = new ChartRegistry();
        chart.setProxyBudget(SpaceSnapshot.MAX_BODIES + 8);
        chart.rebuild(chartBodies);
        chart.evaluate(0.0);
        chart.resolveLods(0.0, 0.0, 0.0, true);
        var sdb = new SpaceSnapshot.Body("xenopixelsmod:sdb/planet/slot-1", 0, 7_000, 20, -4_000, 24);

        var snapshot = SpaceSnapshot.fromChart(chart, 11, Map.of(), List.of(sdb));
        assertEquals(SpaceSnapshot.MAX_BODIES, snapshot.bodies().size());
        assertEquals(sdb, snapshot.bodies().getFirst());
        assertEquals(1, snapshot.bodies().stream().filter(body -> body.id().equals(sdb.id())).count());
        assertTrue(snapshot.bodies().stream().anyMatch(body -> body.id().equals("demo:body0")));
        assertFalse(snapshot.bodies().stream().anyMatch(body -> body.id().equals("demo:body31")));

        var buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            SpaceSnapshot.encode(snapshot, buf);
            assertEquals(snapshot, SpaceSnapshot.decode(buf));
        } finally {
            buf.release();
        }
    }

    @Test void unchartedPlanetIdentityAndRoundedLivePoseAffectSignature() {
        ChartRegistry chart = new ChartRegistry();
        chart.rebuild(List.of(pinned("demo:chart", 25_000, 0, 0, 50, 0)));
        chart.evaluate(0.0);
        chart.resolveLods(0.0, 0.0, 0.0, true);
        var first = new SpaceSnapshot.Body("xenopixelsmod:sdb/planet/slot-1", 0, 1_000, 0, 0, 24);
        var sameBlock = new SpaceSnapshot.Body(first.id(), 0, 1_000.2, 0, 0, 24);
        var moved = new SpaceSnapshot.Body(first.id(), 0, 1_001.1, 0, 0, 24);

        long base = SpaceSnapshot.signature(chart, 3, Map.of(), List.of(first));
        assertEquals(base, SpaceSnapshot.signature(chart, 3, Map.of(), List.of(sameBlock)));
        assertNotEquals(base, SpaceSnapshot.signature(chart, 3, Map.of(), List.of(moved)));
        var otherIdentity = new SpaceSnapshot.Body("xenopixelsmod:sdb/planet/slot-2", 0, 1_000, 0, 0, 24);
        assertNotEquals(base, SpaceSnapshot.signature(chart, 3, Map.of(), List.of(otherIdentity)));
    }

    @Test void snapshotAndSignatureUseTheSamePersistedSolFrameForChartOnlyBodies() {
        ChartRegistry chart = new ChartRegistry();
        chart.rebuild(List.of(
                new CelestialBody("xenopixelsmod:sol", CelestialBody.BodyKind.STAR, "Sol", null,
                        0, 10, 0, null, 50, 0, null, null, 64, 1000, 700, false, false, 0, null, 0),
                new CelestialBody("xenopixelsmod:earth", CelestialBody.BodyKind.PLANET, "Earth", null,
                        1_000, 20, 0, "xenopixelsmod:sol", 50, 0, null, null,
                        64, 1000, 700, false, false, 0, null, 0),
                new CelestialBody("demo:other", CelestialBody.BodyKind.PLANET, "Other", null,
                        1_100, -20, 0, null, 50, 0, null, null, 64, 1000, 700,
                        false, false, 0, null, 0)));
        chart.evaluate(0.0);
        chart.resolveLods(0.0, 0.0, 0.0, true);

        var snapshot = SpaceSnapshot.fromChart(chart, 1, Map.of(), List.of(), 128.0);
        var earth = snapshot.bodies().stream().filter(body -> body.id().equals("xenopixelsmod:earth"))
                .findFirst().orElseThrow();
        var other = snapshot.bodies().stream().filter(body -> body.id().equals("demo:other"))
                .findFirst().orElseThrow();
        assertEquals(148.0, earth.y(), 0.0);
        assertEquals(-20.0, other.y(), 0.0);
        var live = SpaceSnapshot.fromChart(chart, 1,
                Map.of("xenopixelsmod:earth", new Vector3d(1_000, 300, 0)), List.of(), 128.0);
        assertEquals(300.0, live.bodies().stream().filter(body -> body.id().equals("xenopixelsmod:earth"))
                .findFirst().orElseThrow().y(), 0.0, "live Sable centre remains authoritative");
        assertEquals(SpaceSnapshot.signature(chart, 1, Map.of(), List.of(), 128.0),
                SpaceSnapshot.signature(chart, 1, Map.of(), List.of(), 128.4));
        assertNotEquals(SpaceSnapshot.signature(chart, 1, Map.of(), List.of(), 128.0),
                SpaceSnapshot.signature(chart, 1, Map.of(), List.of(), 130.0));
    }

    /**
     * The change detector the visual pass runs every interval, allocation-free. A body drifting on its
     * orbit must not re-send the atlas every tick, so coordinates are compared rounded to a block —
     * and a stage change must not be missed, because that is the whole point of sending it.
     */
    @Test void signatureIgnoresSubBlockDriftAndNoticesAStageChange() {
        ChartRegistry chart = new ChartRegistry();
        chart.rebuild(List.of(pinned("demo:earth", 10_000, 0, 0, 50, 0)));
        chart.evaluate(0.0);
        chart.resolveLods(0.0, 0.0, 0.0, true);
        long base = SpaceSnapshot.signature(chart, 4);
        assertEquals(base, SpaceSnapshot.signature(chart, 4));

        // A body drifting on its orbit must not re-send the atlas every interval.
        chart.moveBody("demo:earth", 10_000.4, 0.2, -0.3);
        chart.evaluate(0.0);
        assertEquals(base, SpaceSnapshot.signature(chart, 4));

        chart.moveBody("demo:earth", 10_005, 0.0, 0.0);
        chart.evaluate(0.0);
        long moved = SpaceSnapshot.signature(chart, 4);
        assertNotEquals(base, moved);

        // ...and a body the ladder took back down off the drawn set changes the answer too.
        chart.moveBody("demo:earth", 100_000, 0.0, 0.0);
        chart.evaluate(0.0);
        chart.resolveLods(0.0, 0.0, 0.0, true);
        assertNotEquals(moved, SpaceSnapshot.signature(chart, 4));

        assertNotEquals(SpaceSnapshot.signature(chart, 4), SpaceSnapshot.signature(chart, 5));
        assertNotEquals(base, SpaceSnapshot.signature(new ChartRegistry(), 4));
    }

    private static CelestialBody pinned(String id, double x, double y, double z, double radius,
                                        int skyStyle) {
        JsonObject data = new JsonObject();
        JsonObject position = new JsonObject();
        position.addProperty("x", x);
        position.addProperty("y", y);
        position.addProperty("z", z);
        data.add("position", position);
        data.addProperty("radius_blocks", radius);
        data.addProperty("sky_style", skyStyle);
        data.addProperty("orbital_motion", false);
        return ChartDefinition.parse(id, data);
    }
}
