package net.bullettrain.xenopixelsmod.space;

import com.google.gson.JsonObject;
import io.netty.buffer.Unpooled;
import net.bullettrain.xenopixelsmod.space.chart.CelestialBody;
import net.bullettrain.xenopixelsmod.space.chart.ChartDefinition;
import net.bullettrain.xenopixelsmod.space.chart.ChartRegistry;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SpaceSnapshotTest {
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
