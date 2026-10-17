package net.bullettrain.xenopixelsmod.config;

import net.bullettrain.xenopixelsmod.config.XenoSpaceConfig.Data;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Headless coverage for {@link XenoSpaceConfig}'s altitude-handoff block — the seven v5 keys, the clamps
 * that keep a hand-edited config from producing an oscillating boundary or a sweep that runs every tick,
 * and the two safety nets the handoff depends on: a malformed host falling back to the shipped Voidsea,
 * and the host being forced into the space-dimension set even when a pack author left it out of the list.
 *
 * <p>{@code apply(Data)} and {@code snapshot()} are static and touch no disk, so this exercises the same
 * bound the server runs on without booting one.
 */
class XenoSpaceConfigTest {
    @Test void fullWidthLogicalUniverseToggleDefaultsOffAndPersists() {
        XenoSpaceConfig.apply(defaults());
        assertFalse(XenoSpaceConfig.logicalUniverseEnabled());

        Data enabled = defaults();
        enabled.logicalUniverseEnabled = true;
        XenoSpaceConfig.apply(enabled);
        assertTrue(XenoSpaceConfig.logicalUniverseEnabled());

        XenoSpaceConfig.apply(XenoSpaceConfig.snapshot());
        assertTrue(XenoSpaceConfig.logicalUniverseEnabled());
        XenoSpaceConfig.apply(defaults());
    }

    @Test void smallPlanetPhysicalizationIsOffByDefaultBoundedAndPersisted() {
        XenoSpaceConfig.apply(defaults());
        assertFalse(XenoSpaceConfig.smallPlanetPhysical());
        assertTrue(XenoSpaceConfig.smallPlanetRadialGravity());
        assertFalse(XenoSpaceConfig.smallPlanetGravityEnabled());
        assertEquals(420.0, XenoSpaceConfig.smallPlanetActivateRange(), 1e-9);
        assertEquals(650.0, XenoSpaceConfig.smallPlanetReleaseRange(), 1e-9);
        assertEquals(2, XenoSpaceConfig.smallPlanetMaxPerPlayer());
        assertEquals(8, XenoSpaceConfig.smallPlanetMaxGlobal());
        assertFalse(XenoSpaceConfig.smallPlanetBuildIncrementalSections());

        Data d = defaults();
        d.enabled = true;
        d.smallPlanetPhysical = true;
        d.smallPlanetRadialGravity = true;
        d.smallPlanetActivateRange = 1_000;
        d.smallPlanetReleaseRange = 1_500;
        d.smallPlanetMaxPerPlayer = 4;
        d.smallPlanetMaxGlobal = 12;
        d.smallPlanetSpawnIntervalTicks = 60;
        d.smallPlanetBuildVoxelsPerTick = 1_000;
        d.smallPlanetBuildIncrementalSections = true;
        XenoSpaceConfig.apply(d);
        Data saved = XenoSpaceConfig.snapshot();
        XenoSpaceConfig.apply(saved);
        assertTrue(XenoSpaceConfig.smallPlanetPhysical());
        assertTrue(XenoSpaceConfig.smallPlanetRadialGravity());
        assertTrue(XenoSpaceConfig.smallPlanetGravityEnabled());
        assertEquals(1_000, XenoSpaceConfig.smallPlanetActivateRange(), 1e-9);
        assertEquals(1_500, XenoSpaceConfig.smallPlanetReleaseRange(), 1e-9);
        assertEquals(4, XenoSpaceConfig.smallPlanetMaxPerPlayer());
        assertEquals(12, XenoSpaceConfig.smallPlanetMaxGlobal());
        assertEquals(60, XenoSpaceConfig.smallPlanetSpawnIntervalTicks());
        assertEquals(1_000, XenoSpaceConfig.smallPlanetBuildVoxelsPerTick());
        assertTrue(XenoSpaceConfig.smallPlanetBuildIncrementalSections());

        d.smallPlanetRadialGravity = false;
        XenoSpaceConfig.apply(d);
        assertFalse(XenoSpaceConfig.smallPlanetRadialGravity());
        assertFalse(XenoSpaceConfig.smallPlanetGravityEnabled());
    }

