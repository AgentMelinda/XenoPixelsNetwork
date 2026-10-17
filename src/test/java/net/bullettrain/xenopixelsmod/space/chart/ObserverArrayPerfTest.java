package net.bullettrain.xenopixelsmod.space.chart;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.bullettrain.xenopixelsmod.space.math.BodyLod;
import org.junit.jupiter.api.Test;

import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** S4 geometry through the live array API. JVM math only, not a live-player/server benchmark. */
class ObserverArrayPerfTest {
    private static final Path PROJECT = Path.of(System.getProperty("xenopixels.projectDir", "."));
    private static final int WARMUP = 20_000, SAMPLES = 128, BATCH = 256;
    private static volatile long consumed;
    private record Observers(String name, double[] x, double[] y, double[] z) {
        int count() { return x.length; }
    }

    private static ChartRegistry fixture() throws Exception {
        Map<String, JsonObject> rows = new LinkedHashMap<>();
        try (var stream = ObserverArrayPerfTest.class.getResourceAsStream("/space/observer-chart50.json")) {
            assertNotNull(stream, "missing authored-distance fixture");
            var data = JsonParser.parseString(new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            assertEquals(45, data.getAsJsonArray("synthetic_bodies").size());
            for (var value : data.getAsJsonArray("synthetic_bodies")) {
                var row = value.getAsJsonObject();
                assertNull(rows.put(row.get("id").getAsString(), row.getAsJsonObject("definition")));
            }
        }
        for (String id : new String[]{"sol", "earth", "namek"}) {
            rows.put("xenopixelsmod:" + id, read("src/main/resources/data/xenopixelsmod/xenospace/planet/" + id + ".json"));
        }
        // The published distribution was measured with the former showcase Moon; keep it as a fixture.
        rows.put("xenopixelsmod:moon", read("src/test/resources/space/showcase_moon_fixture.json"));
        rows.put("xenospace_spike:surface", read("examples/xenospace-handoff-spike/data/xenospace_spike/xenospace/chart_entry/surface.json"));
        var result = ChartAssembly.build(rows, new JsonObject(), Set.of("minecraft:overworld", "dragonminez:namek", "xenospace_spike:surface"));
        assertEquals(java.util.List.of(), result.errors());
        var chart = result.chart();
        chart.setRealOrbits(false);
        chart.setProxyBudget(12);
        chart.setFieldRange(100_000);
        chart.setGravityContributors(3);
        chart.setThresholds(BodyLod.Thresholds.defaults());
        chart.evaluate(0);
        assertEquals(50, chart.size());
        assertEquals(1000, chart.x(chart.slotOf("xenopixelsmod:earth")));
        return chart;
    }
    private static JsonObject read(String file) throws Exception {
        return JsonParser.parseString(Files.readString(PROJECT.resolve(file))).getAsJsonObject();
    }
    private static Observers observers(String name, int count, boolean spread) {
        double[] x = new double[count], y = new double[count], z = new double[count];
        double[] boundCenters = {1000, 4200, 20000};
        for (int i = 0; i < count; i++) x[i] = spread ? boundCenters[i % boundCenters.length] : 1000;
        return new Observers(name, x, y, z);
    }
    private static int[] stages(ChartRegistry chart) {
        int[] counts = new int[BodyLod.values().length];
        for (int i = 0; i < chart.size(); i++) counts[chart.lodOf(i).ordinal()]++;
        return counts;
    }
    private static int resolve(ChartRegistry chart, Observers obs) {
        return chart.resolveLodsForObservers(obs.x(), obs.y(), obs.z(), obs.count(), true);
    }

    @Test void actualArrayPathReproducesPublishedFiftyBodyDistribution() throws Exception {
        var chart = fixture();
        assertEquals(12, resolve(chart, observers("earth", 1, false)));
        assertArrayEquals(new int[]{0, 16, 12, 13, 7, 2}, stages(chart));
        double[] acceleration = new double[3];
        chart.gravityAt(1000, 0, 0, acceleration);
        assertEquals(3, chart.lastGravityContributors());
        assertEquals(17, chart.lastGravityCulled());
        assertArrayEquals(new double[]{-4.4889, -2.4404, 3.7026}, acceleration, 0.001);
    }
    @Test void duplicateAndSpreadBoundObserversKeepCapsAndReleaseOnEmptyInput() throws Exception {
        var chart = fixture();
        resolve(chart, observers("earth", 1, false));
        int[] one = stages(chart);
        assertEquals(12, resolve(chart, observers("earth-32", 32, false)));
        assertArrayEquals(one, stages(chart));
        resolve(chart, observers("bound-centers-32", 32, true));
        for (String id : new String[]{"xenopixelsmod:earth", "xenopixelsmod:namek", "xenospace_spike:surface"})
            assertEquals(BodyLod.CHUNKS, chart.lodOf(chart.slotOf(id)), id);
        assertTrue(stages(chart)[BodyLod.PROXY.ordinal()] <= 12);
        assertEquals(0, resolve(chart, observers("empty", 0, false)));
        assertArrayEquals(new int[]{50, 0, 0, 0, 0, 0}, stages(chart));
        double[] acceleration = {1, 2, 3};
        chart.simulatingGravityAt(1000, 0, 0, acceleration);
        assertArrayEquals(new double[]{0, 0, 0}, acceleration);
        assertEquals(0, chart.lastGravityContributors());
    }

    @Test void reportsObserverScalingAndRealFieldMathWithoutClaimingServerCost() throws Exception {
        var report = new JsonObject();
        report.addProperty("scope", "JVM chart math only; no player collection, scheduler, Sable handles, impulses, terrain, packets or server ticks");
        report.addProperty("java", System.getProperty("java.runtime.version"));
        report.addProperty("os", System.getProperty("os.name") + " " + System.getProperty("os.arch"));
        report.addProperty("bodies", 50);
        report.addProperty("warmup_calls_per_scenario_and_path", WARMUP);
        report.addProperty("samples", SAMPLES);
        report.addProperty("calls_per_sample", BATCH);
        report.addProperty("steady_observers", "Synthetic fixed positions at current bound-dimension chart centers; no approaching-player model");
        var rows = new com.google.gson.JsonArray();
        for (var obs : new Observers[]{observers("idle", 0, false), observers("earth", 1, false),
                observers("earth-8-duplicates", 8, false), observers("bound-centers-8", 8, true),
                observers("bound-centers-32", 32, true)}) {
            for (boolean gravity : new boolean[]{false, true}) {
                var chart = fixture();
                var metrics = measure(chart, obs, gravity);
                metrics.addProperty("scenario", obs.name());metrics.addProperty("observers", obs.count());
                metrics.addProperty("path", gravity ? "array_lod_plus_one_stage_gated_gravity_query_per_observer" : "array_lod_only");
                var distribution = new JsonObject();int[] counts = stages(chart);
                for (var stage : BodyLod.values()) distribution.addProperty(stage.name(), counts[stage.ordinal()]);
                metrics.add("steady_stage_counts", distribution);
                if (obs.count() > 0 && gravity) assertTrue(chart.lastGravityContributors() > 0, "must measure an actual nonempty field sum");
                assertTrue(metrics.get("mean_us_per_call").getAsDouble() < 10_000,
                        "10 ms is a coarse regression guard, not the design's 1 ms acceptance criterion: " + metrics);
                rows.add(metrics);
                System.out.println("SPACE_OBSERVER_MATH " + metrics);
            }
        }
        report.add("results", rows);
        Path output = PROJECT.resolve("build/space-observer-math/report.json");
        Files.createDirectories(output.getParent());
        Files.writeString(output, new GsonBuilder().setPrettyPrinting().create().toJson(report));
    }
    private static long exercise(ChartRegistry chart, Observers obs, boolean gravity, double[] acceleration) {
        long value = resolve(chart, obs);
        if (gravity) for (int i = 0; i < obs.count(); i++) {
            chart.simulatingGravityAt(obs.x()[i], obs.y()[i], obs.z()[i], acceleration);
            value += Double.doubleToLongBits(acceleration[0]) + chart.lastGravityContributors();
        }
        return value;
    }
    private static JsonObject measure(ChartRegistry chart, Observers obs, boolean gravity) {
        double[] acceleration = new double[3];long checksum = 0;
        for (int i = 0; i < WARMUP; i++) checksum += exercise(chart, obs, gravity, acceleration);
        consumed = checksum;
        long[] samples = new long[SAMPLES];
        var bean = ManagementFactory.getThreadMXBean();
        com.sun.management.ThreadMXBean allocations = bean instanceof com.sun.management.ThreadMXBean candidate
                && candidate.isThreadAllocatedMemorySupported() ? candidate : null;
        if (allocations != null && !allocations.isThreadAllocatedMemoryEnabled()) allocations.setThreadAllocatedMemoryEnabled(true);
        long thread = Thread.currentThread().threadId();
        long before = allocations == null ? -1 : allocations.getThreadAllocatedBytes(thread);
        for (int sample = 0; sample < SAMPLES; sample++) {
            long start = System.nanoTime();
            for (int pass = 0; pass < BATCH; pass++) checksum += exercise(chart, obs, gravity, acceleration);
            samples[sample] = System.nanoTime() - start;
        }
        long allocated = allocations == null ? -1 : allocations.getThreadAllocatedBytes(thread) - before;
        consumed = checksum;
        long total = 0;for (long duration : samples) total += duration;
        Arrays.sort(samples);
        var row = new JsonObject();
        row.addProperty("mean_us_per_call", total / (double)(SAMPLES * BATCH) / 1000);
        row.addProperty("median_batch_mean_us_per_call", samples[SAMPLES / 2] / (double)BATCH / 1000);
        row.addProperty("p95_batch_mean_us_per_call", samples[(int)((SAMPLES - 1) * .95)] / (double)BATCH / 1000);
        row.addProperty("max_batch_mean_us_per_call", samples[SAMPLES - 1] / (double)BATCH / 1000);
        row.addProperty("thread_allocated_bytes_in_measured_loop", allocated);
        row.addProperty("measured_calls", SAMPLES * BATCH);
        return row;
    }
}
