package net.bullettrain.xenopixelsmod.compat.linearreader;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class LinearConversionPolicyTest {
    @TempDir Path temporary;
    @AfterEach void stop() { LinearConversionPolicy.stop(); }

    @Test void recognizesEachStorageKindAndVanillaDimensionLayout() {
        for (String kind : new String[]{"region", "entities", "poi"}) {
            assertEquals("minecraft:overworld", dimension(kind));
            assertEquals("minecraft:the_nether", dimension("DIM-1/" + kind));
            assertEquals("minecraft:the_end", dimension("DIM1/" + kind));
            assertEquals("dragonminez:otherworld", dimension("dimensions/dragonminez/otherworld/" + kind));
            assertEquals("example:nested/world", dimension("dimensions/example/nested/world/" + kind + "/r.-1.2.mca"));
        }
        assertNull(dimension("dimensions/dragonminez/otherworld/region-backups/r.0.0.mca"));
        assertNull(LinearConversionPolicy.dimensionAt(temporary, temporary.resolveSibling("another-world/region")));
    }

    private String dimension(String relative) { return LinearConversionPolicy.dimensionAt(temporary, temporary.resolve(relative)); }

    @Test void configAndCommandsPersistIndependentOverridesAndDefaults() throws IOException {
        Path file = temporary.resolve("config.json");
        LinearConversionPolicy.start(file, temporary.resolve("world"));
        var settings = LinearConversionPolicy.settings();
        settings.dimensions.put("dragonminez:otherworld", false);
        LinearConversionPolicy.save(settings);
        assertFalse(LinearConversionPolicy.allowsConversion(temporary.resolve("world/dimensions/dragonminez/otherworld/entities/r.0.0.mca")));
        assertTrue(LinearConversionPolicy.allowsConversion(temporary.resolve("world/region/r.0.0.mca")));
        LinearConversionPolicy.start(file, temporary.resolve("world"));
        assertFalse(LinearConversionPolicy.settings().allows("dragonminez:otherworld"));
        settings = LinearConversionPolicy.settings();
        settings.defaultConversionAllowed = false;
        settings.dimensions.put("minecraft:overworld", true);
        LinearConversionPolicy.save(settings);
        assertTrue(LinearConversionPolicy.settings().allows("minecraft:overworld"));
        assertFalse(LinearConversionPolicy.settings().allows("minecraft:the_end"));
        settings.enabled = false;
        LinearConversionPolicy.save(settings);
        assertTrue(LinearConversionPolicy.settings().allows("dragonminez:otherworld"));
    }

    @Test void invalidReloadRetainsLivePolicyAndDoesNotOverwriteBadFile() throws IOException {
        Path file = temporary.resolve("config.json");
        LinearConversionPolicy.start(file, temporary);
        var settings = LinearConversionPolicy.settings();
        settings.defaultConversionAllowed = false;
        LinearConversionPolicy.save(settings);
        Files.writeString(file, "{\"dimensions\": {\"../world\": false}}");
        assertThrows(IOException.class, LinearConversionPolicy::reload);
        assertFalse(LinearConversionPolicy.settings().defaultConversionAllowed);
        assertTrue(Files.readString(file).contains("../world"));
        assertThrows(IllegalArgumentException.class, () -> LinearConversionPolicy.validateDimension("otherworld"));
    }

    @Test void openedAnvilRegionStaysProtectedUntilRestartEvenWhenPolicyIsDisabled() throws IOException {
        Path file = temporary.resolve("config.json");
        LinearConversionPolicy.start(file, temporary);
        Path region = LinearConversionPolicy.regionPath(temporary.resolve("region"), -2, 3);
        LinearConversionPolicy.openedAnvil(region);
        assertFalse(LinearConversionPolicy.allowsConversion(region));
        var settings = LinearConversionPolicy.settings();
        settings.enabled = false;
        LinearConversionPolicy.save(settings);
        assertFalse(LinearConversionPolicy.allowsConversion(region));
        assertTrue(LinearConversionPolicy.allowsConversion(temporary.resolve("region/r.0.0.mca")));
        LinearConversionPolicy.start(file, temporary);
        assertTrue(LinearConversionPolicy.allowsConversion(region));
    }
}