    @Test void smallPlanetPhysicalizationClampsRangesAndHardBudgets() {
        Data d = defaults();
        d.smallPlanetActivateRange = Double.NaN;
        d.smallPlanetReleaseRange = Double.NEGATIVE_INFINITY;
        d.smallPlanetMaxPerPlayer = Integer.MAX_VALUE;
        d.smallPlanetMaxGlobal = Integer.MAX_VALUE;
        d.smallPlanetSpawnIntervalTicks = Integer.MIN_VALUE;
        d.smallPlanetBuildVoxelsPerTick = Integer.MAX_VALUE;
        d.smallPlanetBuildIncrementalSections = true;
        XenoSpaceConfig.apply(d);
        assertEquals(420.0, XenoSpaceConfig.smallPlanetActivateRange(), 1e-9);
        assertEquals(650.0, XenoSpaceConfig.smallPlanetReleaseRange(), 1e-9);
        assertEquals(16, XenoSpaceConfig.smallPlanetMaxPerPlayer());
        assertEquals(32, XenoSpaceConfig.smallPlanetMaxGlobal());
        assertEquals(1, XenoSpaceConfig.smallPlanetSpawnIntervalTicks());
        assertEquals(2_000, XenoSpaceConfig.smallPlanetBuildVoxelsPerTick());
        assertTrue(XenoSpaceConfig.smallPlanetBuildIncrementalSections());

        d.smallPlanetActivateRange = 20_000;
        d.smallPlanetReleaseRange = 1;
        d.smallPlanetSpawnIntervalTicks = 500_000;
        d.smallPlanetBuildVoxelsPerTick = 1;
        XenoSpaceConfig.apply(d);
        assertEquals(4_096.0, XenoSpaceConfig.smallPlanetActivateRange(), 1e-9);
        assertEquals(4_128.0, XenoSpaceConfig.smallPlanetReleaseRange(), 1e-9);
        assertEquals(1_200, XenoSpaceConfig.smallPlanetSpawnIntervalTicks());
        assertEquals(1, XenoSpaceConfig.smallPlanetBuildVoxelsPerTick());
    }

    @Test void bareHandoffExitKeysMeanThisModsNamespace() {
        Data d = defaults();
        d.handoffExitOverrides = new java.util.HashMap<>(java.util.Map.of("earth", 2032.0, "dragonminez:namek", 900.0));
        XenoSpaceConfig.apply(d);
        assertEquals(2032.0, XenoSpaceConfig.handoffExitOverrides().get("xenopixelsmod:earth"));
        assertEquals(900.0, XenoSpaceConfig.handoffExitOverrides().get("dragonminez:namek"));
        assertNull(XenoSpaceConfig.handoffExitOverrides().get("earth"));
        XenoSpaceConfig.apply(defaults());
    }

