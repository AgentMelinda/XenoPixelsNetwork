package net.bullettrain.xenopixelsmod.aero;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GuidanceConfigPersistTest {
    @TempDir
    Path temp;

    @AfterEach
    void restoreDefault() {
        GuidanceConfig.resetForTest();
    }

    @Test
    void setPersistsAndLoadRoundTrips() throws Exception {
        Path file = temp.resolve("xenopixelsmod-guidance.json");
        GuidanceConfig.usePathForTest(file);
        assertEquals(GuidanceVersion.V2, GuidanceConfig.set(GuidanceVersion.V2));
        assertTrue(Files.exists(file));
        String json = Files.readString(file);
        assertTrue(json.contains("v2"));

        GuidanceConfig.set(GuidanceVersion.V1);
        assertEquals(GuidanceVersion.V1, GuidanceConfig.version());

        GuidanceConfig.load();
        assertEquals(GuidanceVersion.V1, GuidanceConfig.version());

        Files.writeString(file, "{\n  \"version\": \"v3\"\n}\n");
        GuidanceConfig.load();
        // 2026-09-28: V3 is no longer reserved; a stored v3 loads as V3.
        assertEquals(GuidanceVersion.V3, GuidanceConfig.version());
    }
}
