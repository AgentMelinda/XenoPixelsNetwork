package net.bullettrain.xenopixelsmod.dmz.race;

import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;

/**
 * Folder resource pack that maps authored body PNGs into the {@code dragonminez} namespace so
 * {@code TextureCounter} and {@code SkinGathererProvider} see them.
 *
 * <p>MC 1.21.1 {@code pack_format} is 34. Clients on a dedicated server still need this folder
 * (or a copy of the race catalog) locally — same contract as DMZ custom race configs.
 */
public final class RaceAssetPack {
    public static final String PACK_FORMAT = "34";
    public static final String PACK_ID = "xenopixels_race_catalogs";

    private RaceAssetPack() {
    }

    public static Path packRoot() {
        return FMLPaths.CONFIGDIR.get().resolve("xenopixelsmod").resolve("race_assets");
    }

    public static Path packRoot(Path configDir) {
        return configDir.resolve("xenopixelsmod").resolve("race_assets");
    }

    public static void ensureMcmeta(Path packRoot) throws IOException {
        Files.createDirectories(packRoot);
        Path mcmeta = packRoot.resolve("pack.mcmeta");
        String json = "{\n"
                + "  \"pack\": {\n"
                + "    \"description\": \"XenoPixels race catalogs\",\n"
                + "    \"pack_format\": " + PACK_FORMAT + "\n"
                + "  }\n"
                + "}\n";
        Files.writeString(mcmeta, json, StandardCharsets.UTF_8);
    }

    public static void sync(Path dmzConfigRoot, RaceAppearanceCatalog catalog) throws IOException {
        Objects.requireNonNull(catalog, "catalog");
        Path configDir = dmzConfigRoot == null ? FMLPaths.CONFIGDIR.get() : dmzConfigRoot.getParent();
        if (configDir == null) {
            configDir = FMLPaths.CONFIGDIR.get();
        }
        Path pack = packRoot(configDir);
        ensureMcmeta(pack);
        for (RaceAppearanceCatalog.BodyType body : catalog.bodies()) {
            Path src = catalog.bodyPng(dmzConfigRoot, body);
            if (!Files.isRegularFile(src)) {
                continue;
            }
            Path dest = pack.resolve("assets/dragonminez/" + body.texturePath()).normalize();
            if (!dest.toAbsolutePath().startsWith(pack.toAbsolutePath().normalize())) {
                throw new IOException("Body texture path escapes race asset pack");
            }
            Files.createDirectories(dest.getParent());
            Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