    @Test void sharedGeneratedBodyBuildBudgetDefaultsOnMigratesOldZeroAndHasAnExplicitToggle() {
        XenoSpaceConfig.apply(defaults());
        Data d = defaults();
        assertTrue(XenoSpaceConfig.generatedBodyBuildBudgetEnabled());
        assertEquals(2_000, XenoSpaceConfig.generatedBodyBuildVoxelsPerTick());

        d.physicalPlanetMaxRadius = 48;
        d.generatedBodyBuildVoxelsPerTick = 1;
        XenoSpaceConfig.apply(d);
        assertEquals(1, XenoSpaceConfig.generatedBodyBuildVoxelsPerTick());

        d.generatedBodyBuildBudgetEnabled = false;
        XenoSpaceConfig.apply(d);
        assertEquals(0, XenoSpaceConfig.generatedBodyBuildVoxelsPerTick());
        assertFalse(XenoSpaceConfig.generatedBodyBuildBudgetEnabled());
        XenoSpaceConfig.apply(XenoSpaceConfig.snapshot());
        assertFalse(XenoSpaceConfig.generatedBodyBuildBudgetEnabled(), "the operator toggle must survive config snapshots");

        Data old = new Data();
        old.version = 18;
        old.generatedBodyBuildVoxelsPerTick = 0;
        XenoSpaceConfig.apply(old);
        assertTrue(XenoSpaceConfig.generatedBodyBuildBudgetEnabled());
        assertEquals(2_000, XenoSpaceConfig.generatedBodyBuildVoxelsPerTick(),
                "v18's absent/zero shared cap migrates to the bounded v19 default");

        d.physicalPlanetMaxRadius = 400;
        d.generatedBodyBuildVoxelsPerTick = Integer.MAX_VALUE;
        d.generatedBodyBuildBudgetEnabled = true;
        XenoSpaceConfig.apply(d);
        assertEquals(2_000, XenoSpaceConfig.generatedBodyBuildVoxelsPerTick());
        Data snapshot = XenoSpaceConfig.snapshot();
        XenoSpaceConfig.apply(snapshot);
        assertEquals(2_000, XenoSpaceConfig.generatedBodyBuildVoxelsPerTick());
    }

    @Test void incrementalAsteroidSectionWriterIsOptInAndSurvivesSnapshot() {
        XenoSpaceConfig.apply(defaults());
        assertFalse(XenoSpaceConfig.asteroidBuildIncrementalSections());

        Data d = defaults();
        d.asteroidBuildIncrementalSections = true;
        XenoSpaceConfig.apply(d);
        assertTrue(XenoSpaceConfig.asteroidBuildIncrementalSections());
        XenoSpaceConfig.apply(XenoSpaceConfig.snapshot());
        assertTrue(XenoSpaceConfig.asteroidBuildIncrementalSections());
    }

    @Test void asteroidBuildBudgetCanBeTunedDownButCannotExceedHardPerTickCap() {
        Data d = defaults();
        d.asteroidBuildVoxelsPerTick = 1;
        XenoSpaceConfig.apply(d);
        assertEquals(1, XenoSpaceConfig.asteroidBuildVoxelsPerTick());

        d.asteroidBuildVoxelsPerTick = Integer.MAX_VALUE;
        XenoSpaceConfig.apply(d);
        assertEquals(2_000, XenoSpaceConfig.asteroidBuildVoxelsPerTick());
    }

    @Test void asteroidCandidateScanBudgetPersistsAndIsHardBounded() {
        XenoSpaceConfig.apply(defaults());
        assertEquals(4_096, XenoSpaceConfig.asteroidScanCellsPerPass());

        Data d = defaults();
        d.asteroidScanCellsPerPass = 257;
        XenoSpaceConfig.apply(d);
        assertEquals(257, XenoSpaceConfig.asteroidScanCellsPerPass());
        XenoSpaceConfig.apply(XenoSpaceConfig.snapshot());
        assertEquals(257, XenoSpaceConfig.asteroidScanCellsPerPass());

        d.asteroidScanCellsPerPass = Integer.MAX_VALUE;
        XenoSpaceConfig.apply(d);
        assertEquals(65_536, XenoSpaceConfig.asteroidScanCellsPerPass());
        d.asteroidScanCellsPerPass = Integer.MIN_VALUE;
        XenoSpaceConfig.apply(d);
        assertEquals(1, XenoSpaceConfig.asteroidScanCellsPerPass());
    }

