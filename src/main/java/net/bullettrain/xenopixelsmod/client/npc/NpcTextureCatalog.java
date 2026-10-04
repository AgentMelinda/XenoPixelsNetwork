package net.bullettrain.xenopixelsmod.client.npc;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

import java.util.List;

/** Effective client textures, including vanilla, mods and enabled resource packs. */
final class NpcTextureCatalog {
    private NpcTextureCatalog() {
    }

    static List<ResourceLocation> loadedTextures(ResourceManager manager) {
        if (manager == null) return List.of();
        return manager.listResources("textures", id -> id.getPath().endsWith(".png"))
                .keySet().stream()
                .distinct()
                .sorted(java.util.Comparator.comparing(ResourceLocation::toString))
                .toList();
    }

    static String category(ResourceLocation id) {
        String path = id.getPath();
        if (path.startsWith("textures/entity/")) return "Entity";
        if (path.startsWith("textures/block/")) return "Block";
        if (path.startsWith("textures/item/")) return "Item";
        if (path.startsWith("textures/gui/")) return "GUI";
        return "Other";
    }
}
