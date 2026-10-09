package net.bullettrain.xenopixelsmod.client.npc;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Effective client textures, including vanilla, mods and enabled resource packs. */
final class NpcTextureCatalog {
    private NpcTextureCatalog() {
    }

    static List<ResourceLocation> loadedTextures(ResourceManager manager) {
        if (manager == null) {
            return List.of();
        }
        return manager.listResources("textures", id -> id.getPath().endsWith(".png"))
                .keySet().stream()
                .distinct()
                .sorted(java.util.Comparator.comparing(ResourceLocation::toString))
                .toList();
    }

    /**
     * GeckoLib model choices from every loaded namespace.
     *
     * <p>Lists every {@code .geo.json} under each pack's {@code geo} folder (not DMZ-only),
     * converted to editor modelId form {@code namespace:relative/path}. Texture-backed
     * entries that already resolve a geo stay included as additive choices.
     */
    static List<ResourceLocation> loadedModels(ResourceManager manager) {
        if (manager == null) {
            return List.of();
        }
        Set<ResourceLocation> out = new LinkedHashSet<>();
        for (ResourceLocation geo : manager.listResources("geo", id -> id.getPath().endsWith(".geo.json")).keySet()) {
            ResourceLocation modelId = geoToModelId(geo);
            if (modelId != null) {
                out.add(modelId);
            }
        }
        for (ResourceLocation texture : loadedTextures(manager)) {
            if (XenoNpcGeoModel.findModelResource(texture,
                    resource -> manager.getResource(resource).isPresent()) != null) {
                out.add(texture);
            }
        }
        return out.stream().sorted(java.util.Comparator.comparing(ResourceLocation::toString)).toList();
    }

    /** {@code namespace:geo/entity/ogre.geo.json} → {@code namespace:entity/ogre}. */
    static ResourceLocation geoToModelId(ResourceLocation geo) {
        if (geo == null) {
            return null;
        }
        String path = geo.getPath();
        if (!path.startsWith("geo/") || !path.endsWith(".geo.json")) {
            return geo;
        }
        String mid = path.substring("geo/".length(), path.length() - ".geo.json".length());
        if (mid.isBlank()) {
            return null;
        }
        return ResourceLocation.fromNamespaceAndPath(geo.getNamespace(), mid);
    }

    static String category(ResourceLocation id) {
        String path = id.getPath();
        if (path.startsWith("textures/entity/") || path.startsWith("entity/")) {
            return "Entity";
        }
        if (path.startsWith("textures/block/") || path.startsWith("block/")) {
            return "Block";
        }
        if (path.startsWith("textures/item/") || path.startsWith("item/")) {
            return "Item";
        }
        if (path.startsWith("textures/gui/") || path.startsWith("gui/")) {
            return "GUI";
        }
        if (path.startsWith("geo/") || !path.startsWith("textures/")) {
            return "GeckoLib";
        }
        return "Other";
    }
}
