package net.bullettrain.xenopixelsmod.client.npc.dialog;

import net.bullettrain.xenopixelsmod.RepoRoot;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Why dialogue bubbles could not be clicked.
 *
 * <p>{@code WorldToScreenCache} captures the frame's projection on {@code RenderLevelStageEvent}
 * at {@code AFTER_LEVEL}. {@code DialogueBubbleRenderer} projects its option hit boxes on the same
 * event at the same stage. Two {@code @EventBusSubscriber} classes at equal priority run in
 * class-scan order — arbitrary, and stable enough per install to look like a permanent bug. When
 * the renderer won, the cache was invalid, {@code project} returned null, every option came back
 * unusable, and the bubbles drew perfectly while refusing every click.
 *
 * <p>Found in a running game; nothing in the suite could have caught it, because both classes were
 * individually correct.
 */
class BubbleClickOrderingTest {

    private static Path cacheFile() throws IOException {
        try (Stream<Path> paths = Files.walk(RepoRoot.of("src/main/java"))) {
            return paths.filter(p -> p.getFileName().toString().equals("WorldToScreenCache.java"))
                    .findFirst().orElseThrow();
        }
    }

    @Test
    void theProjectionCacheFillsBeforeAnythingReadsIt() throws IOException {
        String cache = Files.readString(cacheFile(), StandardCharsets.UTF_8);
        assertTrue(cache.contains("EventPriority.HIGHEST"),
                "the capture must outrank every consumer of the same event");
    }

    @Test
    void theRendererStillReadsTheCacheRatherThanItsOwnMatrices() throws IOException {
        // Keeping one source of truth for the projection: a second copy in the renderer would
        // drift from the crosshair HUD's and put the hit boxes somewhere the art is not.
        String renderer = Files.readString(
                RepoRoot.of("src/main/java/net/bullettrain/xenopixelsmod/client/npc/dialog",
                        "DialogueBubbleRenderer.java"), StandardCharsets.UTF_8);
        assertTrue(renderer.contains("WorldToScreenCache.project("));
    }

    @Test
    void anUnprojectableOptionIsUnusableRatherThanClickableAtTheOrigin() throws IOException {
        // The fallback matters: a null projection must not collapse to a box at 0,0 that swallows
        // clicks aimed at nothing.
        String renderer = Files.readString(
                RepoRoot.of("src/main/java/net/bullettrain/xenopixelsmod/client/npc/dialog",
                        "DialogueBubbleRenderer.java"), StandardCharsets.UTF_8);
        assertTrue(renderer.contains("return noHit(index);"),
                "a failed projection yields an explicitly unusable hit");
    }
}
