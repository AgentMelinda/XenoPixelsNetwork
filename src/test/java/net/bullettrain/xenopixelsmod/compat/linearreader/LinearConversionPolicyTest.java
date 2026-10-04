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
        settings.allowOutsideYawpClaims = true;
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

    @Test void safeDefaultRequiresWholeFileClaimAndDimensionPermission() throws IOException {
        LinearConversionPolicy.start(temporary.resolve("config.json"), temporary);
        Path file = temporary.resolve("region/r.-1.2.mca");
        assertFalse(LinearConversionPolicy.allowsConversion(file)); // not ready / YAWP absent
        assertFalse(LinearConversionPolicy.allowsConversion(temporary.resolve("unknown-layout/r.0.0.mca")));
        LinearClaimCoverage.publish(LinearClaimCoverage.generation(), java.util.Map.of("minecraft:overworld",
                new LinearClaimCoverage.Dimension(-64, 319, java.util.List.of(
                        new LinearClaimCoverage.Box(-512, -64, 1024, -1, 319, 1535)))));
        assertTrue(LinearConversionPolicy.allowsConversion(file));
        assertFalse(LinearConversionPolicy.allowsConversion(temporary.resolve("entities/r.-2.2.mca")));
        assertTrue(LinearConversionPolicy.allowsConversion(temporary.resolve("poi/r.-1.2.mca")));
        var settings = LinearConversionPolicy.settings();
        settings.dimensions.put("minecraft:overworld", false);
        LinearConversionPolicy.save(settings);
        assertFalse(LinearConversionPolicy.allowsConversion(file));
        LinearClaimCoverage.invalidate();
        assertFalse(LinearConversionPolicy.allowsConversion(file));
    }

    @Test void outsideOverridesPersistAndInvalidNullDoesNotReplacePolicy() throws IOException {
        Path config = temporary.resolve("config.json");
        LinearConversionPolicy.start(config, temporary);
        var settings = LinearConversionPolicy.settings();
        settings.outsideYawpClaimsDimensions.put("minecraft:overworld", true);
        LinearConversionPolicy.save(settings);
        LinearConversionPolicy.reload();
        assertTrue(LinearConversionPolicy.allowsConversion(temporary.resolve("region/r.2.0.mca")));
        assertFalse(LinearConversionPolicy.allowsConversion(temporary.resolve("DIM1/region/r.2.0.mca")));
        Files.writeString(config, "{\"outsideYawpClaimsDimensions\": {\"minecraft:overworld\": null}}");
        assertThrows(IOException.class, LinearConversionPolicy::reload);
        assertTrue(LinearConversionPolicy.settings().allowsOutsideClaims("minecraft:overworld"));
    }

    @Test void nativePinWaitsForAnInFlightConversionBeforeChangingTheFormatDecision() throws Exception {
        Path file = temporary.resolve("region/r.0.0.mca");
        LinearConversionPolicy.start(temporary.resolve("config.json"), temporary);
        var lock = LinearConversionPolicy.regionLock(file);
        lock.lock();
        var started = new java.util.concurrent.CountDownLatch(1);
        var pinned = java.util.concurrent.CompletableFuture.runAsync(() -> {
            started.countDown();
            LinearConversionPolicy.openedAnvil(file);
        });
        try {
            assertTrue(started.await(5, java.util.concurrent.TimeUnit.SECONDS));
            assertFalse(LinearConversionPolicy.retainAnvil(file), "A converter still owns this file");
        } finally { lock.unlock(); }
        pinned.get(5, java.util.concurrent.TimeUnit.SECONDS);
        assertTrue(LinearConversionPolicy.retainAnvil(file));
    }
}
