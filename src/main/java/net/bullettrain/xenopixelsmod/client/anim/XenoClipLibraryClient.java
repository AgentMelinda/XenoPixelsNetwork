package net.bullettrain.xenopixelsmod.client.anim;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.anim.XenoClipLibrary;
import net.bullettrain.xenopixelsmod.client.combat.anim.Bt3AnimationBinding;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Receives the server's clip library and makes it playable on this client.
 *
 * <p>Server clips land in their own folder under the clip directory rather than beside the player's
 * own work: they are not the player's files, they are replaced wholesale on every join, and keeping
 * them apart means a server push can never quietly overwrite something the player authored.
 *
 * <p>They do take priority when a name appears in both, because on a server everyone has to be
 * watching the same animation - and that is logged, so a player whose local clip is being shadowed
 * can find out why.
 */
public final class XenoClipLibraryClient {

    private XenoClipLibraryClient() {}

    public static Path dir() {
        return XenoAnimClip.dir().resolve("server");
    }

    /** Replaces the local copy of the server library with {@code bundle}, then re-bakes. */
    public static void apply(String bundle) {
        Map<String, String> clips = XenoClipLibrary.unbundle(bundle);
        try {
            Path dir = dir();
            Files.createDirectories(dir);
            clearExisting(dir);
            for (Map.Entry<String, String> entry : clips.entrySet()) {
                Files.writeString(dir.resolve(entry.getKey() + ".animation.json"),
                        entry.getValue(), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            XenoPixelsMod.LOGGER.warn("Could not store the server animation library: {}", e.toString());
            return;
        }
        XenoStudioClipCache.reload();
        Bt3AnimationBinding.registerStudioNames();
        XenoPixelsMod.LOGGER.info("Received {} animation clip(s) from the server", clips.size());
    }

    private static void clearExisting(Path dir) throws IOException {
        try (var stream = Files.list(dir)) {
            for (Path file : stream.toList()) {
                if (file.getFileName().toString().endsWith(".animation.json")) {
                    Files.deleteIfExists(file);
                }
            }
        }
    }

    /** Clip names the server supplied, as stored on this client. */
    public static java.util.List<String> names() {
        Path dir = dir();
        if (!Files.isDirectory(dir)) return java.util.List.of();
        try (var stream = Files.list(dir)) {
            return stream.map(p -> p.getFileName().toString())
                    .filter(n -> n.endsWith(".animation.json"))
                    .map(n -> n.replace(".animation.json", ""))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            return java.util.List.of();
        }
    }
}
