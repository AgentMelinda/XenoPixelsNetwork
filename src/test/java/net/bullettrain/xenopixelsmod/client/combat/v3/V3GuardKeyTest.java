package net.bullettrain.xenopixelsmod.client.combat.v3;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * V3's heavy attack is the right mouse button. The legacy guard binding ({@code bt3_guard})
 * defaults to that same button, so V3 must never read it: on 2026-10-07 doing so raised the guard
 * on every right click, which swallowed the heavy and left DragonMineZ refusing left-click attacks
 * because the fighter was "blocking". V3 guards on the v2 guard key, which has its own button.
 */
class V3GuardKeyTest {
    private static Path root() {
        Path dir = Path.of("").toAbsolutePath();
        while (dir != null && !Files.isDirectory(dir.resolve("src/main/java/net/bullettrain"))) dir = dir.getParent();
        assertNotNull(dir, "repository root");
        return dir;
    }

    private static String source(String relative) throws IOException {
        return Files.readString(root().resolve("src/main/java/net/bullettrain/xenopixelsmod").resolve(relative));
    }

    @Test void v3InputNeverReadsTheLegacyGuardBinding() throws IOException {
        String layer = source("client/combat/v3/V3InputLayer.java");
        assertFalse(layer.contains("Bt3CombatClient.GUARD"), "V3InputLayer reads the legacy guard key");
        assertTrue(layer.contains("V2Keys.GUARD"), "V3 guard and grab chord use the v2 guard key");
    }

    @Test void v3BranchGuardsOnTheV2GuardKey() throws IOException {
        String client = source("client/combat/Bt3CombatClient.java");
        int branch = client.indexOf("if (XenoServerClientState.v3Controller()) {\r\n                if (!v3WasActive)");
        if (branch < 0) branch = client.indexOf("if (XenoServerClientState.v3Controller()) {\n                if (!v3WasActive)");
        assertTrue(branch >= 0, "V3 branch of the client tick");
        String body = client.substring(branch, client.indexOf("V3InputLayer.tick(mc);", branch));
        assertFalse(body.contains("tickGuard(mc);"), "the V3 branch ticks the legacy guard key");
        assertTrue(body.contains("V2Keys.GUARD"), "the V3 branch guards on the v2 guard key");
    }

    @Test void grabPromptNamesTheKeyV3ActuallyReads() throws IOException {
        String overlay = source("client/combat/v2/CombatPromptOverlay.java");
        assertTrue(overlay.contains("case GRAB -> key(V2Keys.GUARD)"), "grab prompt key");
    }
}