    @Test void plotBoundsBatchingIsConfigurableAndSurvivesSnapshot() {
        XenoSpaceConfig.apply(defaults());
        assertFalse(XenoSpaceConfig.planetBuildCoalesceChunkBounds(), "keep experimental bounds batching off until gated runtime proof");

        Data d = defaults();
        d.planetBuildCoalesceChunkBounds = true;
        XenoSpaceConfig.apply(d);
        assertTrue(XenoSpaceConfig.planetBuildCoalesceChunkBounds());
        XenoSpaceConfig.apply(XenoSpaceConfig.snapshot());
        assertTrue(XenoSpaceConfig.planetBuildCoalesceChunkBounds());
        d.planetBuildCoalesceChunkBounds = false;
        XenoSpaceConfig.apply(d);
        assertFalse(XenoSpaceConfig.planetBuildCoalesceChunkBounds());
        XenoSpaceConfig.apply(XenoSpaceConfig.snapshot());
        assertFalse(XenoSpaceConfig.planetBuildCoalesceChunkBounds());
    }

    @Test void exitOverridesSurviveSnapshotAndIgnoreMalformedEntries() {
        Data d = defaults();
        d.handoffExitOverrides = new java.util.LinkedHashMap<>();
        d.handoffExitOverrides.put("xenopixelsmod:earth", 240.0);
        d.handoffExitOverrides.put("bad id", 100.0);
        d.handoffExitOverrides.put("xenopixelsmod:moon", Double.NaN);
        XenoSpaceConfig.apply(d);
        assertEquals(java.util.Map.of("xenopixelsmod:earth", 240.0), XenoSpaceConfig.handoffExitOverrides());
        XenoSpaceConfig.apply(XenoSpaceConfig.snapshot());
        assertEquals(240.0, XenoSpaceConfig.handoffExitOverrides().get("xenopixelsmod:earth"));
        XenoSpaceConfig.apply(new Data());
    }
    private static Data defaults() {
        // Apply a fresh Data first so a test that only changes one key starts from the shipped defaults
        // rather than from whatever a previous test left in the static state.
        XenoSpaceConfig.apply(new Data());
        return new Data();
    }

    @Test void shippedDefaultsAreTheConservativeHandoff() {
        XenoSpaceConfig.apply(defaults());
        assertFalse(XenoSpaceConfig.handoffEnabled(), "moves players between dimensions; off by default");
        assertEquals(XenoSpaceConfig.DEFAULT_HANDOFF_HOST, XenoSpaceConfig.handoffHost());
        assertEquals(128.0, XenoSpaceConfig.handoffBandThickness(), 1e-9);
        assertEquals(512.0, XenoSpaceConfig.handoffReach(), 1e-9);
        assertEquals(4, XenoSpaceConfig.handoffIntervalTicks());
        assertTrue(XenoSpaceConfig.velocityCarryover());
        assertTrue(XenoSpaceConfig.flightGrant());
    }

    @Test void theShippedHostIsTheVoidseaDimensionThisRepoProvidesDataFor() {
        // The whole point of the rename: the default host must be the id whose dimension_type this mod
        // actually ships (min_y 0, height 1024), not the visual spike or the pre-rename "orbit".
        assertEquals("xenopixelsmod:voidsea", XenoSpaceConfig.DEFAULT_HANDOFF_HOST.toString());
    }

    @Test void aZeroBandZeroIntervalOrNonPositiveReachFallBackToTheDefaults() {
        Data d = defaults();
        d.handoffBandThickness = 0.0;
        d.handoffIntervalTicks = 0;
        d.handoffReach = -5.0;
        XenoSpaceConfig.apply(d);
        // Each of these would otherwise make the boundary wrong rather than merely ugly: a zero band puts
        // an arrival exactly on its own mirror trigger (oscillation), a zero interval sweeps every tick.
        assertEquals(128.0, XenoSpaceConfig.handoffBandThickness(), 1e-9);
        assertEquals(4, XenoSpaceConfig.handoffIntervalTicks());
        assertEquals(512.0, XenoSpaceConfig.handoffReach(), 1e-9);
    }

    @Test void nonFiniteValuesFallBackRatherThanPropagateNaNIntoTheRule() {
        Data d = defaults();
        d.handoffBandThickness = Double.NaN;
        d.handoffReach = Double.POSITIVE_INFINITY;
        XenoSpaceConfig.apply(d);
        assertEquals(128.0, XenoSpaceConfig.handoffBandThickness(), 1e-9);
        assertEquals(512.0, XenoSpaceConfig.handoffReach(), 1e-9);
    }

