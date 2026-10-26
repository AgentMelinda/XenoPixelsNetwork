package net.bullettrain.xenopixelsmod.space.dragonball;

import org.junit.jupiter.api.Test;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DragonBallPlanetAssignmentsTest {
    /** The shipped datapack assignments, read through the project-dir property the Gradle test task sets. */
    private static final Path PROJECT = Path.of(System.getProperty("xenopixels.projectDir", "."));
    private static final Path BUNDLED_ASSIGNMENTS =
            Path.of("src/main/resources/data/xenopixelsmod/xenospace/superdragonball");

    @Test
    void unavailableHostIsSkippedAndReportedWhileValidAssignmentsRemain() {
        var assignments = Map.of(
                1, new DragonBallPlanetDefinition(1, "xenopixelsmod:jupiter", 10, 0),
                2, new DragonBallPlanetDefinition(2, "pack:removed_world", 20, 1),
                3, new DragonBallPlanetDefinition(3, "xenopixelsmod:namek", 30, 2));
        var errors = new java.util.ArrayList<String>();

        var resolved = DragonBallPlanetAssignments.resolvable(assignments,
                Set.of("xenopixelsmod:jupiter", "xenopixelsmod:namek")::contains, errors);

        assertEquals(Map.of(1, assignments.get(1), 3, assignments.get(3)), resolved);
        assertEquals(List.of(1, 3), new java.util.ArrayList<>(resolved.keySet()),
                "resolved assignments retain deterministic slot order");
        assertEquals(List.of("Super Dragon Ball 2: unavailable host chart body pack:removed_world (assignment skipped)"), errors);
    }

    @Test
    void parsesOptionalPeriodAndDefaultsLegacyDefinitionsToGravityDerivedOrbit() {
        var legacy = DragonBallPlanetDefinition.parse("xenopixelsmod:6",
                com.google.gson.JsonParser.parseString("""
                        {"slot":6,"host_chart_id":"xenopixelsmod:maw_9","orbit_radius_blocks":900}
                        """).getAsJsonObject());
        var authored = DragonBallPlanetDefinition.parse("xenopixelsmod:6",
                com.google.gson.JsonParser.parseString("""
                        {"slot":6,"host_chart_id":"xenopixelsmod:maw_9","orbit_radius_blocks":900,"orbit_period_seconds":7200}
                        """).getAsJsonObject());

        assertEquals(0.0, legacy.orbitPeriodSeconds());
        assertEquals(7200.0, authored.orbitPeriodSeconds());
        assertThrows(IllegalArgumentException.class, () -> DragonBallPlanetDefinition.parse(
                "xenopixelsmod:6",
                com.google.gson.JsonParser.parseString("""
                        {"slot":6,"host_chart_id":"xenopixelsmod:maw_9","orbit_radius_blocks":900,"orbit_period_seconds":-1}
                        """).getAsJsonObject()));
    }

    @Test
    void sameHostRepairsMissingOrbitMetadataAndEmptyInputIsANoOp() {
        DragonBallSavedData saved = new DragonBallSavedData();
        var ball = saved.ball(1);
        ball.hostChartId = "xenopixelsmod:jupiter";
        ball.hasPlanetOrbit = false;

        assertTrue(DragonBallPlanetAssignments.persistResolvedAssignments(saved, Map.of(1,
                new DragonBallPlanetDefinition(1, "xenopixelsmod:jupiter", 64, 0.25))));
        assertTrue(ball.hasPlanetOrbit, "the orbit-present flag must be repairable without changing the host");
        assertEquals(64.0, ball.planetOrbitRadiusBlocks, 1.0e-9);
        assertEquals(0.25, ball.planetOrbitPhaseRadians, 1.0e-9);
        assertEquals(0.0, ball.planetOrbitPeriodSeconds, 1.0e-9);
        assertFalse(DragonBallPlanetAssignments.persistResolvedAssignments(saved, Map.of()));
        assertEquals("xenopixelsmod:jupiter", ball.hostChartId);
    }

    @Test
    void nullAssignmentIsSkippedWithoutAbortingOtherSlots() {
        var assignments = new java.util.HashMap<Integer, DragonBallPlanetDefinition>();
        assignments.put(1, null);
        assignments.put(2, new DragonBallPlanetDefinition(2, "xenopixelsmod:jupiter", 32, 0.5));
        var errors = new java.util.ArrayList<String>();

        var resolved = DragonBallPlanetAssignments.resolvable(assignments, ignored -> true, errors);

        assertEquals(Map.of(2, assignments.get(2)), resolved);
        assertEquals(List.of("Super Dragon Ball 1: missing host assignment (assignment skipped)"), errors);
    }

    @Test
    void physicalAdmissionKeepsClearOrbitableSlotsAndSkipsOnlyUnsupportedOnes() {
        var assignments = Map.of(
                1, new DragonBallPlanetDefinition(1, "pack:planet", 40, 0.0),
                2, new DragonBallPlanetDefinition(2, "pack:zero_mu", 40, 0.5),
                3, new DragonBallPlanetDefinition(3, "pack:too_close", 20, 1.0),
                4, new DragonBallPlanetDefinition(4, "pack:period_host", 40, 1.5, 600));
        var errors = new java.util.ArrayList<String>();

        var admitted = DragonBallPlanetAssignments.physicallySupported(assignments, id -> switch (id) {
            case "pack:planet" -> new DragonBallPlanetAssignments.HostOrbitInfo(20, 9.81);
            case "pack:zero_mu" -> new DragonBallPlanetAssignments.HostOrbitInfo(10, 0);
            case "pack:too_close" -> new DragonBallPlanetAssignments.HostOrbitInfo(8, 9.81);
            case "pack:period_host" -> new DragonBallPlanetAssignments.HostOrbitInfo(20, 0);
            default -> null;
        }, 16, errors);

        assertEquals(java.util.Set.of(1, 4), admitted.keySet());
        assertEquals(2, errors.size());
        assertTrue(errors.stream().anyMatch(error -> error.contains("Super Dragon Ball 2")
                && error.contains("no positive gravitational parameter")));
        assertTrue(errors.stream().anyMatch(error -> error.contains("Super Dragon Ball 3")
                && error.contains("does not clear host radius")));
        assertEquals(16.0, DragonBallPlanetAssignments.PROTOTYPE_BODY_RADIUS_BLOCKS);
        assertThrows(UnsupportedOperationException.class, () -> admitted.put(5,
                new DragonBallPlanetDefinition(5, "pack:planet", 50, 0)));
    }

    @Test
    void physicalAdmissionRejectsInvalidPeriodEvenWhenTheHostHasZeroMu() {
        var assignment = new DragonBallPlanetDefinition(6, "pack:static", 50, 0.25, Double.MIN_VALUE);
        var errors = new java.util.ArrayList<String>();

        var admitted = DragonBallPlanetAssignments.physicallySupported(Map.of(6, assignment),
                id -> new DragonBallPlanetAssignments.HostOrbitInfo(20, 0), 16, errors);

        assertTrue(admitted.isEmpty());
        assertEquals(1, errors.size());
        assertTrue(errors.getFirst().contains("invalid orbit"));
    }

    @Test
    void persistsOnlyResolvedHostsWithoutChangingLegacyBallStateOrIdentity() throws Exception {
        DragonBallSavedData saved = new DragonBallSavedData();
        var held = saved.ball(1);
        held.status = DragonBallSavedData.Status.HELD;
        held.dimension = "minecraft:overworld";
        held.x = 12; held.y = 80; held.z = -4;
        held.hostChartId = "old:host";
        UUID bodyId = UUID.randomUUID();
        assertTrue(saved.bindPlanetBody(1, bodyId));
        var prior = saved.ball(2);
        prior.hostChartId = "pack:previous_host";

        assertTrue(DragonBallPlanetAssignments.persistResolvedAssignments(saved, Map.of(
                1, new DragonBallPlanetDefinition(1, "xenopixelsmod:namek", 64, 0.2),
                3, new DragonBallPlanetDefinition(3, "pack:beerus_world", 96, 1.1),
                4, new DragonBallPlanetDefinition(5, "pack:wrong_slot", 96, 1.1))));

        assertEquals("xenopixelsmod:namek", saved.ball(1).hostChartId);
        assertEquals("pack:previous_host", saved.ball(2).hostChartId, "omitted/invalid slots retain their last valid host");
        assertEquals("pack:beerus_world", saved.ball(3).hostChartId);
        assertEquals("", saved.ball(4).hostChartId, "slot/key mismatches must never bind the wrong identity");
        assertEquals(DragonBallSavedData.Status.HELD, saved.ball(1).status);
        assertEquals("minecraft:overworld", saved.ball(1).dimension);
        assertEquals(12, saved.ball(1).x); assertEquals(80, saved.ball(1).y); assertEquals(-4, saved.ball(1).z);
        assertEquals(bodyId, saved.planetBodyUuid(1));

        CompoundTag persisted = saved.save(new CompoundTag(), null);
        CompoundTag savedFirst = persisted.getList("Balls", Tag.TAG_COMPOUND).getCompound(0);
        assertEquals("xenopixelsmod:namek", savedFirst.getString("HostChartId"));
        assertTrue(savedFirst.getBoolean("HasPlanetOrbit"));
        assertEquals(64.0, savedFirst.getDouble("PlanetOrbitRadiusBlocks"), 1.0e-9);
        assertEquals(0.2, savedFirst.getDouble("PlanetOrbitPhaseRadians"), 1.0e-9);
        assertFalse(DragonBallPlanetAssignments.persistResolvedAssignments(saved, Map.of(1,
                new DragonBallPlanetDefinition(1, "xenopixelsmod:namek", 64, 0.2))));
    }

    @Test
    void persistsAndUpdatesOptionalPeriodWithoutChangingLegacyOrbitFields() {
        DragonBallSavedData saved = new DragonBallSavedData();
        assertTrue(DragonBallPlanetAssignments.persistResolvedAssignments(saved, Map.of(6,
                new DragonBallPlanetDefinition(6, "xenopixelsmod:maw_9", 900, 0.75, 7200))));

        var ball = saved.ball(6);
        assertEquals("xenopixelsmod:maw_9", ball.hostChartId);
        assertEquals(900.0, ball.planetOrbitRadiusBlocks, 1.0e-9);
        assertEquals(0.75, ball.planetOrbitPhaseRadians, 1.0e-9);
        assertEquals(7200.0, ball.planetOrbitPeriodSeconds, 1.0e-9);

        CompoundTag savedTag = saved.save(new CompoundTag(), null);
        CompoundTag entry = savedTag.getList("Balls", Tag.TAG_COMPOUND).getCompound(5);
        assertEquals(5, savedTag.getInt("DataVersion"));
        assertEquals(7200.0, entry.getDouble("PlanetOrbitPeriodSeconds"), 1.0e-9);

        DragonBallSavedData loaded = DragonBallSavedData.load(savedTag, null);
        assertEquals(7200.0, loaded.ball(6).planetOrbitPeriodSeconds, 1.0e-9);
        assertFalse(DragonBallPlanetAssignments.persistResolvedAssignments(loaded, Map.of(6,
                new DragonBallPlanetDefinition(6, "xenopixelsmod:maw_9", 900, 0.75, 7200))));
        assertTrue(DragonBallPlanetAssignments.persistResolvedAssignments(loaded, Map.of(6,
                new DragonBallPlanetDefinition(6, "xenopixelsmod:maw_9", 900, 0.75))));
        assertEquals(0.0, loaded.ball(6).planetOrbitPeriodSeconds, 1.0e-9,
                "removing the optional datapack period clears only the new field");
    }

    @Test
    void manualOrbitChangesEffectiveTrackButCannotMoveInsideHostClearance() {
        var authored = new DragonBallPlanetDefinition(1, "xenopixelsmod:jupiter", 320, 0.4, 0.0);
        var manual = new DragonBallSavedData.ManualOrbit(510, -1.2, 1800);

        var moved = DragonBallPlanetAssignments.effective(authored, manual, 200, 16);
        var blocked = DragonBallPlanetAssignments.effective(authored,
                new DragonBallSavedData.ManualOrbit(216, 0, 1800), 200, 16);
        var reset = DragonBallPlanetAssignments.effective(authored, null, 200, 16);

        assertEquals(510, moved.orbitRadiusBlocks());
        assertEquals(-1.2, moved.orbitPhaseRadians());
        assertEquals(1800, moved.orbitPeriodSeconds());
        assertEquals(authored, blocked, "a saved override that overlaps the current host falls back to the pack track");
        assertEquals(authored, reset, "clearing the saved override restores the authored track");

        DragonBallSavedData saved = new DragonBallSavedData();
        assertTrue(saved.setManualOrbit(1, 510, -1.2, 1800));
        assertFalse(saved.setManualOrbit(1, 510, -1.2, 1800), "an identical orbit edit should not dirty SavedData again");
        assertTrue(saved.clearManualOrbit(1));
        assertFalse(saved.clearManualOrbit(1));
        assertEquals(authored, DragonBallPlanetAssignments.effective(authored, saved.manualOrbit(1), 200, 16));
    }

    /**
     * The shipped slot-6 assignment must be deliverable: Maw-9 authors {@code gravity.surface_ms2: 0}, so its
     * derived gravitational parameter is zero and the gravity-derived track can never be built. The explicit
     * period is the only supported alternative, and 7200 s is a chosen gameplay period for this pack - not an
     * astronomical value and not runtime-verified motion.
     */
    @Test
    void bundledSlotSixAuthorsTheExplicitPeriodItsZeroGravityHostRequires() throws Exception {
        var bundled = bundledAssignments();

        assertEquals(Set.of(1, 2, 3, 4, 5, 6, 7), bundled.keySet(), "seven shipped assignments");
        assertEquals(List.of(6), bundled.entrySet().stream()
                .filter(entry -> entry.getValue().orbitPeriodSeconds() > 0.0)
                .map(Map.Entry::getKey).toList(),
                "only slot 6 authors a period; every other ball still derives its track from host gravity");

        var sixth = bundled.get(6);
        assertEquals("xenopixelsmod:maw_9", sixth.hostChartId());
        assertEquals(900.0, sixth.orbitRadiusBlocks(), 1.0e-9);
        assertEquals(7200.0, sixth.orbitPeriodSeconds(), 1.0e-9);

        // Host geometry as shipped: maw_9.json radius_blocks 40, surface_ms2 0 -> mu 0.
        var errors = new java.util.ArrayList<String>();
        var admitted = DragonBallPlanetAssignments.physicallySupported(Map.of(6, sixth),
                ignored -> new DragonBallPlanetAssignments.HostOrbitInfo(40, 0),
                DragonBallPlanetAssignments.PROTOTYPE_BODY_RADIUS_BLOCKS, errors);
        assertEquals(Map.of(6, sixth), admitted, "the production admission resolver must keep slot 6");
        assertTrue(errors.isEmpty(), () -> "unexpected admission errors: " + errors);

        var orbit = DragonBallPlanetOrbit.createWithPeriod(sixth.orbitRadiusBlocks(),
                sixth.orbitPhaseRadians(), sixth.orbitPeriodSeconds());
        assertEquals(7200.0, orbit.elements().periodSeconds(), 1.0e-9);
        assertEquals(900.0, orbit.elements().semiMajorAxis(), 1.0e-9);
        assertEquals(0.0, orbit.elements().eccentricity(), 1.0e-9);
    }

    /** Falsifier: without the authored period the same shipped host/radius is refused, so the key is load-bearing. */
    @Test
    void droppingTheAuthoredPeriodWouldLeaveSlotSixUndeliverableAgainstItsZeroGravityHost() {
        var legacyShape = new DragonBallPlanetDefinition(6, "xenopixelsmod:maw_9", 900, 4.4879895051);
        var errors = new java.util.ArrayList<String>();

        assertTrue(DragonBallPlanetAssignments.physicallySupported(Map.of(6, legacyShape),
                ignored -> new DragonBallPlanetAssignments.HostOrbitInfo(40, 0), 16, errors).isEmpty());
        assertEquals(1, errors.size());
        assertTrue(errors.getFirst().contains("no authored orbit_period_seconds"), () -> errors.toString());
    }

    private static Map<Integer, DragonBallPlanetDefinition> bundledAssignments() throws Exception {
        Path directory = PROJECT.resolve(BUNDLED_ASSIGNMENTS);
        assertTrue(Files.isDirectory(directory), () -> "missing shipped assignment folder " + directory);
        Map<Integer, DragonBallPlanetDefinition> out = new TreeMap<>();
        try (var stream = Files.list(directory)) {
            for (Path file : stream.filter(path -> path.getFileName().toString().endsWith(".json"))
                    .sorted().toArray(Path[]::new)) {
                String name = file.getFileName().toString();
                String stem = name.substring(0, name.length() - ".json".length());
                var definition = DragonBallPlanetDefinition.parse("xenopixelsmod:" + stem,
                        com.google.gson.JsonParser.parseString(Files.readString(file)).getAsJsonObject());
                assertEquals(stem, Integer.toString(definition.slot()),
                        name + " must declare the slot its file name states");
                assertNull(out.put(definition.slot(), definition), "duplicate shipped slot " + definition.slot());
            }
        }
        return out;
    }
}
