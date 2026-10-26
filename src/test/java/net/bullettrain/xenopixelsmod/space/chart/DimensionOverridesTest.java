package net.bullettrain.xenopixelsmod.space.chart;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class DimensionOverridesTest {
    @TempDir Path directory;
    @Test void overridesBeatDatapacksAndMalformedRowsAreReported() throws Exception {
        Files.writeString(directory.resolve("earth.yml"), """
                dimension: minecraft:overworld
                charted_as: demo:earth
                overrides:
                  radius_blocks: 120
                  exit_altitude: 800
                """);
        Files.writeString(directory.resolve("bad.yml"), "dimension: [broken");
        var base = Map.of("demo:earth", JsonParser.parseString("{\"radius_blocks\":90,\"dimension\":\"minecraft:overworld\"}").getAsJsonObject());
        var result = DimensionOverrides.load(directory, base);
        assertEquals(120, ChartDefinition.parse("demo:earth", result.definitions().get("demo:earth")).radiusBlocks());
        assertEquals(800, ChartDefinition.parse("demo:earth", result.definitions().get("demo:earth")).exitAltitude());
        assertEquals(1, result.errors().size());
        assertEquals(90, base.get("demo:earth").get("radius_blocks").getAsInt());
    }
    @Test void voidDamageOverrideRoundTripsThroughASidecar() throws Exception {
        Files.writeString(directory.resolve("lethal.yml"), """
                dimension: demo:hellmouth
                charted_as: demo:earth
                overrides:
                  void_damage: true
                  sky_style: 2
                """);
        var base = Map.of("demo:earth", JsonParser.parseString("{\"radius_blocks\":90,\"dimension\":\"demo:hellmouth\"}").getAsJsonObject());
        var result = DimensionOverrides.load(directory, base);
        assertTrue(result.errors().isEmpty(), result.errors().toString());
        assertEquals(Boolean.TRUE, ChartDefinition.parse("demo:earth", result.definitions().get("demo:earth")).voidDamage());
        // A server pack may recolor an existing body's art without touching the datapack that authored it.
        assertEquals(2, ChartDefinition.parse("demo:earth", result.definitions().get("demo:earth")).skyStyle());
    }
    @Test void duplicateYamlKeysAndObjectTagsAreRejected() throws Exception {
        Files.writeString(directory.resolve("a.yml"), "dimension: minecraft:overworld\ndimension: minecraft:the_end\n");
        Files.writeString(directory.resolve("b.yml"), "!!java/object:java.lang.String {}");
        assertEquals(2, DimensionOverrides.load(directory, Map.of()).errors().size());
    }
    @Test void implicitBodyUsesExplicitIdAndDimension() throws Exception {
        Files.writeString(directory.resolve("pocket.yml"), """
                dimension: demo:pocket
                implicit_body:
                  id: demo:pocket_body
                  radius_blocks: 32
                  kind: pocket
                """);
        var result = DimensionOverrides.load(directory, Map.of());
        assertTrue(result.errors().isEmpty(), result.errors().toString());
        assertEquals("demo:pocket", ChartDefinition.parse("demo:pocket_body", result.definitions().get("demo:pocket_body")).dimensionId());
    }
    @Test void implicitBodyInheritsGlobalDefaults() throws Exception {
        Files.writeString(directory.resolve("pocket.yml"), "dimension: demo:pocket\nimplicit_body:\n  id: demo:body\n");
        var defaults = JsonParser.parseString("{\"radius_blocks\":120,\"gravity\":{\"surface_ms2\":8}}").getAsJsonObject();
        var result = DimensionOverrides.load(directory, Map.of(), defaults);
        var body = ChartDefinition.parse("demo:body", result.definitions().get("demo:body"));
        assertEquals(120, body.radiusBlocks());
        assertEquals(115200, body.mu());
    }

    @Test void sidecarBindsAnAvailableVanillaEndDimensionIntoTheChart() throws Exception {
        Files.writeString(directory.resolve("minecraft.the_end.yml"), """
                dimension: minecraft:the_end
                charted_as: demo:outer_end
                overrides:
                  radius_blocks: 128
                  exit_altitude: 900
                """);
        var base = Map.of("demo:outer_end", JsonParser.parseString("""
                {"position":{"x":0,"y":512,"z":0},"radius_blocks":96}
                """).getAsJsonObject());

        var sidecars = DimensionOverrides.load(directory, base);
        assertTrue(sidecars.errors().isEmpty(), sidecars.errors().toString());
        assertTrue(sidecars.dimensions().contains("minecraft:the_end"));

        var assembled = ChartAssembly.build(sidecars.definitions(), new com.google.gson.JsonObject(),
                Set.of("minecraft:the_end"));
        assertTrue(assembled.errors().isEmpty(), assembled.errors().toString());
        int slot = assembled.chart().slotForDimension("minecraft:the_end");
        assertTrue(slot >= 0, "the available End dimension should resolve to a chart slot");
        assertEquals("demo:outer_end", assembled.chart().body(slot).id());
        assertEquals(128, assembled.chart().body(slot).radiusBlocks());
        assertEquals(900, assembled.chart().exitAltitude(slot));
    }
    @Test void documentedFilenameAndSpaceSectionsLoadWithValidatedSettings() throws Exception {
        Files.writeString(directory.resolve("minecraft.the_end.yml"), """
                charted_as: outer_end
                space:
                  enabled: true
                  exit_altitude: 720
                  band_thickness: 96
                  reentry_altitude: 680
                  safe_entry_speed: 2.5
                  sky_transition_altitude: 460
                  oxygen: {required_above_exit: true, drain_rate: slow}
                  destructible: gated
                  gravity_multiplier: 1.5
                  orbital_motion: false
                overrides:
                  danger_rating: 3
                flags:
                  allow_ships_in_atmosphere: true
                  allow_ki_spacewalk: true
                  reentry_fx: plasma_shell
                """);
        var base = Map.of("xenopixelsmod:outer_end", JsonParser.parseString("""
                {"position":{"x":0,"y":512,"z":0},"radius_blocks":96,
                 "gravity":{"surface_ms2":8,"falloff":"inverse_square"}}
                """).getAsJsonObject());
        var result = DimensionOverrides.load(directory, base);
        assertTrue(result.errors().isEmpty(), result.errors().toString());
        assertTrue(result.dimensions().contains("minecraft:the_end"));
        var settings = result.settings().get("minecraft:the_end");
        assertEquals(true, settings.enabled());
        assertTrue(settings.enabledOrDefault(false));
        assertEquals(96.0, settings.bandThickness());
        assertEquals(96.0, settings.bandThicknessOr(128.0));
        assertEquals(680.0, settings.reentryAltitudeOr(720.0));
        assertEquals(2.5, settings.safeEntrySpeed());
        assertEquals("slow", settings.oxygenDrainRate());
        assertEquals("gated", settings.destructible());
        assertEquals(true, settings.flags().get("allow_ki_spacewalk"));
        assertTrue(settings.allowShipsInAtmosphereOrDefault());
        assertTrue(settings.reentryPlasmaShellEnabled());
        var body = ChartDefinition.parse("xenopixelsmod:outer_end", result.definitions().get("xenopixelsmod:outer_end"));
        assertEquals(720, body.exitAltitude());
        assertEquals(false, body.orbitalMotion());
        assertEquals(12.0 * body.radiusBlocks() * body.radiusBlocks(), body.mu());
        assertEquals(3, body.dangerRating());
    }

    @Test
    void shipsInAtmosphereFlagDefaultsToAllowedAndHonorsExplicitFalse() {
        var inherited = DimensionSpaceSettings.parse(new JsonObject(), new JsonObject());
        assertTrue(inherited.allowShipsInAtmosphereOrDefault());

        JsonObject flags = new JsonObject();
        flags.addProperty("allow_ships_in_atmosphere", false);
        var restricted = DimensionSpaceSettings.parse(new JsonObject(), flags);
        assertFalse(restricted.allowShipsInAtmosphereOrDefault());
    }

    @Test
    void kiSpacewalkFlagDefaultsToDisabledAndHonorsExplicitSetting() {
        var inherited = DimensionSpaceSettings.parse(new JsonObject(), new JsonObject());
        assertFalse(inherited.allowKiSpacewalkOrDefault());

        JsonObject flags = new JsonObject();
        flags.addProperty("allow_ki_spacewalk", true);
        var enabled = DimensionSpaceSettings.parse(new JsonObject(), flags);
        assertTrue(enabled.allowKiSpacewalkOrDefault());

        flags.addProperty("allow_ki_spacewalk", false);
        var disabled = DimensionSpaceSettings.parse(new JsonObject(), flags);
        assertFalse(disabled.allowKiSpacewalkOrDefault());
    }

    @Test void absentSidecarBandInheritsTheGlobalHandoffBand() throws Exception {
        Files.writeString(directory.resolve("demo.world.yml"), "charted_as: earth\n");
        var base = Map.of("xenopixelsmod:earth", JsonParser.parseString("{}").getAsJsonObject());
        var result = DimensionOverrides.load(directory, base);
        assertTrue(result.errors().isEmpty(), result.errors().toString());
        assertEquals(128.0, result.settings().get("demo:world").bandThicknessOr(128.0));
        assertTrue(result.settings().get("demo:world").enabledOrDefault(true));
        assertFalse(result.settings().get("demo:world").reentryPlasmaShellEnabled());
    }

    @Test void unsupportedReentryFxIsRejected() throws Exception {
        Files.writeString(directory.resolve("demo.world.yml"), """
                dimension: demo:world
                flags:
                  reentry_fx: plasma_ring
                """);
        var result = DimensionOverrides.load(directory, Map.of());
        assertFalse(result.errors().isEmpty());
        assertTrue(result.errors().stream().anyMatch(error -> error.contains("flags.reentry_fx")));
        assertFalse(result.settings().containsKey("demo:world"));
    }

    @Test void implicitSidecarCanDeriveItsBodyIdFromTheDimensionFilename() throws Exception {
        Files.writeString(directory.resolve("demo.pocket.yml"), """
                implicit_body:
                  radius_blocks: 32
                  kind: pocket
                """);
        var result = DimensionOverrides.load(directory, Map.of());
        assertTrue(result.errors().isEmpty(), result.errors().toString());
        String id = "xenopixelsmod:dimension/demo/pocket";
        assertEquals("demo:pocket", ChartDefinition.parse(id, result.definitions().get(id)).dimensionId());
    }

    @Test void invalidSpaceEnumsAreReportedInsteadOfSilentlyIgnored() throws Exception {
        Files.writeString(directory.resolve("demo.world.yml"), """
                charted_as: earth
                space:
                  destructible: sometimes
                """);
        var base = Map.of("xenopixelsmod:earth", JsonParser.parseString("{}").getAsJsonObject());
        var result = DimensionOverrides.load(directory, base);
        assertEquals(1, result.errors().size());
        assertTrue(result.errors().getFirst().contains("destructible"));
        assertFalse(result.settings().containsKey("demo:world"));
    }

    @Test void disabledSidecarOverridesDefaultEnabledState() throws Exception {
        Files.writeString(directory.resolve("demo.world.yml"), "charted_as: earth\nspace:\n  enabled: false\n");
        var base = Map.of("xenopixelsmod:earth", JsonParser.parseString("{}").getAsJsonObject());
        var result = DimensionOverrides.load(directory, base);
        assertTrue(result.errors().isEmpty(), result.errors().toString());
        assertFalse(result.settings().get("demo:world").enabledOrDefault(true));
    }
}