    @Test void oversizedValuesAreClampedNotRejected() {
        Data d = defaults();
        d.handoffBandThickness = 1_000_000.0;
        d.handoffReach = 100_000_000.0;
        d.handoffIntervalTicks = 100_000;
        XenoSpaceConfig.apply(d);
        assertEquals(4096.0, XenoSpaceConfig.handoffBandThickness(), 1e-9);
        assertEquals(100_000.0, XenoSpaceConfig.handoffReach(), 1e-9);
        assertEquals(1200, XenoSpaceConfig.handoffIntervalTicks());
    }

    @Test void aMalformedOrBlankHostFallsBackToTheShippedVoidsea() {
        for (String bad : new String[]{null, "", "   ", "not a valid id!!", "no-colon-path"}) {
            Data d = defaults();
            d.handoffHost = bad;
            XenoSpaceConfig.apply(d);
            assertEquals(XenoSpaceConfig.DEFAULT_HANDOFF_HOST, XenoSpaceConfig.handoffHost(),
                    "host '" + bad + "' should fall back");
        }
    }

    @Test void aWellFormedCustomHostIsHonoured() {
        Data d = defaults();
        d.handoffHost = "xenospace_spike:orbit";
        XenoSpaceConfig.apply(d);
        assertEquals(ResourceLocation.parse("xenospace_spike:orbit"), XenoSpaceConfig.handoffHost());
    }

    @Test void theHostIsAlwaysTreatedAsASpaceDimensionEvenWhenTheListOmitsIt() {
        // A destination that is not "space" gets no void protection and no LOD promotions: a half-working
        // handoff. So withHost() unions it in regardless of what the author listed.
        Data d = defaults();
        d.handoffHost = "xenopixelsmod:voidsea";
        d.spaceDimensions = List.of("minecraft:overworld");
        XenoSpaceConfig.apply(d);
        Set<ResourceLocation> space = XenoSpaceConfig.spaceDimensions();
        assertTrue(space.contains(ResourceLocation.parse("minecraft:overworld")));
        assertTrue(space.contains(ResourceLocation.parse("xenopixelsmod:voidsea")),
                "host must be present even though the author's list omitted it");
    }

    @Test void snapshotAndApplyRoundTripWithoutDrift() {
        Data d = defaults();
        d.handoffEnabled = true;
        d.handoffHost = "xenospace_spike:orbit";
        d.handoffBandThickness = 200.0;
        d.handoffReach = 900.0;
        d.handoffIntervalTicks = 8;
        d.velocityCarryover = false;
        d.flightGrant = false;
        XenoSpaceConfig.apply(d);
        Data written = XenoSpaceConfig.snapshot();
        // Re-reading what we just wrote must be a fixed point — otherwise a save/load cycle silently
        // retunes the boundary the operator configured.
        XenoSpaceConfig.apply(written);
        assertTrue(XenoSpaceConfig.handoffEnabled());
        assertEquals(ResourceLocation.parse("xenospace_spike:orbit"), XenoSpaceConfig.handoffHost());
        assertEquals(200.0, XenoSpaceConfig.handoffBandThickness(), 1e-9);
        assertEquals(900.0, XenoSpaceConfig.handoffReach(), 1e-9);
        assertEquals(8, XenoSpaceConfig.handoffIntervalTicks());
        assertFalse(XenoSpaceConfig.velocityCarryover());
        assertFalse(XenoSpaceConfig.flightGrant());
    }

    @Test void statusLineReportsTheHandoffBlockAnOperatorNeedsToSee() {
        XenoSpaceConfig.apply(defaults());
        String line = XenoSpaceConfig.statusLine();
        assertTrue(line.contains("handoff="), line);
        assertTrue(line.contains("band=128"), line);
        assertTrue(line.contains("flightGrant="), line);
    }
}
