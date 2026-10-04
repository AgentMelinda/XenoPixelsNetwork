package net.bullettrain.xenopixelsmod.client.npc;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.stream.StreamSupport;

final class NpcSoundCatalog {
    private NpcSoundCatalog() {
    }

    static List<String> registryIds() {
        return sortedIds(BuiltInRegistries.SOUND_EVENT.keySet());
    }

    static List<String> sortedIds(Iterable<ResourceLocation> ids) {
        return StreamSupport.stream(ids.spliterator(), false)
                .map(ResourceLocation::toString)
                .distinct()
                .sorted()
                .toList();
    }
}
