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

/**
 * S5: what one ladder sweep costs to turn a player's dimension into a chart slot, measured on all three
 * lookup shapes at once — the {@code ResourceLocation.toString()} plus O(bodies) scan the driver did
 * before S5-B, the bare scan, and the rebuild index it uses now.
 *
 * <p>The scan is O(bodies) per player, so the quantity that matters is not the single-call mean that
 * the 2026-10-13 captures reported — those had zero players and therefore never ran this path at all.
 * It is the cost of a whole observer sweep, and whether that sweep allocates. A sweep is one
 * player-tick's worth of lookups, so {@code ns_per_sweep} is directly comparable to the
 * {@code lod-ladder} probe reading and {@code bytes_per_sweep} is what a 20-player server pays every
 * {@code lodIntervalTicks}.
 *
 * <p>JVM math only, exactly as in {@code ObserverArrayPerfTest}: no player collection, no scheduler,
 * no Sable handles, no server ticks. Numbers here size the lookup, not the pass.
 *
 * <p>The fixture below is a deliberate copy of {@code ObserverArrayPerfTest}'s rather than a shared
 * helper, because that file's hashes are recorded in {@code ai/evidence/space-observer-math-2026-10-13}
 * and editing it would invalidate evidence already written.
 */
class DimensionIndexPerfTest {
    private static final Path PROJECT = Path.of(System.getProperty("xenopixels.projectDir", "."));
    private static final int WARMUP = 20_000, SAMPLES = 128, BATCH = 256;
    /** The design's Q21 observer peak; a sweep is one interval's worth of these lookups. */
    private static final int OBSERVERS = 20;
    private static volatile long consumed;

    /** Every dimension the chart is asked about in a sweep, in the order a player list would yield them. */
    private record Scenario(String name, String[] dimensionIds) {}

