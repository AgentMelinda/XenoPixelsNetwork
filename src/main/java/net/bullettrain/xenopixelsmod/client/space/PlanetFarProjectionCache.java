package net.bullettrain.xenopixelsmod.client.space;

import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.function.Consumer;

/**
 * Bounded registry of downsampled far-projection maps, keyed by the body's near HD map so every body keeps
 * its own far texture. At capacity the least recently drawn entry that was not drawn this frame is released;
 * if every entry was drawn this frame the caller gets null and draws its legacy fallback instead, so a sky
 * with more eligible bodies than the cap never reloads textures every frame.
 */
final class PlanetFarProjectionCache {
    /**
     * One slot for every body a snapshot can carry (the wire format truncates at this count), so every
     * projected body gets its own map; the null fallback below is only a guard if that bound ever changes.
     */
    static final int MAX_TEXTURES = net.bullettrain.xenopixelsmod.space.SpaceSnapshot.MAX_BODIES;

    private static final class Slot {
        final ResourceLocation key;
        long lastFrame;
        Slot(ResourceLocation key, long lastFrame) { this.key = key; this.lastFrame = lastFrame; }
    }

    private final int capacity;
    private final LinkedHashMap<ResourceLocation, Slot> slots = new LinkedHashMap<>(16, 0.75f, true);

    PlanetFarProjectionCache(int capacity) {
        if (capacity <= 0) throw new IllegalArgumentException("capacity must be positive");
        this.capacity = capacity;
    }

    /** Texture-manager key of the far copy of {@code source}; never equal to the near map's own key. */
    static ResourceLocation farKey(ResourceLocation source) {
        return ResourceLocation.fromNamespaceAndPath(source.getNamespace(), "dynamic/far_projection/" + source.getPath());
    }

    /**
     * The far key for {@code source}, registering it through {@code create} on first use. Returns null when the
     * cache is full of entries already drawn during {@code frame}.
     */
    ResourceLocation acquire(ResourceLocation source, long frame,
                             Consumer<ResourceLocation> create, Consumer<ResourceLocation> release) {
        Slot slot = slots.get(source);
        if (slot != null) {
            slot.lastFrame = frame;
            return slot.key;
        }
        if (slots.size() >= capacity) {
            ResourceLocation stale = null;
            for (var entry : slots.entrySet()) {
                if (entry.getValue().lastFrame < frame) { stale = entry.getKey(); break; }
            }
            if (stale == null) return null;
            release.accept(slots.remove(stale).key);
        }
        ResourceLocation key = farKey(source);
        create.accept(key);
        slots.put(source, new Slot(key, frame));
        return key;
    }

    void clear(Consumer<ResourceLocation> release) {
        for (Slot slot : slots.values()) release.accept(slot.key);
        slots.clear();
    }

    int size() { return slots.size(); }
}