    private static ChartRegistry fixture() throws Exception {
        Map<String, JsonObject> rows = new LinkedHashMap<>();
        try (var stream = DimensionIndexPerfTest.class.getResourceAsStream("/space/observer-chart50.json")) {
            assertNotNull(stream, "missing authored-distance fixture");
            var data = JsonParser.parseString(new String(stream.readAllBytes(),
                    java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            assertEquals(45, data.getAsJsonArray("synthetic_bodies").size());
            for (var value : data.getAsJsonArray("synthetic_bodies")) {
                var row = value.getAsJsonObject();
                assertNull(rows.put(row.get("id").getAsString(), row.getAsJsonObject("definition")));
            }
        }
        for (String id : new String[]{"sol", "earth", "namek"}) {
            rows.put("xenopixelsmod:" + id,
                    read("src/main/resources/data/xenopixelsmod/xenospace/planet/" + id + ".json"));
        }
        // The published distribution was measured with the former showcase Moon; keep it as a fixture.
        rows.put("xenopixelsmod:moon", read("src/test/resources/space/showcase_moon_fixture.json"));
        rows.put("xenospace_spike:surface",
                read("examples/xenospace-handoff-spike/data/xenospace_spike/xenospace/chart_entry/surface.json"));
        var result = ChartAssembly.build(rows, new JsonObject(), Set.of(
                "minecraft:overworld", "dragonminez:namek", "xenospace_spike:surface"));
        assertEquals(java.util.List.of(), result.errors());
        var chart = result.chart();
        chart.setRealOrbits(false);
        chart.setProxyBudget(12);
        chart.setFieldRange(100_000);
        chart.setGravityContributors(3);
        chart.setThresholds(BodyLod.Thresholds.defaults());
        chart.evaluate(0);
        assertEquals(50, chart.size());
        return chart;
    }

    private static JsonObject read(String file) throws Exception {
        return JsonParser.parseString(Files.readString(PROJECT.resolve(file))).getAsJsonObject();
    }

    /** The three dimensions the shipped fixture actually binds, plus ones it does not. */
    private static Scenario[] scenarios() {
        var bound = new String[]{"minecraft:overworld", "dragonminez:namek", "xenospace_spike:surface"};
        // A player standing where no body exists is the worst case: the scan runs to the end and
        // returns nothing. In a mixed server it is also the common case, because most players are in
        // ordinary overworld-adjacent dimensions the chart has no body for.
        var unbound = new String[]{"minecraft:the_nether", "minecraft:the_end", "xenospace_spike:orbit"};
        var allUnbound = repeat(unbound, OBSERVERS);
        var allBound = repeat(bound, OBSERVERS);
        var mixed = new String[OBSERVERS];
        for (int i = 0; i < OBSERVERS; i++) {
            mixed[i] = i % 4 == 0 ? bound[i % bound.length] : unbound[i % unbound.length];
        }
        return new Scenario[]{
                new Scenario("all-bound", allBound),
                new Scenario("all-unbound-worst-case", allUnbound),
                new Scenario("mixed-20", mixed)
        };
    }

    private static String[] repeat(String[] cycle, int count) {
        String[] out = new String[count];
        for (int i = 0; i < count; i++) out[i] = cycle[i % cycle.length];
        return out;
    }

    /** One observer sweep under one of the three lookup paths, all of which must agree. */
    private static final int TOSTRING_THEN_SCAN = 0, SCAN_ONLY = 1, INDEX_ONLY = 2;
    private static final String[] PATH_NAMES =
            {"tostring_plus_scan", "scan_only_prebuilt_key", "index_only_prebuilt_resource_location"};

    private static long sweep(ChartRegistry chart, Scenario scenario,
                              net.minecraft.resources.ResourceLocation[] locations, int mode) {
        long value = 0;
        String[] dimensionIds = scenario.dimensionIds();
        for (int i = 0; i < dimensionIds.length; i++) {
            int slot;
            switch (mode) {
                case TOSTRING_THEN_SCAN ->
                        slot = chart.slotForDimension(net.minecraft.resources.ResourceLocation
                                .parse(dimensionIds[i]).toString());
                case SCAN_ONLY -> slot = chart.slotForDimension(dimensionIds[i]);
                default -> slot = chart.slotFor(locations[i]);
            }
            if (slot >= 0) value += chart.x(slot);
        }
        return value;
    }

    /** What the driver actually holds per player: a resolved identifier, no string anywhere. */
    private static net.minecraft.resources.ResourceLocation[] locations(Scenario scenario) {
        String[] ids = scenario.dimensionIds();
        var out = new net.minecraft.resources.ResourceLocation[ids.length];
        for (int i = 0; i < ids.length; i++) out[i] = net.minecraft.resources.ResourceLocation.parse(ids[i]);
        return out;
    }

    @Test void everyDimensionInTheFixtureResolvesThroughTheScanItself() throws Exception {
        var chart = fixture();
        assertEquals(chart.slotOf("xenopixelsmod:earth"), chart.slotForDimension("minecraft:overworld"));
        assertEquals(chart.slotOf("xenopixelsmod:namek"), chart.slotForDimension("dragonminez:namek"));
        assertEquals(chart.slotOf("xenospace_spike:surface"), chart.slotForDimension("xenospace_spike:surface"));
        assertEquals(-1, chart.slotForDimension("minecraft:the_nether"));
        assertEquals(-1, chart.slotForDimension(null));
    }

    /** The three paths are the same question; the benchmark is worthless if they ever disagree. */
    @Test void allThreeLookupPathsAgreeOnEveryScenario() throws Exception {
        for (var scenario : scenarios()) {
            var chart = fixture();
            var keys = locations(scenario);
            long reference = sweep(chart, scenario, keys, TOSTRING_THEN_SCAN);
            assertEquals(reference, sweep(chart, scenario, keys, SCAN_ONLY), scenario.name());
            assertEquals(reference, sweep(chart, scenario, keys, INDEX_ONLY), scenario.name());
        }
    }

    @Test void reportsObserverSweepCostAndAllocationForTheCurrentScan() throws Exception {
        var report = new JsonObject();
        report.addProperty("scope", "JVM dimension-to-slot lookup only; no player collection, scheduler,"
                + " Sable handles, terrain, packets or server ticks");
        report.addProperty("implementation", "ChartRegistry: slotForDimension(String) is an O(bodies)"
                + " String.equals scan; slotFor(ResourceLocation) is the index built once per rebuild"
                + " (S5-B). The live driver path is index_only_prebuilt_resource_location");
        report.addProperty("java", System.getProperty("java.runtime.version"));
        report.addProperty("os", System.getProperty("os.name") + " " + System.getProperty("os.arch"));
        report.addProperty("bodies", 50);
        report.addProperty("observers_per_sweep", OBSERVERS);
        report.addProperty("warmup_calls_per_scenario_and_path", WARMUP);
        report.addProperty("samples", SAMPLES);
        report.addProperty("sweeps_per_sample", BATCH);
        var rows = new com.google.gson.JsonArray();
        for (var scenario : scenarios()) {
            for (int mode = 0; mode < PATH_NAMES.length; mode++) {
                var chart = fixture();
                var metrics = measure(chart, scenario, locations(scenario), mode);
                metrics.addProperty("scenario", scenario.name());
                metrics.addProperty("path", PATH_NAMES[mode]);
                if (mode != TOSTRING_THEN_SCAN) {
                    assertEquals(0.0, metrics.get("bytes_per_sweep").getAsDouble(),
                            "a sweep on the tick path must allocate nothing: " + metrics);
                }
                for (int i = 0; i < chart.size(); i++) {
                    if (chart.lodOf(i) != BodyLod.ABSENT) {
                        metrics.addProperty("non_absent_bodies_after_sweep", countNonAbsent(chart));
                        break;
                    }
                }
                assertTrue(metrics.get("ns_per_sweep").getAsDouble() < 1_000_000,
                        "1 ms per observer sweep is a coarse regression guard, not an acceptance"
                                + " criterion: " + metrics);
                rows.add(metrics);
                System.out.println("SPACE_DIMENSION_INDEX " + metrics);
            }
        }
        report.add("results", rows);
        Path output = PROJECT.resolve("build/space-dimension-index/report.json");
        Files.createDirectories(output.getParent());
        Files.writeString(output, new GsonBuilder().setPrettyPrinting().create().toJson(report));
    }

    private static int countNonAbsent(ChartRegistry chart) {
        int count = 0;
        for (int i = 0; i < chart.size(); i++) if (chart.lodOf(i) != BodyLod.ABSENT) count++;
        return count;
    }

    private static JsonObject measure(ChartRegistry chart, Scenario scenario,
                                      net.minecraft.resources.ResourceLocation[] locations, int mode) {
        long checksum = 0;
        for (int i = 0; i < WARMUP; i++) checksum += sweep(chart, scenario, locations, mode);
        consumed = checksum;
        long[] samples = new long[SAMPLES];
        var bean = ManagementFactory.getThreadMXBean();
        com.sun.management.ThreadMXBean allocations = bean instanceof com.sun.management.ThreadMXBean candidate
                && candidate.isThreadAllocatedMemorySupported() ? candidate : null;
        if (allocations != null && !allocations.isThreadAllocatedMemoryEnabled()) {
            allocations.setThreadAllocatedMemoryEnabled(true);
        }
        long thread = Thread.currentThread().threadId();
        long before = allocations == null ? -1 : allocations.getThreadAllocatedBytes(thread);
        for (int sample = 0; sample < SAMPLES; sample++) {
            long start = System.nanoTime();
            for (int pass = 0; pass < BATCH; pass++) checksum += sweep(chart, scenario, locations, mode);
            samples[sample] = System.nanoTime() - start;
        }
        long allocated = allocations == null ? -1 : allocations.getThreadAllocatedBytes(thread) - before;
        consumed = checksum;
        Arrays.sort(samples);
        var row = new JsonObject();
        row.addProperty("ns_per_sweep", samples[SAMPLES / 2] / (double) BATCH);
        row.addProperty("ns_per_call", samples[SAMPLES / 2] / (double) BATCH / OBSERVERS);
        row.addProperty("p95_ns_per_sweep", samples[(int) ((SAMPLES - 1) * .95)] / (double) BATCH);
        row.addProperty("max_ns_per_sweep", samples[SAMPLES - 1] / (double) BATCH);
        row.addProperty("bytes_per_sweep", allocated < 0 ? -1 : allocated / (double) (SAMPLES * BATCH));
        row.addProperty("measured_sweeps", SAMPLES * BATCH);
        return row;
    }
}
